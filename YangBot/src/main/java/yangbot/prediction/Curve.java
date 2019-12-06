package yangbot.prediction;

import yangbot.manuever.DriveManeuver;
import yangbot.util.CubicHermite;
import yangbot.util.MathUtils;
import yangbot.util.OGH;
import yangbot.vector.Matrix3x3;
import yangbot.vector.Vector3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Curve {

    public float length;
    public final ArrayList<Vector3> points;
    public float[] maxSpeeds;
    private ArrayList<Vector3> tangents;
    private ArrayList<Float> curvatures;
    private float[] distances;

    public Curve() {
        length = -1f;
        points = new ArrayList<>();
        tangents = new ArrayList<>();
        curvatures = new ArrayList<>();
        distances = new float[0];
        maxSpeeds = new float[0];
    }

    public Curve(List<ControlPoint> info) {
        points = new ArrayList<>();
        tangents = new ArrayList<>();
        curvatures = new ArrayList<>();
        maxSpeeds = new float[0];

        int ndiv = 16;
        int num_segments = info.size() - 1;

        points.ensureCapacity(ndiv * num_segments + 2);
        tangents.ensureCapacity(ndiv * num_segments + 2);
        curvatures.ensureCapacity(ndiv * num_segments + 2);

        for (int i = 1; i < num_segments - 1; i++) {
            Vector3 delta_before = info.get(i).p.sub(info.get(i - 1).p).normalized();
            Vector3 delta_after = info.get(i + 1).p.sub(info.get(i).p).normalized();

            float phi_before = (float) Math.asin(info.get(i).t.crossProduct(delta_before).dot(info.get(i).n));
            float phi_after = (float) Math.asin(info.get(i).t.crossProduct(delta_after).dot(info.get(i).n));

            if (phi_before * phi_after > 0f) {
                float phi;

                if (Math.abs(phi_before) < Math.abs(phi_after))
                    phi = phi_before;
                else
                    phi = phi_after;

                ControlPoint p = info.get(i);
                p.t = Matrix3x3.axisToRotation(p.n.mul(phi)).dot(p.t);
                info.set(i, p);
            }
        }

        for (int i = 0; i < num_segments; i++) {
            ControlPoint our = info.get(i);
            ControlPoint next = info.get(i + 1);

            Vector3 P0 = our.p;
            Vector3 P1 = next.p;

            Vector3 V0 = our.t;
            Vector3 V1 = next.t;

            Vector3 N0 = our.n;
            Vector3 N1 = next.n;

            OGH piece = new OGH(P0, V0, P1, V1);

            int is_last = (i == num_segments - 1) ? 1 : 0;

            for (int j = 0; j < (ndiv + is_last); j++) {
                float t = ((float) j) / ((float) ndiv);

                Vector3 g = piece.evaluate(t);
                Vector3 dg = piece.tangent(t);
                Vector3 d2g = piece.acceleration(t);

                float dgMag = (float) dg.magnitude();

                float kappa = (float) (dg
                        .crossProduct(d2g)
                        .dot(
                                N0
                                        .mul(1f - t)
                                        .add(N1.mul(t))
                                        .normalized()
                        ) / (dgMag * dgMag * dgMag));

                points.add(g);
                tangents.add(dg.normalized());
                curvatures.add(kappa);
            }
        }

        calculateDistances();
    }

    public Curve(List<ControlPoint> info, Vector3 dx0, Vector3 dt0, Vector3 dx1, Vector3 dt1, Vector3 start, Vector3 end) {
        int ndiv = 16;
        int num_segments = info.size() - 1;

        points = new ArrayList<>();
        tangents = new ArrayList<>();
        curvatures = new ArrayList<>();
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

            Vector3 P0 = our.p;
            Vector3 P1 = next.p;

            Vector3 V0 = our.t;
            Vector3 V1 = next.t;

            Vector3 N0 = our.n;
            Vector3 N1 = next.n;

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

        curvatures = new ArrayList<>(points.size());
        while (curvatures.size() < points.size()) {
            curvatures.add(0f);
        }

        int last = curvatures.size() - 1;

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
            curvatures.set(i, MathUtils.lerp(kappa1, kappa2, inPlaneWeight));
        }

        m = tangents.get(1).crossProduct(tangents.get(0));
        n = normals.get(0);
        ds = distances[0] - distances[1];
        kappa1 = (float) MathUtils.clip(Math.asin(m.magnitude()) / ds, 0f, kappa_max);
        kappa2 = (float) Math.asin(Math.abs(m.dot(n))) / ds;
        curvatures.set(0, MathUtils.lerp(kappa1, kappa2, inPlaneWeight));

        m = tangents.get(last).crossProduct(tangents.get(last - 1));
        n = normals.get(last);
        ds = distances[last - 1] - distances[last];
        kappa1 = (float) MathUtils.clip(Math.asin(m.magnitude()) / ds, 0f, kappa_max);
        kappa2 = (float) Math.asin(Math.abs(m.dot(n))) / ds;
        curvatures.set(last, MathUtils.lerp(kappa1, kappa2, inPlaneWeight));
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

        maxSpeeds = new float[curvatures.size()];

        for (int i = 0; i < curvatures.size(); i++)
            maxSpeeds[i] = DriveManeuver.maxTurningSpeed(curvatures.get(i) * 1.1f);

        maxSpeeds[0] = Math.min(v0, maxSpeeds[0]);
        maxSpeeds[maxSpeeds.length - 1] = Math.min(vf, maxSpeeds[maxSpeeds.length - 1]);

        for (int i = 1; i < curvatures.size(); i++) {
            float ds = distances[i - 1] - distances[i];
            Vector3 t = tangents.get(i).add(tangents.get(i - 1)).normalized();
            float attainable_speed = maximize_speed_with_throttle((float) (0.9f * DriveManeuver.boost_acceleration + gravity.dot(t)), maxSpeeds[i - 1], ds);
            maxSpeeds[i] = Math.min(maxSpeeds[i], attainable_speed);
        }

        float time = 0.0f;

        for (int i = curvatures.size() - 2; i >= 0; i--) {
            float ds = distances[i] - distances[i + 1];
            Vector3 t = tangents.get(i).add(tangents.get(i + 1)).normalized();
            float attainable_speed = maximize_speed_without_throttle(DriveManeuver.brake_acceleration - (float) gravity.dot(t), maxSpeeds[i + 1], ds);
            maxSpeeds[i] = Math.min(maxSpeeds[i], attainable_speed);
            time += ds / (0.5f * (maxSpeeds[i] + maxSpeeds[i + 1]));
        }

        return time;
    }

    private float maximize_speed_with_throttle(float acceleration, float v0, float sf) {
        float dt = 0.008333f;
        float s = 0.0f;
        float v = v0;
        for (int i = 0; i < 100; i++) {
            float dv = (DriveManeuver.throttle_acceleration(v) + acceleration) * dt;
            float ds = (v + 0.5f * dv) * dt;
            v += dv;
            s += ds;
            if (s > sf) {
                v -= (s - sf) * (dv / ds);
                break;
            }
        }
        return v;
    }

    private float maximize_speed_without_throttle(float acceleration, float v0, float sf) {
        float dt = 0.008333f;
        float s = 0.0f;
        float v = v0;
        float dv = acceleration * dt;
        for (int i = 0; i < 100; i++) {
            float ds = (v + 0.5f * dv) * dt;
            v += dv;
            s += ds;
            if (s > sf) {
                v -= (s - sf) * (dv / ds);
                break;
            }
        }
        return v;
    }

    public static class ControlPoint {
        public final Vector3 p;
        public Vector3 t;
        public final Vector3 n;

        public ControlPoint(Vector3 p, Vector3 t, Vector3 n) {
            this.p = p;
            this.t = t;
            this.n = n;
        }
    }
}
