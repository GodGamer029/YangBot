package yangbot.strategy;

import yangbot.util.ControlsOutput;

import java.util.Optional;

public abstract class Strategy {

    private boolean isDone = false;
    private boolean plannedStrategy = false;

    public final boolean didPlanStrategy(){
        return plannedStrategy;
    }

    public final void setDone(boolean isDone){
        this.isDone = isDone;
        if (isDone)
            plannedStrategy = false;
    }

    public final void setDone() {
        this.setDone(true);
    }

    public final boolean isDone(){
        return isDone;
    }

    protected abstract void planStrategyInternal();

    @SuppressWarnings("WeakerAccess")
    public final void planStrategy(){
        plannedStrategy = true;
        planStrategyInternal();
    }

    protected abstract void stepInternal(float dt, ControlsOutput controlsOutput);

    public final void step(float dt, ControlsOutput controlsOutput){
        if(!plannedStrategy)
            planStrategy();
        stepInternal(dt, controlsOutput);
    }

    public abstract Optional<Strategy> suggestStrategy();

}
