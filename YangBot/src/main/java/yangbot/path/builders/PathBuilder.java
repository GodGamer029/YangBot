package yangbot.path.builders;

import yangbot.path.Curve;
import yangbot.util.AdvancedRenderer;

import java.util.ArrayList;
import java.util.List;

public class PathBuilder {

    private final List<PathSegment> pathSegments;

    public PathBuilder() {
        this.pathSegments = new ArrayList<>();
    }

    public PathBuilder draw(AdvancedRenderer renderer) {
        this.pathSegments.forEach(p -> p.draw(renderer));
        return this;
    }

    public PathBuilder add(PathSegment pathSegment) {
        this.pathSegments.add(pathSegment);
        return this;
    }

    public Curve bake(int numSamplesPerSegment) {
        throw new IllegalStateException("Not implemented in class: " + this.getClass().getSimpleName());
    }

    public Curve bake() {
        return this.bake(16);
    }

}
