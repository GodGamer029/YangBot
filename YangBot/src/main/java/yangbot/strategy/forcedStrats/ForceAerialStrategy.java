package yangbot.strategy.forcedStrats;

import yangbot.input.ControlsOutput;
import yangbot.strategy.Strategy;

import java.util.Optional;

public class ForceAerialStrategy extends Strategy {
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
