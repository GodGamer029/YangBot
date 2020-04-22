package yangbot.strategy.abstraction;

import yangbot.input.ControlsOutput;
import yangbot.input.GameData;

// High Level Maneuvers that are (usually) too complex to be simulated
public abstract class Abstraction {

    private boolean isDone = false;

    public boolean isViable() {
        throw new IllegalStateException("Not implemented in class " + getClass().getSimpleName());
    }

    protected final GameData getGameData() {
        return GameData.current();
    }

    public final RunState step(float dt, ControlsOutput controlsOutput) {
        if (this.isDone)
            throw new IllegalStateException(getClass().getSimpleName() + ".step() was called even though it is done already");

        final RunState runState = stepInternal(dt, controlsOutput);

        if (runState == RunState.DONE)
            this.isDone = true;
        return runState;
    }

    public final boolean isDone() {
        return this.isDone;
    }

    protected abstract RunState stepInternal(float dt, ControlsOutput controlsOutput);

    public boolean canInterrupt() {
        return true;
    }

    public enum RunState {
        CONTINUE,
        DONE
    }
}
