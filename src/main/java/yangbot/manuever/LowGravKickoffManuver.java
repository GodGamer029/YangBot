package yangbot.manuever;

import yangbot.input.CarData;
import yangbot.util.ControlsOutput;

public class LowGravKickoffManuver extends Manuver {

    @Override
    public boolean isViable() {
        return false;
    }

    @Override
    public void step(float dt, ControlsOutput controlsOutput) {

    }

    @Override
    public CarData simulate(CarData car) {
        return null;
    }
}
