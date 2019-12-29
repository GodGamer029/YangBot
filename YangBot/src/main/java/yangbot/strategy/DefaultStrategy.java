package yangbot.strategy;

import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.input.RLConstants;
import yangbot.prediction.YangBallPrediction;
import yangbot.util.ControlsOutput;
import yangbot.vector.Vector3;

import java.util.Optional;

public class DefaultStrategy extends Strategy {

    private Strategy newDecidedStrategy = null;
    private int jumpFromWallTick = 0;

    public static void smartBallChaser(float dt, ControlsOutput controlsOutput) {
        GameData gameData = GameData.current();
        BallData ball = gameData.getBallData();
        YangBallPrediction ballPrediction = gameData.getBallPrediction();
        CarData car = gameData.getCarData();

        Vector3 futureBallPos = ball.position.add(ball.velocity.mul(Math.min(2, car.position.flatten().sub(ball.position.flatten()).magnitude() / car.velocity.flatten().magnitude())));

        controlsOutput.withSteer((float) car.forward().flatten().correctionAngle(futureBallPos.flatten().sub(car.position.flatten())) * 0.9f);
        controlsOutput.withThrottle(Math.max(0.05f, (float) (futureBallPos.flatten().distance(car.position.flatten()) - 100f) / 100f));
        if (Math.abs(controlsOutput.getSteer()) <= 0.1f && car.position.distance(futureBallPos) > 1000 && car.boost > 40)
            controlsOutput.withBoost(true);

        if (Math.abs(controlsOutput.getSteer()) >= 0.95f && car.angularVelocity.magnitude() < 3f)
            controlsOutput.withSlide(true);
    }

    @Override
    public void planStrategyInternal() {
        GameData gameData = GameData.current();
        BallData ball = gameData.getBallData();
        CarData car = gameData.getCarData();

        if (!car.hasWheelContact) {
            newDecidedStrategy = new RecoverStrategy();
            this.setDone();
            return;
        }
        newDecidedStrategy = new GenericStrategyPlanner();
        this.setDone();
    }

    @Override
    public void stepInternal(float dt, ControlsOutput controlsOutput) {
        // Chase the ball like a pro

        GameData gameData = GameData.current();
        BallData ball = gameData.getBallData();
        CarData car = gameData.getCarData();

        if (!car.hasWheelContact) {
            if (jumpFromWallTick > 0) {
                jumpFromWallTick--;
                controlsOutput.withJump(true);
                return;
            } else {
                newDecidedStrategy = new RecoverStrategy();
                this.setDone();
                return;
            }
        } else {
            jumpFromWallTick = 0;
            if (car.position.z > 200) {
                jumpFromWallTick = (int) (0.15f / RLConstants.tickFrequency);
                controlsOutput.withJump(true);
                return;
            }
        }

        DefaultStrategy.smartBallChaser(dt, controlsOutput);

        this.setDone();
    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.ofNullable(newDecidedStrategy);
    }
}
