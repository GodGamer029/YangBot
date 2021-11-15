package yangbot.path;

import yangbot.cpp.FlatCurve;
import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.RLConstants;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.YangBallPrediction;
import yangbot.util.hitbox.YangSphereHitbox;
import yangbot.util.math.CubicHermite;
import yangbot.util.math.MathUtils;
import yangbot.util.math.OGH;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Curve {

    public float length;
    public ArrayList<Vector3> points;
    public float[] maxSpeeds;
    public ArrayList<Vector3> tangents;
    public float[] curvatures;
    public float[] distances;
    public float minimumSpeed = -1;

    private List<ControlPoint> controlPoints;
    private PathCheckStatus pathCheckStatus = new PathCheckStatus(PathStatus.UNKNOWN);
    private static int ndiv = 16;
    private final int numSubDivisions;

    public Curve() {
        this.length = -1f;
        this.points = new ArrayList<>();
        this.tangents = new ArrayList<>();
        this.curvatures = new float[0];
        this.distances = new float[0];
        this.maxSpeeds = new float[0];
        this.controlPoints = new ArrayList<>();
        this.numSubDivisions = ndiv;
    }

    public Curve(List<ControlPoint> info) {
        this(info, ndiv);
    }

    private static final float fac = 6f;
    private static final float interpStart = (float) (1f / (1f + Math.exp(fac)));
    private static final float interpEnd = (float) (1f / (1f + Math.exp(0)));

    public Curve(List<ControlPoint> info, int numSubSegments) {
        this.numSubDivisions = numSubSegments;
        this.controlPoints = info;
        this.points = new ArrayList<>();
        this.tangents = new ArrayList<>();
        this.curvatures = new float[0];
        this.maxSpeeds = new float[0];

        int num_segments = info.size() - 1;

        this.points.ensureCapacity(numSubSegments * num_segments + 1);
        this.tangents.ensureCapacity(numSubSegments * num_segments + 1);
        this.curvatures = new float[numSubSegments * num_segments + 1];

        for (int i = 1; i < num_segments - 1; i++) {
            ControlPoint point = info.get(i);
            Vector3 delta_before = point.pos.sub(info.get(i - 1).pos).normalized();
            Vector3 delta_after = info.get(i + 1).pos.sub(point.pos).normalized();

            float phi_before = (float) Math.asin(point.tangent.crossProduct(delta_before).dot(point.normal));
            float phi_after = (float) Math.asin(point.tangent.crossProduct(delta_after).dot(point.normal));

            if (phi_before * phi_after > 0f) {
                float phi;

                if (Math.abs(phi_before) < Math.abs(phi_after))
                    phi = phi_before;
                else
                    phi = phi_after;

                point.tangent = Matrix3x3.axisToRotation(point.normal.mul(phi)).dot(point.tangent);
                info.set(i, point);
            }
        }

        for (int i = 0; i < num_segments; i++) {
            ControlPoint our = info.get(i);
            ControlPoint next = info.get(i + 1);

            Vector3 P0 = our.pos;
            Vector3 P1 = next.pos;

            Vector3 V0 = our.tangent;
            Vector3 V1 = next.tangent;

            Vector3 N0 = our.normal;
            Vector3 N1 = next.normal;

            OGH piece = new OGH(P0, V0, P1, V1);

            int is_last = (i == num_segments - 1) ? 1 : 0;

            for (int j = 0; j < (numSubSegments + is_last); j++) {
                float t = ((float) j) / ((float) numSubSegments);
                // place more control points toward the beginning, there is a bug in here that fucks up the first control point, so we better place the second one close after the first one, because the second one is fine
                if (t > 0 && t < 1 && i == 0)
                    t = MathUtils.remapClip((float) (1f / (1f + Math.exp(-fac * (t - 1)))), interpStart, interpEnd, 0, 1);

                Vector3 g = piece.evaluate(t);
                Vector3 dg = piece.tangent(t);
                Vector3 d2g = piece.acceleration(t);

                float dgMag = (float) dg.magnitude();

                Vector3 normalAtT = N0.mul(1f - t)
                        .add(N1.mul(t))
                        .normalized();

                double kappa = dg
                        .crossProduct(d2g)
                        .dot(normalAtT)
                        / (dgMag * dgMag * dgMag);

                this.points.add(g);
                this.tangents.add(dg.normalized());
                this.curvatures[this.points.size() - 1] = (float) kappa;
            }
        }
        assert this.points.size() == this.tangents.size() && this.tangents.size() == this.curvatures.length : this.points.size() + " " + this.tangents.size() + " " + this.curvatures.length + " " + this.numSubDivisions + " " + info.size();

        calculateDistances();
    }

    public Curve(List<ControlPoint> info, Vector3 dx0, Vector3 dt0, Vector3 dx1, Vector3 dt1, Vector3 start, Vector3 end) {
        this.controlPoints = info;
        this.numSubDivisions = ndiv;
        int num_segments = info.size() - 1;

        this.points = new ArrayList<>();
        this.tangents = new ArrayList<>();
        this.curvatures = new float[0];
        this.maxSpeeds = new float[0];

        this.points.ensureCapacity(numSubDivisions * num_segments + 2);
        this.tangents.ensureCapacity(numSubDivisions * num_segments + 2);

        ArrayList<Vector3> normals = new ArrayList<>();
        ArrayList<Integer> segment_ids = new ArrayList<>();

        normals.ensureCapacity(numSubDivisions * num_segments + 2);
        segment_ids.ensureCapacity(numSubDivisions * num_segments + 2);

        for (int i = 0; i < num_segments; i++) {
            ControlPoint our = info.get(i);
            ControlPoint next = info.get(i + 1);

            Vector3 P0 = our.pos;
            Vector3 P1 = next.pos;

            Vector3 V0 = our.tangent;
            Vector3 V1 = next.tangent;

            Vector3 N0 = our.normal;
            Vector3 N1 = next.normal;

            OGH piece = new OGH(P0, V0, P1, V1);

            int is_last = (i == num_segments - 1) ? 1 : 0;

            for (int j = 0; j < (numSubDivisions + is_last); j++) {
                float t = ((float) j) / ((float) numSubDivisions);
                // t = MathUtils.remapClip((float)(1f / (1f + Math.exp(-5*t))), 0.05f, 0.95f, 0, 1);

                Vector3 p = piece.evaluate(t);
                Vector3 n = N0.mul(1f - t).add(N1.mul(t)).normalized();

                if (p.sub(P0).dot(N0) < 0f) {
                    p = p.sub(N0.mul(p.sub(P0).dot(N0)));
                }

                if (p.sub(P1).dot(N1) < 0f) {
                    p = p.sub(N1.mul(p.sub(P1).dot(N1)));
                }

                this.points.add(p);
                normals.add(n);
                segment_ids.add(i);
            }
        }

        calculateDistances();

        CubicHermite correction = new CubicHermite(dx0, dt0, dx1, dt1, length);

        for (int i = 0; i < this.points.size(); i++) {
            Vector3 n = normals.get(i);
            Vector3 dx = correction.e(length - distances[i]);
            this.points.set(i, this.points.get(i).add(dx.sub(n.mul(dx.dot(n)))));
        }

        if (start.sub(this.points.get(0)).magnitude() > 1f) {
            this.points.add(0, start);
            normals.add(0, normals.get(0));
        }

        if (end.sub(this.points.get(this.points.size() - 1)).magnitude() > 1f) {
            this.points.add(end);
            normals.add(normals.get(normals.size() - 1));
        }

        calculateDistances();
        calculateTangents();

        this.curvatures = new float[this.points.size()];

        int last = this.curvatures.length - 1;

        Vector3 m, n;

        float inPlaneWeight = 0.5f;
        float kappa_max = 0.004f;
        float kappa1, kappa2, ds;

        for (int i = 1; i < last; i++) {
            m = this.tangents.get(i + 1).crossProduct(this.tangents.get(i - 1));
            n = normals.get(i);

            ds = this.distances[i - 1] - this.distances[i + 1];
            kappa1 = (float) MathUtils.clip(Math.asin(m.magnitude()) / ds, 0f, kappa_max);
            kappa2 = (float) Math.asin(Math.abs(m.dot(n))) / ds;
            this.curvatures[i] = MathUtils.lerp(kappa1, kappa2, inPlaneWeight);
        }

        m = this.tangents.get(1).crossProduct(this.tangents.get(0));
        n = normals.get(0);
        ds = this.distances[0] - this.distances[1];
        kappa1 = (float) MathUtils.clip(Math.asin(m.magnitude()) / ds, 0f, kappa_max);
        kappa2 = (float) Math.asin(Math.abs(m.dot(n))) / ds;
        this.curvatures[0] = MathUtils.lerp(kappa1, kappa2, inPlaneWeight);

        m = this.tangents.get(last).crossProduct(this.tangents.get(last - 1));
        n = normals.get(last);
        ds = this.distances[last - 1] - this.distances[last];
        kappa1 = (float) MathUtils.clip(Math.asin(m.magnitude()) / ds, 0f, kappa_max);
        kappa2 = (float) Math.asin(Math.abs(m.dot(n))) / ds;
        this.curvatures[last] = MathUtils.lerp(kappa1, kappa2, inPlaneWeight);
    }

    public static Curve from(FlatCurve flatCurve) {
        Curve c = new Curve();

        c.length = flatCurve.length();
        if (Float.isNaN(c.length))
            return null;

        FloatBuffer curvaturesBuffer = flatCurve.curvaturesAsByteBuffer().asFloatBuffer();
        c.curvatures = new float[curvaturesBuffer.limit()];
        curvaturesBuffer.get(c.curvatures);

        FloatBuffer distancesBuffer = flatCurve.distancesAsByteBuffer().asFloatBuffer();
        c.distances = new float[distancesBuffer.limit()];
        distancesBuffer.get(c.distances);

        c.points = new ArrayList<>(flatCurve.pointsLength());
        for (int i = 0; i < flatCurve.pointsLength(); i++)
            c.points.add(new Vector3(flatCurve.points(i)));
        for (int i = 0; i < flatCurve.tangentsLength(); i++)
            c.tangents.add(new Vector3(flatCurve.tangents(i)));

        return c;
    }

    public PathCheckStatus doPathChecking(CarData car, float absoluteArrivalTime, YangBallPrediction ballPrediction) {
        assert this.curvatures.length > 0;

        if (ballPrediction == null)
            ballPrediction = YangBallPrediction.empty();
        if (absoluteArrivalTime < 0)
            absoluteArrivalTime = car.elapsedSeconds + (this.length / (DriveManeuver.max_throttle_speed));

        final float dt = RLConstants.simulationTickFrequency;
        final float relativeArrivalTime = absoluteArrivalTime - car.elapsedSeconds;
        final float averageSpeed = this.length / Math.max(relativeArrivalTime, RLConstants.simulationTickFrequency);

        if (averageSpeed > CarData.MAX_VELOCITY + 25) {
            this.pathCheckStatus = new PathCheckStatus(averageSpeed);
            return this.pathCheckStatus;
        }

        if (this.maxSpeeds.length == 0)
            this.calculateMaxSpeeds(CarData.MAX_VELOCITY, CarData.MAX_VELOCITY, 100);

        float currentSpeed = (float) car.velocity.dot(car.forward());

        float distToTarget = this.findNearest(car.position);

        double boost = car.boost;
        boolean collidedWithBall = false;
        float distanceOfBallCollision = 0;
        Vector3 ballCollisionContactPoint = new Vector3();
        Vector3 ballCollisionBallPosition = new Vector3();

        final YangSphereHitbox carBox = car.hitbox.asSphere(1.1f);
        float t = 0;
        for (; t < relativeArrivalTime; t += dt) {
            //if (distToTarget <= 0) // Made it there before time ran out, shouldn't usually happen
            //   break;

            final float timeUntilArrival = relativeArrivalTime - t;
            final float maxSpeed = Math.max(50, this.maxSpeedAt(distToTarget));
            final float avgSpeedAhead = distToTarget / Math.max(timeUntilArrival, dt);

            if (avgSpeedAhead > CarData.MAX_VELOCITY + 50) {
                this.pathCheckStatus = new PathCheckStatus(avgSpeedAhead);
                return this.pathCheckStatus;
            }

            final Optional<YangBallPrediction.YangPredictionFrame> ballAtFrameOptional = ballPrediction.getFrameAtRelativeTime(t);

            // Check for ball collisions
            if (!collidedWithBall && ballAtFrameOptional.isPresent() && distToTarget > Math.max(currentSpeed, 500f) * 0.2f + 50) {
                BallData ballAtFrame = ballAtFrameOptional.get().ballData.makeMutable();

                Vector3 carPos = this.pointAt(distToTarget);
                if (ballAtFrame.collidesWith(carBox, carPos)) { // Collide with ball
                    collidedWithBall = true;
                    distanceOfBallCollision = distToTarget;

                    ballCollisionContactPoint = carBox.getClosestPointOnHitbox(carPos, ballAtFrame.position);
                    ballCollisionBallPosition = ballAtFrame.position;
                    //System.out.println("Colliding with ball at t=" + t + " arr=" + (relativeArrivalTime - t));
                }
            }
            // Simulate the car
            {
                ControlsOutput sampleOutput = new ControlsOutput();
                DriveManeuver.speedController(dt, sampleOutput, currentSpeed, Math.min(maxSpeed, avgSpeedAhead), CarData.MAX_VELOCITY, 0.04f, true);

                if (boost <= 0)
                    sampleOutput.withBoost(false);
                else if (sampleOutput.holdBoost())
                    boost -= CarData.BOOST_CONSUMPTION * dt;

                final float forceForward = CarData.driveForceForward(sampleOutput, currentSpeed, 0, 0);
                final float forceLeft = 0 /*CarData.driveForceLeft(sampleOutput, currentSpeed, 0, 0)*/;

                currentSpeed += forceForward * dt + Math.abs(forceLeft * dt);
                currentSpeed = Math.min(Math.abs(currentSpeed), CarData.MAX_VELOCITY);
                distToTarget -= currentSpeed * dt;
            }
        }

        this.pathCheckStatus = new PathCheckStatus(distToTarget < 150 && distToTarget > -150 ? PathStatus.VALID : PathStatus.SPEED_EXCEEDED, collidedWithBall, distanceOfBallCollision, ballCollisionContactPoint, ballCollisionBallPosition, currentSpeed);
        return this.pathCheckStatus;
    }

    public void draw(AdvancedRenderer renderer, Color color) {
        Vector3 lastPoint = null;
        for (Vector3 point : this.points) {
            if (lastPoint == null) {
                lastPoint = point;
                continue;
            }
            if (lastPoint.distance(point) > 10) {
                renderer.drawLine3d(color, lastPoint, point);
                lastPoint = point;
            }

        }

        if (this.points.size() > 1) {
            renderer.drawCentered3dCube(Color.GREEN, this.points.get(0), 50);
            renderer.drawCentered3dCube(Color.RED, this.points.get(this.points.size() - 1), 50);
        }
    }

    public Vector3 pointAt(float s) {
        s = MathUtils.clip(s, 0, distances[0]);

        for (int i = 0; i < (points.size() - 1); i++) {
            if (distances[i] >= s && s >= distances[i + 1]) {
                float u = (s - distances[i + 1]) / (distances[i] - distances[i + 1]);
                if (Float.isNaN(u)) // Happens when distances are equal
                    return points.get(i + 1);
                return MathUtils.lerp(points.get(i + 1), points.get(i), u);
            }
        }
        return new Vector3();
    }

    public Vector3 tangentAt(float s) {
        if (s == -1)
            s = distances[0];
        s = MathUtils.clip(s, 0, distances[0]);

        for (int i = 0; i < (points.size() - 1); i++) {
            if (distances[i] >= s && s >= distances[i + 1]) {
                float u = (s - distances[i + 1]) / (distances[i] - distances[i + 1]);
                return MathUtils.lerp(tangents.get(i + 1), tangents.get(i), u).normalized();
            }
        }
        assert false : "s="+s+" ds="+distances.length+" d0="+distances[0]+" dE="+distances[distances.length - 1];
        return new Vector3();
    }

    public float curvatureAt(float s) {
        s = MathUtils.clip(s, 0, distances[0]);

        for (int i = 0; i < (points.size() - 1); i++) {
            if (distances[i] >= s && s >= distances[i + 1]) {
                float deltaTheta = (float) tangents.get(i + 1).angle(tangents.get(i));
                float deltaS = distances[i] - distances[i + 1];
                assert Float.isFinite(deltaTheta) : deltaTheta + " " + tangents.get(i + 1) + " " + tangents.get(i);
                return deltaTheta / deltaS;
            }
        }

        return 0f;
    }

    public float maxSpeedAt(float s) {
        s = MathUtils.clip(s, 0, distances[0]);

        if (maxSpeeds.length == 0)
            this.calculateMaxSpeeds(CarData.MAX_VELOCITY, CarData.MAX_VELOCITY, 100);

        for (int i = 0; i < (points.size() - 1); i++) {
            if (distances[i] >= s && s >= distances[i + 1]) {
                float u = (s - distances[i + 1]) / (distances[i] - distances[i + 1]);
                assert u >= 0 && u <= 1 : u + " " + s + " " + distances[i] + " " + distances[i + 1];
                float lerped = MathUtils.lerp(maxSpeeds[i + 1], maxSpeeds[i], u);
                assert Float.isFinite(lerped);
                return lerped;
            }
        }
        return 0f;
    }

    public float getControlPoint(float s) {
        s = MathUtils.clip(s, 0, distances[0]);

        for (int i = 0; i < (points.size() - 1); i++) {
            if (distances[i] >= s && s >= distances[i + 1]) {
                if (i == 0)
                    return 0;
                float u = (s - distances[i + 1]) / (distances[i] - distances[i + 1]);
                return MathUtils.lerp(maxSpeeds[i + 1], maxSpeeds[i], u);
            }
        }
        return (points.size() - 2f) / numSubDivisions;
    }

    public float findNearest(Vector3 c) {
        float s = length;
        float minDistance = (float) c.sub(points.get(0)).magnitude();

        for (int i = 0; i < (points.size() - 1); i++) {
            Vector3 a = points.get(i);
            Vector3 b = points.get(i + 1);

            float alpha = MathUtils.clip(b.sub(a).dot(c.sub(a)) / Math.max(b.sub(a).dot(b.sub(a)), 0.0001f), 0f, 1f);
            assert !Float.isNaN(alpha) : a.toString() + ":" + b.toString() + " " + i + " " + c;
            float distance = (float) c.sub(a.add(b.sub(a).mul(alpha))).magnitude();

            if (distance < minDistance) {
                minDistance = distance;
                s = MathUtils.lerp(distances[i], distances[i + 1], alpha);
            }
        }

        return s;
    }

    private void calculateDistances() {
        distances = new float[points.size()];
        int last = points.size() - 1;

        for (int i = last - 1; i >= 0; i--)
            distances[i] = (float) (distances[i + 1] + points.get(i + 1).sub(points.get(i)).magnitude());

        length = distances[0];
    }

    public void calculateTangents() {
        tangents = new ArrayList<>(points.size());
        while (tangents.size() < points.size())
            tangents.add(new Vector3());

        int last = tangents.size() - 1;

        tangents.set(0,
                points.get(0).mul(-3f)
                        .add(points.get(1).mul(4f))
                        .sub(points.get(2))
                        .normalized()
        );

        for (int i = 1; i < last; i++)
            tangents.set(i, points.get(i + 1).sub(points.get(i - 1)).normalized());

        tangents.set(last,
                points.get(last).mul(3f)
                        .sub(points.get(last - 1).mul(4f))
                        .add(points.get(last - 2))
                        .normalized()
        );

        assert !tangents.get(tangents.size() - 1).isZero();
    }


    @SuppressWarnings("UnusedReturnValue")
    public float calculateMaxSpeeds(float v0, float vf, float boost) {
        boost *= 0.99f;
        //System.out.println("calculateMaxSpeeds v0=" + v0 + " vf=" + vf + " allowBoost=" + allowBoost);
        final Vector3 gravity = new Vector3(0, 0, -650);

        v0 = MathUtils.clip(v0, 0, CarData.MAX_VELOCITY);
        if(vf != -1)
            vf = MathUtils.clip(vf, 0, CarData.MAX_VELOCITY);

        maxSpeeds = new float[curvatures.length];

        float[] tempCurvatures = new float[curvatures.length + 2];
        System.arraycopy(this.curvatures, 0, tempCurvatures, 0, curvatures.length);
        tempCurvatures[curvatures.length] = this.curvatures[curvatures.length - 1]; // pretend like the last two curvatures are 0 (end of path)
        tempCurvatures[curvatures.length + 1] = this.curvatures[curvatures.length - 1];

        for (int i = 0; i < curvatures.length; i++) // average the curvatures
            tempCurvatures[i] = (3 * tempCurvatures[i] + tempCurvatures[i + 1] + tempCurvatures[i + 2]) / 5;

        for (int i = 0; i < curvatures.length; i++) {
            maxSpeeds[i] = DriveManeuver.maxTurningSpeed(tempCurvatures[i] * 1.02f);
            assert maxSpeeds[i] > 0 && Float.isFinite(maxSpeeds[i]) : maxSpeeds[i] + " " + i + " " + tempCurvatures[i] + " " + curvatures[i] + " " + curvatures[Math.min(curvatures.length - 1, i + 1)] + " " + distances[i];
        }

        maxSpeeds[0] = Math.min(v0, maxSpeeds[0]);
        if(vf != -1)
            maxSpeeds[maxSpeeds.length - 1] = Math.min(vf, maxSpeeds[maxSpeeds.length - 1]);

        assert curvatures.length == distances.length : curvatures.length + " " + distances.length;

        for (int i = 1; i < curvatures.length; i++) {
            float ds = distances[i - 1] - distances[i];
            Vector3 t = tangents.get(i).add(tangents.get(i - 1)).normalized();

            float attainable_speed = maximizeSpeedWithThrottleSteer((boost > 0 ? 1f : 0f) * DriveManeuver.boost_acceleration + gravity.dot(t), maxSpeeds[i - 1], ds, tempCurvatures[i]);
            maxSpeeds[i] = Math.min(maxSpeeds[i], attainable_speed);
            assert Float.isFinite(maxSpeeds[i]);
        }

        for (int i = curvatures.length - 2; i >= 0; i--) {
            float ds = distances[i] - distances[i + 1];
            if (Math.abs(ds) < 0.001)
                continue;
            Vector3 t = tangents.get(i).add(tangents.get(i + 1)).normalized();
            float attainable_speed = maximizeSpeedWithoutThrottle(-DriveManeuver.brake_acceleration - gravity.dot(t), maxSpeeds[i + 1], ds);
            maxSpeeds[i] = Math.min(maxSpeeds[i], attainable_speed);
            assert Float.isFinite(maxSpeeds[i]);
        }

        if(boost > 0){
            float minBoostPressTime = 0;
            for (int i = 1; i < curvatures.length; i++) {
                float endS = distances[i];
                float startDs = distances[i - 1] - distances[i];
                float ds = startDs;
                Vector3 t = tangents.get(i).add(tangents.get(i - 1)).normalized();

                float dt = RLConstants.simulationTickFrequency;
                float v = maxSpeeds[i - 1];
                float gAccel = gravity.dot(t);

                for (float et = 0; et < 5; et += dt){
                    float curv = MathUtils.remapClip(ds, startDs, 0, tempCurvatures[i - 1], tempCurvatures[i]);
                    float maxCurv = DriveManeuver.maxTurningCurvature(v);
                    float ratio = MathUtils.clip(Math.abs(curv) / maxCurv, 0, 1);
                    float steerSlowdown = CarData.slowdownForceFromSteering(ratio * ratio, v);
                    assert steerSlowdown <= 0 : steerSlowdown;
                    var c = new ControlsOutput();
                    float targetSpeed = maxSpeedAt(endS + ds);
                    assert Float.isFinite(targetSpeed);
                    assert Float.isFinite(v);
                    assert Float.isFinite(steerSlowdown);
                    DriveManeuver.speedController(dt, c, v, targetSpeed, targetSpeed + 5, 0.04f, true, steerSlowdown);
                    if(boost > 0){
                        if(c.holdBoost()){
                            minBoostPressTime = 0.1f;
                            boost -= CarData.BOOST_CONSUMPTION * dt;

                        }else if(minBoostPressTime > 0){
                            c.withBoost();
                            boost -= CarData.BOOST_CONSUMPTION * dt;
                            minBoostPressTime -= dt;
                        }
                    }else
                        c.withBoost(false);

                    float accel = (CarData.driveForceForward(c, v, 0, 0) + steerSlowdown + gAccel) * dt;
                    float change = (v + 0.5f * accel) * dt;
                    assert Float.isFinite(accel) : accel + " v="+v+ " st="+steerSlowdown+" g="+gAccel+" "+CarData.driveForceForward(c, v, 0, 0)+" thr="+c.getThrottle()+" boo="+c.holdBoost();
                    v += accel;
                    ds -= change;
                    if (ds <= 0) {
                        v -= ds * (accel / change);
                        if(c.holdBoost())
                            boost -= ds * (CarData.BOOST_CONSUMPTION * dt / change);
                        else if(minBoostPressTime > 0 && minBoostPressTime < 0.1f)
                            minBoostPressTime -= ds * (dt / change);
                        break;
                    }
                    v = MathUtils.clip(v, -CarData.MAX_VELOCITY, CarData.MAX_VELOCITY);
                }
                v = MathUtils.clip(v, -CarData.MAX_VELOCITY, CarData.MAX_VELOCITY);
                maxSpeeds[i] = Math.min(maxSpeeds[i], v);
            }
        }

        float time = 0.0f;
        this.minimumSpeed = CarData.MAX_VELOCITY + 100;

        for (int i = curvatures.length - 2; i >= 0; i--) {
            float ds = distances[i] - distances[i + 1];
            if (Math.abs(ds) < 0.001)
                continue;
            time += ds / (0.5f * (maxSpeeds[i] + maxSpeeds[i + 1]));
            if(Math.abs(maxSpeeds[i]) < this.minimumSpeed)
                this.minimumSpeed = maxSpeeds[i];
        }

        if(this.minimumSpeed == CarData.MAX_VELOCITY + 100)
            this.minimumSpeed = -1;
        else
            this.minimumSpeed = MathUtils.clip(this.minimumSpeed, 0, CarData.MAX_VELOCITY);
        //System.out.println("Calc time: "+time);
        assert Float.isFinite(time) && !Float.isNaN(time) : time;
        return time;
    }

    public float maximizeSpeedWithThrottleSteer(float additionalAcceleration, float v0, float distance, float curvature) {
        if (distance == 0)
            return v0;
        final float dt = RLConstants.simulationTickFrequency;
        float currentDistance = 0.0f;
        float currentVelocity = v0;

        for (int i = 0; i < 50; i++) {
            float maxCurv = DriveManeuver.maxTurningCurvature(currentVelocity);
            float ratio = MathUtils.clip(Math.abs(curvature) / maxCurv, 0, 1);
            float steerSlowdown = CarData.slowdownForceFromSteering(ratio * ratio, currentVelocity);

            float accel = (DriveManeuver.throttleAcceleration(currentVelocity) + additionalAcceleration + steerSlowdown) * dt;
            float ds = (currentVelocity + 0.5f * accel) * dt;
            currentVelocity += accel;
            currentDistance += ds;
            if (currentDistance > distance) {
                currentVelocity -= (currentDistance - distance) * (accel / ds);
                break;
            }
        }
        return currentVelocity;
    }

    public float maximizeSpeedWithoutThrottle(float additionalAcceleration, float v0, float distance) {
        final float dt = RLConstants.simulationTickFrequency;
        final float dv = additionalAcceleration * dt;

        float currentDistance = 0.0f;
        float currentVelocity = v0;

        for (int i = 0; i < 100; i++) {
            float ds = (currentVelocity + 0.5f * dv) * dt;
            currentVelocity += dv;
            currentDistance += ds;
            if (currentDistance > distance) {
                currentVelocity -= (currentDistance - distance) * (dv / ds);
                break;
            }
        }
        return currentVelocity;
    }

    public PathCheckStatus getPathCheckStatus() {
        return pathCheckStatus;
    }

    public List<ControlPoint> getControlPoints() {
        return controlPoints;
    }

    public static class ControlPoint {
        public final Vector3 pos;
        public final Vector3 normal;
        public Vector3 tangent;

        public ControlPoint(Vector3 pos, Vector3 tangent, Vector3 normal) {
            this.pos = pos;
            this.tangent = tangent;
            this.normal = normal;
        }

        public ControlPoint(Vector3 pos, Vector3 tangent) {
            this.pos = pos;
            this.tangent = tangent;
            this.normal = new Vector3(0, 0, 1);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("ControlPoint{");
            sb.append("pos=").append(pos);
            sb.append(", normal=").append(normal);
            sb.append(", tangent=").append(tangent);
            sb.append('}');
            return sb.toString();
        }
    }

    public enum PathStatus {
        VALID,
        SPEED_EXCEEDED,
        UNKNOWN
    }

    public static class PathCheckStatus {
        public final PathStatus pathStatus;
        public final boolean collidedWithBall;
        public final float distanceAtBallCollision;
        public final Vector3 ballCollisionContactPoint;
        public final Vector3 ballCollisionBallPosition;
        public final float speedNeeded;

        public PathCheckStatus(PathStatus pathStatus, boolean collidedWithBall, float distanceAtBallCollision, Vector3 ballCollisionContactPoint, Vector3 ballCollisionBallPosition, float speedNeeded) {
            this.pathStatus = pathStatus;
            this.collidedWithBall = collidedWithBall;
            this.distanceAtBallCollision = distanceAtBallCollision;
            this.ballCollisionContactPoint = ballCollisionContactPoint;
            this.ballCollisionBallPosition = ballCollisionBallPosition;
            this.speedNeeded = speedNeeded;
        }

        public PathCheckStatus(PathStatus pathStatus) {
            this.pathStatus = pathStatus;
            this.collidedWithBall = false;
            this.distanceAtBallCollision = 0;
            this.ballCollisionContactPoint = new Vector3();
            this.ballCollisionBallPosition = new Vector3();
            this.speedNeeded = 0;
        }

        public PathCheckStatus(float speedNeeded) {
            this.pathStatus = PathStatus.SPEED_EXCEEDED;
            this.collidedWithBall = false;
            this.distanceAtBallCollision = 0;
            this.ballCollisionContactPoint = new Vector3();
            this.ballCollisionBallPosition = new Vector3();
            this.speedNeeded = speedNeeded;
        }

        public boolean isValid() {
            return pathStatus == PathStatus.VALID;
        }
    }
}
