package yangbot.strategy;

import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.input.RLConstants;
import yangbot.prediction.YangBallPrediction;
import yangbot.util.ControlsOutput;

import java.util.Optional;

public class DefendStrategy extends Strategy {

    private Criticalness criticalness = Criticalness.IDK;

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
                .filter((f) -> Math.signum(f.ballData.position.z) == teamSign && Math.abs(f.ballData.position.z) > RLConstants.goalDistance)
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

        switch (criticalness) {
            case GETTING_SCORED_ON:


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
        IDK
    }
}
