package yangbot.strategy;

import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.manuever.DribbleManuver;
import yangbot.util.ControlsOutput;

import java.util.Optional;

public class DefaultStrategy extends Strategy {

    private Strategy newDecidedStrat = null;

    @Override
    public void planStrategyInternal() {
    }

    @Override
    public void stepInternal(float dt, ControlsOutput controlsOutput) {
        // Chase the ball like a pro
        GameData gameData = GameData.current();
        BallData ball = gameData.getBallData();
        CarData car = gameData.getCarData();

        controlsOutput.withSteer((float) car.forward().flatten().correctionAngle(ball.position.flatten().sub(car.position.flatten())) * 0.9f);
        controlsOutput.withThrottle(Math.max(0.1f, (float) (ball.position.flatten().distance(car.position.flatten()) - 100f) / 100f));
        if (Math.abs(controlsOutput.getSteer()) <= 0.1f && car.position.distance(ball.position) > 1000)
            controlsOutput.withBoost(true);

        if (Math.abs(controlsOutput.getSteer()) >= 0.95f)
            controlsOutput.withSlide(true);

        if (new DribbleManuver().isViable()) {
            newDecidedStrat = new DribbleStrategy();
            this.setDone();
        }
    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.ofNullable(newDecidedStrat);
    }
}