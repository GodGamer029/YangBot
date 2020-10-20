package yangbot.path.builders.segments;

import org.jetbrains.annotations.NotNull;
import yangbot.path.Curve;
import yangbot.path.builders.BakeablePathSegment;
import yangbot.path.builders.PathBuilder;
import yangbot.path.builders.SegmentedPath;
import yangbot.util.AdvancedRenderer;

import java.awt.*;

public class CurveSegment extends BakeablePathSegment {

    private final Curve curve;

    public CurveSegment(Curve c, float startSpeed, float arrivalTime, float arrivalSpeed, float startBoost) {
        this(c, startSpeed, startBoost);
        this.arrivalTime = arrivalTime;
        this.arrivalSpeed = arrivalSpeed;

        this.bake(SegmentedPath.MAX_SAMPLES); // prepare time estimate
    }

    public CurveSegment(Curve c, PathBuilder builder, float arrivalTime, float arrivalSpeed) {
        this(c, builder);
        this.arrivalTime = arrivalTime;
        this.arrivalSpeed = arrivalSpeed;

        this.bake(SegmentedPath.MAX_SAMPLES); // prepare time estimate
    }

    public CurveSegment(Curve c, float startSpeed, float startBoost) {
        super(startSpeed, startBoost, -1, -1);
        this.curve = c;
    }

    public CurveSegment(Curve c, PathBuilder builder) {
        super(builder, -1, -1);
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
