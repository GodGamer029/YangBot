package yangbot.path.builders.segments;

import org.jetbrains.annotations.NotNull;
import yangbot.input.CarData;
import yangbot.input.Physics2D;
import yangbot.input.RLConstants;
import yangbot.path.Curve;
import yangbot.path.builders.BakeablePathSegment;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.math.Car1D;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class TurnCircleSegment extends BakeablePathSegment {

    private static final float MIN_CIRCLE_SPEED = 300;
    private static final float MIN_CIRCLE_RADIUS = 1 / DriveManeuver.maxTurningCurvature(MIN_CIRCLE_SPEED);
    public Vector2 circlePos;
    public final Vector2 tangentPoint;
    private final Vector2 endPos;
    private final Vector2 startPos, startTangent;
    private Vector2 turnCircleStartPos;
    private final float ccw; // clockwise or counterclockwise
    private float circleRadius;
    private float timeEstimate = -1;
    private float endSpeed = -1;
    //private float timeRequiredForInitialSlowdown = -1;

    public TurnCircleSegment(Physics2D start, float circleRadius, float endAngle, float startBoost, boolean allowBoost) {
        super(start.forwardSpeed(), startBoost, MathUtils.clip(DriveManeuver.maxTurningSpeed(1 / circleRadius), 200, allowBoost ? CarData.MAX_VELOCITY : DriveManeuver.max_throttle_speed), -1);
        this.allowBoost = allowBoost;

        this.circleRadius = circleRadius;
        this.startPos = start.position;
        this.startTangent = start.forward();

        this.turnCircleStartPos = this.startPos.add(startTangent.mul(distanceOfSlowDownRequired(this.startSpeed, circleRadius)));

        this.circlePos = turnCircleStartPos.add(start.right().mul(Math.signum(endAngle)).mul(this.circleRadius));
        this.endPos = this.turnCircleStartPos.sub(this.circlePos).rotateBy(endAngle).add(circlePos);
        this.tangentPoint = this.endPos;

        this.ccw = Math.signum(this.circlePos.sub(turnCircleStartPos).withZ(0).normalized().crossProduct(start.forward().withZ(0)).z);
    }

    public TurnCircleSegment(Physics2D start, float circleRadius, Vector2 endPos, float startBoost, boolean allowBoost) {
        super(start.forwardSpeed(), startBoost, MathUtils.clip(DriveManeuver.maxTurningSpeed(1 / circleRadius), 200, allowBoost ? CarData.MAX_VELOCITY : DriveManeuver.max_throttle_speed), -1);
        this.allowBoost = allowBoost;

        assert circleRadius > MIN_CIRCLE_RADIUS : circleRadius;

        this.circleRadius = circleRadius;
        this.endPos = endPos;
        this.startPos = start.position;
        this.startTangent = start.forward();
        float speed = start.forwardSpeed();
        float steerDirection = (float) Math.signum(start.forward().correctionAngle(endPos.sub(this.startPos).normalized()));

        // give the car enough time to steer and get angular velocity up, this is especially important in cases like this where we steer at the max turning circle
        // do some vodoo magic with angular velocity to determine if we even need this
        float minAngularTurnupDist = speed * MathUtils.remapClip(steerDirection == Math.signum(start.angularVelocity) ? Math.abs(start.angularVelocity) : 0, 0, 2, 10 * RLConstants.tickFrequency, 2 * RLConstants.tickFrequency);

        /* Consider following scenario;
         - end pos within turn circle, so we need to scale down turn circle
         -> leads to smaller turn radius, so we need to be slower
         -> we need to be slower so we need more time to slow down
         -> turning circle becomes even smaller
         I have no idea if this ever converges, i just implemented this without testing, all impossible turns should be caught by the MIN_CIRCLE_RADIUS condition
         */
        Function<Float, Float> errorFunc = radius -> {
            float slowdownDistRequired = Math.max(minAngularTurnupDist, this.distanceOfSlowDownRequired(this.startSpeed, radius));
            this.turnCircleStartPos = this.startPos.add(this.startTangent.mul(slowdownDistRequired));
            var startToEnd = endPos.sub(turnCircleStartPos).normalized();
            var correctionAngle = start.forward().correctionAngle(startToEnd);
            this.circlePos = turnCircleStartPos.add(start.right().mul(Math.signum(correctionAngle)).mul(radius));

            return (float) this.circlePos.distance(endPos) - radius;
            // negative when end is inside circle
        };
        float curError = errorFunc.apply(circleRadius);
        if (curError < 0) {
            // end is inside circle, try to optimize
            // spray the function to find a positive value
            float v, otherError = 0;
            for (v = DriveManeuver.maxTurningSpeed(1 / circleRadius) - 20; v > MIN_CIRCLE_SPEED; v -= 50) {
                otherError = errorFunc.apply(1 / DriveManeuver.maxTurningCurvature(v)) - 1;
                if (otherError > 0)
                    break;
            }

            if (otherError > 0) {
                // we can actually fix this mess
                var result = MathUtils.smartBisectionMethod(errorFunc.andThen(fix -> fix - 1), circleRadius, 1 / DriveManeuver.maxTurningCurvature(v));
                assert Math.abs(result.getValue()) < 0.1f : result;
                this.circleRadius = result.getKey();
                float slowdownDistRequired = Math.max(minAngularTurnupDist, this.distanceOfSlowDownRequired(this.startSpeed, this.circleRadius));
                this.turnCircleStartPos = this.startPos.add(this.startTangent.mul(slowdownDistRequired));
                var startToEnd = endPos.sub(turnCircleStartPos).normalized();
                var correctionAngle = start.forward().correctionAngle(startToEnd);
                this.circlePos = turnCircleStartPos.add(start.right().mul(Math.signum(correctionAngle)).mul(this.circleRadius));
            } else {
                this.tangentPoint = null;
                this.ccw = 0;
                this.circlePos = turnCircleStartPos;
                return;
            }
        }

        if (this.circlePos.distance(endPos) <= this.circleRadius) {
            assert false;
            throw new RuntimeException();
        }

        // Find the 2 tangent points
        // https://www.onlinemathlearning.com/tangent-circle.html
        // https://www.onlinemathlearning.com/image-files/tangent-circle_clip_image001.gif
        float circleEndDist = (float) this.circlePos.distance(endPos);
        float alpha = (float) Math.acos(this.circleRadius / circleEndDist);
        var circleToEndPos = endPos.sub(this.circlePos).normalized();

        var t1 = this.circlePos.add(circleToEndPos.mul(this.circleRadius).rotateBy(alpha));
        var t2 = this.circlePos.add(circleToEndPos.mul(this.circleRadius).rotateBy(-alpha));

        float car_cc = Math.signum(this.circlePos.sub(turnCircleStartPos).normalized().withZ(0).crossProduct(start.forward().withZ(0)).z);
        float t1_cc = Math.signum(this.circlePos.sub(t1).normalized().withZ(0).crossProduct(endPos.sub(t1).normalized().withZ(0)).z);
        float t2_cc = Math.signum(this.circlePos.sub(t2).normalized().withZ(0).crossProduct(endPos.sub(t2).normalized().withZ(0)).z);

        assert t1_cc != t2_cc : t1_cc + " : " + t2_cc + " alpha=" + alpha;

        this.ccw = car_cc;

        if (t1_cc == car_cc)
            this.tangentPoint = t1;
        else
            this.tangentPoint = t2;
    }

    private float distanceOfSlowDownRequired(float curSpeed, float circleRadius) {
        float targetSpeed = DriveManeuver.maxTurningSpeed(1f / circleRadius);
        if (curSpeed < targetSpeed + 100)
            return 0;

        var simResult = Car1D.simulateDriveDistanceForSlowdown(curSpeed, targetSpeed);
        //this.timeRequiredForInitialSlowdown = simResult.getValue();
        return simResult.distanceTraveled;
    }

    @Override
    public float getTimeEstimate() {
        if (this.timeEstimate >= 0)
            return this.timeEstimate;

        var startDir = this.turnCircleStartPos.sub(this.circlePos).normalized();
        var endDir = this.tangentPoint.sub(this.circlePos).normalized();

        float targetSpeed = MathUtils.clip(DriveManeuver.maxTurningSpeed((1 / circleRadius) * 1.03f), 100, CarData.MAX_VELOCITY);
        float corrAng = (float) startDir.angleBetween(endDir);
        if (Math.signum(-this.ccw) != Math.signum(corrAng))
            corrAng = (float) (Math.signum(-this.ccw) * (Math.PI - Math.abs(corrAng) + Math.PI));
        float dist = Math.abs(this.circleRadius * corrAng);

        var sim = Car1D.simulateDriveDistanceSpeedController((float) this.startPos.distance(this.turnCircleStartPos), this.startSpeed, targetSpeed, this.startBoost, 0);

        boolean didProper = false;
        if (Math.abs(sim.speed - targetSpeed) > 30) {
            didProper = true;
            // we need to do a proper simulation to take into account the acceleration to reach the target speed
            var sim2 = Car1D.simulateDriveDistanceSpeedController(dist, sim.speed, targetSpeed, sim.boost, 0);
            this.endSpeed = sim2.speed;
            this.timeEstimate = sim.time + sim2.time;
        } else {
            this.endSpeed = targetSpeed;
            this.timeEstimate = sim.time + dist / targetSpeed;
            // easy case! approximate!
        }

        /*float oldEstimate = super.getTimeEstimate();
        if(Float.isInfinite(oldEstimate)){
            System.out.println(this.startPos+" "+this.turnCircleStartPos+" "+this.circlePos+" "+this.tangentPoint+" "+this.ccw);
        }

        if(Math.abs(oldEstimate - this.timeEstimate) > 0.0){
            System.out.println("old: "+oldEstimate+" new: "+this.timeEstimate+" oldDist: "+this.bakedPath.distances[0]+" newDist: "+(this.startPos.distance(this.turnCircleStartPos) + dist)+" targetSpeed: "+targetSpeed+" initDist: "+(this.startPos.distance(this.turnCircleStartPos))+" startSpeed: "+this.startSpeed+" method: "+(didProper ? "proper" : "approx")+" oldSpeed="+this.getEndSpeed()+" newSpeed="+this.endSpeed);
            System.out.println(this.startPos+" "+this.startTangent+" "+this.startSpeed+" rad "+this.circleRadius+" end="+endPos+" "+startBoost+" "+allowBoost+" "+this.tangentPoint+" "+this.turnCircleStartPos);
        }*/

        return this.timeEstimate;
    }

    @Override
    public float getEndSpeed() {
        if (this.endSpeed < 0)
            this.getTimeEstimate();
        assert this.endSpeed >= 0;

        return this.endSpeed;
    }

    @Override
    public Vector3 getEndPos() {
        return this.tangentPoint.withZ(RLConstants.carElevation);
    }

    @Override
    public Vector3 getEndTangent() {
        return this.tangentPoint.sub(this.circlePos).normalized().cross().mul(-ccw).withZ(0);
    }

    /*@Override
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
    }*/

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
        if (this.startPos.distance(this.turnCircleStartPos) > 1)
            pointList.add(new Curve.ControlPoint(this.startPos.withZ(z), this.startTangent.withZ(0)));

        boolean shouldExit = false;
        for (float currentAngle = startAngle; Math.signum(currentAngle - endAngle) == Math.signum(startAngle - endAngle); currentAngle += angleDiv) {
            final var thisAngle = Vector2.fromAngle(currentAngle);

            if (Math.signum(currentAngle + angleDiv - endAngle) != Math.signum(startAngle - endAngle)) {
                angleDiv = endAngle - currentAngle;
                if (Math.abs(angleDiv * circleRadius) < 1f)
                    break; // Don't make a control point if the angle difference leads to less than 1uu dist to end pos
                shouldExit = true;
            }

            // Calculate tangent
            Vector3 tangent = Vector2.fromAngle(currentAngle)
                    .cross()
                    .mul(Math.signum(angleDiv))
                    .normalized().withZ(0);

            var thisPos = this.circlePos.add(thisAngle.mul(this.circleRadius)).withZ(z);
            pointList.add(new Curve.ControlPoint(thisPos, tangent));

            if (shouldExit)
                break;
        }

        var endTangent = this.getEndTangent();

        if (pointList.size() == 0) // Add a point, to not confuse the pathing
            pointList.add(new Curve.ControlPoint(this.tangentPoint.withZ(z).sub(endTangent), endTangent));
        // End point
        pointList.add(new Curve.ControlPoint(this.tangentPoint.withZ(z), endTangent));

        return new Curve(pointList, MathUtils.clip(maxSamples, 4, 64));
    }
}
