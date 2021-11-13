package yangbot.path.builders.segments;

import org.jetbrains.annotations.NotNull;
import yangbot.input.Physics2D;
import yangbot.input.RLConstants;
import yangbot.path.Curve;
import yangbot.path.builders.BakeablePathSegment;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Matrix2x2;
import yangbot.util.math.vector.Vector2;

import java.util.ArrayList;

public class ArcLineArc extends BakeablePathSegment {

    private Vector2 p1, p2;
    private Vector2 q1, q2;
    private Vector2 t1, t2;
    private Vector2 n1, n2;
    private Vector2 o1, o2;

    private float r1, r2;
    private float phi1, phi2;

    private float L[] = new float[5];
    private float length;

    public ArcLineArc(Physics2D start, float startBoost, Vector2 endPos, Vector2 endTangent, float startOffset, float r1, float endOffset, float r2) {
        super(start.forwardSpeed(), startBoost, -1, -1);

        this.p1 = start.position.add(start.forward().mul(startOffset));
        this.t1 = start.forward();
        this.n1 = this.t1.cross();
        this.r1 = r1;
        this.o1 = p1.add(n1.mul(r1));

        this.p2 = endPos.sub(endTangent.mul(endOffset));
        this.t2 = endTangent;
        this.n2 = this.t2.cross();
        this.r2 = r2;
        this.o2 = p2.add(n2.mul(r2));

        var deltaO = o2.sub(o1);

        // figure out if we transition from CW to CCW or vice versa
        // and compute some of the characteristic lengths for the problem
        float sign = -Math.signum(r1) * Math.signum(r2);
        float R = Math.abs(r1) + sign * Math.abs(r2);
        float o1o2 = (float) deltaO.magnitude();
        float beta = 0.97f;

        if (((R * R) / (o1o2 * o1o2)) > beta) {
            //System.out.println("Scaling back circles");
            var delta_p = p2.sub(p1);
            var delta_n = n2.mul(r2).sub(n1.mul(r1));

            float a = beta * delta_n.dot(delta_n) - R * R;
            float b = 2.0f * beta * delta_n.dot(delta_p);
            float c = beta * delta_p.dot(delta_p);

            float alpha = (-b - (float) Math.sqrt(b * b - 4.0f * a * c)) / (2.0f * a);

            // scale the radii by alpha, and update the relevant quantities
            r1 *= alpha;
            r2 *= alpha;
            R *= alpha;

            o1 = p1.add(n1.mul(r1));
            o2 = p2.add(n2.mul(r2));

            deltaO = o2.sub(o1);
            o1o2 = (float) deltaO.magnitude();
        }

        // set up a coordinate system along the axis
        // connecting the two circle's centers
        var e1 = deltaO.normalized();
        var e2 = e1.cross().mul(-Math.signum(r1));

        float H = (float) Math.sqrt(o1o2 * o1o2 - R * R);

        // the endpoints of the line segment connecting the circles
        q1 = o1.add((e1.mul(R / o1o2).add(e2.mul(H / o1o2))).mul(Math.abs(r1)));
        q2 = o2.sub((e1.mul(R / o1o2).add(e2.mul(H / o1o2))).mul(sign * Math.abs(r2)));

        var pq1 = q1.sub(p1).normalized();
        phi1 = 2.0f * Math.signum(pq1.dot(t1)) * (float) Math.asin(Math.abs(pq1.dot(n1)));
        if (phi1 < 0.0f) phi1 += 2.0f * Math.PI;

        var pq2 = q2.sub(p2).normalized();
        phi2 = -2.0f * Math.signum(pq2.dot(t2)) * (float) Math.asin(Math.abs(pq2.dot(n2)));
        if (phi2 < 0.0f) phi2 += 2.0f * Math.PI;

        L[0] = startOffset;
        L[1] = phi1 * Math.abs(r1);
        L[2] = (float) q2.distance(q1);
        L[3] = phi2 * Math.abs(r2);
        L[4] = endOffset;
        length = L[0] + L[1] + L[2] + L[3] + L[4];
    }

    public boolean isValid(){
        return DriveManeuver.maxTurningSpeed(1 / this.r1) > 100 &&
                DriveManeuver.maxTurningSpeed(1 / this.r2) > 100;
    }

    @Override
    protected @NotNull Curve bakeInternal(int maxSamples) {
        Curve c = new Curve();
        maxSamples *= 10;
        float ds = this.length / maxSamples;

        var segments = new int[5];
        int capacity = 1;

        for (int i = 0; i < 5; i++) {
            segments[i] = (int) Math.ceil(L[i] / ds);
            capacity += segments[i];
        }

        c.length = length;
        c.points = new ArrayList<>(capacity);
        c.distances = new float[capacity];
        c.curvatures = new float[capacity];

        int id = 0;
        float s = length;

        var m1 = p1.sub(t1.mul(L[0]));
        var m2 = p2.add(t2.mul(L[4]));

        ds = L[0] / segments[0];
        for (int i = 0; i < segments[0]; i++) {
            c.points.add(MathUtils.lerp(m1, p1, (float)i / segments[0]).withZ(RLConstants.carElevation));
            c.curvatures[id] = 0.0f;
            c.distances[id] = s;
            id++;
            s -= ds;
        }

        ds = L[1] / segments[1];
        var r = p1.sub(o1);
        var Q = Matrix2x2.fromRotation(Math.signum(r1) * phi1 / segments[1]);
        for (int i = 0; i < segments[1]; i++) {
            c.points.add(o1.add(r).withZ(RLConstants.carElevation));
            c.curvatures[id] = ((i == 0) ? 0.5f : 1) / r1;
            c.distances[id] = s;
            id++;
            s -= ds;
            r = Q.dot(r);
        }


        ds = L[2] / segments[2];
        for (int i = 0; i < segments[2]; i++) {
            c.points.add(MathUtils.lerp(q1, q2, (float)i / segments[2]).withZ(RLConstants.carElevation));
            c.curvatures[id] = ((i == 0) ? 0.5f / r1 : 0.0f);
            c.distances[id] = s;
            id++;
            s -= ds;
        }


        ds = L[3] / segments[3];
        r = q2.sub(o2);
        Q = Matrix2x2.fromRotation(Math.signum(r2) * phi2 / segments[3]);
        for (int i = 0; i < segments[3]; i++) {
            c.points.add(o2.add(r).withZ(RLConstants.carElevation));
            c.curvatures[id] = ((i == 0) ? 0.5f : 1) / r2;
            c.distances[id] = s;
            id++;
            s -= ds;
            r = Q.dot(r);
        }


        ds = L[4] / segments[4];
        for (int i = 0; i <= segments[4]; i++) {
            c.points.add(MathUtils.lerp(p2, m2, (float)i / segments[4]).withZ(RLConstants.carElevation));
            c.curvatures[id] = ((i == 0) ? 0.5f / r2 : 0.0f);
            c.distances[id] = i == segments[4] ? 0 : s;
            id++;
            s -= ds;
        }

        c.calculateTangents();

        return c;
    }
}
