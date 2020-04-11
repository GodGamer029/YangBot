package yangbot.util.math;

import yangbot.util.math.vector.Vector2;

public class Triangle {

    public final Vector2 a, b, c;

    public Triangle(Vector2 a, Vector2 b, Vector2 c) {
        this.a = a;
        this.b = b;
        this.c = c;
    }

    public float getSignedArea() {
        throw new IllegalStateException("not implemented");
    }

    public boolean isPointInside(Vector2 point) {
        throw new IllegalStateException("not implemented");
    }
}
