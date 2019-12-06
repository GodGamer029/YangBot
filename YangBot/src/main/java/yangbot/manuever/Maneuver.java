package yangbot.manuever;

import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.util.ControlsOutput;

import java.util.ArrayList;
import java.util.List;

public abstract class Maneuver {
    private boolean isDone;
    private boolean isFooling = false;
    private GameData foolGameData = null;

    private List<Maneuver> maneuverList = new ArrayList<>();

    public Maneuver() {
    }

    public abstract boolean isViable();

    public abstract void step(float dt, ControlsOutput controlsOutput);

    public abstract CarData simulate(CarData car);

    protected final GameData getGameData() {
        if (isFooling)
            return foolGameData;
        return GameData.current();
    }

    public void setIsDone(boolean done) {
        this.isDone = done;
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
