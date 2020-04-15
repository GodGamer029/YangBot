package yangbot.strategy.manuever;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.GameData;

import java.util.ArrayList;
import java.util.List;

public abstract class Maneuver {
    private boolean isDone;
    private boolean isFooling = false;
    private GameData foolGameData = null;

    private List<Maneuver> maneuverList = new ArrayList<>();

    public Maneuver() {
    }

    public abstract void step(float dt, ControlsOutput controlsOutput);

    public CarData simulate(CarData car) {
        throw new IllegalStateException("not implemented in class " + this.getClass().getSimpleName());
    }

    protected final GameData getGameData() {
        if (isFooling)
            return foolGameData;
        return GameData.current();
    }

    public void setIsDone(boolean done) {
        this.isDone = done;
    }

    public void setDone() {
        this.isDone = true;
    }

    public boolean isDone() {
        return isDone;
    }

    public void fool(GameData g) {
        if (g != null) {
            isFooling = true;
            foolGameData = g;
        } else {
            isFooling = false;
        }
    }
}
