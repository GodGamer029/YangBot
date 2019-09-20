package yangbot.strategy;

import yangbot.util.ControlsOutput;

import java.util.List;

public abstract class Strategy {

    private boolean isDone = false;
    private boolean plannedStrategy = false;

    public boolean didPlanStrategy(){
        return plannedStrategy;
    }

    public void setDone(boolean isDone){
        this.isDone = isDone;
    }

    public boolean isDone(){
        return isDone;
    }

    public void planStrategy(){
        plannedStrategy = true;
    }

    public void step(float dt, ControlsOutput controlsOutput){
        if(!plannedStrategy)
            planStrategy();
    }
    public abstract List<Strategy> suggestStrategy();

}
