package yangbot.strategy;

import rlbot.cppinterop.RLBotDll;
import rlbot.flat.QuickChatSelection;
import yangbot.input.*;
import yangbot.input.fieldinfo.BoostManager;
import yangbot.input.fieldinfo.BoostPad;
import yangbot.input.interrupt.BallTouchInterrupt;
import yangbot.input.interrupt.InterruptManager;
import yangbot.path.Curve;
import yangbot.path.EpicMeshPlanner;
import yangbot.path.builders.PathBuilder;
import yangbot.path.builders.SegmentedPath;
import yangbot.path.builders.segments.AtbaSegment;
import yangbot.path.builders.segments.DriftSegment;
import yangbot.path.builders.segments.StraightLineSegment;
import yangbot.path.builders.segments.TurnCircleSegment;
import yangbot.strategy.abstraction.DriveStrikeAbstraction;
import yangbot.strategy.abstraction.IdleAbstraction;
import yangbot.strategy.advisor.RotationAdvisor;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.strategy.manuever.FollowPathManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.Line2;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class OffensiveStrategy extends Strategy {

    private DriveStrikeAbstraction strikeAbstraction;
    private FollowPathManeuver followPathManeuver = new FollowPathManeuver();
    private State state = State.INVALID;
    private RotationAdvisor.Advice lastAdvice = RotationAdvisor.Advice.NO_ADVICE;
    private BallTouchInterrupt ballTouchInterrupt;
    private SegmentedPath drivePath;
    private IdleAbstraction idleAbstraction;

    private boolean planGoForBoost() {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final int teamSign = car.getTeamSign();
        final ImmutableBallData ball = gameData.getBallData();
        final YangBallPrediction ballPrediction = gameData.getBallPrediction();

        if (car.boost < 70 && car.position.z < 50 && car.position.distance(ball.position) > 1500 && ball.velocity.magnitude() < 2000) {
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
                            float speed = (float) mate.velocity.dot(carToPad);
                            if (speed < 0)
                                return false;
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
                Curve shortestPath = null;
                float shortestPathLength = 2000;
                for (BoostPad pad : closestPadList) {

                    Vector3 padLocation = pad.getLocation().withZ(car.position.z);
                    Vector3 offToBallLocation = pad.getLocation().withZ(car.position.z)
                            .add(
                                    ball.position
                                            .add(ball.velocity.mul(0.6f))
                                            .sub(pad.getLocation())
                                            .withZ(0)
                                            .normalized().mul(100)
                            );
                    Vector3 offsetPos = padLocation.add(car.position.sub(padLocation).normalized().mul(30));

                    Curve path = new EpicMeshPlanner()
                            .withStart(car)
                            .addPoint(offsetPos, car.position.sub(padLocation).normalized())
                            .withEnd(padLocation, car.position.sub(padLocation).normalized()/*offToBallLocation.sub(padLocation).normalized()*/)
                            .plan().get();
                    float pathLength = path.length;
                    if (pad.isFullBoost())
                        pathLength -= 800;

                    if (pathLength < shortestPathLength && path.length > 0) {
                        shortestPathLength = Math.max(0, pathLength);
                        shortestPath = path;
                    }
                }

                if (shortestPath != null) {
                    this.followPathManeuver.path = shortestPath;
                    this.followPathManeuver.arrivalTime = -1;
                    this.followPathManeuver.arrivalSpeed = -1;
                    this.state = State.GET_BOOST;
                    RLBotDll.sendQuickChat(car.playerIndex, true, QuickChatSelection.Information_NeedBoost);
                }
            }
        }

        return this.state == State.GET_BOOST;
    }

    private void planStrategyAttack() {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final int teamSign = car.getTeamSign();
        final ImmutableBallData ball = gameData.getBallData();
        final YangBallPrediction ballPrediction = gameData.getBallPrediction();

        /*if (DribbleStrategy.isViable()) {
            suggestedStrat = new DribbleStrategy();
            this.setDone();
            return;
        }*/

        // Make sure we don't hit the ball back to our goal
        //assert Math.signum(ball.position.y - car.position.y) == -teamSign : "We should be in front of the ball, not ahead car: " + car.position + " ball: " + ball.position;

        final boolean allowWallHits = false;

        List<YangBallPrediction.YangPredictionFrame> strikeableFrames = ballPrediction.getFramesBetweenRelative(0.15f, 1.75f)
                .stream()
                .filter((frame) -> Math.signum(frame.ballData.position.y - (car.position.y + car.velocity.y * frame.relativeTime * 0.6f)) == -teamSign) // Ball is closer to enemy goal than to own
                .filter((frame) -> (frame.ballData.position.z <= BallData.COLLISION_RADIUS + 250 /*|| (allowWallHits && RLConstants.isPosNearWall(frame.ballData.position.flatten(), BallData.COLLISION_RADIUS * 1.5f))*/)
                        && !frame.ballData.makeMutable().isInAnyGoal())
                .collect(Collectors.toList());

        if (strikeableFrames.size() == 0) {
            this.state = State.IDLE;
            return;
        }

        Curve validPath = null;
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
                t += RLConstants.simulationTickFrequency * 1;
            else if (t > 1.5f) // Speed it up, not as important
                t += RLConstants.simulationTickFrequency * 4;
            else // default
                t += RLConstants.simulationTickFrequency * 2;

            final var interceptFrame = interceptFrameOptional.get();

            if (interceptFrame.ballData.velocity.magnitude() > 4000)
                continue;

            final Vector3 targetBallPos = interceptFrame.ballData.position;

            final Vector2 closestScoringPosition = enemyGoalLine.closestPointOnLine(targetBallPos.flatten());
            final Vector3 ballTargetToGoalTarget = closestScoringPosition.sub(targetBallPos.flatten()).normalized().withZ(0);

            Vector3 ballHitTarget = targetBallPos.sub(ballTargetToGoalTarget.mul(BallData.COLLISION_RADIUS + car.hitbox.permutatePoint(new Vector3(), 1, 0, 0).magnitude()));
            if (!RLConstants.isPosNearWall(ballHitTarget.flatten(), 10))
                ballHitTarget = ballHitTarget.withZ(RLConstants.carElevation);

            ballAtTargetPos = targetBallPos;

            final Vector3 carToDriveTarget = ballHitTarget.sub(startPosition).normalized();
            final Vector3 endTangent = carToDriveTarget.mul(4).add(ballTargetToGoalTarget).withZ(0).normalized();

            Curve currentPath;
            Optional<Curve> curveOptional = Optional.empty();
            //if ((RLConstants.isPosNearWall(startPosition.flatten(), 100) || RLConstants.isPosNearWall(ballHitTarget.flatten(), 50)) && startPosition.distance(ballHitTarget) > 400)
            //   curveOptional = YangBotJNAInterop.findPath(startPosition, startTangent, ballHitTarget, endTangent, 25);

            if (curveOptional.isPresent())
                currentPath = curveOptional.get();
            else {
                currentPath = new EpicMeshPlanner()
                        .withStart(startPosition, startTangent)
                        .withEnd(ballHitTarget, endTangent.mul(-1))
                        .plan().get();
            }

            if (currentPath.length <= 1 || Float.isNaN(currentPath.length) || currentPath.points.size() == 0)
                continue;

            if (currentPath.tangentAt(currentPath.length).dot(car.forward()) < 0)
                continue; // We aint drivin backward

            // Check if path is valid
            {
                currentPath.calculateMaxSpeeds(CarData.MAX_VELOCITY, CarData.MAX_VELOCITY);

                Curve.PathCheckStatus pathStatus = currentPath.doPathChecking(car, interceptFrame.absoluteTime, ballPrediction);

                if (pathStatus.isValid()) {
                    validPath = currentPath;
                    arrivalTime = interceptFrame.absoluteTime;
                    break;
                } else if (curveOptional.isEmpty() && currentPath.length < 700) {
                    //System.out.println("Short path was deemed impossible: leng=" + currentPath.length + " stat=" + pathStatus.pathStatus.name() + " speed=" + pathStatus.speedNeeded);
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

        float zDiff = ballAtTargetPos.z - 0.7f * BallData.COLLISION_RADIUS - car.position.z;
        if (zDiff < 30)
            this.strikeAbstraction.jumpBeforeStrikeDelay = 0.2f;
        else
            this.strikeAbstraction.jumpBeforeStrikeDelay = MathUtils.clip(CarData.getJumpTimeForHeight(zDiff, gameData.getGravity().z) + 0.05f, 0.2f, 1f);

        this.strikeAbstraction.maxJumpDelay = Math.max(0.6f, this.strikeAbstraction.jumpBeforeStrikeDelay + 0.1f);
        this.strikeAbstraction.jumpDelayStep = Math.max(0.1f, (this.strikeAbstraction.maxJumpDelay - /*duration*/ 0.2f) / 5 - 0.02f);
    }

    private void planStrategyRetreat() {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final int teamSign = car.getTeamSign();
        final ImmutableBallData ball = gameData.getBallData();
        final YangBallPrediction ballPrediction = gameData.getBallPrediction();

        // Go back to where the ball is headed,
        Vector3 endPos = ball.position.add(ball.velocity.mul(0.3f)).withZ(RLConstants.carElevation).add(0, teamSign * 2000, 0);
        endPos = endPos.withX(endPos.x * 0.5f); // slightly centered
        endPos = endPos.withX(MathUtils.lerp(car.position.x, endPos.x, 0.75f));
        Vector3 endTangent = new Vector3(0, teamSign, 0)
                .add(car.forward())
                .add(ball.velocity.normalized())
                .normalized();

        /*Optional<Curve> optionalCurve = new EpicPathPlanner()
                .withStart(car)
                .withEnd(endPos, endTangent)
                //.withBallAvoidance(true, car, -1, false)
                .withCreationStrategy(RLConstants.isPosNearWall(car.position.flatten(), 50) ? EpicPathPlanner.PathCreationStrategy.NAVMESH : EpicPathPlanner.PathCreationStrategy.JAVA_NAVMESH)
                .plan();

        if (optionalCurve.isPresent()) {
            this.state = State.ROTATE;
            this.followPathManeuver = new FollowPathManeuver();
            this.followPathManeuver.arrivalTime = car.elapsedSeconds + Math.min(1.5f, Math.max(0.3f, optionalCurve.get().length / (DriveManeuver.max_throttle_speed - 50)));
            this.followPathManeuver.path = optionalCurve.get();
        } else
            this.state = State.GO_BACK_SOMEHOW; // Possible if navmesh hasn't loaded yet

         */

        this.state = State.DRIVE_AT_POINT_WITHPATH;
        var builder = new PathBuilder(car)
                .optimize();

        if (car.position.z > 50 || builder.getCurrentPosition().distance(endPos) < 30) {
            var atba = new AtbaSegment(builder.getCurrentPosition(), endPos);
            builder.add(atba);
        } else if (car.angularVelocity.magnitude() < 0.1f && car.forwardVelocity() > 300) {
            var drift = new DriftSegment(builder.getCurrentPosition(), builder.getCurrentTangent(), endPos.sub(car.position).normalized(), builder.getCurrentSpeed());
            builder.add(drift);
        } else {
            var turn = new TurnCircleSegment(car.toPhysics2d(), 1 / DriveManeuver.maxTurningCurvature(Math.max(900, builder.getCurrentSpeed())), endPos.flatten());
            if (turn.tangentPoint != null)
                builder.add(turn);
        }

        if (builder.getCurrentPosition().distance(endPos) > 20)
            builder.add(new StraightLineSegment(builder.getCurrentPosition(), endPos));

        this.drivePath = builder.build();
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

            this.planStrategyRetreat();
        }
    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {

        var oldState = state;
        if (this.reevaluateStrategy(4f)) {
            assert false : "States/Abstractions didn't finish automatically! (" + oldState.name() + ")";
        }
        assert this.state != State.INVALID : "Invalid state! Last advice: " + this.lastAdvice;

        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final ImmutableBallData ball = gameData.getBallData();
        final AdvancedRenderer renderer = gameData.getAdvancedRenderer();
        final YangBallPrediction ballPrediction = gameData.getBallPrediction();

        final int teamSign = car.team * 2 - 1;
        final Vector2 ownGoal = new Vector2(0, teamSign * (RLConstants.goalDistance + 100));

        switch (this.state) {
            case GO_BACK_SOMEHOW: {
                DriveManeuver.steerController(controlsOutput, car, ownGoal.withZ(0));
                DriveManeuver.speedController(dt, controlsOutput, (float) car.forward().dot(car.velocity), DriveManeuver.max_throttle_speed * 0.9f, CarData.MAX_VELOCITY, 0.4f);

                if (!car.hasWheelContact && this.reevaluateStrategy(0.05f))
                    return;
                if (this.reevaluateStrategy(0.2f) || this.reevaluateStrategy(ballTouchInterrupt, 0.05f))
                    return;
                break;
            }
            case GET_BOOST: {

                if (!car.hasWheelContact && this.reevaluateStrategy(0.05f))
                    return;

                if (this.reevaluateStrategy(ballTouchInterrupt, 0.05f) || this.reevaluateStrategy(2f))
                    return;

                this.followPathManeuver.step(dt, controlsOutput);
                //this.followPathManeuver.draw(renderer, car);
                this.followPathManeuver.path.draw(renderer, Color.YELLOW.brighter());
                if (this.followPathManeuver.isDone()) {
                    this.reevaluateStrategy(0);
                    return;
                }

                break;
            }
            case IDLE: {
                if (this.reevaluateStrategy(this.idleAbstraction.canInterrupt() ? 0.1f : 2.5f))
                    return;

                this.idleAbstraction.step(dt, controlsOutput);
                if (this.idleAbstraction.isDone() && this.reevaluateStrategy(0))
                    return;
                break;
            }
            case ROTATE: { // Basically deprecated at this point

                if (!car.hasWheelContact && this.reevaluateStrategy(0.05f))
                    return;

                if (this.reevaluateStrategy(0.5f))
                    return;

                if (car.position.z > 300) {
                    // Get back to ground level
                    DefaultStrategy.smartBallChaser(dt, controlsOutput);
                } else {
                    this.followPathManeuver.step(dt, controlsOutput);
                    //this.followPathManeuver.draw(renderer, car);
                    this.followPathManeuver.path.draw(renderer, Color.YELLOW.darker());
                    if (this.followPathManeuver.isDone() && this.reevaluateStrategy(0))
                        return;
                }

                break;
            }
            case FOLLOW_PATH_STRIKE: {
                if (this.strikeAbstraction.canInterrupt() && this.reevaluateStrategy(1.8f))
                    return;

                if (this.strikeAbstraction.canInterrupt() && this.reevaluateStrategy(ballTouchInterrupt))
                    return;

                this.strikeAbstraction.step(dt, controlsOutput);

                if (this.strikeAbstraction.isDone() && this.reevaluateStrategy(0))
                    return;

                break;
            }
            case DRIVE_AT_POINT_WITHPATH: {
                if (!car.hasWheelContact && !this.drivePath.shouldBeInAir() && this.reevaluateStrategy(0.05f))
                    return;

                if (this.reevaluateStrategy(this.drivePath.canInterrupt() ? 0.4f : 1f))
                    return;

                if (this.drivePath == null && this.reevaluateStrategy(0))
                    return;

                this.drivePath.draw(renderer);
                if (this.drivePath.step(dt, controlsOutput) && this.reevaluateStrategy(0))
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
        ROTATE,
        GO_BACK_SOMEHOW,
        GET_BOOST,
        DRIVE_AT_POINT_WITHPATH,
        INVALID
    }
}
