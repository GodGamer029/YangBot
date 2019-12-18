package yangbot.strategy;

import yangbot.input.GameData;
import yangbot.util.ControlsOutput;

import java.util.Optional;

public abstract class Strategy {

    private boolean isDone = false;
    private boolean plannedStrategy = false;
    private float lastStrategyPlan = 0;
    private float lastResetCheck = -1;

    public final boolean didPlanStrategy() {
        return plannedStrategy;
    }

    public final void setDone() {
        this.setDone(true);
    }

    public final boolean isDone() {
        return isDone;
    }

    public final void setDone(boolean isDone) {
        this.isDone = isDone;
        if (isDone)
            plannedStrategy = false;
    }

    protected abstract void planStrategyInternal();

    public final void planStrategy() {
        planStrategy(false);
    }

    @SuppressWarnings("WeakerAccess")
    public final void planStrategy(boolean force) {
        float currentGameTime = GameData.current().getCarData().elapsedSeconds;
        if (lastResetCheck < 0)
            lastResetCheck = currentGameTime;
        if (plannedStrategy && !force)
            return;
        lastStrategyPlan = currentGameTime;
        planStrategyInternal();
        plannedStrategy = true;
    }

    protected final boolean reevaluateStrategy(float timeout) {
        float currentSeconds = GameData.current().getCarData().elapsedSeconds;
        if (currentSeconds < lastStrategyPlan)
            lastStrategyPlan = -10; // Something wierd is going on, replan strat

        if (currentSeconds - lastStrategyPlan > timeout)
            planStrategy(true);

        return this.isDone();
    }

    protected final boolean checkReset(float timeout) {
        float currentSeconds = GameData.current().getCarData().elapsedSeconds;
        if (!this.plannedStrategy)
            lastResetCheck = currentSeconds;
        if (currentSeconds < lastResetCheck)
            lastResetCheck = -10; // Something wierd is going on, replan strat

        if (currentSeconds - lastResetCheck > timeout) {
            this.setDone();
            this.lastResetCheck = currentSeconds;
        }

        return this.isDone();
    }

    protected abstract void stepInternal(float dt, ControlsOutput controlsOutput);

    public final void step(float dt, ControlsOutput controlsOutput) {
        if (!plannedStrategy)
            planStrategy();
        stepInternal(dt, controlsOutput);
    }

    public abstract Optional<Strategy> suggestStrategy();

}
