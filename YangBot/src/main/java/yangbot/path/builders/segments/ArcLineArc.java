package yangbot.path.builders.segments;

import org.jetbrains.annotations.NotNull;
import yangbot.input.Physics2D;
import yangbot.path.Curve;
import yangbot.path.builders.BakeablePathSegment;
import yangbot.util.math.vector.Vector2;

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

    // loat startSpeed, float startBoost, float endSpeed, float arrivalTime)
    public ArcLineArc(Physics2D start, float startBoost, Vector2 endPos, Vector2 endTangent, float l1, float r1, float l2, float r2) {
        super(start.forwardSpeed(), startBoost, -1, -1);

        this.p1 = start.position.add(start.forward().mul(l1));
        this.t1 = start.forward();
        this.n1 = this.t1.cross();
        this.r1 = r1;
        this.o1 = p1.add(n1.mul(r1));

        this.p2 = endPos.sub(endTangent.mul(l2));
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

        L[0] = l1;
        L[1] = phi1 * Math.abs(r1);
        L[2] = (float) q2.distance(q1);
        L[3] = phi2 * Math.abs(r2);
        L[4] = l2;
        length = L[0] + L[1] + L[2] + L[3] + L[4];
    }

    @Override
    protected @NotNull Curve bakeInternal(int maxSamples) {
        return null;
    }
}
