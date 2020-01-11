package yangbot.strategy;

import yangbot.input.ControlsOutput;

import java.util.Optional;

public abstract class StrategyPlanner extends Strategy {

    protected Strategy newDecidedStrategy = null;

    @Override
    protected final void stepInternal(float dt, ControlsOutput controlsOutput) {
        throw new IllegalStateException(this.getClass().getSimpleName() + ": A Strategy Planner is not allowed to have a behaviour! Did you forget to call setDone() in planStrategyInternal() ?");
    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.ofNullable(newDecidedStrategy);
    }
}
