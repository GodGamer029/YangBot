package yangbot.strategy;

import yangbot.input.*;
import yangbot.input.fieldinfo.BoostManager;
import yangbot.input.fieldinfo.BoostPad;
import yangbot.input.interrupt.BallTouchInterrupt;
import yangbot.input.interrupt.InterruptManager;
import yangbot.optimizers.graders.DefensiveGrader;
import yangbot.path.Curve;
import yangbot.path.EpicMeshPlanner;
import yangbot.strategy.abstraction.IdleAbstraction;
import yangbot.strategy.abstraction.StrikeAbstraction;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.strategy.manuever.FollowPathManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.Area2;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class DefendStrategy extends Strategy {

    private State state = State.INVALID;
    private FollowPathManeuver followPathManeuver = new FollowPathManeuver();
    private static float xDefendDist = RLConstants.goalCenterToPost + 400;
    private static float yDefendDist = RLConstants.goalDistance * 0.4f;
    private StrikeAbstraction strikeAbstraction;
    private BallTouchInterrupt ballTouchInterrupt = null;
    private IdleAbstraction idleAbstraction;

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
                }
            }
        }

        return this.state == State.GET_BOOST;
    }

    private boolean planIntercept(YangBallPrediction ballPrediction) {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();

        if (car.position.z > 50)
            return false;

        Curve validPath = null;
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
            final Vector3 endTangent = new Vector3(0, -car.getTeamSign(), 0);

            var pathPlanner = new EpicMeshPlanner()
                    .withStart(car, 10)
                    .withEnd(targetPos.withZ(car.position.z).sub(endTangent.mul(BallData.COLLISION_RADIUS * 0.7f)), endTangent)
                    .withBallAvoidance(true, car, interceptFrame.absoluteTime, true)
                    .withCreationStrategy(EpicMeshPlanner.PathCreationStrategy.SIMPLE);
            Optional<Curve> curveOptional = pathPlanner.plan();

            if (curveOptional.isPresent()) {
                boolean isValid = pathPlanner.isUsingBallAvoidance();
                if (!isValid) {
                    var curv = curveOptional.get();
                    var status = curv.doPathChecking(car, interceptFrame.absoluteTime, ballPrediction);
                    isValid = status.isValid();
                }

                if (isValid) {
                    validPath = curveOptional.get();
                    arrivalTime = interceptFrame.absoluteTime;
                    targetBallPos = targetPos;
                }

                break;
            }

            t = interceptFrame.relativeTime;
            t += RLConstants.simulationTickFrequency * 4; // 15 ticks / s
        } while (t < ballPrediction.relativeTimeOfLastFrame());

        if (validPath == null)
            return false;

        this.strikeAbstraction = new StrikeAbstraction(validPath, new DefensiveGrader());
        this.strikeAbstraction.arrivalTime = arrivalTime;
        this.strikeAbstraction.maxJumpDelay = 0.6f;
        this.strikeAbstraction.jumpDelayStep = 0.15f;
        float zDiff = targetBallPos.z - car.position.z;
        if (zDiff < 50)
            this.strikeAbstraction.jumpBeforeStrikeDelay = 0.3f;
        else
            this.strikeAbstraction.jumpBeforeStrikeDelay = MathUtils.clip(CarData.getJumpTimeForHeight(zDiff, gameData.getGravity().z), 0.3f, 0.6f);

        this.state = State.FOLLOW_PATH_STRIKE;
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
        this.idleAbstraction.maxIdleDistance = 3000;
        this.strikeAbstraction = null;
        this.state = State.INVALID;

        if (this.checkReset(1f))
            return;

        if (!car.hasWheelContact) {
            this.setDone();
            return;
        }

        int teamSign = car.team * 2 - 1;

        // Own goaling
        {
            Optional<YangBallPrediction.YangPredictionFrame> firstConcedingGoalFrame = ballPrediction.getFramesBeforeRelative(4f)
                    .stream()
                    .filter((f) -> (int) Math.signum(f.ballData.position.y) == teamSign && f.ballData.makeMutable().isInOwnGoal(teamSign))
                    .findFirst();

            if (firstConcedingGoalFrame.isPresent()) { // We getting scored on
                final YangBallPrediction.YangPredictionFrame frameConceding = firstConcedingGoalFrame.get();

                YangBallPrediction framesBeforeGoal = ballPrediction.getBeforeRelative(frameConceding.relativeTime);
                if (framesBeforeGoal.frames.size() == 0) {
                    return;
                }

                framesBeforeGoal = YangBallPrediction.from(framesBeforeGoal.frames.stream()
                        .filter((frame) -> frame.ballData.position.z < 250)
                        .collect(Collectors.toList()), framesBeforeGoal.tickFrequency);

                if (!this.planIntercept(framesBeforeGoal))
                    this.state = State.BALLCHASE;

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


            if (ballInDefendAreaFrame.isPresent()) { // We getting scored on
                final YangBallPrediction.YangPredictionFrame frameAreaEnter = ballInDefendAreaFrame.get();

                YangBallPrediction framesBeforeAreaEnter = ballPrediction.getBeforeRelative(frameAreaEnter.relativeTime + 0.5f);
                if (framesBeforeAreaEnter.frames.size() == 0) {
                    return;
                }

                framesBeforeAreaEnter = YangBallPrediction.from(framesBeforeAreaEnter.frames.stream()
                        .filter((frame) -> frame.ballData.position.z < 250)
                        .collect(Collectors.toList()), framesBeforeAreaEnter.tickFrequency);

                if (this.planIntercept(framesBeforeAreaEnter))
                    return;
            }
        }


        final Vector2 ownGoal = new Vector2(0, teamSign * RLConstants.goalDistance);
        if (car.position.flatten().distance(ownGoal) < ball.position.flatten().distance(ownGoal)) {
            // Just boom it bruh
            {
                YangBallPrediction framesBeforeGoal = ballPrediction.getBeforeRelative(3f);

                framesBeforeGoal = YangBallPrediction.from(framesBeforeGoal.frames.stream()
                        .filter((frame) -> frame.ballData.position.z < 250)
                        .filter((frame) -> Math.signum(car.position.y - frame.ballData.position.y) == car.getTeamSign())
                        .filter((frame) -> {
                            var ballData = frame.ballData;
                            float yDist = Math.abs(car.position.y - ballData.position.y);
                            float xDist = Math.abs(car.position.x - ballData.position.x);
                            return yDist > xDist * 0.75f;
                        })
                        .collect(Collectors.toList()), framesBeforeGoal.tickFrequency);

                if (framesBeforeGoal.frames.size() > 0 && this.planIntercept(framesBeforeGoal))
                    return;

            }

            if (this.planGoForBoost())
                return;

            state = State.IDLE; // We are in a defensive position
        } else {
            // Rotate
            Optional<Curve> pathOpt = new EpicMeshPlanner()
                    .withStart(car)
                    .withEnd(new Vector3(0, RLConstants.goalDistance * 0.9f * teamSign, RLConstants.carElevation), new Vector3(0, teamSign, 0))
                    .withBallAvoidance(true, car, -1, false)
                    .plan();

            assert pathOpt.isPresent() : "Path should be present when no ball avoidance is required";

            this.followPathManeuver.path = pathOpt.get();
            this.followPathManeuver.arrivalTime = -1;
            this.followPathManeuver.arrivalSpeed = -1;
            this.state = State.ROTATE;
        }

    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {
        assert !this.reevaluateStrategy(4f) : this.state.name();

        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final AdvancedRenderer renderer = gameData.getAdvancedRenderer();
        final YangBallPrediction ballPrediction = gameData.getBallPrediction();

        final int teamSign = car.getTeamSign();

        final Area2 defendArea = new Area2(List.of(
                new Vector2(-xDefendDist, RLConstants.goalDistance * teamSign),
                new Vector2(xDefendDist, RLConstants.goalDistance * teamSign),
                new Vector2(xDefendDist * 1.7f, (RLConstants.goalDistance - yDefendDist) * teamSign),
                new Vector2(-xDefendDist * 1.7f, (RLConstants.goalDistance - yDefendDist) * teamSign)
        ));
        defendArea.draw(renderer, 50);

        switch (this.state) {
            case GET_BOOST:
            case ROTATE: {

                if (this.reevaluateStrategy(this.ballTouchInterrupt))
                    return;

                if (car.position.z > 50) {
                    DriveManeuver.steerController(controlsOutput, car, car.position.withZ(RLConstants.carElevation));
                    controlsOutput.withThrottle(1);
                    return;
                }

                if (!car.hasWheelContact && this.reevaluateStrategy(0.05f))
                    return;

                if (this.reevaluateStrategy(this.followPathManeuver.arrivalTime == -1 ? 0.5f : 1.5f))
                    return;

                this.followPathManeuver.path.draw(renderer, Color.YELLOW);
                this.followPathManeuver.step(dt, controlsOutput);

                if (this.followPathManeuver.isDone()) {
                    this.reevaluateStrategy(0f);
                    return;
                }

                final float distanceOffPath = (float) car.position.flatten().distance(this.followPathManeuver.path.pointAt(this.followPathManeuver.path.findNearest(car.position)).flatten());

                if (distanceOffPath > 100 && this.reevaluateStrategy(0.25f))
                    return;


                if (state == State.ROTATE) {
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
                } else if (state == State.GET_BOOST) {
                    this.followPathManeuver.arrivalSpeed = -1;
                }

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
                if (this.reevaluateStrategy(0.1f) || this.reevaluateStrategy(this.ballTouchInterrupt))
                    return;
                DefaultStrategy.smartBallChaser(dt, controlsOutput);
                break;
            }
            case IDLE: {
                if (this.reevaluateStrategy(this.idleAbstraction.canInterrupt() ? 0.3f : 1.5f) || this.reevaluateStrategy(ballTouchInterrupt, this.idleAbstraction.canInterrupt() ? 0.1f : 0.9f))
                    return;
                this.idleAbstraction.step(dt, controlsOutput);
                if (this.idleAbstraction.isDone() && this.reevaluateStrategy(0))
                    return;
                break;
            }
            default:
                assert false : this.state.name();
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
        GET_BOOST,
        ROTATE,
        FOLLOW_PATH_STRIKE,
        BALLCHASE,
        IDLE
    }
}
