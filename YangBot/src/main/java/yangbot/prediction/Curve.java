package yangbot.prediction;

import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.RLConstants;
import yangbot.manuever.DriveManeuver;
import yangbot.util.*;
import yangbot.util.hitbox.YangSphereHitbox;
import yangbot.vector.Matrix3x3;
import yangbot.vector.Vector3;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class Curve {

    public float length;
    public final ArrayList<Vector3> points;
    public float[] maxSpeeds;
    private ArrayList<Vector3> tangents;
    private float[] curvatures;
    private float[] distances;

    private static int ndiv = 20;

    public Curve() {
        length = -1f;
        points = new ArrayList<>();
        tangents = new ArrayList<>();
        curvatures = new float[0];
        distances = new float[0];
        maxSpeeds = new float[0];
    }

    public Curve(List<ControlPoint> info) {
        points = new ArrayList<>();
        tangents = new ArrayList<>();
        curvatures = new float[0];
        maxSpeeds = new float[0];

        int num_segments = info.size() - 1;

        points.ensureCapacity(ndiv * num_segments + 2);
        tangents.ensureCapacity(ndiv * num_segments + 2);
        ArrayList<Float> tempCurvature = new ArrayList<>(ndiv * num_segments + 2);

        for (int i = 1; i < num_segments - 1; i++) {
            Vector3 delta_before = info.get(i).point.sub(info.get(i - 1).point).normalized();
            Vector3 delta_after = info.get(i + 1).point.sub(info.get(i).point).normalized();

            float phi_before = (float) Math.asin(info.get(i).tangent.crossProduct(delta_before).dot(info.get(i).normal));
            float phi_after = (float) Math.asin(info.get(i).tangent.crossProduct(delta_after).dot(info.get(i).normal));

            if (phi_before * phi_after > 0f) {
                float phi;

                if (Math.abs(phi_before) < Math.abs(phi_after))
                    phi = phi_before;
                else
                    phi = phi_after;

                ControlPoint p = info.get(i);
                p.tangent = Matrix3x3.axisToRotation(p.normal.mul(phi)).dot(p.tangent);
                info.set(i, p);
            }
        }

        for (int i = 0; i < num_segments; i++) {
            ControlPoint our = info.get(i);
            ControlPoint next = info.get(i + 1);

            Vector3 P0 = our.point;
            Vector3 P1 = next.point;

            Vector3 V0 = our.tangent;
            Vector3 V1 = next.tangent;

            Vector3 N0 = our.normal;
            Vector3 N1 = next.normal;

            OGH piece = new OGH(P0, V0, P1, V1);

            int is_last = (i == num_segments - 1) ? 1 : 0;

            for (int j = 0; j < (ndiv + is_last); j++) {
                float t = ((float) j) / ((float) ndiv);

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

                points.add(g);
                tangents.add(dg.normalized());
                tempCurvature.add((float) kappa);
            }
        }

        curvatures = new float[tempCurvature.size()];
        for (int i = 0; i < curvatures.length; i++) {
            curvatures[i] = tempCurvature.get(i);
        }
        calculateDistances();
    }

    public Curve(List<ControlPoint> info, Vector3 dx0, Vector3 dt0, Vector3 dx1, Vector3 dt1, Vector3 start, Vector3 end) {
        int num_segments = info.size() - 1;

        points = new ArrayList<>();
        tangents = new ArrayList<>();
        curvatures = new float[0];
        maxSpeeds = new float[0];

        points.ensureCapacity(ndiv * num_segments + 2);
        tangents.ensureCapacity(ndiv * num_segments + 2);

        ArrayList<Vector3> normals = new ArrayList<>();
        ArrayList<Integer> segment_ids = new ArrayList<>();

        normals.ensureCapacity(ndiv * num_segments + 2);
        segment_ids.ensureCapacity(ndiv * num_segments + 2);

        for (int i = 0; i < num_segments; i++) {
            ControlPoint our = info.get(i);
            ControlPoint next = info.get(i + 1);

            Vector3 P0 = our.point;
            Vector3 P1 = next.point;

            Vector3 V0 = our.tangent;
            Vector3 V1 = next.tangent;

            Vector3 N0 = our.normal;
            Vector3 N1 = next.normal;

            OGH piece = new OGH(P0, V0, P1, V1);

            int is_last = (i == num_segments - 1) ? 1 : 0;

            for (int j = 0; j < (ndiv + is_last); j++) {
                float t = ((float) j) / ((float) ndiv);

                Vector3 p = piece.evaluate(t);
                Vector3 n = N0.mul(1f - t).add(N1.mul(t)).normalized();

                if (p.sub(P0).dot(N0) < 0f) {
                    p = p.sub(N0.mul(p.sub(P0).dot(N0)));
                }

                if (p.sub(P1).dot(N1) < 0f) {
                    p = p.sub(N1.mul(p.sub(P1).dot(N1)));
                }

                points.add(p);
                normals.add(n);
                segment_ids.add(i);
            }
        }

        calculateDistances();

        CubicHermite correction = new CubicHermite(dx0, dt0, dx1, dt1, length);

        for (int i = 0; i < points.size(); i++) {
            Vector3 n = normals.get(i);
            Vector3 dx = correction.e(length - distances[i]);
            points.set(i, points.get(i).add(dx.sub(n.mul(dx.dot(n)))));
        }

        /*for(int i = 1; i < points.size() - 1; i++){
            float s0 = distances[i];
            Vector3 p0 = points.get(i);
            Vector3 n0 = normals.get(i);
            Vector3 t0 = points.get(i+1).sub(points.get(i-1)).normalized();

            for(int j = points.size() - 2; j > i + 1; j--){
                if(segment_ids.get(i).equals(segment_ids.get(j)))
                    break;

                float s1 = distances[j];
                Vector3 p1 = points.get(j);
                Vector3 n1 = normals.get(j);
                Vector3 t1 = points.get(j + 1).sub(points.get(j-1)).normalized();

                float inplane = (float) n0.dot(n1);
                float collinearity = (float) (p1.sub(p0).dot(t0) / p1.sub(p0).magnitude());
                float avg_curvature = (float) (t0.crossProduct(t1).dot(n1) / (s1 - s0));
            }
        }*/

        if (start.sub(points.get(0)).magnitude() > 1f) {
            points.add(0, start);
            normals.add(0, normals.get(0));
        }

        if (end.sub(points.get(points.size() - 1)).magnitude() > 1f) {
            points.add(end);
            normals.add(normals.get(normals.size() - 1));
        }

        calculateDistances();
        calculateTangents();

        curvatures = new float[points.size()];

        int last = curvatures.length - 1;

        Vector3 m, n;

        float inPlaneWeight = 0.5f;

        float kappa_max = 0.004f;

        float kappa1, kappa2, ds;

        for (int i = 1; i < last; i++) {
            m = tangents.get(i + 1).crossProduct(tangents.get(i - 1));
            n = normals.get(i);

            ds = distances[i - 1] - distances[i + 1];
            kappa1 = (float) MathUtils.clip(Math.asin(m.magnitude()) / ds, 0f, kappa_max);
            kappa2 = (float) Math.asin(Math.abs(m.dot(n))) / ds;
            curvatures[i] = MathUtils.lerp(kappa1, kappa2, inPlaneWeight);
        }

        m = tangents.get(1).crossProduct(tangents.get(0));
        n = normals.get(0);
        ds = distances[0] - distances[1];
        kappa1 = (float) MathUtils.clip(Math.asin(m.magnitude()) / ds, 0f, kappa_max);
        kappa2 = (float) Math.asin(Math.abs(m.dot(n))) / ds;
        curvatures[0] = MathUtils.lerp(kappa1, kappa2, inPlaneWeight);

        m = tangents.get(last).crossProduct(tangents.get(last - 1));
        n = normals.get(last);
        ds = distances[last - 1] - distances[last];
        kappa1 = (float) MathUtils.clip(Math.asin(m.magnitude()) / ds, 0f, kappa_max);
        kappa2 = (float) Math.asin(Math.abs(m.dot(n))) / ds;
        curvatures[last] = MathUtils.lerp(kappa1, kappa2, inPlaneWeight);
    }

    public PathCheckStatus doPathChecking(CarData car, float absoluteArrivalTime, YangBallPrediction ballPrediction) {
        if (ballPrediction == null)
            ballPrediction = YangBallPrediction.empty();
        if (absoluteArrivalTime < 0)
            absoluteArrivalTime = car.elapsedSeconds + (this.length / (DriveManeuver.max_throttle_speed));
        final float dt = RLConstants.simulationTickFrequency;
        final float relativeArrivalTime = absoluteArrivalTime - car.elapsedSeconds;
        final float averageSpeed = this.length / Math.max(relativeArrivalTime, RLConstants.simulationTickFrequency);

        if (averageSpeed > CarData.MAX_VELOCITY + 25)
            return new PathCheckStatus(PathStatus.SPEED_EXCEEDED);

        if (this.maxSpeeds.length == 0)
            this.calculateMaxSpeeds(CarData.MAX_VELOCITY, CarData.MAX_VELOCITY);

        float currentSpeed = (float) car.velocity.dot(car.forward());
        float distToTarget = this.findNearest(car.position);
        double boost = car.boost;
        boolean collidedWithBall = false;
        float distanceOfBallCollision = 0;
        Vector3 ballCollisionContactPoint = new Vector3();
        Vector3 ballCollisionBallPosition = new Vector3();

        final YangSphereHitbox carBox = car.hitbox.asSphere(1.1f);

        for (float t = 0; t < relativeArrivalTime; t += dt) {
            if (distToTarget <= 0) // Made it there before time ran out, shouldn't usually happen
                break;
            final float timeUntilArrival = relativeArrivalTime - t;
            final float maxSpeed = Math.max(10, this.maxSpeedAt(distToTarget));

            final float avgSpeedAhead = distToTarget / Math.max(timeUntilArrival, dt);

            final Optional<YangBallPrediction.YangPredictionFrame> ballAtFrameOptional = ballPrediction.getFrameAtRelativeTime(t);

            // Check for ball collisions
            if (!collidedWithBall && ballAtFrameOptional.isPresent() && distToTarget > Math.max(currentSpeed, 500f) * 0.2f + 50) {
                BallData ballAtFrame = ballAtFrameOptional.get().ballData;

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
                DriveManeuver.speedController(dt, sampleOutput, currentSpeed, Math.min(maxSpeed, avgSpeedAhead));

                if (boost <= 0)
                    sampleOutput.withBoost(false);
                else if (sampleOutput.holdBoost())
                    boost -= 33.3f * dt;

                final float forceForward = CarData.driveForceForward(sampleOutput, currentSpeed, 0, 0);
                final float forceLeft = CarData.driveForceLeft(sampleOutput, currentSpeed, 0, 0);

                currentSpeed += forceForward * dt + Math.abs(forceLeft * dt);
                distToTarget -= currentSpeed * dt;
            }
        }

        return new PathCheckStatus(distToTarget < 150 ? PathStatus.VALID : PathStatus.SPEED_EXCEEDED, collidedWithBall, distanceOfBallCollision, ballCollisionContactPoint, ballCollisionBallPosition);
    }



    public void draw(AdvancedRenderer renderer) {
        Vector3 lastPoint = null;
        for (Vector3 point : this.points) {
            if (lastPoint == null) {
                lastPoint = point;
                continue;
            }
            renderer.drawLine3d(Color.RED, lastPoint, point);
            lastPoint = point;
        }

        if (this.points.size() > 1) {
            renderer.drawCentered3dCube(Color.YELLOW, this.points.get(0), 50);
            renderer.drawCentered3dCube(Color.YELLOW, this.points.get(this.points.size() - 1), 50);
        }
    }

    public Vector3 pointAt(float s) {
        s = MathUtils.clip(s, 0, distances[0]);

        for (int i = 0; i < (points.size() - 1); i++) {
            if (distances[i] >= s && s >= distances[i + 1]) {
                float u = (s - distances[i + 1]) / (distances[i] - distances[i + 1]);
                return MathUtils.lerp(points.get(i + 1), points.get(i), u);
            }
        }
        return new Vector3();
    }

    public Vector3 tangentAt(float s) {
        s = MathUtils.clip(s, 0, distances[0]);

        for (int i = 0; i < (points.size() - 1); i++) {
            if (distances[i] >= s && s >= distances[i + 1]) {
                float u = (s - distances[i + 1]) / (distances[i] - distances[i + 1]);
                return MathUtils.lerp(tangents.get(i + 1), tangents.get(i), u).normalized();
            }
        }
        return new Vector3();
    }

    public float curvatureAt(float s) {
        s = MathUtils.clip(s, 0, distances[0]);

        for (int i = 0; i < (points.size() - 1); i++) {
            if (distances[i] >= s && s >= distances[i + 1]) {
                float deltaTheta = (float) tangents.get(i + 1).angle(tangents.get(i));
                float deltaS = distances[i] - distances[i + 1];

                return deltaTheta / deltaS;
            }
        }

        return 0f;
    }

    public float maxSpeedAt(float s) {
        s = MathUtils.clip(s, 0, distances[0]);

        for (int i = 0; i < (points.size() - 1); i++) {
            if (distances[i] >= s && s >= distances[i + 1]) {
                float u = (s - distances[i + 1]) / (distances[i] - distances[i + 1]);
                return MathUtils.lerp(maxSpeeds[i + 1], maxSpeeds[i], u);
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
        return (points.size() - 2f) / ndiv;
    }

    public float findNearest(Vector3 c) {
        float s = length;
        float minDistance = (float) c.sub(points.get(0)).magnitude();

        for (int i = 0; i < (points.size() - 1); i++) {
            Vector3 a = points.get(i);
            Vector3 b = points.get(i + 1);

            float alpha = (float) MathUtils.clip(b.sub(a).dot(c.sub(a)) / b.sub(a).dot(b.sub(a)), 0f, 1f);

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
        Arrays.fill(distances, 0f);
        int last = points.size() - 1;

        for (int i = last - 1; i >= 0; i--)
            distances[i] = (float) (distances[i + 1] + points.get(i + 1).sub(points.get(i)).magnitude());

        length = distances[0];
    }

    private void calculateTangents() {
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
    }


    @SuppressWarnings("UnusedReturnValue")
    public float calculateMaxSpeeds(float v0, float vf) {
        final Vector3 gravity = new Vector3(0, 0, -650);

        maxSpeeds = new float[curvatures.length];

        for (int i = 0; i < curvatures.length; i++)
            maxSpeeds[i] = DriveManeuver.maxTurningSpeed(curvatures[i] * 1.1f);

        maxSpeeds[0] = Math.min(v0, maxSpeeds[0]);
        maxSpeeds[maxSpeeds.length - 1] = Math.min(vf, maxSpeeds[maxSpeeds.length - 1]);

        for (int i = 1; i < curvatures.length; i++) {
            float ds = distances[i - 1] - distances[i];
            Vector3 t = tangents.get(i).add(tangents.get(i - 1)).normalized();
            float attainable_speed = maximizeSpeedWithThrottle((float) (0.9f * DriveManeuver.boost_acceleration + gravity.dot(t)), maxSpeeds[i - 1], ds);
            maxSpeeds[i] = Math.min(maxSpeeds[i], attainable_speed);
        }

        float time = 0.0f;

        for (int i = curvatures.length - 2; i >= 0; i--) {
            float ds = distances[i] - distances[i + 1];
            Vector3 t = tangents.get(i).add(tangents.get(i + 1)).normalized();
            float attainable_speed = maximizeSpeedWithoutThrottle(DriveManeuver.brake_acceleration - (float) gravity.dot(t), maxSpeeds[i + 1], ds);
            maxSpeeds[i] = Math.min(maxSpeeds[i], attainable_speed);
            time += ds / (0.5f * (maxSpeeds[i] + maxSpeeds[i + 1]));
        }

        return time;
    }

    private float maximizeSpeedWithThrottle(float additionalAcceleration, float v0, float distance) {
        final float dt = RLConstants.simulationTickFrequency;
        float currentDistance = 0.0f;
        float currentVelocity = v0;

        for (int i = 0; i < 50; i++) {
            float dv = (DriveManeuver.throttleAcceleration(currentVelocity) + additionalAcceleration) * dt;
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

    private float maximizeSpeedWithoutThrottle(float additionalAcceleration, float v0, float distance) {
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

    public static class ControlPoint {
        public final Vector3 point;
        public final Vector3 normal;
        public Vector3 tangent;

        public ControlPoint(Vector3 point, Vector3 tangent, Vector3 normal) {
            this.point = point;
            this.tangent = tangent;
            this.normal = normal;
        }

        public ControlPoint(Vector3 point, Vector3 tangent) {
            this.point = point;
            this.tangent = tangent;
            this.normal = new Vector3(0, 0, 1);
        }
    }

    public enum PathStatus {
        VALID,
        SPEED_EXCEEDED
    }

    public static class PathCheckStatus {
        public final PathStatus pathStatus;
        public final boolean collidedWithBall;
        public final float distanceAtBallCollision;
        public final Vector3 ballCollisionContactPoint;
        public final Vector3 ballCollisionBallPosition;

        public PathCheckStatus(PathStatus pathStatus, boolean collidedWithBall, float distanceAtBallCollision, Vector3 ballCollisionContactPoint, Vector3 ballCollisionBallPosition) {
            this.pathStatus = pathStatus;
            this.collidedWithBall = collidedWithBall;
            this.distanceAtBallCollision = distanceAtBallCollision;
            this.ballCollisionContactPoint = ballCollisionContactPoint;
            this.ballCollisionBallPosition = ballCollisionBallPosition;
        }

        public PathCheckStatus(PathStatus pathStatus) {
            this.pathStatus = pathStatus;
            this.collidedWithBall = false;
            this.distanceAtBallCollision = 0;
            this.ballCollisionContactPoint = new Vector3();
            this.ballCollisionBallPosition = new Vector3();
        }

        public boolean isValid() {
            return pathStatus == PathStatus.VALID;
        }
    }
}
