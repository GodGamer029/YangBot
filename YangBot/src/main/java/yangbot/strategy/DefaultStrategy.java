package yangbot.strategy;

import yangbot.util.ControlsOutput;

import java.util.Optional;

public class DefaultStrategy extends Strategy {

    private Strategy newDecidedStrategy = null;
    private int jumpFromWallTick = 0;

    @Override
    public void planStrategyInternal() {
        newDecidedStrategy = new GenericStrategyPlanner();
        this.setDone();
    }

    @Override
    public void stepInternal(float dt, ControlsOutput controlsOutput) {
        // Chase the ball like a pro
       /* GameData gameData = GameData.current();
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

        if (new DribbleManeuver().isViable()) {
            newDecidedStrategy = new DribbleStrategy();
            this.setDone();
            return;
        }*/
        this.setDone();
    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.ofNullable(newDecidedStrategy);
    }
}
