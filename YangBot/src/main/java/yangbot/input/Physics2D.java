package yangbot.input;

import yangbot.util.math.vector.Matrix2x2;
import yangbot.util.math.vector.Vector2;

public class Physics2D {

    public final Vector2 position, velocity;
    public final Matrix2x2 orientation;
    public final float angularVelocity;

    public Physics2D(Vector2 position, Vector2 velocity, Matrix2x2 orientation, float angularVelocity) {
        this.position = position;
        this.velocity = velocity;
        this.orientation = orientation;
        this.angularVelocity = angularVelocity;
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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Physics2D{");
        sb.append("position=").append(position);
        sb.append(", velocity=").append(velocity);
        sb.append(", orientation=").append(orientation);
        sb.append(", angularVelocity=").append(angularVelocity);
        sb.append('}');
        return sb.toString();
    }
}
