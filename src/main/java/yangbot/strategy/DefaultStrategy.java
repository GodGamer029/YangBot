package yangbot.strategy;

import yangbot.util.ControlsOutput;

import java.util.Optional;

public class DefaultStrategy extends Strategy {

    @Override
    public void planStrategyInternal() {
    }

    @Override
    public void stepInternal(float dt, ControlsOutput controlsOutput) {

    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.empty();
    }
}
