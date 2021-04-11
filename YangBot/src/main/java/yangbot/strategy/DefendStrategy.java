package yangbot.strategy;

import rlbot.cppinterop.RLBotDll;
import rlbot.flat.QuickChatSelection;
import yangbot.input.*;
import yangbot.input.interrupt.BallTouchInterrupt;
import yangbot.input.interrupt.InterruptManager;
import yangbot.optimizers.graders.DefensiveGrader;
import yangbot.path.EpicMeshPlanner;
import yangbot.strategy.abstraction.*;
import yangbot.strategy.manuever.DodgeManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.Tuple;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.Area2;
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

public class DefendStrategy extends Strategy {

    private State state = State.INVALID;
    private static float xDefendDist = RLConstants.goalCenterToPost + 400;
    private static float yDefendDist = RLConstants.goalDistance * 0.4f;
    private Abstraction strikeAbstraction;
    private BallTouchInterrupt ballTouchInterrupt = null;
    private IdleAbstraction idleAbstraction = new IdleAbstraction();
    private AerialAbstraction aerialAbstraction;
    private GetBoostAbstraction boostAbstraction;

    private boolean planGoForBoost() {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();

        this.boostAbstraction = new GetBoostAbstraction();
        if (!this.boostAbstraction.isViable()) {
            this.boostAbstraction = null;
            return false;
        }

        System.out.println("Getting myself some boost!");
        this.state = State.GET_BOOST;
        RLBotDll.sendQuickChat(car.playerIndex, true, QuickChatSelection.Information_NeedBoost);
        return true;
    }

