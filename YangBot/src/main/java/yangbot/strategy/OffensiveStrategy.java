package yangbot.strategy;

import rlbot.cppinterop.RLBotDll;
import rlbot.flat.QuickChatSelection;
import yangbot.input.*;
import yangbot.input.interrupt.BallTouchInterrupt;
import yangbot.input.interrupt.InterruptManager;
import yangbot.path.EpicMeshPlanner;
import yangbot.path.builders.SegmentedPath;
import yangbot.strategy.abstraction.*;
import yangbot.strategy.advisor.RotationAdvisor;
import yangbot.strategy.manuever.DodgeManeuver;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.Tuple;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.Line2;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class OffensiveStrategy extends Strategy {

    private Abstraction strikeAbstraction;
    private State state = State.INVALID;
    private RotationAdvisor.Advice lastAdvice = RotationAdvisor.Advice.NO_ADVICE;
    private BallTouchInterrupt ballTouchInterrupt;
    private SegmentedPath drivePath;
    private GetBoostAbstraction boostAbstraction;
    private IdleAbstraction idleAbstraction = new IdleAbstraction();
    private AerialAbstraction aerialAbstraction;

    private boolean planGoForBoost() {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();

        this.boostAbstraction = new GetBoostAbstraction();
        if (!this.boostAbstraction.isViable()) {
            this.boostAbstraction = null;
            return false;
        }

        this.state = State.GET_BOOST;
        RLBotDll.sendQuickChat(car.playerIndex, true, QuickChatSelection.Information_NeedBoost);
        return true;
    }

    private Optional<StrikeInfo> planAerialIntercept(YangBallPrediction ballPrediction, boolean debug) {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final Vector2 ownGoal = new Vector2(0, car.getTeamSign() * RLConstants.goalDistance);
        final Vector2 enemyGoal = new Vector2(0, -car.getTeamSign() * RLConstants.goalDistance);

        float t = DodgeManeuver.max_duration + 0.1f;

        // Find intercept
        do {
            final Optional<YangBallPrediction.YangPredictionFrame> interceptFrameOptional = ballPrediction.getFrameAfterRelativeTime(t);
            if (interceptFrameOptional.isEmpty())
                break;

            final YangBallPrediction.YangPredictionFrame interceptFrame = interceptFrameOptional.get();
            if (interceptFrame.ballData.isInAnyGoal())
                break;

            final Vector3 ballPos = interceptFrame.ballData.position;
            final var carToBall = ballPos.sub(car.position).normalized();
            final var goalToBall = (ballPos.sub(ownGoal.withZ(100)).normalized())
                    .add(enemyGoal.withZ(100).sub(ballPos).normalized())
                    .normalized();
            final Vector3 targetOffset = carToBall.add(goalToBall).normalized();
            final Vector3 targetPos = ballPos.sub(targetOffset.mul(BallData.COLLISION_RADIUS + car.hitbox.getForwardExtent() * 0.95f - 10));

            // We should arrive at the ball a bit early to catch it
            boolean isPossible = AerialAbstraction.isViable(car, targetPos, interceptFrame.absoluteTime);
            if (isPossible) {
                return Optional.of(new StrikeInfo(interceptFrame.absoluteTime, StrikeInfo.StrikeType.AERIAL, (o) -> {
                    this.aerialAbstraction = new AerialAbstraction(targetPos);
                    this.aerialAbstraction.targetOrientPos = ballPos;
                    this.aerialAbstraction.arrivalTime = interceptFrame.absoluteTime;
                    this.state = State.AERIAL;
                }));
            }

            t = interceptFrame.relativeTime;
            t += RLConstants.simulationTickFrequency * 4; // 15 ticks / s
        } while (t < ballPrediction.relativeTimeOfLastFrame());

        return Optional.empty();
    }

    private Optional<StrikeInfo> planGroundStrike(YangBallPrediction strikePrediction) {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        int teamSign = car.getTeamSign();
        final Vector2 enemyGoal = new Vector2(0, -teamSign * (RLConstants.goalDistance + 1000));
        float maxT = strikePrediction.relativeTimeOfLastFrame() - RLConstants.simulationTickFrequency * 2;
        float t = strikePrediction.firstFrame().relativeTime;

        final float goalCenterToPostDistance = RLConstants.goalCenterToPost - BallData.COLLISION_RADIUS * 2 - 50 /* tolerance */;
        assert goalCenterToPostDistance > 100; // Could fail with smaller goals
        assert enemyGoal.x == 0; // Could fail with custom goals
        final Line2 enemyGoalLine = new Line2(enemyGoal.sub(goalCenterToPostDistance, 0), enemyGoal.add(goalCenterToPostDistance, 0));

        final boolean verboseDebug = false;

        if (verboseDebug)
            System.out.println("##### start path finder");

        // Path finder
        while (t < maxT) {
            final Optional<YangBallPrediction.YangPredictionFrame> interceptFrameOptional = strikePrediction.getFrameAtRelativeTime(t);
            if (interceptFrameOptional.isEmpty())
                break;
            final var interceptFrame = interceptFrameOptional.get();

            t = interceptFrame.relativeTime;

            if (t > 1.5f) // Speed it up, not as important
                t += RLConstants.simulationTickFrequency * 4; // 15hz
            else // default
                t += RLConstants.simulationTickFrequency * 2; // 30hz

            if (interceptFrame.ballData.isInAnyGoal())
                break;

            if (interceptFrame.ballData.velocity.magnitude() > 4000)
                continue;

            final Vector3 targetBallPos = interceptFrame.ballData.position;

            {
                float zDiff = targetBallPos.z - 0.7f * BallData.COLLISION_RADIUS - car.position.z;
                float jumpDelay;
                if (zDiff < 5)
                    jumpDelay = 0.25f;
                else
                    jumpDelay = MathUtils.clip(CarData.getJumpTimeForHeight(zDiff, gameData.getGravity().z) + 0.05f, 0.25f, 1f);

                if (interceptFrame.relativeTime < jumpDelay)
                    continue;
            }

            final Vector2 closestScoringPosition = enemyGoalLine.closestPointOnLine(targetBallPos.flatten());
            final Vector3 ballTargetToGoalTarget = closestScoringPosition.sub(targetBallPos.flatten()).normalized().withZ(0);
            final Vector3 carToBall = targetBallPos.sub(car.position).withZ(0).normalized();

            Vector3 ballHitTarget = targetBallPos.sub(ballTargetToGoalTarget.mul(BallData.COLLISION_RADIUS + car.hitbox.getForwardExtent()));

            //Vector3 ballHitTarget = targetBallPos.sub(carToBall.mul(BallData.COLLISION_RADIUS + car.hitbox.getDiagonalExtent()));
            ballHitTarget = ballHitTarget.withZ(RLConstants.carElevation);

            final Vector3 carToDriveTarget = ballHitTarget.sub(car.position).normalized();
            final Vector3 endTangent = carToDriveTarget.mul(4).add(ballTargetToGoalTarget).withZ(0).normalized();

            var currentPathOptional = new EpicMeshPlanner()
                    .withStart(car)
                    .withEnd(ballHitTarget, endTangent)
                    .withArrivalTime(interceptFrame.absoluteTime)
                    .withArrivalSpeed(2300)
                    .allowFullSend(car.boost > 20)
                    .allowOptimize(false)
                    .withCreationStrategy(EpicMeshPlanner.PathCreationStrategy.YANGPATH)
                    .plan();

            if (currentPathOptional.isEmpty())
                continue;

            var currentPath = currentPathOptional.get();

            if (ballTargetToGoalTarget.dot(currentPath.getEndTangent()) < 0)
                continue;

            // Check if path is valid
            {
                if (currentPath.getTotalTimeEstimate() <= interceptFrame.relativeTime) {
                    if (verboseDebug)
                        System.out.println("##### end path finder at t=" + interceptFrame.relativeTime + " with path total=" + currentPath.getTotalTimeEstimate());
                    return Optional.of(new StrikeInfo(interceptFrame.absoluteTime, StrikeInfo.StrikeType.DODGE, (o) -> {
                        this.state = State.FOLLOW_PATH_STRIKE;
                        var dodgeStrikeAbstraction = new DriveDodgeStrikeAbstraction(currentPath);

                        dodgeStrikeAbstraction.arrivalTime = interceptFrame.absoluteTime;
                        dodgeStrikeAbstraction.originalTargetBallPos = targetBallPos;

                        float zDiff = targetBallPos.z - 0.3f * BallData.COLLISION_RADIUS - car.position.z;
                        if (zDiff < 5)
                            dodgeStrikeAbstraction.jumpBeforeStrikeDelay = 0.25f;
                        else
                            dodgeStrikeAbstraction.jumpBeforeStrikeDelay = MathUtils.clip(CarData.getJumpTimeForHeight(zDiff, gameData.getGravity().z) + 0.05f, 0.25f, 1f);

                        System.out.println("Setting jumpBeforeStrikeDelay=" + dodgeStrikeAbstraction.jumpBeforeStrikeDelay + " zDiff=" + zDiff + " ballTargetZ=" + targetBallPos.z + " carZ=" + car.position.z);

                        dodgeStrikeAbstraction.strikeAbstraction.optimizer.maxJumpDelay = Math.max(0.6f, dodgeStrikeAbstraction.jumpBeforeStrikeDelay + 0.1f);
                        dodgeStrikeAbstraction.strikeAbstraction.optimizer.jumpDelayStep = Math.max(0.1f, (dodgeStrikeAbstraction.strikeAbstraction.optimizer.maxJumpDelay - /*duration*/ 0.2f) / 5 - 0.02f);
                        this.strikeAbstraction = dodgeStrikeAbstraction;
                    }));
                } else if (verboseDebug) {
                    String s = "continue path finder at t=" + interceptFrame.relativeTime + " with path total=" + currentPath.getTotalTimeEstimate() + " ";
                    for (var seg : currentPath.getSegmentList()) {
                        var est = seg.getTimeEstimate();
                        s += seg.getClass().getSimpleName() + " est=" + est + " ";
                    }

                    System.out.println(s);
                }


            }
        }
        if (verboseDebug)
            System.out.println("##### end path finder");
        return Optional.empty();
    }

    private Optional<StrikeInfo> planChipStrike(YangBallPrediction strikePrediction) {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        int teamSign = car.getTeamSign();
        final Vector2 enemyGoal = new Vector2(0, -teamSign * (RLConstants.goalDistance + 1000));
        float maxT = strikePrediction.relativeTimeOfLastFrame();
        float t = strikePrediction.firstFrame().relativeTime;

        final float goalCenterToPostDistance = RLConstants.goalCenterToPost - BallData.COLLISION_RADIUS * 2 - 50 /* tolerance */;
        assert goalCenterToPostDistance > 100; // Could fail with smaller goals
        assert enemyGoal.x == 0; // Could fail with custom goals
        final Line2 enemyGoalLine = new Line2(enemyGoal.sub(goalCenterToPostDistance, 0), enemyGoal.add(goalCenterToPostDistance, 0));

        // Path finder
        while (t < maxT) {
            final Optional<YangBallPrediction.YangPredictionFrame> interceptFrameOptional = strikePrediction.getFrameAtRelativeTime(t);
            if (interceptFrameOptional.isEmpty())
                break;

            if (t > 1.5f) // Speed it up, not as important
                t += RLConstants.simulationTickFrequency * 4; // 15hz
            else // default
                t += RLConstants.simulationTickFrequency * 2; // 30hz

            final var interceptFrame = interceptFrameOptional.get();
            if (interceptFrame.ballData.isInAnyGoal())
                break;

            var ballVel = interceptFrame.ballData.velocity;

            if (ballVel.z < -10)
                continue;

            if (ballVel.magnitude() > 4000)
                continue;

            final Vector3 targetBallPos = interceptFrame.ballData.position;

            final Vector2 closestScoringPosition = enemyGoalLine.closestPointOnLine(targetBallPos.flatten());
            final Vector3 ballTargetToGoalTarget = closestScoringPosition.sub(targetBallPos.flatten()).normalized().withZ(0);

            var ballExtension = DriveChipAbstraction.getBallExtension(targetBallPos.z);
            if (ballExtension == -1)
                continue;

            Vector3 ballHitTarget = targetBallPos.sub(ballTargetToGoalTarget.mul(ballExtension + car.hitbox.getForwardExtent()));
            ballHitTarget = ballHitTarget.withZ(RLConstants.carElevation);

            final Vector3 carToDriveTarget = ballHitTarget.sub(car.position).normalized();
            final Vector3 endTangent = carToDriveTarget.mul(4).add(ballTargetToGoalTarget).withZ(0).normalized();

            var currentPathOptional = new EpicMeshPlanner()
                    .withStart(car)
                    .withEnd(ballHitTarget, endTangent)
                    .withArrivalTime(interceptFrame.absoluteTime)
                    .withArrivalSpeed(2300)
                    .allowFullSend(car.boost > 20)
                    .allowOptimize(car.boost < 30)
                    .withCreationStrategy(EpicMeshPlanner.PathCreationStrategy.YANGPATH)
                    .plan();

            if (currentPathOptional.isEmpty())
                continue;

            var currentPath = currentPathOptional.get();

            if (ballTargetToGoalTarget.dot(currentPath.getEndTangent()) < 0)
                continue;

            final Vector2 hitTangent = targetBallPos.flatten().sub(ballHitTarget.flatten()).normalized();
            if (hitTangent.dot(currentPath.getEndTangent().flatten().normalized()) < 0.6f)
                continue; // We should be driving at the ball, otherwise the code fails horribly

            var relativeVel = currentPath.getEndTangent().mul(currentPath.getEndSpeed()).sub(interceptFrame.ballData.velocity).flatten();
            if (relativeVel.magnitude() < 1400)
                continue; // We want boomers, not atbas

            // Check if path is valid
            {
                if (currentPath.getTotalTimeEstimate() <= interceptFrame.relativeTime) {
                    return Optional.of(new StrikeInfo(interceptFrame.absoluteTime, StrikeInfo.StrikeType.CHIP, (o) -> {
                        //System.out.println("<< relative vel: "+relativeVel.magnitude() + " endSpeed="+currentPath.getEndSpeed() + " endTang="+currentPath.getEndTangent()+" ballV="+interceptFrame.ballData.velocity);
                        this.state = State.FOLLOW_PATH_STRIKE;
                        var chipStrikeAbstraction = new DriveChipAbstraction(currentPath);

                        chipStrikeAbstraction.arrivalTime = interceptFrame.absoluteTime;
                        chipStrikeAbstraction.originalTargetBallPos = targetBallPos;

                        this.strikeAbstraction = chipStrikeAbstraction;
                    }));
                }
            }
        }
        return Optional.empty();
    }

    private void planStrategyAttack() {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final int teamSign = car.getTeamSign();
        YangBallPrediction ballPrediction = gameData.getBallPrediction();
        //ballPrediction = YangBotJNAInterop.getBallPrediction(gameData.getBallData().makeMutable(), RLConstants.tickRate);

        /*if (DribbleStrategy.isViable()) {
            this.state = State.DRIBBLE;
            return;
        }*/

        // Make sure we don't hit the ball back to our goal

        final float MIN_HEIGHT_AERIAL = 500;
        final float MAX_HEIGHT_AERIAL = RLConstants.arenaHeight - 200;

        List<StrikeInfo> possibleStrikes = new ArrayList<>();

        if (car.boost > 10) {
            var aerialStrikeFrames = YangBallPrediction.from(ballPrediction.getFramesBetweenRelative(0.3f, 3.5f)
                    .stream()
                    .filter((frame) -> Math.signum(frame.ballData.position.y - (car.position.y + car.velocity.y * Math.max(0, frame.relativeTime * 0.6f - 0.1f))) == -teamSign) // Ball is closer to enemy goal than to own
                    .filter((frame) -> (frame.ballData.position.z >= MIN_HEIGHT_AERIAL && frame.ballData.position.z < MAX_HEIGHT_AERIAL)
                            && !frame.ballData.makeMutable().isInAnyGoal())
                    .collect(Collectors.toList()), ballPrediction.tickFrequency);

            var airStrike = this.planAerialIntercept(aerialStrikeFrames, false);
            airStrike.ifPresent(possibleStrikes::add);
        }

        {
            List<YangBallPrediction.YangPredictionFrame> strikeableFrames = ballPrediction.getFramesBetweenRelative(0.25f, 3.5f)
                    .stream()
                    .filter((frame) -> (frame.ballData.position.z <= DriveDodgeStrikeAbstraction.MAX_STRIKE_HEIGHT))
                    .filter((frame) -> Math.signum(frame.ballData.position.y - (car.position.y + car.velocity.y * Math.max(0, frame.relativeTime * 0.6f - 0.1f))) == -teamSign) // Ball is closer to enemy goal than to own
                    .collect(Collectors.toList());

            if (!strikeableFrames.isEmpty()) {
                YangBallPrediction strikePrediction = YangBallPrediction.from(strikeableFrames, RLConstants.tickFrequency);
                var groundStrike = this.planGroundStrike(strikePrediction);
                groundStrike.ifPresent(possibleStrikes::add);
            }
        }

        {
            List<YangBallPrediction.YangPredictionFrame> chipableFrames = ballPrediction.getFramesBetweenRelative(0.25f, 3.5f)
                    .stream()
                    .filter((frame) -> frame.ballData.position.z <= DriveChipAbstraction.MAX_CHIP_HEIGHT && (frame.ballData.velocity.z > 0 || Math.abs(frame.ballData.velocity.z) < 10))
                    .filter((frame) -> Math.signum(frame.ballData.position.y - (car.position.y + car.velocity.y * Math.max(0, frame.relativeTime * 0.6f - 0.1f))) == -teamSign) // Ball is closer to enemy goal than to own
                    .filter(frame -> frame.ballData.position.distance(car.position) > BallData.COLLISION_RADIUS + car.hitbox.getForwardExtent() + 100)
                    .collect(Collectors.toList());

            if (!chipableFrames.isEmpty()) {
                YangBallPrediction strikePrediction = YangBallPrediction.from(chipableFrames, RLConstants.tickFrequency);
                var groundStrike = this.planChipStrike(strikePrediction);
                groundStrike.ifPresent(possibleStrikes::add);
            }
        }


        if (possibleStrikes.isEmpty()) {
            this.state = State.IDLE;
            return;
        }

        assert this.state == State.INVALID;

        var nextStrike = possibleStrikes
                .stream()
                .map(s -> {
                    float val = s.timeAtStrike - car.elapsedSeconds;

                    switch (s.strikeType) {
                        case CHIP:
                            val *= 0.9f;
                            val -= 0.3f;
                            break;
                    }

                    return new Tuple<>(s, val);
                })
                .min(Comparator.comparingDouble(Tuple::getValue))
                .map(Tuple::getKey).get();

        nextStrike.execute();
        //System.out.println("Running "+nextStrike.strikeType.name()+" at state "+ ScenarioUtil.getEncodedGameState(gameData));

        assert this.state != State.INVALID;
    }

    @Override
    protected void planStrategyInternal() {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final int teamSign = car.getTeamSign();
        final ImmutableBallData ball = gameData.getBallData();
        final YangBallPrediction ballPrediction = gameData.getBallPrediction();
        this.state = State.INVALID;
        this.ballTouchInterrupt = InterruptManager.get().getBallTouchInterrupt();
        this.idleAbstraction.targetSpeed = DriveManeuver.max_throttle_speed;

        // Check if ball will go in our goal
        if (Math.signum(ball.position.y) == teamSign || Math.signum(ball.velocity.y) == teamSign) { // our half
            float t = 0;
            while (t < Math.min(3, ballPrediction.relativeTimeOfLastFrame())) {
                var frame = ballPrediction.getFrameAtRelativeTime(t);
                if (frame.isEmpty())
                    break;
                var ballAtFrame = frame.get().ballData;

                if (ballAtFrame.makeMutable().isInOwnGoal(teamSign)) {
                    if (this.checkReset(0.25f))
                        return;
                    break;
                }

                t += 0.25f;
            }
        }

        if (this.checkReset(1.5f))
            return;

        if (!car.hasWheelContact) {
            this.setDone();
            return;
        }

        var rotationAdvice = RotationAdvisor.whatDoIDo(gameData);
        this.lastAdvice = rotationAdvice;
        switch (rotationAdvice) {
            case PREPARE_FOR_PASS:
                //this.idleAbstraction.targetSpeed = 900;
            case IDLE: {
                if (car.position.flatten().distance(ball.position.flatten()) > 2000) {
                    if (this.planGoForBoost())
                        return;
                }
                this.state = State.IDLE;
            }
            return;
            case ATTACK: {
                this.planStrategyAttack();
            }
            return;
            case RETREAT: {
                this.idleAbstraction.forceRetreatTimeout = 0.5f;
                RLBotDll.sendQuickChat(car.playerIndex, true, QuickChatSelection.Information_AllYours);
                this.state = State.IDLE;
            }
            return;
            default:
                System.err.println("Unhandled Advice: " + rotationAdvice);
                this.state = State.IDLE;
                assert false;
        }
        assert false;
    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {
        var oldState = state;
        assert !this.reevaluateStrategy(4f) : "States/Abstractions didn't finish automatically! (" + oldState.name() + ")";
        assert this.state != State.INVALID : "Invalid state! Last advice: " + this.lastAdvice;

        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final AdvancedRenderer renderer = gameData.getAdvancedRenderer();

        final int teamSign = car.getTeamSign();

        switch (this.state) {
            case GET_BOOST: {
                if (this.boostAbstraction.canInterrupt() && this.reevaluateStrategy(this.ballTouchInterrupt))
                    return;

                if (this.boostAbstraction.step(dt, controlsOutput).isDone()) {
                    this.reevaluateStrategy(0);
                    return;
                }

            }
            break;
            case AERIAL: {
                if (this.reevaluateStrategy(3.5f))
                    return; // Aerial shouldn't exceed this duration anyways

                if (this.aerialAbstraction.arrivalTime - car.elapsedSeconds > 0.3f && this.reevaluateStrategy(ballTouchInterrupt))
                    return;

                this.aerialAbstraction.draw(renderer);
                this.aerialAbstraction.step(dt, controlsOutput);
                if (this.aerialAbstraction.isDone() && this.reevaluateStrategy(0f))
                    return;

            }
            break;
            case FOLLOW_PATH_STRIKE: {
                if (this.strikeAbstraction.canInterrupt() && this.reevaluateStrategy(3.5f))
                    return;

                if (this.strikeAbstraction.canInterrupt() && this.reevaluateStrategy(ballTouchInterrupt))
                    return;

                this.strikeAbstraction.step(dt, controlsOutput);

                if (this.strikeAbstraction.isDone() && this.reevaluateStrategy(0))
                    return;

            }
            break;
            case IDLE: {

                if (this.reevaluateStrategy(this.idleAbstraction.canInterrupt() ? 0.1f : 2.5f))
                    return;

                if (this.idleAbstraction.canInterrupt() && this.reevaluateStrategy(ballTouchInterrupt, 0.1f))
                    return;

                this.idleAbstraction.step(dt, controlsOutput);
                if (this.idleAbstraction.isDone() && this.reevaluateStrategy(0))
                    return;

            }
            break;
        }
    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.empty();
    }

    @Override
    public String getAdditionalInformation() {
        return "State: " + this.state + " \nAdvice: " + this.lastAdvice.name();
    }

    enum State {
        IDLE,
        DRIBBLE,
        FOLLOW_PATH_STRIKE,
        GET_BOOST,
        INVALID,
        AERIAL
    }

    private static class StrikeInfo {
        public final float timeAtStrike;
        public final StrikeType strikeType;
        private final Consumer<Object> executor;

        private StrikeInfo(float timeAtStrike, StrikeType strikeType, Consumer<Object> executor) {
            this.timeAtStrike = timeAtStrike;
            this.strikeType = strikeType;
            this.executor = executor;
        }

        public void execute() {
            this.executor.accept(null);
        }

        enum StrikeType {
            AERIAL,
            DODGE,
            CHIP
        }
    }
}
