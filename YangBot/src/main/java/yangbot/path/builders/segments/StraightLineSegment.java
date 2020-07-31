package yangbot.path.builders.segments;

import org.jetbrains.annotations.NotNull;
import yangbot.path.Curve;
import yangbot.path.builders.BakeablePathSegment;
import yangbot.util.AdvancedRenderer;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
import java.util.List;

public class StraightLineSegment extends BakeablePathSegment {

    private final Vector3 startPos, endPos, normal;

    public StraightLineSegment(Vector3 startPos, float startSpeed, Vector3 endPos) {
        this(startPos, startSpeed, endPos, new Vector3(0, 0, 1), -1, -1, false);
    }

    public StraightLineSegment(Vector3 startPos, float startSpeed, Vector3 endPos, float endSpeed, float arrivalTime, boolean allowBoost) {
        this(startPos, startSpeed, endPos, new Vector3(0, 0, 1), endSpeed, arrivalTime, allowBoost);
    }

    public StraightLineSegment(Vector3 startPos, float startSpeed, Vector3 endPos, Vector3 normal, float endSpeed, float arrivalTime, boolean allowBoost) {
        super(startSpeed, endSpeed, arrivalTime);
        this.allowBoost = allowBoost;
        this.startPos = startPos;
        this.endPos = endPos;
        this.normal = normal;
    }

    public Vector3 getStartPos() {
        return this.startPos;
    }

    public Vector3 getStartTangent() {
        return this.endPos.sub(this.startPos).normalized();
    }

    @Override
    public Vector3 getEndPos() {
        return this.endPos;
    }

    @Override
    public Vector3 getEndTangent() {
        return this.getStartTangent();
    }

    @Override
    protected @NotNull Curve bakeInternal(int maxSamples) {
        return new Curve(List.of(
                new Curve.ControlPoint(startPos, getStartTangent(), normal),
                new Curve.ControlPoint(endPos, getEndTangent(), normal)),
                maxSamples);
    }

    @Override
    public void draw(AdvancedRenderer renderer, Color color) {
        renderer.drawCentered3dCube(Color.GREEN, this.startPos, 20);
        renderer.drawCentered3dCube(Color.RED, this.endPos, 20);
        renderer.drawLine3d(color, this.startPos, this.endPos);
    }
}
