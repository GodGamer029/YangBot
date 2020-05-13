package yangbot.strategy;

import yangbot.input.ControlsOutput;
import yangbot.input.GameData;
import yangbot.input.RLConstants;
import yangbot.input.interrupt.Interrupt;

import java.util.Optional;

public abstract class Strategy {

    private boolean isDone = false;
    private boolean plannedStrategy = false;
    protected float lastStrategyPlan = 0;
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
            this.plannedStrategy = false;
    }

    protected abstract void planStrategyInternal();

    public final void planStrategy() {
        planStrategy(false);
    }

    @SuppressWarnings("WeakerAccess")
    public final void planStrategy(boolean force) {
        final GameData gameData = GameData.current();
        float currentGameTime = gameData.getCarData().elapsedSeconds;
        if (this.lastResetCheck < 0)
            lastResetCheck = currentGameTime;
        if (this.plannedStrategy && !force)
            return;
        this.lastStrategyPlan = currentGameTime;

        long ms = System.currentTimeMillis();
        planStrategyInternal();
        long duration = System.currentTimeMillis() - ms;
        if (duration > RLConstants.tickFrequency * 1000 * 2.5)
            System.out.println(gameData.getBotIndex() + ": " + this.getClass().getSimpleName() + " took " + duration + "ms to plan its strategy");

        plannedStrategy = true;
    }

    protected final boolean reevaluateStrategy(Interrupt interrupt, float timeout) {
        if (interrupt.hasInterrupted()) {
            return reevaluateStrategy(timeout);
        }

        return this.isDone();
    }

    protected final boolean reevaluateStrategy(Interrupt interrupt) {
        if (interrupt.hasInterrupted()) {
            planStrategy(true);
            return true;
        }

        return this.isDone();
    }

    protected final boolean reevaluateStrategy(float timeout) {
        float currentSeconds = GameData.current().getCarData().elapsedSeconds;
        if (currentSeconds < this.lastStrategyPlan)
            this.lastStrategyPlan = -10; // Something wierd is going on, replan strat

        assert currentSeconds - this.lastStrategyPlan >= 0 : "Strategy was possibly planned twice in the same frame";

        if (currentSeconds - this.lastStrategyPlan > timeout || timeout == 0) {
            planStrategy(true);
            return true;
        }

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
        final GameData gameData = GameData.current();
        if (!plannedStrategy)
            planStrategy();

        if (this.getClass() != DefaultStrategy.class)
            assert !this.isDone : this.getClass().getSimpleName() + " was already done, can't execute step()";

        long ms = System.currentTimeMillis();
        stepInternal(dt, controlsOutput);
        long duration = System.currentTimeMillis() - ms;
        if (duration > RLConstants.tickFrequency * 1000 * 2)
            System.out.println(gameData.getBotIndex() + ": " + this.getClass().getSimpleName() + " took " + duration + "ms to execute its strategy");
    }

    public abstract Optional<Strategy> suggestStrategy();

    public String getAdditionalInformation() {
        return "";
    }
}
