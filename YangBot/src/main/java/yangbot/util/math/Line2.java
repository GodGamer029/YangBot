package yangbot.util.math;

import yangbot.util.math.vector.Vector2;

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
}
