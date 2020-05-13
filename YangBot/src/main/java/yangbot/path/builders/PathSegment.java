package yangbot.path.builders;

import yangbot.input.ControlsOutput;
import yangbot.util.AdvancedRenderer;
import yangbot.util.math.vector.Vector3;

import java.awt.*;

public abstract class PathSegment {

    protected float timer = 0;

    public boolean step(float dt, ControlsOutput output) {
        this.timer += dt;
        return true;
    }

    public abstract Vector3 getEndPos();

    public abstract Vector3 getEndTangent();

    public abstract float getEndSpeed();

    public abstract float getTimeEstimate();

    public boolean canInterrupt() {
        return true;
    }

    public void draw(AdvancedRenderer renderer, Color color) {
    }

    public boolean shouldBeInAir() {
        return false;
    }
}
