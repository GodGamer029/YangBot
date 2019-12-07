package yangbot.strategy;

import yangbot.util.ControlsOutput;

import java.util.Optional;

public class NeutralStrategy extends Strategy {
    @Override
    protected void planStrategyInternal() {
        this.setDone();
    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {
        this.setDone();
    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.empty();
    }
}
