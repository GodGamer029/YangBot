package yangbot.strategy.abstraction;

import yangbot.input.*;
import yangbot.path.EpicMeshPlanner;
import yangbot.path.builders.SegmentedPath;
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
    public float maxIdleDistance = RLConstants.arenaLength * 0.5f;

    private SegmentedPath currentPath = null;
    private Vector3 pathTarget = new Vector3(0, 0, -999999);
    private float lastPathBuild = 0;
    private boolean canInterruptRightNow = true;

    private Vector2 findIdlePosition(CarData localCar, List<Tuple<CarData, Float>> teammates, Vector2 futureBallPos) {
        //assert teammates.size() > 0;

        final int teamSign = localCar.getTeamSign();
        final AdvancedRenderer renderer = GameData.current().getAdvancedRenderer();
        final Vector2 ownGoal = new Vector2(0, teamSign * RLConstants.goalDistance);

        // Distances will be scaled back if they exceed this value
        final float maxAbsoluteIdleY = RLConstants.arenaHalfLength - 190;
        final float maxAbsoluteIdleX = RLConstants.arenaHalfWidth - 100;

        final Line2 goalToBall = new Line2(
                ownGoal,
                (Math.abs(futureBallPos.y) > maxAbsoluteIdleY - 10) ?
                        futureBallPos.withY((maxAbsoluteIdleY - 10) * Math.signum(futureBallPos.y)) : futureBallPos);

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
        float[] yGrid = new float[Math.max(15, teammates.size() * 3)];

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

                /*if(this.doBothPredictionAndCurrent){
                    // Also include current position
                    float currentMateYPos = carMate.position.y;
                    distance = MathUtils.distance(posY, currentMateYPos);
                    yGrid[i] += (1 / Math.max(distance, 10)) * mate.getValue() * 0.5f /*don't weigh it as much as prediction/;
                }*/
            }
        }

        int lowestYSpot = 0;

        // Y
        {
            float lowestYDist = 9999999;
            float highestYDist = 0;
            boolean goBackFurther = false;// When on low boost: go back further
            boolean doTeammatesHaveBoost = teammates.stream()
                    .anyMatch((t) -> t.getKey().boost > 30);
            if (doTeammatesHaveBoost && localCar.boost < 30) // Only go back when teammates do have boost
                goBackFurther = true;

            for (int i = 0; i < yGrid.length; i++) {
                if (goBackFurther && i < yGrid.length / 2)
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
            var carMate = mate.getKey();

            // X-Distances
            for (int i = 0; i < xGrid.length; i++) {
                float posX = xIndexToAbs.apply(i);
                float futureMateXPos = carMate.position.x + carMate.velocity.x * this.teammatePredictionDelay;

                float distance = MathUtils.distance(posX, futureMateXPos);
                xGrid[i] += (1 / Math.max(distance, 10)) * mate.getValue();

                if (this.doBothPredictionAndCurrent) {
                    float currentMateXPos = carMate.position.x;

                    distance = MathUtils.distance(posX, currentMateXPos);
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

        return new Vector2(xIndexToAbs.apply(lowestXSpot), yIndexToAbs.apply(lowestYSpot));
    }

    @Override
    protected RunState stepInternal(float dt, ControlsOutput controlsOutput) {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final ImmutableBallData ball = gameData.getBallData();
        final YangBallPrediction ballPrediction = gameData.getBallPrediction();
        final int teamSign = car.getTeamSign();
        final AdvancedRenderer renderer = gameData.getAdvancedRenderer();

        if (Math.abs(car.position.y) > RLConstants.goalDistance) {
            DriveManeuver.steerController(controlsOutput, car, new Vector3());
            // Ideal turning speed
            DriveManeuver.speedController(dt, controlsOutput, (float) car.forward().dot(car.velocity), 850f, 950f, 0.04f, false);
            this.currentPath = null;
            return RunState.CONTINUE;
        }

        if (car.position.z > 50 && car.hasWheelContact) {
            DriveManeuver.steerController(controlsOutput, car, car.position.withZ(RLConstants.carElevation));
            DriveManeuver.speedController(dt, controlsOutput, (float) car.forward().dot(car.velocity), 1050f, 1150f, 0.04f, false);
            this.currentPath = null;
            return RunState.CONTINUE;
        }

        // Use position of the ball in x seconds
        float futureBallPosDelay = 0.5f;
        // If the ball is up high retreat, because this bot is too bad to hit them anyways
        futureBallPosDelay += MathUtils.remapClip(ball.position.z, 400, 2000, 0, 0.4f);

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
        var teammates = gameData.getAllCars().stream()
                .filter(c -> c.team == car.team && c.playerIndex != car.playerIndex)
                .filter(c -> !c.isDemolished)
                //.filter(c -> RotationAdvisor.isInfrontOfBall(c, ball))
                .map(c -> new Tuple<>(c, c.getPlayerInfo().isActiveRotator() ? 1 : 0.3f))
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

        if (this.currentPath == null || (this.currentPath.canInterrupt() && (idleTarget.distance(this.pathTarget) > 300 || car.elapsedSeconds - this.lastPathBuild > 0.2f))) {
            this.lastPathBuild = car.elapsedSeconds;
            this.pathTarget = idleTarget;

            /*var builder = new PathBuilder(car)
                    .optimize();
            if (car.position.z > 50 || builder.getCurrentPosition().distance(idleTarget) < 30) {
                var atba = new AtbaSegment(builder.getCurrentPosition(), builder.getCurrentSpeed(), idleTarget);
                builder.add(atba);
            } else if (car.angularVelocity.magnitude() < 0.1f && car.forwardSpeed() > 300 /*&& Math.abs(car.forward().flatten().angleBetween(idleTarget.sub(car.position).flatten().normalized())) > 30 * (Math.PI / 180)*) {
                var drift = new DriftSegment(builder.getCurrentPosition(), builder.getCurrentTangent(), idleTarget.sub(car.position).normalized(), builder.getCurrentSpeed());
                builder.add(drift);
            } else {
                var turn = new TurnCircleSegment(car.toPhysics2d(), 1 / DriveManeuver.maxTurningCurvature(Math.max(1100, builder.getCurrentSpeed())), idleTarget.flatten());
                if (turn.tangentPoint != null)
                    builder.add(turn);
            }

            if (builder.getCurrentPosition().distance(idleTarget) > 20)
                builder.add(new StraightLineSegment(builder.getCurrentPosition(), builder.getCurrentSpeed(), idleTarget));
            */
            var pathOptional = new EpicMeshPlanner()
                    .withStart(car)
                    .withEnd(idleTarget, idleTarget.sub(car.position).normalized())
                    .withCreationStrategy(EpicMeshPlanner.PathCreationStrategy.YANGPATH)
                    .plan();

            this.currentPath = pathOptional.get();
        }

        //renderer.drawCentered3dCube(Color.YELLOW, this.pathTarget, 50);
        //renderer.drawLine3d(Color.YELLOW, car.position, this.pathTarget.withZ(30));

        assert Math.abs(idleTarget.y) <= RLConstants.goalDistance : idleTarget.toString();

        if (this.currentPath.step(dt, controlsOutput) || this.currentPath.shouldReset(car)) {
            this.currentPath = null;
            this.lastPathBuild = car.elapsedSeconds - 10;
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
