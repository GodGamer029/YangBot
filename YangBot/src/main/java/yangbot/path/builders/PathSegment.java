package yangbot.path.builders;

import yangbot.path.Curve;
import yangbot.util.AdvancedRenderer;

public abstract class PathSegment {

    protected PathSegment() {

    }

    public Curve bake(int maxSamples) {
        throw new IllegalStateException("Not implemented in class: " + this.getClass().getSimpleName());
    }

    public void draw(AdvancedRenderer renderer) {
        throw new IllegalStateException("Not implemented in class: " + this.getClass().getSimpleName());
    }
}
