package yangbot.strategy;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.GameData;
import yangbot.strategy.abstraction.DribbleAbstraction;
import yangbot.util.math.vector.Vector3;

import java.util.Optional;

public class DribbleStrategy extends Strategy {

    private DribbleAbstraction dribbleAbstraction;

    public static boolean isViable() {
        return new DribbleAbstraction().isViable();
    }

    @Override
    protected void planStrategyInternal() {
        GameData gameData = GameData.current();
        CarData car = gameData.getCarData();

        if (!car.hasWheelContact || car.up().angle(new Vector3(0, 0, 1)) > Math.PI / 4) {
            this.setDone();
            return;
        }

        dribbleAbstraction = new DribbleAbstraction();
        if (!dribbleAbstraction.isViable())
            this.setDone(true);
    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();

        if (!car.hasWheelContact || car.up().angle(new Vector3(0, 0, 1)) > Math.PI / 4) {
            this.setDone();
            return;
        }

        var dribbleRunState = dribbleAbstraction.step(dt, controlsOutput);
        if (dribbleRunState.isDone())
            this.setDone();
    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.of(new DefaultStrategy());
    }
}
