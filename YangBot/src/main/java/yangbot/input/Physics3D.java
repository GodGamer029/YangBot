package yangbot.input;

import rlbot.gamestate.PhysicsState;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector3;

public class Physics3D {

    public final Vector3 position, velocity, angularVelocity;
    public final Matrix3x3 orientation;

    public Physics3D(Vector3 position, Vector3 velocity, Matrix3x3 orientation, Vector3 angularVelocity) {
        this.position = position;
        this.velocity = velocity;
        this.orientation = orientation;
        this.angularVelocity = angularVelocity;
    }

    public Physics2D flatten() {
        return new Physics2D(this.position.flatten(), this.velocity.flatten(), this.orientation.flatten(), this.angularVelocity.dot(this.orientation.up()));
    }

    public float forwardSpeed() {
        return (float) this.forward().dot(this.velocity);
    }

    public Vector3 forward() {
        return this.orientation.forward();
    }

    public Vector3 right() {
        return this.orientation.right();
    }

    public PhysicsState toPhysicsState() {
        return new PhysicsState().withLocation(this.position.toDesiredVector()).withVelocity(this.velocity.toDesiredVector()).withAngularVelocity(this.angularVelocity.toDesiredVector()).withRotation(this.orientation.toEuler().toDesiredRotation());
    }
}
