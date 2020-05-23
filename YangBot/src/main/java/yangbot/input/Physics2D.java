package yangbot.input;

import yangbot.util.math.vector.Matrix2x2;
import yangbot.util.math.vector.Vector2;

public class Physics2D {

    public final Vector2 position, velocity;
    public final Matrix2x2 orientation;

    public Physics2D(Vector2 position, Vector2 velocity, Matrix2x2 orientation) {
        this.position = position;
        this.velocity = velocity;
        this.orientation = orientation;
    }

    public float forwardSpeed() {
        return this.forward().dot(this.velocity);
    }

    public Vector2 forward() {
        return this.orientation.forward();
    }

    public Vector2 right() {
        return this.orientation.right();
    }
}
