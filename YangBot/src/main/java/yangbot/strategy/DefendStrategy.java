package yangbot.strategy;

import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.input.RLConstants;
import yangbot.manuever.FollowPathManeuver;
import yangbot.prediction.Curve;
import yangbot.prediction.YangBallPrediction;
import yangbot.util.AdvancedRenderer;
import yangbot.util.ControlsOutput;
import yangbot.vector.Vector3;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DefendStrategy extends Strategy {

    private Criticalness criticalness = Criticalness.IDK;
    private FollowPathManeuver followPathManeuver = new FollowPathManeuver();

    @Override
    protected void planStrategyInternal() {
        // How critical is the situation?
        GameData gameData = GameData.current();
        CarData car = gameData.getCarData();
        BallData ball = gameData.getBallData();
        YangBallPrediction ballPrediction = gameData.getBallPrediction();

        int teamSign = car.team * 2 - 1;

        Optional<YangBallPrediction.YangPredictionFrame> firstConcedingGoalFrame = ballPrediction.frames
                .stream()
                .filter((f) -> (int) Math.signum(f.ballData.position.y) == teamSign && Math.abs(f.ballData.position.y) > RLConstants.goalDistance - 50 && f.ballData.position.z <= BallData.COLLISION_RADIUS + 10)
                .findFirst();

        if (firstConcedingGoalFrame.isPresent()) { // We getting scored on
            criticalness = Criticalness.GETTING_SCORED_ON_GROUND;
        }
    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {
        if (this.reevaluateStrategy(criticalness == Criticalness.FOLLOW_PATH ? 0.8f : 0.5f))
            return; // Return if we are done

        GameData gameData = GameData.current();
        CarData car = gameData.getCarData();
        BallData ball = gameData.getBallData();
        AdvancedRenderer renderer = gameData.getAdvancedRenderer();
        YangBallPrediction ballPrediction = gameData.getBallPrediction();

        int teamSign = car.team * 2 - 1;

        switch (criticalness) {
            case GETTING_SCORED_ON_GROUND: {
                final Optional<YangBallPrediction.YangPredictionFrame> firstConcedingGoalFrame = ballPrediction.frames
                        .stream()
                        .filter((f) -> Math.signum(f.ballData.position.y) == teamSign && Math.abs(f.ballData.position.y) > RLConstants.goalDistance - 50 && f.ballData.position.z <= BallData.COLLISION_RADIUS + 10)
                        .findFirst();

                if (!firstConcedingGoalFrame.isPresent()) {
                    this.reevaluateStrategy(0);
                    return;
                }

                final YangBallPrediction.YangPredictionFrame frameConceding = firstConcedingGoalFrame.get();

                final YangBallPrediction framesBeforeGoal = ballPrediction.getBeforeRelative(frameConceding.relativeTime);
                if (framesBeforeGoal.frames.size() == 0) {
                    this.reevaluateStrategy(0);
                    return;
                }

                Curve validPath = null;
                float arrivalTime = 0;

                float t = frameConceding.relativeTime - RLConstants.simulationTickFrequency * 2;

                // Path finder
                do {
                    final Optional<YangBallPrediction.YangPredictionFrame> interceptFrameOptional = framesBeforeGoal.getFrameAtRelativeTime(t);
                    if (!interceptFrameOptional.isPresent())
                        break;

                    final YangBallPrediction.YangPredictionFrame interceptFrame = interceptFrameOptional.get();
                    final Vector3 targetPos = interceptFrame.ballData.position;

                    Curve currentPath;
                    // Construct Path
                    {
                        final List<Curve.ControlPoint> controlPoints = new ArrayList<>();
                        controlPoints.add(new Curve.ControlPoint(car.position.add(car.forward().mul(10)), car.forward()));
                        controlPoints.add(new Curve.ControlPoint(targetPos.withZ(car.position.z), new Vector3(0, -teamSign, 0)));

                        currentPath = new Curve(controlPoints);
                    }

                    // Check if path is valid
                    {
                        Curve.PathStatus pathStatus = currentPath.doPathChecking(car, interceptFrame.absoluteTime, ballPrediction);
                        if (pathStatus == Curve.PathStatus.VALID || validPath == null) {
                            validPath = currentPath;
                            arrivalTime = interceptFrame.absoluteTime;
                        }
                    }
                    t -= RLConstants.simulationTickFrequency * 1;
                } while (t > 0);

                criticalness = Criticalness.FOLLOW_PATH;

                if (validPath == null) {
                    this.reevaluateStrategy(0);
                    return;
                }
                followPathManeuver.path = validPath;
                followPathManeuver.arrivalTime = arrivalTime;
                break;
            }

            case FOLLOW_PATH:
                break;
            case IDK:
                //System.err.println("IDK"); // Fall through to ballchaser
                break;
            default:

                controlsOutput.withSteer((float) car.forward().flatten().correctionAngle(ballPrediction.getFrameAtRelativeTime(0.3f).get().ballData.position.flatten().sub(car.position.flatten())));
                controlsOutput.withThrottle(Math.max(0.1f, (float) (ball.position.flatten().distance(car.position.flatten()) - 100f) / 100f));
                if (Math.abs(controlsOutput.getSteer()) <= 0.1f && car.position.distance(ball.position) > 1000)
                    controlsOutput.withBoost(true);

                if (Math.abs(controlsOutput.getSteer()) >= 0.95f && car.angularVelocity.magnitude() < 3f)
                    controlsOutput.withSlide(true);
                break;
        }

        if (criticalness == Criticalness.FOLLOW_PATH) {
            followPathManeuver.path.draw(renderer);
            followPathManeuver.step(dt, controlsOutput);
            if (followPathManeuver.isDone())
                this.reevaluateStrategy(0);

            renderer.drawString2d(String.format("Arriving in %.1fs", followPathManeuver.arrivalTime - car.elapsedSeconds), Color.WHITE, new Point(500, 450), 2, 2);
            renderer.drawString2d(String.format("Max speed: %.0fuu/s", followPathManeuver.path.maxSpeedAt(followPathManeuver.path.findNearest(car.position))), Color.WHITE, new Point(500, 490), 2, 2);
            renderer.drawString2d(String.format("Max drive: %.0fuu/s", followPathManeuver.driveManeuver.speed), Color.WHITE, new Point(500, 530), 2, 2);
        }
    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.empty();
    }

    enum Criticalness {
        GETTING_SCORED_ON_GROUND,
        FOLLOW_PATH,
        IDK
    }
}
