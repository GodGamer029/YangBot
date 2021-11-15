package yangbot.path.builders.segments;

import org.jetbrains.annotations.NotNull;
import yangbot.input.Physics2D;
import yangbot.input.RLConstants;
import yangbot.path.Curve;
import yangbot.path.builders.BakeablePathSegment;
import yangbot.path.builders.SegmentedPath;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Matrix2x2;
import yangbot.util.math.vector.Vector2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class ArcLineArc extends BakeablePathSegment {

    private final Vector2 startPos, endPos;
    private final float startOffset, endOffset;
    private final Vector2 startTangent, endTangent;
    private final Vector2 startNormal, endNormal;

    private Vector2 c1Center, c2Center;
    private Vector2 q1, q2;

    private float r1, r2;
    private float phi1, phi2;

    private final float[] L = new float[5];
    private float length;

    public ArcLineArc(Physics2D start, float startBoost, Vector2 endPos, Vector2 endTangent, float startOffset, float r1, float endOffset, float r2) {
        super(start.forwardSpeed(), startBoost, -1, -1);

        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.startPos = start.position.add(start.forward().mul(startOffset));
        this.startTangent = start.forward();
        this.startNormal = this.startTangent.cross();

        this.endPos = endPos.sub(endTangent.mul(endOffset));
        this.endTangent = endTangent;
        this.endNormal = this.endTangent.cross();

        this.calculateArcs(r1, r2);
    }

    public static Optional<ArcLineArc> findOptimalALA(Physics2D start, float startBoost, Vector2 endPos, Vector2 endTangent, float startOffset, float r1, float endOffset, float r2){
        r1 = Math.abs(r1);
        assert r1 > 0;
        r2 = Math.abs(r2);
        assert r2 > 0;
        return Stream.of(
                new ArcLineArc(start, startBoost, endPos, endTangent, startOffset, r1, endOffset, r2),
                new ArcLineArc(start, startBoost, endPos, endTangent, startOffset, r1, endOffset, -r2),
                new ArcLineArc(start, startBoost, endPos, endTangent, startOffset, -r1, endOffset, r2),
                new ArcLineArc(start, startBoost, endPos, endTangent, startOffset, -r1, endOffset, -r2)
            )
                .filter(a -> a.isValid(100))
                .filter(a -> {
                    a.bakedPath = a.bakeInternal(SegmentedPath.MAX_SAMPLES);
                    return a.bakedPath.points.stream().noneMatch(RLConstants::isOutOfBounds);
                })
                .min(Comparator.comparingDouble(a -> a.length));
    }

    private void calculateArcs(float rad1, float rad2){
        this.r1 = rad1;
        this.c1Center = startPos.add(startNormal.mul(this.r1));
        this.r2 = rad2;
        this.c2Center = this.endPos.add(endNormal.mul(this.r2));

        var deltaO = this.c2Center.sub(this.c1Center);

        // figure out if we transition from CW to CCW or vice versa
        // and compute some of the characteristic lengths for the problem
        float sign = -Math.signum(this.r1) * Math.signum(this.r2);
        float R = Math.abs(this.r1) + sign * Math.abs(this.r2);
        float o1o2 = (float) deltaO.magnitude();
        float beta = 0.97f;

        if (((R * R) / (o1o2 * o1o2)) > beta) {
            //System.out.println("Scaling back circles");
            var delta_p = this.endPos.sub(this.startPos);
            var delta_n = this.endNormal.mul(this.r2).sub(this.startNormal.mul(this.r1));

            float a = beta * delta_n.dot(delta_n) - R * R;
            float b = 2.0f * beta * delta_n.dot(delta_p);
            float c = beta * delta_p.dot(delta_p);

            float alpha = (-b - (float) Math.sqrt(b * b - 4.0f * a * c)) / (2.0f * a);

            // scale the radii by alpha, and update the relevant quantities
            this.r1 *= alpha;
            this.r2 *= alpha;
            R *= alpha;

            this.c1Center = this.startPos.add(this.startNormal.mul(this.r1));
            this.c2Center = this.endPos.add(this.endNormal.mul(this.r2));

            deltaO = this.c2Center.sub(this.c1Center);
            o1o2 = (float) deltaO.magnitude();
        }

        // set up a coordinate system along the axis
        // connecting the two circle's centers
        var e1 = deltaO.normalized();
        var e2 = e1.cross().mul(-Math.signum(r1));

        float H = (float) Math.sqrt(o1o2 * o1o2 - R * R);

        // the endpoints of the line segment connecting the circles
        this.q1 = this.c1Center.add((e1.mul(R / o1o2).add(e2.mul(H / o1o2))).mul(Math.abs(this.r1)));
        this.q2 = this.c2Center.sub((e1.mul(R / o1o2).add(e2.mul(H / o1o2))).mul(sign * Math.abs(this.r2)));

        var pq1 = this.q1.sub(this.startPos).normalized();
        this.phi1 = 2.0f * Math.signum(pq1.dot(this.startTangent)) * (float) Math.asin(Math.abs(pq1.dot(this.startNormal)));
        if (this.phi1 < 0.0f) this.phi1 += 2.0f * Math.PI;

        var pq2 = this.q2.sub(this.endPos).normalized();
        this.phi2 = -2.0f * Math.signum(pq2.dot(this.endTangent)) * (float) Math.asin(Math.abs(pq2.dot(this.endNormal)));
        if (this.phi2 < 0.0f) this.phi2 += 2.0f * Math.PI;

        L[0] = startOffset;
        L[1] = this.phi1 * Math.abs(r1);
        L[2] = (float) this.q2.distance(q1);
        L[3] = this.phi2 * Math.abs(r2);
        L[4] = endOffset;
        this.length = L[0] + L[1] + L[2] + L[3] + L[4];
    }

    public boolean isValid(float minSpeed){
        return DriveManeuver.maxTurningSpeed(1 / this.r1) > minSpeed &&
                DriveManeuver.maxTurningSpeed(1 / this.r2) > minSpeed;
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

        var m1 = startPos.sub(startTangent.mul(L[0]));
        var m2 = endPos.add(endTangent.mul(L[4]));

        ds = L[0] / segments[0];
        for (int i = 0; i < segments[0]; i++) {
            c.points.add(MathUtils.lerp(m1, startPos, (float)i / segments[0]).withZ(RLConstants.carElevation));
            c.curvatures[id] = 0.0f;
            c.distances[id] = s;
            id++;
            s -= ds;
        }

        ds = L[1] / segments[1];
        var r = startPos.sub(c1Center);
        var Q = Matrix2x2.fromRotation(Math.signum(r1) * phi1 / segments[1]);
        for (int i = 0; i < segments[1]; i++) {
            c.points.add(c1Center.add(r).withZ(RLConstants.carElevation));
            c.curvatures[id] = 1 / r1;
            c.distances[id] = s;
            id++;
            s -= ds;
            r = Q.dot(r);
        }


        ds = L[2] / segments[2];
        for (int i = 0; i < segments[2]; i++) {
            c.points.add(MathUtils.lerp(q1, q2, (float)i / segments[2]).withZ(RLConstants.carElevation));
            c.curvatures[id] = 0;
            c.distances[id] = s;
            id++;
            s -= ds;
        }


        ds = L[3] / segments[3];
        r = q2.sub(c2Center);
        Q = Matrix2x2.fromRotation(Math.signum(r2) * phi2 / segments[3]);
        for (int i = 0; i < segments[3]; i++) {
            c.points.add(c2Center.add(r).withZ(RLConstants.carElevation));
            c.curvatures[id] = 1 / r2;
            c.distances[id] = s;
            id++;
            s -= ds;
            r = Q.dot(r);
        }

        ds = L[4] / segments[4];
        for (int i = 0; i <= segments[4]; i++) {
            c.points.add(MathUtils.lerp(endPos, m2, (float)i / segments[4]).withZ(RLConstants.carElevation));
            c.curvatures[id] = 0;
            c.distances[id] = i == segments[4] ? 0 : s;
            id++;
            s -= ds;
        }

        c.calculateTangents();

        return c;
    }
}
