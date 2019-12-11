package yangbot.strategy;

import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.input.RLConstants;
import yangbot.manuever.FollowPathManeuver;
import yangbot.prediction.Curve;
import yangbot.prediction.YangBallPrediction;
import yangbot.util.ControlsOutput;
import yangbot.vector.Vector3;

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
            criticalness = Criticalness.GETTING_SCORED_ON;
        }
    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {
        if (this.reevaluateStrategy(0.5f))
            return; // Return if we are done

        GameData gameData = GameData.current();
        CarData car = gameData.getCarData();
        BallData ball = gameData.getBallData();
        YangBallPrediction ballPrediction = gameData.getBallPrediction();

        int teamSign = car.team * 2 - 1;

        switch (criticalness) {
            case GETTING_SCORED_ON:
                Optional<YangBallPrediction.YangPredictionFrame> firstConcedingGoalFrame = ballPrediction.frames
                        .stream()
                        .filter((f) -> Math.signum(f.ballData.position.y) == teamSign && Math.abs(f.ballData.position.y) > RLConstants.goalDistance - 50 && f.ballData.position.z <= BallData.COLLISION_RADIUS + 10)
                        .findFirst();

                if (!firstConcedingGoalFrame.isPresent()) {

                    this.reevaluateStrategy(0);
                    return;
                }

                YangBallPrediction.YangPredictionFrame frameConceding = firstConcedingGoalFrame.get();

                List<YangBallPrediction.YangPredictionFrame> framesBeforeGoal = ballPrediction.getFramesBeforeRelative(frameConceding.relativeTime);
                if (framesBeforeGoal.size() == 0) {
                    this.reevaluateStrategy(0);
                    return;
                }
                YangBallPrediction.YangPredictionFrame interceptFrame = framesBeforeGoal.get(Math.max(0, framesBeforeGoal.size() - 1));

                Vector3 targetPos = interceptFrame.ballData.position;

                List<Curve.ControlPoint> controlPoints = new ArrayList<>();
                controlPoints.add(new Curve.ControlPoint(car.position, car.forward(), new Vector3(0, 0, 1)));
                controlPoints.add(new Curve.ControlPoint(car.position.add(car.forward().mul(20)), car.forward(), new Vector3(0, 0, 1)));

                controlPoints.add(new Curve.ControlPoint(targetPos, new Vector3(0, -teamSign, 0), new Vector3(0, 0, 1)));

                followPathManeuver.path = new Curve(controlPoints);
                followPathManeuver.arrivalTime = interceptFrame.absoluteTime;
                criticalness = Criticalness.FOLLOW_PATH;

                break;
            case FOLLOW_PATH:
                followPathManeuver.path.draw(gameData.getAdvancedRenderer());
                followPathManeuver.step(dt, controlsOutput);
                if (followPathManeuver.isDone())
                    this.reevaluateStrategy(0);
                break;
            case IDK:
                System.out.println("IDK");
            default:

                controlsOutput.withSteer((float) car.forward().flatten().correctionAngle(ballPrediction.getFrameAtRelativeTime(0.3f).get().ballData.position.flatten().sub(car.position.flatten())));
                controlsOutput.withThrottle(Math.max(0.1f, (float) (ball.position.flatten().distance(car.position.flatten()) - 100f) / 100f));
                if (Math.abs(controlsOutput.getSteer()) <= 0.1f && car.position.distance(ball.position) > 1000)
                    controlsOutput.withBoost(true);

                if (Math.abs(controlsOutput.getSteer()) >= 0.95f && car.angularVelocity.magnitude() < 3f)
                    controlsOutput.withSlide(true);
                break;
        }
    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.empty();
    }

    enum Criticalness {
        GETTING_SCORED_ON,
        FOLLOW_PATH,
        IDK
    }
}
