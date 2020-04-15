package yangbot.path.builders;

import yangbot.input.Physics2D;
import yangbot.util.AdvancedRenderer;
import yangbot.util.math.vector.Vector2;

import java.awt.*;

public class TurnCircleSegment extends PathSegment {

    public final Vector2 circlePos;
    private final float circleRadius;
    private final Vector2 tangent;
    private final Vector2 endPos;

    public TurnCircleSegment(Physics2D start, float circleRadius, Vector2 endPos) {
        this.circleRadius = circleRadius;
        this.endPos = endPos;

        final var startPos = start.position;

        var startToEnd = endPos.sub(startPos).normalized();
        var correctionAngle = start.forward().correctionAngle(startToEnd);

        this.circlePos = startPos.add(start.right().mul(Math.signum(correctionAngle)).mul(circleRadius));

        if (this.circlePos.distance(endPos) <= circleRadius) {
            // endPos within circle
            this.tangent = null;
            return;
        }

        // Find the 2 tangent points
        // https://www.onlinemathlearning.com/tangent-circle.html
        float circleEndDist = (float) this.circlePos.distance(endPos);
        float alpha = (float) Math.acos(circleRadius / circleEndDist);
        var circleToEndPos = endPos.sub(this.circlePos).normalized();

        var t1 = this.circlePos.add(circleToEndPos.mul(circleRadius).rotateBy(alpha));
        var t2 = this.circlePos.add(circleToEndPos.mul(circleRadius).rotateBy(-alpha));

        float car_cc = Math.signum(this.circlePos.sub(startPos).withZ(0).normalized().crossProduct(start.forward().withZ(0)).z);
        float t1_cc = Math.signum(this.circlePos.sub(t1).normalized().withZ(0).crossProduct(endPos.sub(t1).normalized().withZ(0)).z);
        float t2_cc = Math.signum(this.circlePos.sub(t2).normalized().withZ(0).crossProduct(endPos.sub(t2).normalized().withZ(0)).z);

        assert t1_cc != t2_cc : t1_cc + " : " + t2_cc;

        if (t1_cc == car_cc)
            this.tangent = t1;
        else
            this.tangent = t2;
    }

    @Override
    public void draw(AdvancedRenderer renderer) {
        renderer.drawCircle(Color.BLACK, circlePos.withZ(20), this.circleRadius);
        if (this.tangent != null)
            renderer.drawLine3d(Color.YELLOW, endPos.withZ(20), this.tangent.withZ(20));
    }

}
