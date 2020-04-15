package yangbot.strategy;

import yangbot.cpp.YangBotJNAInterop;
import yangbot.input.*;
import yangbot.input.fieldinfo.BoostManager;
import yangbot.input.fieldinfo.BoostPad;
import yangbot.input.interrupt.BallTouchInterrupt;
import yangbot.input.interrupt.InterruptManager;
import yangbot.optimizers.graders.OffensiveGrader;
import yangbot.path.Curve;
import yangbot.path.EpicPathPlanner;
import yangbot.strategy.abstraction.StrikeAbstraction;
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
import java.util.function.Function;
import java.util.stream.Collectors;

public class OffensiveStrategy extends Strategy {

    private StrikeAbstraction strikeAbstraction = new StrikeAbstraction(new OffensiveGrader());
    private FollowPathManeuver followPathManeuver = new FollowPathManeuver();
    private State state = State.INVALID;
    private RotationAdvisor.Advice lastAdvice = RotationAdvisor.Advice.NO_ADVICE;
    private BallTouchInterrupt ballTouchInterrupt;
    private Vector3 drivePosition;

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

                    Curve path = new EpicPathPlanner()
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
                .filter((frame) -> (frame.ballData.position.z <= BallData.COLLISION_RADIUS + 120 /*|| (allowWallHits && RLConstants.isPosNearWall(frame.ballData.position.flatten(), BallData.COLLISION_RADIUS * 1.5f))*/)
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

        float oldPathArrival = this.strikeAbstraction.arrivalTime;
        if (oldPathArrival < car.elapsedSeconds || oldPathArrival > strikeableFrames.get(strikeableFrames.size() - 1).absoluteTime)
            oldPathArrival = -1;
        else
            oldPathArrival -= car.elapsedSeconds;

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
            final Vector3 carToDriveTarget = ballHitTarget.sub(startPosition).normalized();
            final Vector3 endTangent = carToDriveTarget.mul(4).add(ballTargetToGoalTarget).withZ(0).normalized();

            Curve currentPath;
            Optional<Curve> curveOptional = Optional.empty();
            if ((RLConstants.isPosNearWall(startPosition.flatten(), 100) || RLConstants.isPosNearWall(ballHitTarget.flatten(), 50)) && startPosition.distance(ballHitTarget) > 400)
                curveOptional = YangBotJNAInterop.findPath(startPosition, startTangent, ballHitTarget, endTangent, 25);

            if (curveOptional.isPresent())
                currentPath = curveOptional.get();
            else {
                currentPath = new EpicPathPlanner()
                        .withStart(startPosition, startTangent)
                        .withEnd(ballHitTarget, endTangent.mul(-1))
                        .plan().get();
            }

            if (currentPath.length == 0 || Float.isNaN(currentPath.length) || currentPath.points.size() == 0)
                continue;

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
        this.strikeAbstraction = new StrikeAbstraction(validPath);
        this.strikeAbstraction.arrivalTime = arrivalTime;
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

        this.state = State.DRIVE_AT_POINT;
        this.drivePosition = endPos;
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

        final RotationAdvisor.Advice rotationAdvice = RotationAdvisor.whatDoIDo(gameData);
        this.lastAdvice = rotationAdvice;
        switch (rotationAdvice) {
            case PREPARE_FOR_PASS:
            case IDLE:
                this.state = State.IDLE;
                if (car.position.flatten().distance(ball.position.flatten()) > 1000) {
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

    private Vector2 findIdlePosition(CarData localCar, List<CarData> teammates, Vector2 futureBallPos) {
        assert teammates.size() > 0;

        final int teamSign = localCar.getTeamSign();
        final AdvancedRenderer renderer = GameData.current().getAdvancedRenderer();
        final Vector2 ownGoal = new Vector2(0, teamSign * RLConstants.goalDistance);

        // Distances will be scaled back if they exceed this value
        final float maxAbsoluteIdleY = RLConstants.arenaHalfLength - 120;
        final float maxAbsoluteIdleX = RLConstants.arenaHalfWidth - 100;

        final Line2 goalToBall = new Line2(
                ownGoal,
                (Math.abs(futureBallPos.y) > maxAbsoluteIdleY - 10) ?
                        futureBallPos.withY((maxAbsoluteIdleY - 10) * Math.signum(futureBallPos.y)) : futureBallPos);

        // Predict teammate in future in x seconds
        final float teammatePredictionDelay = 0.5f;

        // Y-Distance from ball when idle
        // These may be scaled down, if too close to own goal
        float minIdleDistance = 1500;
        float maxIdleDistance = 4000;

        // Scale back idling distances (Too close to own goal for example)
        {
            float effectiveIdlingDistance = futureBallPos.y + maxIdleDistance * teamSign;
            if (Math.signum(effectiveIdlingDistance) == teamSign && Math.abs(effectiveIdlingDistance) > maxAbsoluteIdleY) {

                float diff = Math.abs(effectiveIdlingDistance) - maxAbsoluteIdleY;
                float proportion = 1 - (diff / maxIdleDistance);
                maxIdleDistance *= proportion;
                minIdleDistance *= proportion;
            }
        }

        final float effectiveMinIdleDistance = minIdleDistance;
        final float effectiveMaxIdleDistance = maxIdleDistance;

        // Grids: higher resolution if we have more teammates
        float[] yGrid = new float[Math.max(15, teammates.size() * 3)];

        // These functions return the absolute position values on the field
        final Function<Integer, Float> yIndexToAbs = ind -> {
            assert ind >= 0 && ind < yGrid.length : ind;

            float gridSpot = MathUtils.remap(ind, 0, yGrid.length - 1, effectiveMinIdleDistance, effectiveMaxIdleDistance);
            return teamSign * gridSpot + futureBallPos.y;
        };

        // Fill grid with distances from teammates
        for (var mate : teammates) {
            // Y-Distances
            for (int i = 0; i < yGrid.length; i++) {
                float posY = yIndexToAbs.apply(i);
                float futureMateYPos = mate.position.y + mate.velocity.y * teammatePredictionDelay;

                float distance = MathUtils.distance(posY, futureMateYPos);
                yGrid[i] += 1 / Math.max(distance, 10);
            }
        }

        int lowestYSpot = 0;

        // Y
        {
            float lowestYDist = 9999999;
            float highestYDist = 0;
            for (int i = 0; i < yGrid.length; i++) {
                if (localCar.boost < 10 && i < yGrid.length / 2)
                    continue; // When on low boost: go back farther

                if (yGrid[i] < lowestYDist) {
                    lowestYDist = yGrid[i];
                    lowestYSpot = i;
                }
                if (yGrid[i] > highestYDist) {
                    highestYDist = yGrid[i];
                }
            }

            // Draw
            if (false) {
                for (int i = 0; i < yGrid.length; i++) {
                    float yPos = yIndexToAbs.apply(i);
                    float val = 1 - MathUtils.clip(MathUtils.remap(yGrid[i], lowestYDist, highestYDist, 0, 1), 0, 1);
                    var col = new Color(val, val, val);

                    if (i == lowestYSpot)
                        col = Color.GREEN;

                    float ySize = (effectiveMaxIdleDistance - effectiveMinIdleDistance) / (yGrid.length - 1);

                    renderer.drawCentered3dCube(col, new Vector3(localCar.position.x, yPos, 50), new Vector3(10, ySize, 200));
                    //renderer.drawString3d(String.format("%.5f", yGrid[i]), Color.WHITE, new Vector3(localCar.position.x, yPos, 200), 1, 1);
                }
            }
        }

        float[] xGrid = new float[Math.max(6, teammates.size() * 2)];
        final float decidedYPos = yIndexToAbs.apply(lowestYSpot);
        final float xChannelHalfWidth = RLConstants.goalCenterToPost * 1.2f;

        assert maxAbsoluteIdleX > xChannelHalfWidth;

        final Function<Integer, Float> xIndexToAbs = ind -> {
            float relativeX = MathUtils.remap(ind, 0, xGrid.length - 1, -xChannelHalfWidth, xChannelHalfWidth);
            var intersectionOpt = goalToBall.getIntersectionPointWithInfOtherLine(new Line2(new Vector2(-1, decidedYPos), new Vector2(1, decidedYPos)));
            assert intersectionOpt.isPresent() : goalToBall.toString() + " " + decidedYPos;
            float intersectedX = intersectionOpt.get().x;
            if (Math.abs(intersectedX) + xChannelHalfWidth > maxAbsoluteIdleX)
                intersectedX = (maxAbsoluteIdleX - xChannelHalfWidth) * Math.signum(intersectedX);

            return relativeX + intersectedX;
        };

        // Pretend like there are teammates at the edges of the xGrid
        // Prevents cluttering of bots at unnecessary positions
        for (int i = 0; i < xGrid.length; i++) {
            // Negative x
            {
                float posX = xIndexToAbs.apply(i);
                float distance = MathUtils.distance(posX, -xChannelHalfWidth);

                xGrid[i] += 1 / Math.max(distance, 10);
            }
            // Positive x
            {
                float posX = xIndexToAbs.apply(i);
                float distance = MathUtils.distance(posX, xChannelHalfWidth);

                xGrid[i] += 1 / Math.max(distance, 10);
            }
        }

        for (var mate : teammates) {
            // X-Distances
            for (int i = 0; i < xGrid.length; i++) {
                float posX = xIndexToAbs.apply(i);
                float futureMateXPos = mate.position.x + mate.velocity.x * teammatePredictionDelay;

                float distance = MathUtils.distance(posX, futureMateXPos);
                xGrid[i] += 1 / Math.max(distance, 10);
            }
        }

        int lowestXSpot = 0;
        // X
        {
            float highestXDist = 0;
            float lowestXDist = 9999999;
            for (int i = 0; i < xGrid.length; i++) {
                if (xGrid[i] < lowestXDist) {
                    lowestXSpot = i;
                    lowestXDist = xGrid[i];
                }
                if (xGrid[i] > highestXDist) {
                    highestXDist = xGrid[i];
                }
            }

            // Draw
            {
                float yPos = yIndexToAbs.apply(lowestYSpot);
                for (int i = 0; i < xGrid.length; i++) {
                    float xPos = xIndexToAbs.apply(i);
                    float val = 1 - MathUtils.clip(MathUtils.remap(xGrid[i], lowestXDist, highestXDist, 0, 1), 0, 1);
                    var col = new Color(val, val, val);

                    if (i == lowestXSpot)
                        col = Color.GREEN;

                    float xSize = (xChannelHalfWidth * 2) / (xGrid.length - 1);

                    renderer.drawCentered3dCube(col, new Vector3(xPos, yPos, 50), new Vector3(xSize, 150, 100));
                    //renderer.drawString3d(String.format("%.5f", xGrid[i]), Color.WHITE, new Vector3(xPos, yPos, 200), 1, 1);
                }
            }
        }

        return new Vector2(xIndexToAbs.apply(lowestXSpot), yIndexToAbs.apply(lowestYSpot));
    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {

        var oldState = state;
        if (this.reevaluateStrategy(4f)) {
            assert false : "States/Abstractions didn't finish automatically! (" + oldState.getClass().getSimpleName() + ")";
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
                this.followPathManeuver.step(dt, controlsOutput);
                //this.followPathManeuver.draw(renderer, car);
                this.followPathManeuver.path.draw(renderer);
                if (this.followPathManeuver.isDone()) {
                    this.reevaluateStrategy(0);
                    return;
                }

                if (!car.hasWheelContact && this.reevaluateStrategy(0.05f))
                    return;

                if (this.reevaluateStrategy(ballTouchInterrupt, 0.05f) || this.reevaluateStrategy(2f))
                    return;

                break;
            }
            case IDLE: {

                if (!car.hasWheelContact && this.reevaluateStrategy(0.05f))
                    return;

                // Go back to ball if we are completely out of the play (demolished probably)
                if (car.position.flatten().distance(ball.position.flatten()) > RLConstants.arenaLength * 0.8f) {
                    DefaultStrategy.smartBallChaser(dt, controlsOutput);
                    break;
                }

                if (this.reevaluateStrategy(ballTouchInterrupt, 0.05f) || this.reevaluateStrategy(1f))
                    return;

                // Use position of the ball in x seconds
                final float futureBallPosDelay = 0.3f;

                // Clips idlingTarget
                final float maxX = RLConstants.arenaHalfWidth * 0.9f;
                final float maxY = RLConstants.arenaHalfLength;

                // Actual Idling distances the bot will follow (Changed when not alone on team)
                float preferredIdlingY = 1800;
                float preferredIdlingX = 0;

                final Vector2 futureBallPos;

                // Calculate where the ball will go in the future
                // TODO: estimate where the ball will be shot for preemptive rotation
                {
                    Vector2 futureBallPosTemp;

                    var frameOpt = ballPrediction.getFrameAtRelativeTime(futureBallPosDelay);
                    if (frameOpt.isPresent())
                        futureBallPosTemp = frameOpt.get().ballData.position.flatten();
                    else
                        futureBallPosTemp = ball.position.flatten().add(ball.velocity.flatten().mul(futureBallPosDelay));

                    // Don't over-commit if the ball is rolling towards opponents
                    if (Math.signum(futureBallPosTemp.y - ball.position.y) == -teamSign)
                        futureBallPosTemp = ball.position.flatten();

                    futureBallPos = futureBallPosTemp;
                }

                // find out where my teammates are idling
                List<CarData> teammates = gameData.getAllCars().stream()
                        .filter(c -> c.team == car.team && c.playerIndex != car.playerIndex)
                        //.filter(c -> RotationAdvisor.isInfrontOfBall(c, ball))
                        .collect(Collectors.toList());

                if (teammates.size() > 0) {
                    var idlePos = this.findIdlePosition(car, teammates, futureBallPos);
                    preferredIdlingX = idlePos.x;
                    preferredIdlingY = idlePos.y;

                    assert Math.abs(preferredIdlingY) < RLConstants.arenaHalfLength : idlePos.toString();
                } else {
                    // Convert relative to absolute
                    preferredIdlingY = futureBallPos.y + teamSign * preferredIdlingY;
                }

                // Hover around the middle area
                Vector3 idleTarget = new Vector3(
                        MathUtils.clip(preferredIdlingX, -maxX, maxX),
                        MathUtils.clip(preferredIdlingY, -maxY, maxY),
                        0);

                renderer.drawCentered3dCube(Color.YELLOW, idleTarget, 100);
                renderer.drawLine3d(Color.YELLOW, car.position, idleTarget.withZ(50));

                assert Math.abs(idleTarget.y) < RLConstants.goalDistance : idleTarget.toString();

                // Speed "controller"
                float carToIdleDist = (float) Math.abs(car.position.y - idleTarget.y);
                if (Math.signum(car.position.y - idleTarget.y) == teamSign)
                    carToIdleDist = 0;

                float minSpeed = DriveManeuver.max_throttle_speed * 0.9f;
                if (carToIdleDist > 800)
                    minSpeed = MathUtils.remapClip(carToIdleDist - 800, 0, 3000, minSpeed, CarData.MAX_VELOCITY * 0.95f);

                DriveManeuver.steerController(controlsOutput, car, idleTarget);
                DriveManeuver.speedController(dt, controlsOutput, (float) car.forward().dot(car.velocity), minSpeed, CarData.MAX_VELOCITY, 0.5f);

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
                    this.followPathManeuver.path.draw(renderer);
                    if (this.followPathManeuver.isDone() && this.reevaluateStrategy(0))
                        return;
                }

                break;
            }
            case FOLLOW_PATH_STRIKE: {
                this.strikeAbstraction.step(dt, controlsOutput);
                if (this.strikeAbstraction.isDone() && this.reevaluateStrategy(0))
                    return;

                if (this.strikeAbstraction.canInterrupt() && this.reevaluateStrategy(1.8f))
                    return;
                break;
            }
            case DRIVE_AT_POINT: {
                if (car.position.distance(this.drivePosition) < 200 && this.reevaluateStrategy(0.1f))
                    return;

                if (!car.hasWheelContact && this.reevaluateStrategy(0.05f))
                    return;

                if (this.reevaluateStrategy(0.5f))
                    return;


                DriveManeuver.steerController(controlsOutput, car, this.drivePosition);
                DriveManeuver.speedController(dt, controlsOutput, (float) car.forward().dot(car.velocity), DriveManeuver.max_throttle_speed * 0.9f, CarData.MAX_VELOCITY, 0.4f);
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
        DRIVE_AT_POINT,
        INVALID
    }
}
