package yangbot.strategy;

import javafx.util.Pair;
import yangbot.cpp.YangBotJNAInterop;
import yangbot.input.*;
import yangbot.input.fieldinfo.BoostManager;
import yangbot.input.fieldinfo.BoostPad;
import yangbot.optimizers.graders.OffensiveGrader;
import yangbot.path.Curve;
import yangbot.path.EpicPathPlanner;
import yangbot.strategy.abstraction.StrikeAbstraction;
import yangbot.strategy.advisor.RotationAdvisor;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.strategy.manuever.FollowPathManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.YangBallPrediction;
import yangbot.util.hitbox.YangCarHitbox;
import yangbot.util.math.Line2;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class OffensiveStrategy extends Strategy {

    private StrikeAbstraction strikeAbstraction = new StrikeAbstraction(new OffensiveGrader());
    private FollowPathManeuver followPathManeuver = new FollowPathManeuver();
    private State state = State.INVALID;
    private YangBallPrediction hitPrediction = null;
    private CarData hitCar = null;
    private Vector3 contactPoint = null;
    private Vector3 contactNormal = null;
    private BallData hitBall = null;
    private YangCarHitbox hitboxAtBallHit;
    private Vector3 positionAtBallHit;
    private float lastBallHit = 0;
    private Strategy suggestedStrat = null;
    private int idleDirection = 0;
    private RotationAdvisor.Advice lastAdvice = RotationAdvisor.Advice.NO_ADVICE;

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
                .filter((frame) -> (frame.ballData.position.z <= BallData.COLLISION_RADIUS + 80 /*|| (allowWallHits && RLConstants.isPosNearWall(frame.ballData.position.flatten(), BallData.COLLISION_RADIUS * 1.5f))*/)
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
            this.state = State.BALLCHASE;
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

        Optional<Curve> optionalCurve = new EpicPathPlanner()
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
    }

    @Override
    protected void planStrategyInternal() {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final int teamSign = car.getTeamSign();
        final ImmutableBallData ball = gameData.getBallData();
        final YangBallPrediction ballPrediction = gameData.getBallPrediction();
        this.state = State.INVALID;
        this.idleDirection = 0;

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
            return;
        }
    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {
        if (this.reevaluateStrategy(this.strikeAbstraction.canInterrupt() ? 1f : 1.4f))
            return;
        assert this.state != State.INVALID : "Invalid state! Last advice: " + this.lastAdvice;

        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final ImmutableBallData ball = gameData.getBallData();
        final AdvancedRenderer renderer = gameData.getAdvancedRenderer();
        final YangBallPrediction ballPrediction = gameData.getBallPrediction();

        final int teamSign = car.team * 2 - 1;
        final Vector2 ownGoal = new Vector2(0, teamSign * (RLConstants.goalDistance + 100));

        if (this.hitPrediction != null) {
            this.hitPrediction.draw(renderer, Color.MAGENTA, 0);
            this.hitCar.hitbox.draw(renderer, this.hitCar.position, 1, Color.GREEN);
            renderer.drawCentered3dCube(Color.GREEN, this.contactPoint, 10);
            renderer.drawLine3d(Color.MAGENTA, this.contactPoint, this.contactPoint.add(this.contactNormal.mul(100)));

            if (ball.hasBeenTouched()) {
                BallTouch latestTouch = ball.getLatestTouch();
                if (latestTouch.gameSeconds > this.lastBallHit) {
                    this.lastBallHit = latestTouch.gameSeconds;
                    this.hitboxAtBallHit = car.hitbox;
                    this.positionAtBallHit = car.position;
                }
                if (this.lastBallHit > 0) {
                    this.hitboxAtBallHit.draw(renderer, this.positionAtBallHit, 1, Color.BLUE);
                }
                renderer.drawCentered3dCube(Color.BLUE, latestTouch.position, 10);
                renderer.drawLine3d(Color.RED, latestTouch.position, latestTouch.position.add(latestTouch.normal.mul(100)));
            }

            renderer.drawCentered3dCube(Color.BLUE, this.hitBall.position, BallData.RADIUS * 2);
            renderer.drawCentered3dCube(Color.CYAN, ball.position, BallData.RADIUS * 2);
            car.hitbox.draw(renderer, car.position, 1, Color.BLUE);
        }

        switch (this.state) {
            case GO_BACK_SOMEHOW: {
                DriveManeuver.steerController(controlsOutput, car, ownGoal.withZ(0));
                break;
            }
            case BALLCHASE: {
                DefaultStrategy.smartBallChaser(dt, controlsOutput);
                break;
            }
            case GET_BOOST: {
                this.followPathManeuver.step(dt, controlsOutput);
                //this.followPathManeuver.draw(renderer, car);
                this.followPathManeuver.path.draw(renderer);
                if (this.followPathManeuver.isDone())
                    this.reevaluateStrategy(0);
                break;
            }
            case IDLE: {
                // Go back to ball if we are completely out of the play (demolished probably)
                if (car.position.flatten().distance(ball.position.flatten()) > RLConstants.arenaLength * 0.8f) {
                    DefaultStrategy.smartBallChaser(dt, controlsOutput);
                    break;
                }

                if (this.idleDirection == 0) {
                    this.idleDirection = (int) -Math.signum(car.position.x);
                }

                final float halfWidthFactor = 0.15f;

                // Move left and right on the field, always keeping a bit of distance to the ball
                if (Math.abs(car.position.x) > RLConstants.arenaHalfWidth * halfWidthFactor && Math.signum(car.position.x) == this.idleDirection)
                    this.idleDirection *= -1;

                Vector2 futureBallPosTemp = ball.position.flatten().add(ball.velocity.flatten().mul(0.3f));
                var frameOpt = ballPrediction.getFrameAtRelativeTime(0.3f);
                if (frameOpt.isPresent())
                    futureBallPosTemp = frameOpt.get().ballData.position.flatten();

                if (Math.signum(futureBallPosTemp.y - ball.position.y) == -teamSign)
                    futureBallPosTemp = ball.position.flatten();

                final Vector2 futureBallPos = futureBallPosTemp;

                float preferredIdlingDistance = 1800;
                float preferredIdlingX = 0;

                // find out where my teammates are idling
                List<Pair<CarData, /* idling distance */Float>> teammates = gameData.getAllCars().stream()
                        .filter(c -> c.team == car.team && c.playerIndex != car.playerIndex)
                        .filter(c -> RotationAdvisor.isInfrontOfBall(c, ball))
                        .map(c -> new Pair<>(c, Math.abs((c.position.y + car.velocity.y * 0.3f) - futureBallPos.y)))
                        .collect(Collectors.toList());

                final float maxX = RLConstants.arenaHalfWidth * 0.9f;

                if (teammates.size() > 0) {
                    float minIdleDistance = 1500;
                    float maxIdleDistance = 4500;

                    {
                        float temp = futureBallPos.y + maxIdleDistance * teamSign;
                        if (Math.signum(temp) == teamSign && Math.abs(temp) > RLConstants.arenaHalfLength - 100) {
                            // scale it back
                            float diff = Math.abs(temp) - RLConstants.arenaHalfLength - 100;
                            float range = maxIdleDistance - minIdleDistance;
                            float rangeOld = range;
                            float proportion = 1 - (diff / range);
                            proportion = MathUtils.clip(proportion, 0, 1);

                            float tempIdleDist = minIdleDistance;

                            minIdleDistance *= proportion;
                            minIdleDistance = MathUtils.remap(minIdleDistance, 0, tempIdleDist, 200, tempIdleDist);
                            range *= proportion;
                            range = MathUtils.remap(range, 0, rangeOld, 200, rangeOld);

                            maxIdleDistance = tempIdleDist + range;
                            maxIdleDistance = Math.max(minIdleDistance + 100, maxIdleDistance);
                        }
                    }

                    final int gridSize = Math.max(15, teammates.size() * 3);

                    final float xHalfWidth = RLConstants.arenaHalfWidth * 0.6f;

                    float[] yGrid = new float[gridSize];
                    float[] xGrid = new float[Math.max(8, teammates.size() * 2)];

                    // Pretend like there are teammates at the edges of the xGrid
                    for (int i = 0; i < xGrid.length; i++) {
                        // negative x
                        {
                            float gridSpotX = MathUtils.remap(i, 0, xGrid.length - 1, -xHalfWidth, xHalfWidth);
                            float distance = Math.abs(gridSpotX - -xHalfWidth);
                            if (distance >= 0)
                                xGrid[i] += 1 / Math.max(distance, 10);
                        }
                        // positive x
                        {
                            float gridSpotX = MathUtils.remap(i, 0, xGrid.length - 1, -xHalfWidth, xHalfWidth);
                            float distance = Math.abs(gridSpotX - xHalfWidth);
                            if (distance >= 0)
                                xGrid[i] += 1 / Math.max(distance, 10);
                        }
                    }

                    for (var p : teammates) {
                        for (int i = 0; i < gridSize; i++) {
                            float gridSpotY = MathUtils.remap(i, 0, gridSize - 1, minIdleDistance, maxIdleDistance);
                            final Vector2 gridSpot = new Vector2(0, gridSpotY);
                            float idlePosY = MathUtils.clip(p.getValue(), minIdleDistance, maxIdleDistance);
                            final Vector2 idlePos = new Vector2(0, idlePosY);
                            float distance = (float) idlePos.distance(gridSpot);
                            if (distance >= 0)
                                yGrid[i] += 1 / Math.max(distance, 10);
                        }

                        for (int i = 0; i < xGrid.length; i++) {
                            float gridSpotX = MathUtils.remap(i, 0, xGrid.length - 1, -xHalfWidth, xHalfWidth);
                            float distance = Math.abs(gridSpotX - p.getKey().position.x + p.getKey().velocity.x * 0.4f);
                            if (distance >= 0)
                                xGrid[i] += 1 / Math.max(distance, 10);
                        }
                    }

                    int lowestYSpot = 0;
                    int lowestXSpot = 0;

                    // y
                    {
                        float lowestYDist = 9999999;
                        float highestYDist = 0;
                        for (int i = 0; i < gridSize; i++) {
                            if (yGrid[i] < lowestYDist) {
                                lowestYDist = yGrid[i];
                                lowestYSpot = i;
                            }
                            if (yGrid[i] > highestYDist) {
                                highestYDist = yGrid[i];
                            }
                        }

                        // Draw
                        {
                            for (int i = 0; i < gridSize; i++) {
                                float gridSpot = MathUtils.remap(i, 0, gridSize - 1, minIdleDistance, maxIdleDistance);
                                float val = 1 - MathUtils.clip(MathUtils.remap(yGrid[i], lowestYDist, highestYDist, 0, 1), 0, 1);
                                var col = new Color(val, val, val);

                                if (i == lowestYSpot)
                                    col = Color.GREEN;
                                float ySize = (maxIdleDistance - minIdleDistance) / (gridSize - 1);
                                renderer.drawCentered3dCube(col, new Vector3(car.position.x, teamSign * gridSpot + futureBallPos.y, 50), new Vector3(10, ySize, 200));
                                //renderer.drawString3d(String.format("%.5f", grid[i]), Color.WHITE, new Vector3(car.position.x, teamSign * gridSpot + ball.position.y, 200), 1, 1);
                            }
                        }
                    }
                    // x
                    {
                        float lowestXDist = 9999999;
                        for (int i = 0; i < xGrid.length; i++) {
                            if (xGrid[i] < lowestXDist) {
                                lowestXSpot = i;
                                lowestXDist = xGrid[i];
                            }
                        }
                    }

                    preferredIdlingDistance = MathUtils.remap(lowestYSpot, 0, yGrid.length - 1, minIdleDistance, maxIdleDistance);
                    preferredIdlingX = MathUtils.remap(lowestXSpot, 0, xGrid.length - 1, -xHalfWidth, xHalfWidth);
                }

                // Hover around the middle area
                Vector3 idleTarget = new Vector3(MathUtils.clip(preferredIdlingX, -maxX, maxX), MathUtils.clip(futureBallPos.y + teamSign * preferredIdlingDistance, -RLConstants.arenaLength, RLConstants.arenaLength), 0);

                renderer.drawCentered3dCube(Color.YELLOW, idleTarget, 150);
                renderer.drawLine3d(Color.YELLOW, car.position, idleTarget.withZ(50));

                if (Math.abs(idleTarget.y) > RLConstants.goalDistance * 0.95f) {
                    idleTarget = idleTarget.withY(MathUtils.lerp(futureBallPos.y, ownGoal.y, 0.3f));
                }

                float minSpeed = DriveManeuver.max_throttle_speed * 0.7f;
                if (Math.abs(car.position.y - idleTarget.y) > 500)
                    minSpeed = MathUtils.remap(Math.min(2000, Math.abs(car.position.y - idleTarget.y) - 500), 0, 2000, minSpeed, CarData.MAX_VELOCITY * 0.95f);

                DriveManeuver.steerController(controlsOutput, car, idleTarget);
                DriveManeuver.speedController(dt, controlsOutput, (float) car.forward().dot(car.velocity), minSpeed, CarData.MAX_VELOCITY, 0.6f);

                break;
            }
            case ROTATE: {
                if (car.position.z > 300) {
                    DefaultStrategy.smartBallChaser(dt, controlsOutput);
                } else {
                    this.followPathManeuver.step(dt, controlsOutput);
                    //this.followPathManeuver.draw(renderer, car);
                    this.followPathManeuver.path.draw(renderer);
                    if (this.followPathManeuver.isDone())
                        this.reevaluateStrategy(0);
                }

                break;
            }
            case FOLLOW_PATH_STRIKE: {
                this.strikeAbstraction.step(dt, controlsOutput);
                if (this.strikeAbstraction.isDone())
                    this.reevaluateStrategy(0);

                break;
            }
        }
    }

    @Override
    public String getAdditionalInformation() {
        return "State: " + this.state;
    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.ofNullable(suggestedStrat);
    }

    enum State {
        IDLE,
        FOLLOW_PATH_STRIKE,
        ROTATE,
        GO_BACK_SOMEHOW,
        BALLCHASE,
        GET_BOOST,
        INVALID
    }
}
