package yangbot.strategy;

import yangbot.manuever.DribbleManuver;
import yangbot.util.ControlsOutput;

import java.util.Optional;

public class DribbleStrategy extends Strategy {

    private DribbleManuver dribbleManuver;

    public static boolean isViable() {
        return new DribbleManuver().isViable();
    }

    @Override
    protected void planStrategyInternal() {
        dribbleManuver = new DribbleManuver();
        if (!dribbleManuver.isViable())
            this.setDone(true);
    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {
        dribbleManuver.step(dt, controlsOutput);
        if (dribbleManuver.isDone())
            this.setDone();
    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.of(new DefaultStrategy());
    }
}
