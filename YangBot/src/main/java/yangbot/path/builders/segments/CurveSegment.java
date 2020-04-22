package yangbot.path.builders.segments;

import org.jetbrains.annotations.NotNull;
import yangbot.path.Curve;
import yangbot.path.builders.BakeablePathSegment;
import yangbot.util.AdvancedRenderer;

import java.awt.*;

public class CurveSegment extends BakeablePathSegment {

    private final Curve curve;

    public CurveSegment(Curve c) {
        this.curve = c;
    }

    @Override
    protected @NotNull Curve bakeInternal(int maxSamples) {
        return this.curve;
    }

    @Override
    public void draw(AdvancedRenderer renderer, Color color) {
        curve.draw(renderer, color);
    }
}
