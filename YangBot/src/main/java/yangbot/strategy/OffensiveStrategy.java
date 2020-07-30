package yangbot.strategy;

import rlbot.cppinterop.RLBotDll;
import rlbot.flat.QuickChatSelection;
import yangbot.input.*;
import yangbot.input.interrupt.BallTouchInterrupt;
import yangbot.input.interrupt.InterruptManager;
import yangbot.path.EpicMeshPlanner;
import yangbot.path.builders.SegmentedPath;
import yangbot.path.builders.segments.CurveSegment;
import yangbot.strategy.abstraction.AerialAbstraction;
import yangbot.strategy.abstraction.DriveStrikeAbstraction;
import yangbot.strategy.abstraction.GetBoostAbstraction;
import yangbot.strategy.abstraction.IdleAbstraction;
import yangbot.strategy.advisor.RotationAdvisor;
import yangbot.strategy.manuever.DodgeManeuver;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.Line2;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class OffensiveStrategy extends Strategy {

    private DriveStrikeAbstraction strikeAbstraction;
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

        this.state = OffensiveStrategy.State.GET_BOOST;
        RLBotDll.sendQuickChat(car.playerIndex, true, QuickChatSelection.Information_NeedBoost);
        return true;
    }

    private boolean planAerialIntercept(YangBallPrediction ballPrediction, boolean debug) {
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
            final Vector3 ballPos = interceptFrame.ballData.position;
            final var carToBall = ballPos.sub(car.position).normalized();
            final var goalToBall = ballPos.sub(ownGoal.withZ(100)).normalized().add(enemyGoal.withZ(100).sub(ballPos).normalized()).normalized();
            final Vector3 targetOffset = carToBall.mul(1.5f).add(goalToBall).normalized();
            final Vector3 targetPos = ballPos.sub(targetOffset.mul(BallData.COLLISION_RADIUS + car.hitbox.getForwardExtent() * 0.95f - 10));

            // We should arrive at the ball a bit early to catch it
            boolean isPossible = AerialAbstraction.isViable(car, targetPos, interceptFrame.absoluteTime);
            if (isPossible) {
                this.aerialAbstraction = new AerialAbstraction();
                this.aerialAbstraction.targetPos = targetPos;
                this.aerialAbstraction.targetOrientPos = ballPos;
                this.aerialAbstraction.arrivalTime = interceptFrame.absoluteTime;
                this.state = State.AERIAL;
                return true;
            }

            t = interceptFrame.relativeTime;
            t += RLConstants.simulationTickFrequency * 4; // 15 ticks / s
        } while (t < ballPrediction.relativeTimeOfLastFrame());

        return false;
    }

    private void planStrategyAttack() {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final int teamSign = car.getTeamSign();
        final YangBallPrediction ballPrediction = gameData.getBallPrediction();

        /*if (DribbleStrategy.isViable()) {
            suggestedStrat = new DribbleStrategy();
            this.setDone();
            return;
        }*/

        // Make sure we don't hit the ball back to our goal
        //assert Math.signum(ball.position.y - car.position.y) == -teamSign : "We should be in front of the ball, not ahead car: " + car.position + " ball: " + ball.position;

        final float MAX_HEIGHT_GROUND_SHOT = 230f + BallData.COLLISION_RADIUS * 0.65f;
        final float MAX_HEIGHT_DOUBLEJUMP = 500;
        final float MAX_HEIGHT_AERIAL = RLConstants.arenaHeight - 200;

        if (car.boost > 10) {
            var aerialStrikeFrames = YangBallPrediction.from(ballPrediction.getFramesBetweenRelative(0.3f, 3.5f)
                    .stream()
                    .filter((frame) -> Math.signum(frame.ballData.position.y - (car.position.y + car.velocity.y * frame.relativeTime * 0.6f)) == -teamSign) // Ball is closer to enemy goal than to own
                    .filter((frame) -> (frame.ballData.position.z >= MAX_HEIGHT_DOUBLEJUMP && frame.ballData.position.z < MAX_HEIGHT_AERIAL)
                            && !frame.ballData.makeMutable().isInAnyGoal())
                    .collect(Collectors.toList()), ballPrediction.tickFrequency);

            if (this.planAerialIntercept(aerialStrikeFrames, false))
                return;
        }

        List<YangBallPrediction.YangPredictionFrame> strikeableFrames = ballPrediction.getFramesBetweenRelative(0.15f, 3.5f)
                .stream()
                .filter((frame) -> Math.signum(frame.ballData.position.y - (car.position.y + car.velocity.y * Math.max(0, frame.relativeTime * 0.6f - 0.1f))) == -teamSign) // Ball is closer to enemy goal than to own
                .filter((frame) -> (frame.ballData.position.z <= MAX_HEIGHT_GROUND_SHOT /*|| (allowWallHits && RLConstants.isPosNearWall(frame.ballData.position.flatten(), BallData.COLLISION_RADIUS * 1.5f))*/))
                .collect(Collectors.toList());

        if (strikeableFrames.size() == 0) {
            this.state = State.IDLE;
            return;
        }

        SegmentedPath validPath = null;
        float arrivalTime = 0;

        float maxT = strikeableFrames.get(strikeableFrames.size() - 1).relativeTime - RLConstants.simulationTickFrequency * 2;
        float t = strikeableFrames.get(0).relativeTime;

        YangBallPrediction strikePrediction = YangBallPrediction.from(strikeableFrames, RLConstants.tickFrequency);

        final Vector2 enemyGoal = new Vector2(0, -teamSign * (RLConstants.goalDistance + 1000));
        final float goalCenterToPostDistance = RLConstants.goalCenterToPost - BallData.COLLISION_RADIUS * 2 - 50 /* tolerance */;
        assert goalCenterToPostDistance > 100; // Could fail with smaller goals
        assert enemyGoal.x == 0; // Could fail with custom goals
        final Line2 enemyGoalLine = new Line2(enemyGoal.sub(goalCenterToPostDistance, 0), enemyGoal.add(goalCenterToPostDistance, 0));
        final Vector3 startPosition = car.position.add(car.velocity.mul(RLConstants.tickFrequency * 2));
        final Vector3 startTangent = car.forward().mul(3).add(car.velocity.normalized()).normalized();

        float oldPathArrival = -1;
        if (this.strikeAbstraction != null) {
            oldPathArrival = this.strikeAbstraction.arrivalTime;
            if (oldPathArrival < car.elapsedSeconds || oldPathArrival > strikeableFrames.get(strikeableFrames.size() - 1).absoluteTime)
                oldPathArrival = -1;
            else
                oldPathArrival -= car.elapsedSeconds;
        }

        Vector3 ballAtTargetPos = null;

        // Path finder
        while (t < maxT) {
            final Optional<YangBallPrediction.YangPredictionFrame> interceptFrameOptional = strikePrediction.getFrameAtRelativeTime(t);
            if (interceptFrameOptional.isEmpty())
                break;

            if (Math.abs(oldPathArrival - t) < 0.1f) // Be extra precise, we are trying to rediscover our last path
                t += RLConstants.simulationTickFrequency * 1; // 60hz
            else if (t > 1.5f) // Speed it up, not as important
                t += RLConstants.simulationTickFrequency * 4; // 15hz
            else // default
                t += RLConstants.simulationTickFrequency * 2; // 30hz

            final var interceptFrame = interceptFrameOptional.get();
            if (interceptFrame.ballData.makeMutable().isInAnyGoal())
                break;

            if (interceptFrame.ballData.velocity.magnitude() > 4000)
                continue;

            final Vector3 targetBallPos = interceptFrame.ballData.position;

            final Vector2 closestScoringPosition = enemyGoalLine.closestPointOnLine(targetBallPos.flatten());
            final Vector3 ballTargetToGoalTarget = closestScoringPosition.sub(targetBallPos.flatten()).normalized().withZ(0);

            Vector3 ballHitTarget = targetBallPos.sub(ballTargetToGoalTarget.mul(BallData.COLLISION_RADIUS + car.hitbox.getForwardExtent()));
            ballHitTarget = ballHitTarget.withZ(RLConstants.carElevation);

            ballAtTargetPos = targetBallPos;

            final Vector3 carToDriveTarget = ballHitTarget.sub(startPosition).normalized();
            final Vector3 endTangent = carToDriveTarget.mul(4).add(ballTargetToGoalTarget).withZ(0).normalized();

            var currentPathOptional = new EpicMeshPlanner()
                    .withStart(startPosition, startTangent)
                    .withEnd(ballHitTarget, endTangent)
                    .withArrivalTime(interceptFrame.absoluteTime)
                    .withArrivalSpeed(2300)
                    .allowOptimize(car.boost < 30)
                    .withCreationStrategy(EpicMeshPlanner.PathCreationStrategy.YANGPATH)
                    .plan();

            if (currentPathOptional.isEmpty())
                continue;

            var currentPath = currentPathOptional.get();

            if (currentPath.getCurrentPathSegment().get() instanceof CurveSegment && ((CurveSegment) currentPath.getCurrentPathSegment().get()).getBakedPath().tangentAt(-1).dot(car.forward()) < 0)
                continue;

            if (ballTargetToGoalTarget.dot(currentPath.getEndTangent()) < 0)
                continue;

            // Check if path is valid
            {
                if (currentPath.getTotalTimeEstimate() <= interceptFrame.relativeTime) {
                    validPath = currentPath;
                    arrivalTime = interceptFrame.absoluteTime;
                    break;
                }
            }
        }

        if (validPath == null) {
            this.state = State.IDLE;
            return;
        }

        this.state = State.FOLLOW_PATH_STRIKE;
        this.strikeAbstraction = new DriveStrikeAbstraction(validPath);
        this.strikeAbstraction.arrivalTime = arrivalTime;
        this.strikeAbstraction.originalTargetBallPos = ballAtTargetPos;

        float zDiff = ballAtTargetPos.z - 0.5f * BallData.COLLISION_RADIUS - car.position.z;
        if (zDiff < 5)
            this.strikeAbstraction.jumpBeforeStrikeDelay = 0.25f;
        else
            this.strikeAbstraction.jumpBeforeStrikeDelay = MathUtils.clip(CarData.getJumpTimeForHeight(zDiff, gameData.getGravity().z) + 0.05f, 0.25f, 1f);

        System.out.println("Setting jumpBeforeStrikeDelay=" + this.strikeAbstraction.jumpBeforeStrikeDelay + " zDiff=" + zDiff + " ballTargetZ=" + ballAtTargetPos.z + " carZ=" + car.position.z);

        this.strikeAbstraction.maxJumpDelay = Math.max(0.6f, this.strikeAbstraction.jumpBeforeStrikeDelay + 0.1f);
        this.strikeAbstraction.jumpDelayStep = Math.max(0.1f, (this.strikeAbstraction.maxJumpDelay - /*duration*/ 0.2f) / 5 - 0.02f);
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

        // Check if ball will go in our goal
        if (Math.signum(ball.position.y) == teamSign) { // our half
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
                this.idleAbstraction.forceRetreatTimeout = 0.7f;
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
        final Vector2 ownGoal = new Vector2(0, teamSign * (RLConstants.goalDistance + 100));

        switch (this.state) {
            case GO_BACK_SOMEHOW: {

                DriveManeuver.steerController(controlsOutput, car, ownGoal.withZ(0));
                DriveManeuver.speedController(dt, controlsOutput, (float) car.forward().dot(car.velocity), DriveManeuver.max_throttle_speed * 0.9f, CarData.MAX_VELOCITY, 0.04f, false);

                if (!car.hasWheelContact && this.reevaluateStrategy(0))
                    return;
                if (this.reevaluateStrategy(0.1f) || this.reevaluateStrategy(ballTouchInterrupt))
                    return;

            }
            break;
            case GET_BOOST: {

                if (this.boostAbstraction.canInterrupt() && this.reevaluateStrategy(this.ballTouchInterrupt))
                    return;

                if (this.boostAbstraction.step(dt, controlsOutput).isDone()) {
                    this.reevaluateStrategy(0);
                    return;
                }

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
            case FOLLOW_PATH_STRIKE: {
                if (this.strikeAbstraction.canInterrupt() && this.reevaluateStrategy(2.9f))
                    return;

                if (this.strikeAbstraction.canInterrupt() && this.reevaluateStrategy(ballTouchInterrupt))
                    return;

                this.strikeAbstraction.step(dt, controlsOutput);

                if (this.strikeAbstraction.isDone() && this.reevaluateStrategy(0))
                    return;

            }
            break;
            case DRIVE_AT_POINT_WITHPATH: {
                if (!car.hasWheelContact && this.drivePath.shouldBeOnGround() && this.reevaluateStrategy(0.05f))
                    return;

                if (this.reevaluateStrategy(this.drivePath.canInterrupt() ? 0.1f : 1.8f))
                    return;

                if (this.drivePath == null && this.reevaluateStrategy(0))
                    return;

                this.drivePath.draw(renderer);
                if (this.drivePath.step(dt, controlsOutput) && this.reevaluateStrategy(0))
                    return;

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
        FOLLOW_PATH_STRIKE,
        GO_BACK_SOMEHOW,
        GET_BOOST,
        DRIVE_AT_POINT_WITHPATH,
        INVALID,
        AERIAL
    }
}
