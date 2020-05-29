package yangbot.strategy;

import rlbot.cppinterop.RLBotDll;
import rlbot.flat.QuickChatSelection;
import yangbot.input.*;
import yangbot.input.fieldinfo.BoostManager;
import yangbot.input.fieldinfo.BoostPad;
import yangbot.input.interrupt.BallTouchInterrupt;
import yangbot.input.interrupt.InterruptManager;
import yangbot.optimizers.graders.DefensiveGrader;
import yangbot.path.EpicMeshPlanner;
import yangbot.path.builders.PathBuilder;
import yangbot.path.builders.SegmentedPath;
import yangbot.path.builders.segments.*;
import yangbot.strategy.abstraction.AerialAbstraction;
import yangbot.strategy.abstraction.DriveStrikeAbstraction;
import yangbot.strategy.abstraction.IdleAbstraction;
import yangbot.strategy.manuever.DodgeManeuver;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.Tuple;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.Area2;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class DefendStrategy extends Strategy {

    private State state = State.INVALID;
    private static float xDefendDist = RLConstants.goalCenterToPost + 400;
    private static float yDefendDist = RLConstants.goalDistance * 0.4f;
    private DriveStrikeAbstraction strikeAbstraction;
    private BallTouchInterrupt ballTouchInterrupt = null;
    private IdleAbstraction idleAbstraction;
    private AerialAbstraction aerialAbstraction;
    private SegmentedPath segmentedPath;

    private boolean planGoForBoost() {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final int teamSign = car.getTeamSign();
        final ImmutableBallData ball = gameData.getBallData();
        final YangBallPrediction ballPrediction = gameData.getBallPrediction();

        if (car.boost < 70 && car.position.z < 50 && car.position.distance(ball.position) > 1000 && ball.velocity.magnitude() < 2000) {
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

                    Vector3 offsetPos = padLocation.add(car.position.sub(padLocation).normalized().mul(30));

                    var path = new EpicMeshPlanner()
                            .withStart(car)
                            .addPoint(offsetPos, car.position.sub(padLocation).normalized())
                            .withEnd(padLocation, car.position.sub(padLocation).normalized()/*offToBallLocation.sub(padLocation).normalized()*/)
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
                    this.segmentedPath = shortestPath;
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

        float t = DodgeManeuver.max_duration + 0.15f;

        // Find intercept
        do {
            final Optional<YangBallPrediction.YangPredictionFrame> interceptFrameOptional = ballPrediction.getFrameAfterRelativeTime(t);
            if (interceptFrameOptional.isEmpty())
                break;

            final YangBallPrediction.YangPredictionFrame interceptFrame = interceptFrameOptional.get();
            final Vector3 targetPos = interceptFrame.ballData.position;

            // We should arrive at the ball a bit early to catch it
            boolean isPossible = AerialAbstraction.isViable(car, targetPos, interceptFrame.absoluteTime);
            if (isPossible) {
                this.aerialAbstraction = new AerialAbstraction();
                this.aerialAbstraction.targetPos = targetPos;
                this.aerialAbstraction.arrivalTime = interceptFrame.absoluteTime;
                this.state = State.AERIAL;
                return true;
            }

            t = interceptFrame.relativeTime;
            t += RLConstants.simulationTickFrequency * 4; // 15 ticks / s
        } while (t < ballPrediction.relativeTimeOfLastFrame());

        return false;
    }

    private boolean planGroundIntercept(YangBallPrediction ballPrediction, boolean debug) {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();

        if (car.position.z > 50) {
            if (debug)
                System.out.println("Car too high up");
            return false;
        }

        final Vector2 ownGoal = new Vector2(0, car.getTeamSign() * RLConstants.goalDistance);
        SegmentedPath validPath = null;
        float arrivalTime = 0;
        Vector3 targetBallPos = null;

        float t = 0;

        // Path finder
        do {
            final Optional<YangBallPrediction.YangPredictionFrame> interceptFrameOptional = ballPrediction.getFrameAfterRelativeTime(t);
            if (interceptFrameOptional.isEmpty())
                break;

            final YangBallPrediction.YangPredictionFrame interceptFrame = interceptFrameOptional.get();
            final Vector3 targetPos = interceptFrame.ballData.position;
            final Vector3 endTangent = new Vector3(targetPos.flatten().sub(ownGoal).normalized().mul(2).add(targetPos.flatten().sub(car.position.flatten()).normalized()).normalized(), 0);
            final Vector3 endPos = targetPos.withZ(car.position.z).sub(endTangent.mul(BallData.COLLISION_RADIUS + car.hitbox.getMinHitboxExtent()));

            var pathPlanner = new EpicMeshPlanner()
                    .withStart(car, 10)
                    .withEnd(endPos, endTangent)
                    //.withBallAvoidance(true, car, interceptFrame.absoluteTime, true)
                    .withArrivalTime(interceptFrame.absoluteTime)
                    .withArrivalSpeed(MathUtils.remapClip((float) car.boost, 0, 50, DriveManeuver.max_throttle_speed, CarData.MAX_VELOCITY))
                    .withCreationStrategy(EpicMeshPlanner.PathCreationStrategy.YANGPATH);
            var pathOptional = pathPlanner.plan();

            if (pathOptional.isPresent()) {
                boolean isValid = true;
                if (pathOptional.get().getCurrentPathSegment().get() instanceof CurveSegment && ((CurveSegment) pathOptional.get().getCurrentPathSegment().get()).getBakedPath().tangentAt(-1).dot(car.forward()) < 0)
                    isValid = false;
                if (pathOptional.get().getTotalTimeEstimate() < interceptFrame.relativeTime && isValid) {
                    validPath = pathOptional.get();
                    arrivalTime = interceptFrame.absoluteTime;
                    targetBallPos = targetPos;
                    break;
                }
            }

            t = interceptFrame.relativeTime;
            t += RLConstants.simulationTickFrequency * 4; // 15 ticks / s
        } while (t < ballPrediction.relativeTimeOfLastFrame());

        if (validPath == null)
            return false;

        this.strikeAbstraction = new DriveStrikeAbstraction(validPath, new DefensiveGrader());
        this.strikeAbstraction.arrivalTime = arrivalTime;
        this.strikeAbstraction.maxJumpDelay = 0.6f;
        this.strikeAbstraction.jumpDelayStep = 0.15f;

        float zDiff = targetBallPos.z - 0.5f * BallData.COLLISION_RADIUS - car.position.z;
        if (zDiff < 5)
            this.strikeAbstraction.jumpBeforeStrikeDelay = 0.2f;
        else
            this.strikeAbstraction.jumpBeforeStrikeDelay = MathUtils.clip(CarData.getJumpTimeForHeight(zDiff, gameData.getGravity().z), 0.2f, 1f);

        this.strikeAbstraction.maxJumpDelay = Math.max(0.6f, this.strikeAbstraction.jumpBeforeStrikeDelay + 0.1f);
        this.strikeAbstraction.jumpDelayStep = Math.max(0.1f, (this.strikeAbstraction.maxJumpDelay - /*duration*/ 0.2f) / 5 - 0.02f);

        this.state = State.FOLLOW_PATH_STRIKE;
        if (debug)
            this.strikeAbstraction.debugMessages = true;
        return true;
    }

    @Override
    protected void planStrategyInternal() {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final ImmutableBallData ball = gameData.getBallData();
        final YangBallPrediction ballPrediction = gameData.getBallPrediction();

        this.ballTouchInterrupt = InterruptManager.get().getBallTouchInterrupt(-1);
        this.idleAbstraction = new IdleAbstraction();
        this.idleAbstraction.minIdleDistance = 500;
        this.strikeAbstraction = null;
        this.state = State.INVALID;

        if (this.checkReset(1f))
            return;

        if (!car.hasWheelContact) {
            this.setDone();
            return;
        }

        int teamSign = car.team * 2 - 1;

        final float MAX_HEIGHT_GROUND_SHOT = 230f + BallData.COLLISION_RADIUS * 0.65f;
        final float MAX_HEIGHT_DOUBLEJUMP = 500;
        final float MAX_HEIGHT_AERIAL = RLConstants.arenaHeight - 200;

        // Getting scored on
        {
            Optional<YangBallPrediction.YangPredictionFrame> firstConcedingGoalFrame = ballPrediction.getFramesBeforeRelative(4f)
                    .stream()
                    .filter((f) -> (int) Math.signum(f.ballData.position.y) == teamSign && f.ballData.makeMutable().isInOwnGoal(teamSign))
                    .findFirst();

            if (firstConcedingGoalFrame.isPresent()) { // We getting scored on
                final YangBallPrediction.YangPredictionFrame frameConceding = firstConcedingGoalFrame.get();

                YangBallPrediction framesBeforeGoal = ballPrediction.getBeforeRelative(frameConceding.relativeTime);
                if (framesBeforeGoal.frames.size() == 0) {
                    this.state = State.BALLCHASE;
                    return;
                }

                var aerialFrames = YangBallPrediction.from(framesBeforeGoal.frames.stream()
                        .filter((frame) -> frame.relativeTime < 3.5f)
                        .filter((frame) -> frame.ballData.position.z < MAX_HEIGHT_AERIAL && frame.ballData.position.z > MAX_HEIGHT_DOUBLEJUMP)
                        .collect(Collectors.toList()), framesBeforeGoal.tickFrequency);

                if (this.planAerialIntercept(aerialFrames, false))
                    return;

                var groundFrames = YangBallPrediction.from(framesBeforeGoal.frames.stream()
                        .filter((frame) -> frame.ballData.position.z < MAX_HEIGHT_GROUND_SHOT)
                        .collect(Collectors.toList()), framesBeforeGoal.tickFrequency);

                if (this.planGroundIntercept(groundFrames, false))
                    return;

                this.state = State.IDLE;
                return;
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
                    .filter((f) -> f.ballData.position.z < 1000 && defendArea.contains(f.ballData.position.flatten()))
                    .findFirst();


            if (ballInDefendAreaFrame.isPresent()) {
                final YangBallPrediction.YangPredictionFrame frameAreaEnter = ballInDefendAreaFrame.get();

                YangBallPrediction framesBeforeAreaEnter = ballPrediction.getBeforeRelative(frameAreaEnter.relativeTime + 0.5f);
                if (framesBeforeAreaEnter.frames.size() > 0) {

                    if (car.boost > 10) {
                        var aerialFrames = YangBallPrediction.from(framesBeforeAreaEnter.frames.stream()
                                .filter((frame) -> frame.relativeTime < 3.5f)
                                .filter((frame) -> frame.ballData.position.z < MAX_HEIGHT_AERIAL && frame.ballData.position.z > MAX_HEIGHT_DOUBLEJUMP)
                                .collect(Collectors.toList()), framesBeforeAreaEnter.tickFrequency);

                        if (this.planAerialIntercept(aerialFrames, false))
                            return;
                    }


                    var groundFrames = YangBallPrediction.from(framesBeforeAreaEnter.frames.stream()
                            .filter((frame) -> frame.ballData.position.z < MAX_HEIGHT_GROUND_SHOT)
                            .collect(Collectors.toList()), framesBeforeAreaEnter.tickFrequency);

                    if (this.planGroundIntercept(groundFrames, false))
                        return;
                }
            }
        }

        // Clear it
        final Vector2 ownGoal = new Vector2(0, teamSign * RLConstants.goalDistance);
        if (car.position.flatten().distance(ownGoal) < ball.position.flatten().distance(ownGoal)) {
            // Just boom it bruh
            {
                YangBallPrediction framesBeforeGoal = ballPrediction.getBeforeRelative(3f);
                final float carToOwnGoalDist = (float) car.position.flatten().distance(ownGoal);
                framesBeforeGoal = YangBallPrediction.from(framesBeforeGoal.frames.stream()
                        // Reachable on z axis
                        .filter((frame) -> frame.ballData.position.z < MAX_HEIGHT_GROUND_SHOT)

                        // car is closer to goal than ball (Hitting away from goal)
                        .map((frame) -> new Tuple<>(frame, frame.ballData.position.flatten().distance(ownGoal)))
                        .filter((frameTup) -> Math.signum(frameTup.getValue() - carToOwnGoalDist) == 1)
                        .map(Tuple::getKey)

                        /*.filter((frame) -> {
                           var ballData = frame.ballData;
                           float yDist = Math.abs(car.position.y - ballData.position.y);
                           float xDist = Math.abs(car.position.x - ballData.position.x);
                           return yDist > xDist * 0.65f;
                       })*/
                        .collect(Collectors.toList()), framesBeforeGoal.tickFrequency);

                if (framesBeforeGoal.frames.size() > 0 && this.planGroundIntercept(framesBeforeGoal, false))
                    return;
            }

            //if (this.planGoForBoost())
            //    return;

            this.state = State.IDLE; // We are in a defensive position
            return;
        }
        // Rotate
        {
            var rotateTarget = ownGoal.withZ(RLConstants.carElevation);

            var builder = new PathBuilder(car).optimize();
            if (car.position.z > 30 || builder.getCurrentPosition().distance(rotateTarget) < 30) {
                var atba = new AtbaSegment(builder.getCurrentPosition(), builder.getCurrentSpeed(), rotateTarget);
                builder.add(atba);
            } else if (car.angularVelocity.magnitude() < 0.1f && car.forwardSpeed() > 300 /*&& Math.abs(car.forward().flatten().angleBetween(rotateTarget.sub(car.position).flatten().normalized())) > 45 * (Math.PI / 180)*/) {
                var drift = new DriftSegment(builder.getCurrentPosition(), builder.getCurrentTangent(), rotateTarget.sub(car.position).normalized(), builder.getCurrentSpeed());
                builder.add(drift);
            } else {
                var turn = new TurnCircleSegment(car.toPhysics2d(), 1 / DriveManeuver.maxTurningCurvature(Math.max(1100, builder.getCurrentSpeed())), rotateTarget.flatten());
                if (turn.tangentPoint != null)
                    builder.add(turn);
            }

            if (builder.getCurrentPosition().distance(rotateTarget) > 20)
                builder.add(new StraightLineSegment(builder.getCurrentPosition(), builder.getCurrentSpeed(), rotateTarget));

            this.segmentedPath = builder.build();
            this.state = State.ROTATE;
        }
        assert this.state != State.INVALID;
    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {
        assert !this.reevaluateStrategy(4f) : this.state.name();

        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final AdvancedRenderer renderer = gameData.getAdvancedRenderer();

        switch (this.state) {
            case AERIAL: {

                if (this.reevaluateStrategy(3.5f))
                    return; // Aerial shouldn't exceed this duration anyways

                this.aerialAbstraction.draw(renderer);
                this.aerialAbstraction.step(dt, controlsOutput);
                if (this.aerialAbstraction.isDone() && this.reevaluateStrategy(0f))
                    return;

                break;
            }
            case ROTATE: {

                if (this.segmentedPath.canInterrupt() && this.reevaluateStrategy(this.ballTouchInterrupt))
                    return;

                if (this.segmentedPath.shouldBeOnGround() && !car.hasWheelContact && this.reevaluateStrategy(0.05f))
                    return;

                if (this.reevaluateStrategy(this.segmentedPath.canInterrupt() ? 0.2f : 1.8f))
                    return;

                this.segmentedPath.draw(renderer);
                if (this.segmentedPath.step(dt, controlsOutput) && this.reevaluateStrategy(0))
                    return;
                /*
                if (this.state == State.ROTATE) {
                    final Vector2 ownGoal = new Vector2(0, teamSign * RLConstants.goalDistance);
                    final float distanceCarToDefend = (float) car.position.add(car.velocity.mul(0.3f)).flatten().distance(ownGoal);
                    final float distanceBallToDefend = (float) ballPrediction.getFrameAtRelativeTime(0.5f).get().ballData.position.flatten().distance(ownGoal);

                    if (distanceCarToDefend + 300 > distanceBallToDefend && (this.followPathManeuver.arrivalTime < 0 || this.followPathManeuver.arrivalTime - car.elapsedSeconds > 0.4f)) {
                        this.followPathManeuver.arrivalSpeed = 2200;
                    } else {
                        // Arrival in 0.4s?
                        if (Math.max(1, car.position.distance(this.followPathManeuver.path.pointAt(0))) / Math.max(1, car.velocity.flatten().magnitude()) < 0.4f)
                            this.followPathManeuver.arrivalSpeed = DriveManeuver.max_throttle_speed - 10;
                        else
                            this.followPathManeuver.arrivalSpeed = -1;
                    }
                }
                 */
                break;
            }
            case GET_BOOST: {

                if (this.segmentedPath.canInterrupt() && this.reevaluateStrategy(this.ballTouchInterrupt))
                    return;

                if (car.position.z > 50 && car.hasWheelContact) {
                    DriveManeuver.steerController(controlsOutput, car, car.position.withZ(RLConstants.carElevation));
                    controlsOutput.withThrottle(1);
                    return;
                }

                if (this.segmentedPath.shouldReset(car) && this.reevaluateStrategy(0))
                    return;

                if (this.reevaluateStrategy(this.segmentedPath.canInterrupt() ? 0.3f : 2f))
                    return;

                this.segmentedPath.step(dt, controlsOutput);

                if (this.segmentedPath.isDone() && this.reevaluateStrategy(0f))
                    return;

                break;
            }
            case FOLLOW_PATH_STRIKE: {
                if (!car.hasWheelContact && this.strikeAbstraction.canInterrupt() && this.reevaluateStrategy(0.05f))
                    return;

                if (this.strikeAbstraction.canInterrupt() && this.reevaluateStrategy(2f))
                    return;

                if (this.strikeAbstraction.canInterrupt() && this.reevaluateStrategy(ballTouchInterrupt))
                    return;

                this.strikeAbstraction.step(dt, controlsOutput);

                if (this.strikeAbstraction.isDone() && this.reevaluateStrategy(0f))
                    return;

                break;
            }
            case BALLCHASE: {
                if (this.reevaluateStrategy(0.05f) || this.reevaluateStrategy(this.ballTouchInterrupt))
                    return;
                DefaultStrategy.smartBallChaser(dt, controlsOutput);
                break;
            }
            case IDLE: {
                if (this.reevaluateStrategy(this.idleAbstraction.canInterrupt() ? 0.1f : 2f))
                    return;

                if (this.idleAbstraction.canInterrupt() && this.reevaluateStrategy(ballTouchInterrupt))
                    return;

                this.idleAbstraction.step(dt, controlsOutput);
                if (this.idleAbstraction.isDone() && this.reevaluateStrategy(0))
                    return;
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
        ROTATE,
        FOLLOW_PATH_STRIKE,
        BALLCHASE,
        IDLE
    }
}
