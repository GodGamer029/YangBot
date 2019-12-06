package yangbot.strategy;

import yangbot.manuever.DribbleManeuver;
import yangbot.util.ControlsOutput;

import java.util.Optional;

public class DribbleStrategy extends Strategy {

    private DribbleManeuver dribbleManeuver;

    public static boolean isViable() {
        return new DribbleManeuver().isViable();
    }

    @Override
    protected void planStrategyInternal() {
        dribbleManeuver = new DribbleManeuver();
        if (!dribbleManeuver.isViable())
            this.setDone(true);
    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {
        dribbleManeuver.step(dt, controlsOutput);
        if (dribbleManeuver.isDone())
            this.setDone();
    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.of(new DefaultStrategy());
    }
}
