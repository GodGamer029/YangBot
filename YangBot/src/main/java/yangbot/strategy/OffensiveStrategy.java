package yangbot.strategy;

import yangbot.util.ControlsOutput;

import java.util.Optional;

public class OffensiveStrategy extends Strategy {
    @Override
    protected void planStrategyInternal() {
        if (this.checkReset(0.5f))
            return;
    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {
        if (this.reevaluateStrategy(0.5f))
            return;
        DefaultStrategy.smartBallChaser(dt, controlsOutput);
    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.empty();
    }
}
