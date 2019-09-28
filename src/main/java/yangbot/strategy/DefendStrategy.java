package yangbot.strategy;

import yangbot.util.ControlsOutput;

import java.util.Optional;

public class DefendStrategy extends Strategy {
    @Override
    protected void planStrategyInternal() {

    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {

    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.empty();
    }
}
