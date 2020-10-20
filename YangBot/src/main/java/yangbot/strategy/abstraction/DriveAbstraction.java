package yangbot.strategy.abstraction;

import yangbot.input.ControlsOutput;
import yangbot.path.builders.SegmentedPath;

public class DriveAbstraction extends Abstraction {

    public final SegmentedPath path;
    public boolean doDrawPath = true;

    public DriveAbstraction(SegmentedPath path) {
        this.path = path;
    }

    @Override
    protected RunState stepInternal(float dt, ControlsOutput controlsOutput) {
        var gameData = this.getGameData();
        var renderer = gameData.getAdvancedRenderer();
        var car = gameData.getCarData();

        if (this.doDrawPath)
            this.path.draw(renderer);
        this.path.step(dt, controlsOutput);

        if (this.path.shouldReset(car))
            return RunState.FAILED;

        return this.path.isDone() ? RunState.DONE : RunState.CONTINUE;
    }

    // TODO: implement canInterrupt
}
