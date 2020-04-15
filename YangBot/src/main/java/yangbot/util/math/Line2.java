package yangbot.util.math;

import yangbot.util.math.vector.Vector2;

import java.util.Optional;

public class Line2 {

    public final Vector2 a, b;

    public Line2(Vector2 a, Vector2 b) {
        this.a = a;
        this.b = b;
    }

    public Vector2 closestPointOnLine(Vector2 point) {
        final Vector2 ap = point.sub(a);
        final Vector2 ab = b.sub(a);

        final float mag = (float) ab.magnitudeSquared();
        final float dot = ap.dot(ab);
        final float dist = dot / mag;

        if (dist < 0)
            return a;
        if (dist > 1)
            return b;

        return a.add(ab.mul(dist));
    }

    public Optional<Vector2> getIntersectionPointWithInfOtherLine(Line2 o) {
        // Determine if Lines intersect
        float den = (a.x - b.x) * (o.a.y - o.b.y) - (a.y - b.y) * (o.a.x - o.b.x);
        if (den == 0) // Lines are parallel
            return Optional.empty();

        float t = ((a.x - o.a.x) * (o.a.y - o.b.y) - (a.y - o.a.y) * (o.a.x - o.b.x)) / den;

        if (t > 0 && t < 1) { // Intersection point falls within our line segment
            return Optional.of(new Vector2(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y)));
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return "Line2(" + "a=" + a + ", b=" + b + ')';
    }
}
