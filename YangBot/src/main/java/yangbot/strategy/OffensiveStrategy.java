package yangbot.strategy;

import rlbot.cppinterop.RLBotDll;
import rlbot.flat.QuickChatSelection;
import yangbot.input.*;
import yangbot.input.fieldinfo.BoostManager;
import yangbot.input.fieldinfo.BoostPad;
import yangbot.input.interrupt.BallTouchInterrupt;
import yangbot.input.interrupt.InterruptManager;
import yangbot.path.EpicMeshPlanner;
import yangbot.path.builders.PathBuilder;
import yangbot.path.builders.SegmentedPath;
import yangbot.path.builders.segments.*;
import yangbot.strategy.abstraction.AerialAbstraction;
import yangbot.strategy.abstraction.DriveStrikeAbstraction;
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
    private IdleAbstraction idleAbstraction;
    private AerialAbstraction aerialAbstraction;

    private boolean planGoForBoost() {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final int teamSign = car.getTeamSign();
        final ImmutableBallData ball = gameData.getBallData();
        final YangBallPrediction ballPrediction = gameData.getBallPrediction();

        if (car.boost < 70 && car.position.z < 50 && car.position.flatten().distance(ball.position.flatten()) > 1000 && ball.velocity.magnitude() < 2000) {
            List<BoostPad> fullPads = BoostManager.getAllBoosts();
            List<CarData> teammates = gameData.getAllCars().stream().filter(c -> c.team == car.team && c.playerIndex != car.playerIndex).collect(Collectors.toList());
            List<BoostPad> closestPadList = fullPads.stream()
                    // Pad is active
                    .filter((pad) -> pad.isActive() || pad.boostAvailableIn() < 1)
                    // Pad is closer to our goal than ball
                    .filter((pad) -> Math.signum(ball.position.y - pad.getLocation().y) == -teamSign)
                    // We don't go out of position (go closer to enemy goal)
                    .filter((pad) -> Math.abs(pad.getLocation().y - car.position.y) < 350 || Math.signum(car.position.y - pad.getLocation().y) == -teamSign)
                    // We don't have to change our angle that much
                    .filter((pad) -> Math.abs(car.forward().flatten().correctionAngle(pad.getLocation().flatten().sub(car.position.add(car.velocity.mul(0.2f)).flatten()).normalized())) < 1f)
                    // Of our teammates, we are the fastest to the boost
                    .filter((pad) -> {
                        if (teammates.size() == 0)
                            return true;

                        float myDist = (float) pad.getLocation().distance(car.position);
                        Vector3 carToPad = pad.getLocation().sub(car.position).withZ(0).normalized();
                        float ourSpeed = (float) car.velocity.dot(carToPad);
                        if (ourSpeed < 0) // We are going away from the pad
                            return false;
                        ourSpeed += 50;
                        float ourTime = (float) pad.getLocation().distance(car.position) / ourSpeed;
                        if (ourTime < 0.1f) // If we're that close, might as well get it
                            return true;

                        // Loop through teammates
                        for (CarData mate : teammates) {
                            if (mate.playerIndex == car.playerIndex)
                                continue;

                            if (pad.getLocation().distance(mate.position) < myDist)
                                return false;

                            Vector3 mateToPad = pad.getLocation().sub(mate.position).withZ(0).normalized();
                            float speed = (float) mate.velocity.dot(mateToPad);
                            if (speed < 0)
                                continue;
                            speed += 50;
                            float mateTime = (float) pad.getLocation().distance(car.position) / speed;

                            if (mateTime < ourTime) // If they beat us, don't go
                                return false;
                        }
                        return true;
                    })
                    // Sort by distance
                    .sorted((a, b) -> (int) (a.getLocation().distance(car.position) - b.getLocation().distance(car.position)))
                    .limit(5)
                    .collect(Collectors.toList());

            if (closestPadList.size() > 0) {
                SegmentedPath shortestPath = null;
                float shortestPathTimeEstimate = 2;
                for (BoostPad pad : closestPadList) {

                    Vector3 padLocation = pad.getLocation().withZ(car.position.z);

                    var path = new EpicMeshPlanner()
                            .withStart(car)
                            .withEnd(padLocation, car.position.sub(padLocation).normalized()/*offToBallLocation.sub(padLocation).normalized()*/)
                            .withCreationStrategy(EpicMeshPlanner.PathCreationStrategy.YANGPATH)
                            .plan().get();
                    float pathTimeEstimate = path.getTotalTimeEstimate();
                    if (pad.isFullBoost())
                        pathTimeEstimate *= 0.8f;

                    if (pathTimeEstimate < shortestPathTimeEstimate) {
                        shortestPathTimeEstimate = pathTimeEstimate;
                        shortestPath = path;
                    }
                }

                if (shortestPath != null) {
                    this.drivePath = shortestPath;
                    this.state = State.GET_BOOST;
                    RLBotDll.sendQuickChat(car.playerIndex, true, QuickChatSelection.Information_NeedBoost);
                }
            }
        }

        return this.state == State.GET_BOOST;
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
            final Vector3 targetPos = ballPos.sub(targetOffset.mul(BallData.COLLISION_RADIUS + car.hitbox.getForwardExtent() * 0.9f));

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

        List<YangBallPrediction.YangPredictionFrame> strikeableFrames = ballPrediction.getFramesBetweenRelative(0.15f, 2.75f)
                .stream()
                .filter((frame) -> Math.signum(frame.ballData.position.y - (car.position.y + car.velocity.y * frame.relativeTime * 0.6f)) == -teamSign) // Ball is closer to enemy goal than to own
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

            if (ballTargetToGoalTarget.dot(startTangent) < 0)
                continue;

            Vector3 ballHitTarget = targetBallPos.sub(ballTargetToGoalTarget.mul(BallData.COLLISION_RADIUS + car.hitbox.getForwardExtent()));
            ballHitTarget = ballHitTarget.withZ(RLConstants.carElevation);

            ballAtTargetPos = targetBallPos;

            final Vector3 carToDriveTarget = ballHitTarget.sub(startPosition).normalized();
            final Vector3 endTangent = carToDriveTarget.mul(4).add(ballTargetToGoalTarget).withZ(0).normalized();

            var currentPathOptional = new EpicMeshPlanner()
                    .withStart(startPosition, startTangent)
                    .withEnd(ballHitTarget, endTangent)
                    .withArrivalTime(interceptFrame.absoluteTime)
                    .allowOptimize(false) // no flips
                    .withCreationStrategy(EpicMeshPlanner.PathCreationStrategy.YANGPATH)
                    .plan();

            if (currentPathOptional.isEmpty())
                continue;

            var currentPath = currentPathOptional.get();

            if (currentPath.getCurrentPathSegment().get() instanceof CurveSegment && ((CurveSegment) currentPath.getCurrentPathSegment().get()).getBakedPath().tangentAt(-1).dot(car.forward()) < 0)
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

    private void planStrategyRetreat() {
        assert false : "Old code, use EpicMeshPlanner";
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final int teamSign = car.getTeamSign();
        final ImmutableBallData ball = gameData.getBallData();

        // Go back to where the ball is headed,
        Vector3 endPos = ball.position.add(ball.velocity.mul(0.3f)).withZ(RLConstants.carElevation).add(0, teamSign * 2000, 0);
        endPos = endPos.withX(endPos.x * 0.5f); // slightly centered
        endPos = endPos.withX(MathUtils.lerp(car.position.x, endPos.x, 0.75f));

        this.state = State.DRIVE_AT_POINT_WITHPATH;
        var builder = new PathBuilder(car)
                .optimize();

        if (car.position.z > 50 || builder.getCurrentPosition().distance(endPos) < 30) {
            var atba = new AtbaSegment(builder.getCurrentPosition(), builder.getCurrentSpeed(), endPos);
            builder.add(atba);
        } else if (car.angularVelocity.magnitude() < 0.1f && car.forwardSpeed() > 300) {
            var drift = new DriftSegment(builder.getCurrentPosition(), builder.getCurrentTangent(), endPos.sub(car.position).normalized(), builder.getCurrentSpeed());
            builder.add(drift);
        } else {
            var turn = new TurnCircleSegment(car.toPhysics2d(), 1 / DriveManeuver.maxTurningCurvature(Math.max(1100, builder.getCurrentSpeed())), endPos.flatten());
            if (turn.tangentPoint != null)
                builder.add(turn);
        }

        if (builder.getCurrentPosition().distance(endPos) > 20)
            builder.add(new StraightLineSegment(builder.getCurrentPosition(), builder.getCurrentSpeed(), endPos));

        this.drivePath = builder.build();
    }

    @Override
    protected void planStrategyInternal() {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final int teamSign = car.getTeamSign();
        final ImmutableBallData ball = gameData.getBallData();
        final YangBallPrediction ballPrediction = gameData.getBallPrediction();
        //this.state = State.INVALID;
        this.ballTouchInterrupt = InterruptManager.get().getBallTouchInterrupt();
        this.idleAbstraction = new IdleAbstraction();

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

        final RotationAdvisor.Advice rotationAdvice = RotationAdvisor.whatDoIDo(gameData);
        this.lastAdvice = rotationAdvice;
        switch (rotationAdvice) {
            case PREPARE_FOR_PASS:
            case IDLE:
                this.state = State.IDLE;
                if (car.position.flatten().distance(ball.position.flatten()) > 2000) {
                    if (this.planGoForBoost())
                        return;
                }
                return;
            case ATTACK:
                this.planStrategyAttack();
                return;
            case RETREAT: // Ignore
                break;
            default:
                System.out.println("Unhandled Advice: " + rotationAdvice);
                this.state = State.IDLE;
        }

        // Ball is closer to own goal / out of position
        if (rotationAdvice == RotationAdvisor.Advice.RETREAT ||
                Math.signum((ball.position.y + ball.velocity.y * 0.4f) - (car.position.y + car.velocity.y * 0.2f) + teamSign * 100) == teamSign) {

            //this.planStrategyRetreat();
            this.state = State.IDLE;
        }
    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {
        var oldState = state;
        assert !this.reevaluateStrategy(4f) : "States/Abstractions didn't finish automatically! (" + oldState.name() + ")";
        assert this.state != State.INVALID : "Invalid state! Last advice: " + this.lastAdvice;

        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final AdvancedRenderer renderer = gameData.getAdvancedRenderer();

        final int teamSign = car.team * 2 - 1;
        final Vector2 ownGoal = new Vector2(0, teamSign * (RLConstants.goalDistance + 100));

        switch (this.state) {
            case GO_BACK_SOMEHOW: {
                DriveManeuver.steerController(controlsOutput, car, ownGoal.withZ(0));
                DriveManeuver.speedController(dt, controlsOutput, (float) car.forward().dot(car.velocity), DriveManeuver.max_throttle_speed * 0.9f, CarData.MAX_VELOCITY, 0.04f, false);

                if (!car.hasWheelContact && this.reevaluateStrategy(0))
                    return;
                if (this.reevaluateStrategy(0.1f) || this.reevaluateStrategy(ballTouchInterrupt))
                    return;
                break;
            }
            case GET_BOOST: {

                if (this.drivePath.shouldReset(car) && this.reevaluateStrategy(0))
                    return;

                if (this.drivePath.canInterrupt() && (this.reevaluateStrategy(ballTouchInterrupt) || this.reevaluateStrategy(2f)))
                    return;

                if (this.drivePath.step(dt, controlsOutput)) {
                    this.reevaluateStrategy(0);
                    return;
                }

                break;
            }
            case IDLE: {
                if (this.reevaluateStrategy(this.idleAbstraction.canInterrupt() ? 0.1f : 2.5f))
                    return;

                if (this.idleAbstraction.canInterrupt() && this.reevaluateStrategy(ballTouchInterrupt))
                    return;

                this.idleAbstraction.step(dt, controlsOutput);
                if (this.idleAbstraction.isDone() && this.reevaluateStrategy(0))
                    return;
                break;
            }
            case FOLLOW_PATH_STRIKE: {
                if (this.strikeAbstraction.canInterrupt() && this.reevaluateStrategy(2.9f))
                    return;

                if (this.strikeAbstraction.canInterrupt() && this.reevaluateStrategy(ballTouchInterrupt))
                    return;

                this.strikeAbstraction.step(dt, controlsOutput);

                if (this.strikeAbstraction.isDone() && this.reevaluateStrategy(0))
                    return;

                break;
            }
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

                break;
            }
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
        IDLE,
        FOLLOW_PATH_STRIKE,
        GO_BACK_SOMEHOW,
        GET_BOOST,
        DRIVE_AT_POINT_WITHPATH,
        INVALID,
        AERIAL
    }
}
