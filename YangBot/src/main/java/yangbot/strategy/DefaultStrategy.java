package yangbot.strategy;

import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.manuever.DribbleManeuver;
import yangbot.util.ControlsOutput;

import java.util.Optional;

public class DefaultStrategy extends Strategy {

    private Strategy newDecidedStrategy = null;

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

        if (Math.abs(controlsOutput.getSteer()) >= 0.95f && car.angularVelocity.magnitude() < 3f)
            controlsOutput.withSlide(true);

        if (new DribbleManeuver().isViable()) {
            newDecidedStrategy = new DribbleStrategy();
            this.setDone();
            return;
        }

        if (ball.position.z > 300 && car.hasWheelContact) {

        }

        if (!car.hasWheelContact) {
            newDecidedStrategy = new RecoverStrategy();
            this.setDone();
            return;
        } else if (car.position.z > 150) {
            controlsOutput.withThrottle(-1);
            controlsOutput.withJump(true);
        }
    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.ofNullable(newDecidedStrategy);
    }
}
