package yangbot.manuever;

import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.GameData;
import yangbot.vector.Vector3;

public class CeilingShotManeuver extends Maneuver {

    private State state = State.ROLLUP;

    @Override
    public boolean isViable() {
        return false;
    }

    @Override
    public void step(float dt, ControlsOutput controlsOutput) {
        final GameData gameData = this.getGameData();
        final Vector3 gravity = gameData.getGravity();
        final CarData car = gameData.getCarData();
        final BallData ball = gameData.getBallData();

        throw new IllegalStateException("Not implemented");
        /*switch (state) {
            case ROLLUP:
                controlsOutput.withSteer((float) car.forward().flatten().correctionAngle(ball.position.sub(car.position).flatten()));
                //controlsOutput.withThrottle(1);
                break;
        }*/
    }

    @Override
    public CarData simulate(CarData car) {
        return null;
    }

    enum State {
        ROLLUP,
        FALL_DOWN
    }
}
