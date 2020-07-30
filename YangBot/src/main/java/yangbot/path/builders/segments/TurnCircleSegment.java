package yangbot.path.builders.segments;

import org.jetbrains.annotations.NotNull;
import yangbot.input.Physics2D;
import yangbot.input.RLConstants;
import yangbot.path.Curve;
import yangbot.path.builders.BakeablePathSegment;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class TurnCircleSegment extends BakeablePathSegment {

    public final Vector2 circlePos;
    private static final float MIN_CIRCLE_RADIUS = 1 / DriveManeuver.maxTurningCurvature(600);
    public final Vector2 tangentPoint;
    private final Vector2 endPos;
    private final Vector2 startPos, startTangent;
    private final Vector2 turnCircleStartPos;
    private final float ccw; // clockwise or counterclockwise
    private float circleRadius;

    public TurnCircleSegment(Physics2D start, float circleRadius, Vector2 endPos) {
        super(start.forwardSpeed(), MathUtils.clip(DriveManeuver.maxTurningSpeed(1 / circleRadius), 200, DriveManeuver.max_throttle_speed), -1);

        this.circleRadius = circleRadius;
        this.endPos = endPos;
        this.startPos = start.position;
        this.startTangent = start.forward();
        float speed = start.forwardSpeed();
        this.turnCircleStartPos = this.startPos.add(this.startTangent.mul(speed * 0.08f)); // give the car enough time to steer and get angular velocity up, this is especially important in cases like this where we steer at the max. turning circle

        var startToEnd = endPos.sub(turnCircleStartPos).normalized();
        var correctionAngle = start.forward().correctionAngle(startToEnd);

        var localEnd = endPos.sub(turnCircleStartPos).dot(start.orientation);
        float minR = (float) Math.abs((localEnd.magnitudeSquared() / (2 * localEnd.y))) - 5;
        if (minR < MIN_CIRCLE_RADIUS) {
            this.tangentPoint = null;
            this.ccw = 0;
            this.circlePos = turnCircleStartPos;
            return;
        }
        this.circleRadius = Math.min(minR, circleRadius);

        this.circlePos = turnCircleStartPos.add(start.right().mul(Math.signum(correctionAngle)).mul(this.circleRadius));

        if (this.circlePos.distance(endPos) <= this.circleRadius) {
            assert false;
            // endPos within circle
            // try fitting the circle to MIN_CIRCLE_RADIUS
            this.tangentPoint = null;
            this.ccw = 0;
            return;
        }

        // Find the 2 tangent points
        // https://www.onlinemathlearning.com/tangent-circle.html
        // https://www.onlinemathlearning.com/image-files/tangent-circle_clip_image001.gif
        float circleEndDist = (float) this.circlePos.distance(endPos);
        float alpha = (float) Math.acos(this.circleRadius / circleEndDist);
        var circleToEndPos = endPos.sub(this.circlePos).normalized();

        var t1 = this.circlePos.add(circleToEndPos.mul(this.circleRadius).rotateBy(alpha));
        var t2 = this.circlePos.add(circleToEndPos.mul(this.circleRadius).rotateBy(-alpha));

        float car_cc = Math.signum(this.circlePos.sub(turnCircleStartPos).withZ(0).normalized().crossProduct(start.forward().withZ(0)).z);
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
        //renderer.drawCircle(Color.PINK, circlePos.withZ(40), this.circleRadius);
        var startDir = this.turnCircleStartPos.sub(this.circlePos).normalized();
        var endDir = this.tangentPoint.sub(this.circlePos).normalized();

        float startAngle = (float) startDir.angle();
        float corrAng = (float) startDir.angleBetween(endDir);
        if (Math.signum(-this.ccw) != Math.signum(corrAng))
            corrAng = (float) (Math.signum(-this.ccw) * (Math.PI - Math.abs(corrAng) + Math.PI));
        float endAngle = startAngle + corrAng;

        renderer.drawCircle(color, circlePos.withZ(20), this.circleRadius, startAngle, endAngle);
        renderer.drawLine3d(color, this.startPos.withZ(20), this.turnCircleStartPos.withZ(20));
        //renderer.drawLine3d(color.darker(), endPos.withZ(20), this.tangentPoint.withZ(20));
    }

    @Override
    protected @NotNull Curve bakeInternal(int maxSamples) {
        assert tangentPoint != null;

        var startDir = this.turnCircleStartPos.sub(this.circlePos).normalized();
        var endDir = this.tangentPoint.sub(this.circlePos).normalized();

        float startAngle = (float) startDir.angle();
        float corrAng = (float) startDir.angleBetween(endDir);
        if (Math.signum(-this.ccw) != Math.signum(corrAng))
            corrAng = (float) (Math.signum(-this.ccw) * (Math.PI - Math.abs(corrAng) + Math.PI));
        float endAngle = startAngle + corrAng;

        float angleDiv = (endAngle - startAngle) / MathUtils.clip(maxSamples, 4, 10);
        if (Math.abs(angleDiv) < 0.1f) {
            angleDiv = 0.1f * Math.signum(angleDiv);
        }
        assert angleDiv != 0 : angleDiv + " start: " + startAngle + " end: " + endAngle + " start: " + startDir + " end: " + endDir;

        List<Curve.ControlPoint> pointList = new ArrayList<>();
        float z = RLConstants.carElevation;
        pointList.add(new Curve.ControlPoint(this.startPos.withZ(z), this.startTangent.withZ(0)));

        boolean shouldExit = false;
        for (float currentAngle = startAngle; Math.signum(currentAngle - endAngle) == Math.signum(startAngle - endAngle); currentAngle += angleDiv) {
            final var thisAngle = Vector2.fromAngle(currentAngle);

            if (Math.signum(currentAngle + angleDiv - endAngle) != Math.signum(startAngle - endAngle)) {
                angleDiv = endAngle - currentAngle;
                if (Math.abs(angleDiv) < 0.001f)
                    break;
                shouldExit = true;
            }

            // Calculate tangent
            Vector3 tangent;
            {
                /*var prevAngle = Vector2.fromAngle(currentAngle - angleDiv * 0.1f);
                var nexAngle = Vector2.fromAngle(currentAngle + angleDiv * 0.1f);
                var prev = prevAngle;
                var nex = nexAngle;
                tangent = nex.sub(prev).normalized().withZ(0);
                System.out.println(tangent.flatten() + ":"+Vector2.fromAngle(currentAngle).cross().mul(Math.signum(angleDiv)).normalized()+" "+angleDiv);
*/
                tangent = Vector2.fromAngle(currentAngle)
                        .cross()
                        .mul(Math.signum(angleDiv))
                        .normalized().withZ(0);
            }

            var thisPos = thisAngle.mul(this.circleRadius).add(this.circlePos).withZ(z);
            pointList.add(new Curve.ControlPoint(thisPos, tangent));

            if (shouldExit)
                break;
        }

        var endTangent = this.endPos.sub(this.tangentPoint).normalized().withZ(0);

        if (pointList.size() == 0) // Add a point, to not confuse the pathing
            pointList.add(new Curve.ControlPoint(this.tangentPoint.withZ(z).sub(endTangent), endTangent));
        // End point
        pointList.add(new Curve.ControlPoint(this.tangentPoint.withZ(z), endTangent));

        var curvRet = new Curve(pointList, 16);
        return curvRet;
    }
}
