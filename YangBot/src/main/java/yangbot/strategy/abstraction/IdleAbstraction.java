package yangbot.strategy.abstraction;

import yangbot.input.*;
import yangbot.path.EpicMeshPlanner;
import yangbot.path.builders.SegmentedPath;
import yangbot.strategy.advisor.RotationAdvisor;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.Tuple;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.Line2;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class IdleAbstraction extends Abstraction {

    // Predict teammate in future in x seconds
    public float teammatePredictionDelay = 0.5f;
    // Useful for not bumping into teammates
    public boolean doBothPredictionAndCurrent = true;
    // Y-Distance from ball when idle
    // These may be scaled down, if too close to own goa l
    public float minIdleDistance = 1200;
    public float maxIdleDistance = RLConstants.arenaLength * 0.55f;
    public float maxAbsoluteIdleY = RLConstants.arenaHalfLength - 350;
    public float maxAbsoluteIdleX = RLConstants.arenaHalfWidth - 140;
    public float forceRetreatTimeout = 0;
    public float retreatPercentage = 0.5f; // 1 = all the way back, 0 = no change at all
    public float targetSpeed = DriveManeuver.max_throttle_speed;

    private SegmentedPath currentPath = null;
    private Vector3 pathTarget = new Vector3(0, 0, -999999);
    private float lastPathBuild = 0;
    private boolean canInterruptRightNow = true;

    private Vector2 findIdlePosition(CarData localCar, List<Tuple<CarData, Float>> teammates, Vector2 futureBallPos) {
        //assert teammates.size() > 0;

        final int teamSign = localCar.getTeamSign();
        final AdvancedRenderer renderer = GameData.current().getAdvancedRenderer();

        // Distances will be scaled back if they exceed this value

        final float minAbsoluteChannelBallDist = 1000;

        final Vector2 ownGoal = new Vector2(0, teamSign * Math.min(RLConstants.goalDistance, maxAbsoluteIdleY + 10));

        assert Math.abs(ownGoal.y) > minAbsoluteChannelBallDist;

        var futureBallPosFake = futureBallPos;
        if (Math.abs(futureBallPosFake.y) > maxAbsoluteIdleY - 10) // Satisfy max abs constraint
            futureBallPosFake = futureBallPosFake.withY((maxAbsoluteIdleY - 10) * Math.signum(futureBallPos.y));
        if (Math.abs(futureBallPosFake.y - ownGoal.y) < minAbsoluteChannelBallDist) // Satisfy min dist constraint
            futureBallPosFake = futureBallPosFake.withY(Math.signum(ownGoal.y) * (Math.abs(ownGoal.y) - minAbsoluteChannelBallDist));

        // This line is used to keep the bots between the own goal and the ball, keeping them close to a possible save
        final Line2 goalToBallFake = new Line2(ownGoal.add(0, teamSign * 500), futureBallPosFake);

        float minIdleDistance = this.minIdleDistance;
        float maxIdleDistance = this.maxIdleDistance;

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
        float[] yGrid = new float[(int) MathUtils.clip(teammates.size() * 6 + 1, 13, 21)];

        // These functions return the absolute position values on the field
        final Function<Integer, Float> yIndexToAbs = ind -> {
            assert ind >= 0 && ind < yGrid.length : ind;

            float gridSpot = MathUtils.remap(ind, 0, yGrid.length - 1, effectiveMinIdleDistance, effectiveMaxIdleDistance);
            return MathUtils.clip(teamSign * gridSpot + futureBallPos.y, -maxAbsoluteIdleY, maxAbsoluteIdleY);
        };

        // Fill grid with distances from teammates
        for (var mate : teammates) {
            var carMate = mate.getKey();
            // Y-Distances
            for (int i = 0; i < yGrid.length; i++) {
                float posY = yIndexToAbs.apply(i);
                float futureMateYPos = carMate.position.y + carMate.velocity.y * this.teammatePredictionDelay;

                float distance = MathUtils.distance(posY, futureMateYPos);
                yGrid[i] += (1 / Math.max(distance, 10)) * mate.getValue();

                if (this.doBothPredictionAndCurrent) {
                    // Also include current position
                    float currentMateYPos = carMate.position.y;
                    distance = MathUtils.distance(posY, currentMateYPos);
                    yGrid[i] += (1 / Math.max(distance, 10)) * mate.getValue() * 0.5f /*don't weigh it as much as prediction*/;
                }
            }
        }

        int lowestYSpot = 0;

        // Y
        {
            float lowestYDist = 9999999;
            float highestYDist = 0;
            boolean goBackFurther = false;
            // When on low boost or we aren't supposed to be attacking: go back further
            var teammatesWithBoostHereToHelp = teammates.stream()
                    .filter((t) -> t.getKey().boost > 30)
                    .anyMatch(t -> Math.abs(t.getKey().position.y - localCar.position.y) < RLConstants.arenaLength * 0.4f);
            if (teammatesWithBoostHereToHelp && localCar.boost < 30) // Only go back when teammates do have boost
                goBackFurther = true;
            if (!localCar.getPlayerInfo().isActiveShooter())
                goBackFurther = true;
            if (this.forceRetreatTimeout > 0)
                goBackFurther = true;

            for (int i = 0; i < yGrid.length; i++) {
                if (goBackFurther && i < yGrid.length * retreatPercentage && i < yGrid.length - 1)
                    continue;

                if (yGrid[i] < lowestYDist) {
                    lowestYDist = yGrid[i];
                    lowestYSpot = i;
                }
                if (yGrid[i] > highestYDist) {
                    highestYDist = yGrid[i];
                }
            }

            // Draw
            if (true) {
                for (int i = 0; i < yGrid.length; i++) {
                    float yPos = yIndexToAbs.apply(i);
                    float val = 1 - MathUtils.clip(MathUtils.remap(yGrid[i], lowestYDist, highestYDist, 0, 1), 0, 1);
                    var col = new Color(val, val, val);

                    if (i == lowestYSpot)
                        col = Color.GREEN;

                    float ySize = (effectiveMaxIdleDistance - effectiveMinIdleDistance) / (yGrid.length - 1);

                    renderer.drawCentered3dCube(col, new Vector3(localCar.position.x, yPos, 50), new Vector3(10, ySize, 100));
                    //renderer.drawString3d(String.format("%.5f", yGrid[i]), Color.WHITE, new Vector3(localCar.position.x, yPos, 200), 1, 1);
                }
            }
        }

        float[] xGrid = new float[(int) MathUtils.clip(teammates.size() * 2 + 1, 5, 11)]; // Always uneven, to make sure theres a segment in the middle
        final float decidedYPos = yIndexToAbs.apply(lowestYSpot);

        final float xChannelHalfWidth;
        {
            final float distToOwnGoal = Math.abs(decidedYPos - ownGoal.y);
            xChannelHalfWidth = MathUtils.remapClip(distToOwnGoal, 0, 1000, RLConstants.goalCenterToPost * 0.9f, RLConstants.goalCenterToPost * 1.2f);
        }

        assert maxAbsoluteIdleX > xChannelHalfWidth;

        final Function<Integer, Float> xIndexToAbs = ind -> {
            float relativeX = MathUtils.remapClip(ind, 0, xGrid.length - 1, -xChannelHalfWidth, xChannelHalfWidth);
            var intersectionOpt = goalToBallFake.getIntersectionPointWithInfOtherLine(new Line2(new Vector2(-1, decidedYPos), new Vector2(1, decidedYPos)));
            assert intersectionOpt.isPresent() : goalToBallFake + " " + decidedYPos;
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
                float distance = MathUtils.distance(posX, xIndexToAbs.apply(0));

                xGrid[i] += (1 / Math.max(distance, 10)) * 0.2f;
            }
            // Positive x
            {
                float posX = xIndexToAbs.apply(i);
                float distance = MathUtils.distance(posX, xIndexToAbs.apply(xGrid.length - 1));

                xGrid[i] += (1 / Math.max(distance, 10)) * 0.2f;
            }
        }

        teammates.add(new Tuple<>(localCar, 0.8f));
        for (var mate : teammates) {
            var carMate = mate.getKey();

            // X-Distances
            for (int i = 0; i < xGrid.length; i++) {
                float posX = xIndexToAbs.apply(i);
                if(carMate.playerIndex != localCar.playerIndex){
                    float futureMateXPos = carMate.position.x + carMate.velocity.x * this.teammatePredictionDelay;

                    float distance = MathUtils.distance(posX, futureMateXPos);
                    xGrid[i] += (1 / Math.max(distance, 10)) * mate.getValue();
                }

                if (this.doBothPredictionAndCurrent) {
                    float currentMateXPos = carMate.position.x;

                    var distance = MathUtils.distance(posX, currentMateXPos);
                    xGrid[i] += (1 / Math.max(distance, 10)) * mate.getValue() * 0.5f;
                }
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
            if (true) {
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

        float ySpot = yIndexToAbs.apply(lowestYSpot);
        if (lowestYSpot > 0 && lowestYSpot < yGrid.length - 1) { // Place the car within a tolerance zone, so we don't need to always drive back and forth
            float halfBoundary = (effectiveMaxIdleDistance - effectiveMinIdleDistance) / (yGrid.length - 1);
            halfBoundary *= 0.5f;
            ySpot = MathUtils.remapClip(localCar.position.y, ySpot - halfBoundary, ySpot + halfBoundary, ySpot - halfBoundary, ySpot + halfBoundary);
        }

        return new Vector2(xIndexToAbs.apply(lowestXSpot), ySpot);
    }

    @Override
    protected RunState stepInternal(float dt, ControlsOutput controlsOutput) {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final ImmutableBallData ball = gameData.getBallData();
        final YangBallPrediction ballPrediction = gameData.getBallPrediction();
        final int teamSign = car.getTeamSign();
        final AdvancedRenderer renderer = gameData.getAdvancedRenderer();

        if (this.forceRetreatTimeout > 0)
            this.forceRetreatTimeout = Math.max(0, this.forceRetreatTimeout - dt);

        // Use position of the ball in x seconds
        float futureBallPosDelay = 0.4f;
        // If the ball is up high retreat, because this bot is too bad to hit them anyways
        futureBallPosDelay += MathUtils.remapClip(ball.position.z + ball.velocity.z * 0.2f, 400, 2000, 0f, 0.6f);

        // Clips idlingTarget
        final float maxX = RLConstants.arenaHalfWidth * 0.9f;
        final float maxY = RLConstants.arenaHalfLength;

        // Actual Idling distances the bot will follow (Changed when not alone on team)
        float preferredIdlingY;
        float preferredIdlingX;

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

        if(Math.signum(futureBallPos.y) == teamSign &&
                Math.abs(futureBallPos.y - car.position.y) > BallData.COLLISION_RADIUS + 300 &&
                Math.signum(futureBallPos.y - car.position.y) == car.getTeamSign() &&
                Math.abs(car.position.y) < RLConstants.goalDistance - 2000){
            var gameValue = 1 - Math.abs(car.team - gameData.getGameValue());
            if(gameValue < 0.3f)
                this.forceRetreatTimeout = 0.4f;
        }

        // find out where my teammates are idling
        var teammates = gameData.getAllCars().stream()
                .filter(c -> c.team == car.team && c.playerIndex != car.playerIndex)
                .filter(c -> !c.isDemolished && c.hasWheelContact)
                // either moving toward enemy or in defense
                .filter(c -> (Math.abs(c.velocity.y) < 300 || Math.signum(c.velocity.y) == -teamSign || Math.signum(c.position.y) == teamSign))
                .map(c -> new Tuple<>(c, c.getPlayerInfo().isActiveRotator() ? 1 : 0.2f))
                .collect(Collectors.toList());

        {
            var idlePos = this.findIdlePosition(car, teammates, futureBallPos);
            preferredIdlingX = idlePos.x;
            preferredIdlingY = idlePos.y;

            assert Math.abs(preferredIdlingY) < RLConstants.arenaHalfLength : idlePos.toString();
        }

        // Hover around the middle area
        Vector3 idleTarget = new Vector3(
                MathUtils.clip(preferredIdlingX, -maxX, maxX),
                MathUtils.clip(preferredIdlingY, -maxY, maxY),
                RLConstants.carElevation);

        if (this.currentPath == null || (this.currentPath.canInterrupt() && car.elapsedSeconds - this.lastPathBuild > (idleTarget.distance(this.pathTarget) > 500 ? 0.1f : 0.6f))) {

            this.lastPathBuild = car.elapsedSeconds;
            this.pathTarget = idleTarget;

            float targetArrivalSpeed = this.targetSpeed;
            if (Math.abs(idleTarget.y) > RLConstants.goalDistance * 0.8f)
                targetArrivalSpeed = Math.min(targetArrivalSpeed, 1100); // We don't want to faceplant the goal wall

            targetArrivalSpeed = MathUtils.clip(targetArrivalSpeed, 200, CarData.MAX_VELOCITY);

            var pathOptional = new EpicMeshPlanner()
                    .withStart(car, 0.05f)
                    .withEnd(idleTarget, idleTarget.sub(car.position).normalized())
                    .withArrivalSpeed(targetArrivalSpeed)
                    .withCreationStrategy(EpicMeshPlanner.PathCreationStrategy.YANGPATH)
                    .snapToBoost(car.boost < 90 ? 300 : 10)
                    .plan();

            if (pathOptional.isEmpty()) {
                System.out.println(car.playerIndex+": We have no path! " + car.position + " to " + idleTarget);
                var simplePath = new EpicMeshPlanner()
                        .withStart(car)
                        .withEnd(idleTarget, idleTarget.sub(car.position).normalized())
                        .withCreationStrategy(EpicMeshPlanner.PathCreationStrategy.ATBA_YANGPATH)
                        .plan();
                assert simplePath.isPresent();
                this.currentPath = simplePath.get();
            } else {
                this.currentPath = pathOptional.get();
            }
        }

        //renderer.drawCentered3dCube(Color.YELLOW, this.pathTarget, 50);
        //renderer.drawLine3d(Color.YELLOW, car.position, this.pathTarget.withZ(30));

        assert Math.abs(idleTarget.y) <= RLConstants.goalDistance : idleTarget.toString();

        if (this.currentPath.shouldReset(car) || this.currentPath.step(dt, controlsOutput)) {
            this.currentPath = null;
            this.lastPathBuild = car.elapsedSeconds;
            this.canInterruptRightNow = true;
        } else {
            this.currentPath.draw(renderer);
            this.canInterruptRightNow = this.currentPath.canInterrupt();
        }
        return RunState.CONTINUE;
    }

    @Override
    public boolean canInterrupt() {
        return canInterruptRightNow;
    }
}