    private Optional<StrikeInfo> planAerialIntercept(YangBallPrediction ballPrediction, boolean debug) {
        if (ballPrediction.isEmpty())
            return Optional.empty();

        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final Vector2 ownGoal = new Vector2(0, car.getTeamSign() * RLConstants.goalDistance);

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
            final var goalToBall = ballPos.sub(ownGoal.withZ(100)).normalized();
            final Vector3 targetOffset = carToBall.add(goalToBall).normalized();
            final Vector3 targetPos = ballPos.sub(targetOffset.mul(BallData.COLLISION_RADIUS + car.hitbox.getForwardExtent() * 0.95f - 10));

            // We should arrive at the ball a bit early to catch it
            boolean isPossible = AerialAbstraction.isViable(car, targetPos, interceptFrame.absoluteTime);
            if (isPossible) {
                return Optional.of(new StrikeInfo(interceptFrame.absoluteTime, StrikeInfo.StrikeType.AERIAL, (o) -> {
                    this.aerialAbstraction = new AerialAbstraction();
                    this.aerialAbstraction.targetPos = targetPos;
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

    private Optional<StrikeInfo> planGroundIntercept(YangBallPrediction ballPrediction, boolean debug) {
        if (ballPrediction.isEmpty())
            return Optional.empty();

        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final Vector2 ownGoal = new Vector2(0, car.getTeamSign() * RLConstants.goalDistance);

        final Line2 ownGoalLine = new Line2(ownGoal.sub(RLConstants.goalCenterToPost, 0), ownGoal.add(RLConstants.goalCenterToPost, 0));

        float t = 0;

        // Path finder
        while (t < ballPrediction.relativeTimeOfLastFrame()) {
            final Optional<YangBallPrediction.YangPredictionFrame> interceptFrameOptional = ballPrediction.getFrameAtRelativeTime(t);
            if (interceptFrameOptional.isEmpty())
                break;
            final YangBallPrediction.YangPredictionFrame interceptFrame = interceptFrameOptional.get();

            t = interceptFrame.relativeTime;
            t += RLConstants.simulationTickFrequency * 4; // 15 ticks / s

            if (interceptFrame.ballData.isInAnyGoal())
                break;

            if (interceptFrame.ballData.velocity.magnitude() > 4000)
                continue;

            final Vector3 targetPos = interceptFrame.ballData.position;

            {
                float zDiff = targetPos.z - 0.7f * BallData.COLLISION_RADIUS - car.position.z;
                float jumpDelay;
                if (zDiff < 5)
                    jumpDelay = 0.25f;
                else
                    jumpDelay = MathUtils.clip(CarData.getJumpTimeForHeight(zDiff, gameData.getGravity().z) + 0.05f, 0.25f, 1f);

                if (interceptFrame.relativeTime < jumpDelay)
                    continue;
            }

            final Vector3 endTangent = new Vector3(
                    targetPos.flatten().sub(ownGoal).normalized().mul(2)
                            .add(targetPos.flatten().sub(car.position.flatten()).normalized())
                            .normalized()
                    , 0);
            final Vector3 endPos = targetPos.withZ(car.position.z).sub(endTangent.mul(BallData.COLLISION_RADIUS + car.hitbox.getForwardExtent()));

            if (endTangent.flatten().normalized().dot(ownGoal.sub(endPos.flatten())) > 0 /*facing goal*/ && ownGoalLine.getIntersectionPointWithInfOtherLine(new Line2(endPos.flatten(), endPos.add(endTangent).flatten())).isPresent()) {
                continue;
            }

            var pathOptional = new EpicMeshPlanner()
                    .withStart(car)
                    .withEnd(endPos, endTangent)
                    .withArrivalTime(interceptFrame.absoluteTime)
                    .withArrivalSpeed(CarData.MAX_VELOCITY)
                    .allowFullSend(car.boost > 20)
                    .allowOptimize(car.boost < 30)
                    .withCreationStrategy(EpicMeshPlanner.PathCreationStrategy.YANGPATH)
                    .plan();

            if (pathOptional.isEmpty())
                continue;

            var currentPath = pathOptional.get();

            if (currentPath.getEndTangent().dot(targetPos.flatten().sub(ownGoal).normalized().withZ(0)) < -0.5f)
                continue;

            float timeEstimate = currentPath.getTotalTimeEstimate();
            if (timeEstimate <= interceptFrame.relativeTime) {
                return Optional.of(new StrikeInfo(interceptFrame.absoluteTime, StrikeInfo.StrikeType.DODGE, (o) -> {
                    this.state = State.FOLLOW_PATH_STRIKE;

                    DriveDodgeStrikeAbstraction dodgeStrikeAbstraction = new DriveDodgeStrikeAbstraction(currentPath, new DefensiveGrader());
                    dodgeStrikeAbstraction.arrivalTime = interceptFrame.absoluteTime;
                    dodgeStrikeAbstraction.originalTargetBallPos = targetPos;

                    float zDiff = targetPos.z - 0.4f * BallData.COLLISION_RADIUS - car.position.z;
                    if (zDiff < 5)
                        dodgeStrikeAbstraction.jumpBeforeStrikeDelay = 0.25f;
                    else
                        dodgeStrikeAbstraction.jumpBeforeStrikeDelay = MathUtils.clip(CarData.getJumpTimeForHeight(zDiff, gameData.getGravity().z) + 0.05f, 0.25f, 1f);

                    System.out.println("Setting jumpBeforeStrikeDelay=" + dodgeStrikeAbstraction.jumpBeforeStrikeDelay + " zDiff=" + zDiff + " ballTargetZ=" + targetPos.z + " carZ=" + car.position.z);

                    dodgeStrikeAbstraction.strikeAbstraction.optimizer.maxJumpDelay = Math.max(0.6f, dodgeStrikeAbstraction.jumpBeforeStrikeDelay + 0.1f);
                    dodgeStrikeAbstraction.strikeAbstraction.optimizer.jumpDelayStep = Math.max(0.1f, (dodgeStrikeAbstraction.strikeAbstraction.optimizer.maxJumpDelay - /*duration*/ 0.2f) / 5 - 0.02f);

                    if (debug)
                        dodgeStrikeAbstraction.debugMessages = true;

                    this.strikeAbstraction = dodgeStrikeAbstraction;
                }));
            }
        }

        return Optional.empty();
    }

    private Optional<StrikeInfo> planChipIntercept(YangBallPrediction ballPrediction, boolean debug) {
        if (ballPrediction.isEmpty())
            return Optional.empty();

        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final Vector2 ownGoal = new Vector2(0, car.getTeamSign() * RLConstants.goalDistance);

        float t = 0;

        // Path finder
        while (t < ballPrediction.relativeTimeOfLastFrame()) {
            final Optional<YangBallPrediction.YangPredictionFrame> interceptFrameOptional = ballPrediction.getFrameAtRelativeTime(t);
            if (interceptFrameOptional.isEmpty())
                break;
            final YangBallPrediction.YangPredictionFrame interceptFrame = interceptFrameOptional.get();

            t = interceptFrame.relativeTime;
            t += RLConstants.simulationTickFrequency * 4; // 15 ticks / s

            if (interceptFrame.ballData.isInAnyGoal())
                break;

            var ballVel = interceptFrame.ballData.velocity;

            if (ballVel.z < -10)
                continue;

            if (ballVel.magnitude() > 4000)
                continue;

            final Vector3 targetBallPos = interceptFrame.ballData.position;
            final Vector3 endTangent =
                    targetBallPos.flatten().sub(ownGoal).normalized().mul(2)
                            .add(targetBallPos.flatten().sub(car.position.flatten()).normalized())
                            .normalized().withZ(0);
            var ballExtension = DriveChipAbstraction.getBallExtension(targetBallPos.z);
            if (ballExtension == -1)
                continue;

            Vector3 ballHitTarget = targetBallPos.sub(endTangent.mul(ballExtension + car.hitbox.getForwardExtent()));
            ballHitTarget = ballHitTarget.withZ(RLConstants.carElevation);

            var currentPathOptional = new EpicMeshPlanner()
                    .withStart(car)
                    .withEnd(ballHitTarget, endTangent)
                    .withArrivalTime(interceptFrame.absoluteTime)
                    .withArrivalSpeed(CarData.MAX_VELOCITY)
                    .allowOptimize(car.boost < 30)
                    .allowFullSend(car.boost > 20)
                    .withCreationStrategy(EpicMeshPlanner.PathCreationStrategy.YANGPATH)
                    .plan();

            if (currentPathOptional.isEmpty())
                continue;

            var currentPath = currentPathOptional.get();

            if (currentPath.getEndTangent().dot(targetBallPos.flatten().sub(ownGoal).normalized().withZ(0)) < 0)
                continue;

            final Vector2 hitTangent = targetBallPos.flatten().sub(ballHitTarget.flatten()).normalized();
            if (hitTangent.dot(currentPath.getEndTangent().flatten().normalized()) < 0.6f)
                continue; // We should be driving at the ball, otherwise the code fails horribly

            var relativeVel = currentPath.getEndTangent().mul(currentPath.getEndSpeed()).sub(interceptFrame.ballData.velocity).flatten();
            if (relativeVel.magnitude() < 1200)
                continue; // We want boomers, not atbas

            float timeEstimate = currentPath.getTotalTimeEstimate();
            if (timeEstimate <= interceptFrame.relativeTime) {
                return Optional.of(new StrikeInfo(interceptFrame.absoluteTime, StrikeInfo.StrikeType.CHIP, (o) -> {
                    this.state = State.FOLLOW_PATH_STRIKE;
                    var chipStrikeAbstraction = new DriveChipAbstraction(currentPath);

                    chipStrikeAbstraction.arrivalTime = interceptFrame.absoluteTime;
                    chipStrikeAbstraction.originalTargetBallPos = targetBallPos;

                    this.strikeAbstraction = chipStrikeAbstraction;
                }));
            }
        }

        return Optional.empty();
    }

    @Override
    protected void planStrategyInternal() {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final ImmutableBallData ball = gameData.getBallData();
        final YangBallPrediction ballPrediction = gameData.getBallPrediction();

        this.ballTouchInterrupt = InterruptManager.get().getBallTouchInterrupt(-1);
        this.idleAbstraction.minIdleDistance = 800;
        this.strikeAbstraction = null;
        this.state = State.INVALID;

        if (this.checkReset(1f))
            return;

        if (!car.hasWheelContact) {
            this.setDone();
            return;
        }

        int teamSign = car.team * 2 - 1;

        final float MAX_HEIGHT_DOUBLEJUMP = 500;
        final float MAX_HEIGHT_AERIAL = RLConstants.arenaHeight - 200;

        YangBallPrediction aerialFrames = YangBallPrediction.empty();
        YangBallPrediction dodgeFrames = YangBallPrediction.empty();
        YangBallPrediction chipFrames = YangBallPrediction.empty();

        boolean isConceding = false;

        // Getting scored on
        {
            Optional<YangBallPrediction.YangPredictionFrame> firstConcedingGoalFrame = ballPrediction.getFramesBeforeRelative(3.5f)
                    .stream()
                    .filter((f) -> (int) Math.signum(f.ballData.position.y) == teamSign && f.ballData.makeMutable().isInOwnGoal(teamSign))
                    .findFirst();

            if (firstConcedingGoalFrame.isPresent()) { // We getting scored on
                isConceding = true;
                final YangBallPrediction.YangPredictionFrame frameConceding = firstConcedingGoalFrame.get();

                YangBallPrediction framesBeforeGoal = ballPrediction.getBeforeRelative(frameConceding.relativeTime);
                if (framesBeforeGoal.frames.size() == 0) {
                    this.state = State.BALLCHASE;
                    return;
                }

                if (car.boost > 10) {
                    var newAerialFrames = YangBallPrediction.from(framesBeforeGoal.frames.stream()
                            .filter((frame) -> frame.ballData.position.z < MAX_HEIGHT_AERIAL && frame.ballData.position.z > MAX_HEIGHT_DOUBLEJUMP)
                            .collect(Collectors.toList()), framesBeforeGoal.tickFrequency);

                    aerialFrames = YangBallPrediction.merge(aerialFrames, newAerialFrames);
                }

                {
                    var newDodgeFrames = YangBallPrediction.from(framesBeforeGoal.frames.stream()
                            .filter((frame) -> frame.ballData.position.z < DriveDodgeStrikeAbstraction.MAX_STRIKE_HEIGHT)
                            .collect(Collectors.toList()), framesBeforeGoal.tickFrequency);

                    dodgeFrames = YangBallPrediction.merge(dodgeFrames, newDodgeFrames);
                }
            }
        }

        // Defend area
        {

            final Area2 defendArea = new Area2(List.of(
                    new Vector2(-xDefendDist, RLConstants.goalDistance * teamSign),
                    new Vector2(xDefendDist, RLConstants.goalDistance * teamSign),
                    new Vector2(xDefendDist * 1.7f, (RLConstants.goalDistance - yDefendDist) * teamSign),
                    new Vector2(-xDefendDist * 1.7f, (RLConstants.goalDistance - yDefendDist) * teamSign)
            ));

            Optional<YangBallPrediction.YangPredictionFrame> ballInDefendAreaFrame = ballPrediction.getFramesBeforeRelative(3f)
                    .stream()
                    .filter((f) -> f.ballData.position.z < 1300 && defendArea.contains(f.ballData.position.flatten()))
                    .findFirst();

            if (ballInDefendAreaFrame.isPresent()) {
                final YangBallPrediction.YangPredictionFrame frameAreaEnter = ballInDefendAreaFrame.get();

                YangBallPrediction framesBeforeAreaEnter = ballPrediction.getBeforeRelative(frameAreaEnter.relativeTime + 0.5f);
                if (framesBeforeAreaEnter.frames.size() > 0) {

                    if (car.boost > 10) {
                        var newAerialFrames = YangBallPrediction.from(framesBeforeAreaEnter.frames.stream()
                                .filter((frame) -> frame.ballData.position.z < MAX_HEIGHT_AERIAL && frame.ballData.position.z > MAX_HEIGHT_DOUBLEJUMP)
                                .collect(Collectors.toList()), framesBeforeAreaEnter.tickFrequency);

                        aerialFrames = YangBallPrediction.merge(aerialFrames, newAerialFrames);
                    }

                    var newDodgeFrames = YangBallPrediction.from(framesBeforeAreaEnter.frames.stream()
                            .filter((frame) -> frame.ballData.position.z < DriveDodgeStrikeAbstraction.MAX_STRIKE_HEIGHT)
                            .collect(Collectors.toList()), framesBeforeAreaEnter.tickFrequency);

                    dodgeFrames = YangBallPrediction.merge(dodgeFrames, newDodgeFrames);
                }
            }
        }

        // Clear it
        final Vector2 ownGoal = new Vector2(0, teamSign * RLConstants.goalDistance);
        if (car.position.flatten().distance(ownGoal) < ball.position.flatten().distance(ownGoal)) {
            // Just boom it bruh
            {
                YangBallPrediction framesBeforeGoal = ballPrediction.getBeforeRelative(3.5f);
                final float carToOwnGoalDist = (float) car.position.flatten().distance(ownGoal);
                framesBeforeGoal = YangBallPrediction.from(framesBeforeGoal.frames.stream()
                        // Reachable on z axis
                        .filter((frame) -> frame.ballData.position.z < DriveDodgeStrikeAbstraction.MAX_STRIKE_HEIGHT)

                        // car is closer to goal than ball (Hitting away from goal)
                        .map((frame) -> new Tuple<>(frame, frame.ballData.position.flatten().distance(ownGoal)))
                        .filter((frameTup) -> Math.signum(frameTup.getValue() - carToOwnGoalDist) == 1)
                        .map(Tuple::getKey)

                        .collect(Collectors.toList()), framesBeforeGoal.tickFrequency);

                dodgeFrames = YangBallPrediction.merge(dodgeFrames, framesBeforeGoal);
                chipFrames = YangBallPrediction.merge(chipFrames, framesBeforeGoal);
            }
        }

        List<StrikeInfo> possibleStrikes = new ArrayList<>();
        var airStrike = this.planAerialIntercept(aerialFrames, false);
        airStrike.ifPresent(possibleStrikes::add);

        var dodgeStrike = this.planGroundIntercept(dodgeFrames, false);
        dodgeStrike.ifPresent(possibleStrikes::add);

        var chipStrike = this.planChipIntercept(chipFrames, false);
        chipStrike.ifPresent(possibleStrikes::add);

        if (possibleStrikes.isEmpty()) {
            if (!isConceding && car.position.flatten().distance(ownGoal) < ball.position.flatten().distance(ownGoal) && this.planGoForBoost())
                return;
            this.state = State.IDLE;
            return;
        }

        possibleStrikes
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
                .map(Tuple::getKey).get()
                .execute();

        assert this.state != State.INVALID;
    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {
        var oldState = state;
        assert !this.reevaluateStrategy(4f) : this.state.name() + " didnt  finish correctly: " + oldState;
        assert this.state != State.INVALID : "Invalid state!";

        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final AdvancedRenderer renderer = gameData.getAdvancedRenderer();

        switch (this.state) {
            case GET_BOOST: {
                if (this.boostAbstraction.canInterrupt() && this.reevaluateStrategy(this.ballTouchInterrupt))
                    return;

                if (this.boostAbstraction.canInterrupt() && this.reevaluateStrategy(3f))
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

                break;
            }
            case FOLLOW_PATH_STRIKE: {

                if (this.strikeAbstraction.canInterrupt() && this.reevaluateStrategy(3.5f))
                    return;

                if (this.strikeAbstraction.canInterrupt() && this.reevaluateStrategy(ballTouchInterrupt))
                    return;

                this.strikeAbstraction.step(dt, controlsOutput);

                if (this.strikeAbstraction.isDone() && this.reevaluateStrategy(0f))
                    return;

                break;
            }
            case IDLE: {
                if (this.reevaluateStrategy(this.idleAbstraction.canInterrupt() ? 0.1f : 2.5f))
                    return;

                if (this.idleAbstraction.canInterrupt() && this.reevaluateStrategy(ballTouchInterrupt, 0.1f))
                    return;

                this.idleAbstraction.step(dt, controlsOutput);
                if (this.idleAbstraction.isDone() && this.reevaluateStrategy(0))
                    return;
                break;
            }
            case BALLCHASE: {
                if (this.reevaluateStrategy(0.05f) || this.reevaluateStrategy(this.ballTouchInterrupt))
                    return;
                DefaultStrategy.smartBallChaser(dt, controlsOutput);
                break;
            }
            default:
                assert false : this.state.name() + " lastPlan: " + this.lastStrategyPlan + " current: " + car.elapsedSeconds;
                break;
        }
    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.empty();
    }

    @Override
    public String getAdditionalInformation() {
        return "State: " + this.state;
    }

    enum State {
        INVALID,
        AERIAL,
        GET_BOOST,
        FOLLOW_PATH_STRIKE,
        BALLCHASE,
        IDLE
    }

    private static class StrikeInfo {
        public final float timeAtStrike;
        public final StrikeInfo.StrikeType strikeType;
        private final Consumer<Object> executor;

        private StrikeInfo(float timeAtStrike, StrikeInfo.StrikeType strikeType, Consumer<Object> executor) {
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
