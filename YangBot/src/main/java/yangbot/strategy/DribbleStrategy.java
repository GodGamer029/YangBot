package yangbot.strategy;

import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.manuever.DribbleManeuver;
import yangbot.util.ControlsOutput;
import yangbot.vector.Vector3;

import java.util.Optional;

public class DribbleStrategy extends Strategy {

    private DribbleManeuver dribbleManeuver;

    public static boolean isViable() {
        return new DribbleManeuver().isViable();
    }

    @Override
    protected void planStrategyInternal() {
        GameData gameData = GameData.current();
        CarData car = gameData.getCarData();

        if (!car.hasWheelContact || car.up().angle(new Vector3(0, 0, 1)) > Math.PI / 4) {
            this.setDone();
            return;
        }

        dribbleManeuver = new DribbleManeuver();
        if (!dribbleManeuver.isViable())
            this.setDone(true);
    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {
        GameData gameData = GameData.current();
        CarData car = gameData.getCarData();

        if (!car.hasWheelContact || car.up().angle(new Vector3(0, 0, 1)) > Math.PI / 4) {
            this.setDone();
            return;
        }

        dribbleManeuver.step(dt, controlsOutput);
        if (dribbleManeuver.isDone())
            this.setDone();
    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.of(new DefaultStrategy());
    }
}
