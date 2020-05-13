package yangbot.path.builders.segments;

import org.jetbrains.annotations.NotNull;
import yangbot.input.Physics2D;
import yangbot.input.RLConstants;
import yangbot.path.Curve;
import yangbot.path.builders.BakeablePathSegment;
import yangbot.util.AdvancedRenderer;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class TurnCircleSegment extends BakeablePathSegment {

    public final Vector2 circlePos;
    private final float circleRadius;
    public final Vector2 tangentPoint;
    private final Vector2 endPos;
    private final Vector2 startPos;
    private final float ccw; // clockwise or counterclockwise

    public TurnCircleSegment(Physics2D start, float circleRadius, Vector2 endPos) {
        this.circleRadius = circleRadius;
        this.endPos = endPos;
        this.startPos = start.position;

        final var startPos = start.position;

        var startToEnd = endPos.sub(startPos).normalized();
        var correctionAngle = start.forward().correctionAngle(startToEnd);

        this.circlePos = startPos.add(start.right().mul(Math.signum(correctionAngle)).mul(circleRadius));

        if (this.circlePos.distance(endPos) <= circleRadius) {
            // endPos within circle
            this.tangentPoint = null;
            this.ccw = 0;
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

        assert t1_cc != t2_cc : t1_cc + " : " + t2_cc + " alpha=" + alpha;

        this.ccw = car_cc;

        if (t1_cc == car_cc)
            this.tangentPoint = t1;
        else
            this.tangentPoint = t2;
    }

    @Override
    public void draw(AdvancedRenderer renderer, Color color) {

        assert tangentPoint != null;
        //renderer.drawCircle(color, circlePos.withZ(20), this.circleRadius);
        var startDir = this.startPos.sub(this.circlePos).normalized();
        var endDir = this.tangentPoint.sub(this.circlePos).normalized();

        float startAngle = (float) startDir.angle();
        float endAngle = startAngle + (float) Math.abs(startDir.correctionAngle(endDir)) * Math.signum(-this.ccw);

        renderer.drawCircle(color, circlePos.withZ(20), this.circleRadius, startAngle, endAngle);
        //renderer.drawLine3d(color.darker(), endPos.withZ(20), this.tangentPoint.withZ(20));
    }

    @Override
    protected @NotNull Curve bakeInternal(int maxSamples) {
        assert tangentPoint != null;

        var startDir = this.startPos.sub(this.circlePos).normalized();
        var endDir = this.tangentPoint.sub(this.circlePos).normalized();

        float startAngle = (float) startDir.angle();
        float endAngle = startAngle + (float) Math.abs(startDir.correctionAngle(endDir)) * Math.signum(-this.ccw);

        boolean isReversed = startAngle > endAngle;

        float angleDiv = (endAngle - startAngle) / MathUtils.clip(maxSamples, 4, 16);
        if (Math.abs(angleDiv) < 0.1f) {
            angleDiv = 0.1f * Math.signum(angleDiv);
        }
        assert angleDiv != 0 : angleDiv + " start: " + startAngle + " end: " + endAngle + " start: " + startDir + " end: " + endDir;

        List<Curve.ControlPoint> pointList = new ArrayList<>();
        float z = RLConstants.carElevation;

        boolean shouldExit = false;
        for (float currentAngle = startAngle; Math.signum(currentAngle - endAngle) == Math.signum(startAngle - endAngle); currentAngle += angleDiv) {
            final var thisAngle = Vector2.fromAngle(currentAngle);

            var nextAngle = Vector2.fromAngle(currentAngle + angleDiv);
            if (Math.signum(currentAngle + angleDiv - endAngle) != Math.signum(startAngle - endAngle)) {
                angleDiv = endAngle - currentAngle;
                if (Math.abs(angleDiv) < 0.001f)
                    break;
                nextAngle = Vector2.fromAngle(currentAngle + angleDiv);
                shouldExit = true;
            }

            var thisPos = thisAngle.mul(this.circleRadius).add(this.circlePos).withZ(z);
            var nextPos = nextAngle.mul(this.circleRadius).add(this.circlePos).withZ(z);

            // Calculate tangent
            Vector3 tangent;
            {
                var prevAngle = Vector2.fromAngle(currentAngle - angleDiv * 0.1f);
                var nexAngle = Vector2.fromAngle(currentAngle + angleDiv * 0.1f);
                var prev = prevAngle.mul(this.circleRadius).add(this.circlePos).withZ(z);
                var nex = nexAngle.mul(this.circleRadius).add(this.circlePos).withZ(z);
                tangent = nex.sub(prev).withZ(0).normalized();
            }

            pointList.add(new Curve.ControlPoint(thisPos, tangent));

            if (shouldExit)
                break;
        }

        var endTangent = this.endPos.sub(this.tangentPoint).normalized().withZ(0);

        if (pointList.size() == 0) // Add a point, to not confuse the pathing
            pointList.add(new Curve.ControlPoint(this.tangentPoint.withZ(z).sub(endTangent), endTangent));
        // End point
        pointList.add(new Curve.ControlPoint(this.tangentPoint.withZ(z), endTangent));

        var curvyBoi = new Curve(pointList, 6);
        return curvyBoi;
    }
}
