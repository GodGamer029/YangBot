package yangbot.util.math;

import yangbot.input.ControlsOutput;
import yangbot.strategy.manuever.AerialManeuver;
import yangbot.util.math.vector.Vector2;

public class Car2D {
    public Vector2 position, velocity, tangent;
    public float time, angularVelocity;

    public Car2D(Vector2 position, Vector2 velocity, Vector2 tangent, float angularVelocity, float time) {
        this.position = position;
        this.velocity = velocity;
        this.tangent = tangent;
        this.angularVelocity = angularVelocity;
        this.time = time;
    }

    public void step(ControlsOutput input, float dt) {
        this.time += dt;

        var right = this.tangent.cross();
        final float v_f = velocity.dot(this.tangent);
        final float v_l = velocity.dot(right);
        final float w_u = angularVelocity;

        Vector2 force = tangent.mul(driveForceForward(input, v_f, v_l, w_u))/*.add(right.mul(driveForceLeft(in, v_f, v_l, w_u)))*/;

        //Vector3 torque = up().mul(driveTorqueUp(in, v_f, w_u));

        velocity = velocity.add(force.mul(dt));
        position = position.add(velocity.mul(dt));

        //angularVelocity = angularVelocity.add(torque.mul(dt));
        //orientation = Matrix3x3.axisToRotation(angularVelocity.mul(dt)).matrixMul(orientation);
    }

    private float driveForceForward(ControlsOutput in, float velocityForward, float velocityLeft, float angularUp) {
        final float driving_speed = 1450.0f;
        final float braking_force = -3500.0f;
        final float coasting_force = -525.0f;
        final float throttle_threshold = 0.05f;
        final float throttle_force = 1550.0f;
        final float max_speed = 2275.0f;
        final float min_speed = 10.0f;
        final float braking_threshold = -0.001f;
        final float supersonic_turn_drag = -98.25f;

        final float turn_damping = (-0.07186693033945346f * Math.abs(in.getSteer()) + -0.05545323728191764f * Math.abs(angularUp) + 0.00062552963716722f * Math.abs((float) velocityLeft)) * (float) velocityForward;

        if (in.holdBoost()) {
            if (velocityForward < 0.0f)
                return -braking_force;
            else {
                if (velocityForward < driving_speed)
                    return max_speed - velocityForward;
                else {
                    if (velocityForward < max_speed)
                        return AerialManeuver.boost_acceleration + turn_damping;
                    else
                        return supersonic_turn_drag * Math.abs(angularUp);
                }
            }
        } else { // Not boosting
            if ((in.getThrottle() * Math.signum(velocityForward) <= braking_threshold) &&
                    Math.abs(velocityForward) > min_speed) {
                return braking_force * Math.signum(velocityForward);
                // not braking
            } else {
                // coasting
                if (Math.abs(in.getThrottle()) < throttle_threshold && Math.abs(velocityForward) > min_speed) {
                    return coasting_force * Math.signum(velocityForward) + turn_damping;
                    // accelerating
                } else {
                    if (Math.abs(velocityForward) > driving_speed) {
                        return turn_damping;
                    } else {
                        return in.getThrottle() * (throttle_force - Math.abs(velocityForward)) + turn_damping;
                    }
                }
            }
        }
    }

    // Brake until the car gets to a full stop, useful for timing and positional stuff
    public void simulateFullStop() {
        assert angularVelocity == 0 : "this could get wonky";
        float speed = this.velocity.dot(this.tangent);
        if (Math.abs(speed) < 10)
            return;
        float direction = Math.signum(speed);
        for (float t = 0; t < 3 /*max*/; t += 0.05f) {
            this.step(new ControlsOutput().withThrottle(-direction), 0.05f);

            speed = this.velocity.dot(this.tangent);
            if (Math.abs(speed) < 10)
                break;
            float newDirection = Math.signum(speed);
            if (direction != newDirection)
                break;
        }
    }

    public void simulateDriveDistanceForward(float distance, boolean useBoost) {
        assert !useBoost : "not implemented";

        assert angularVelocity == 0 : "this could get wonky";

        float dt = 0.05f;
        for (float t = 0; t < 7 /*max*/; t += dt) {
            this.step(new ControlsOutput().withThrottle(1), dt);

            distance -= this.velocity.mul(dt).magnitude();
            if (distance <= 0)
                return;
        }
    }
}
