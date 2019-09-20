package yangbot.manuever;

import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.util.ControlsOutput;

public abstract class Manuver {
    private boolean isDone;

    public abstract boolean isViable();
    public abstract void step(float dt, ControlsOutput controlsOutput);
    public abstract CarData simulate(CarData car);

    public Manuver(){ }

    protected final GameData getGameData(){
        return GameData.current();
    }

    public void setIsDone(boolean done){
        this.isDone = done;
    }

    public boolean isDone(){
        return isDone;
    }
}
