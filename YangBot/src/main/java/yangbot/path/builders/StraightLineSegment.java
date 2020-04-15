package yangbot.path.builders;

import yangbot.path.Curve;
import yangbot.util.AdvancedRenderer;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
import java.util.List;

public class StraightLineSegment extends PathSegment {

    private final Vector3 startPos, endPos, normal;

    public StraightLineSegment(Vector3 startPos, Vector3 endPos) {
        this(startPos, endPos, new Vector3(0, 0, 1));
    }

    public StraightLineSegment(Vector3 startPos, Vector3 endPos, Vector3 normal) {
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

    public Vector3 getEndPos() {
        return this.endPos;
    }

    public Vector3 getEndTangent() {
        return this.getStartTangent();
    }

    public Vector3 getNormal() {
        return this.normal;
    }

    @Override
    public Curve bake(int maxSamples) {
        return new Curve(List.of(
                new Curve.ControlPoint(startPos, getStartTangent(), normal),
                new Curve.ControlPoint(endPos, getEndTangent(), normal)),
                1);
    }

    @Override
    public void draw(AdvancedRenderer renderer) {
        renderer.drawCentered3dCube(Color.GREEN, this.startPos, 20);
        renderer.drawCentered3dCube(Color.RED, this.endPos, 20);
        renderer.drawLine3d(Color.YELLOW, this.startPos, this.endPos);
    }
}
