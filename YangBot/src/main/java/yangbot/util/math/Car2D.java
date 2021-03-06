package yangbot.util.math;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.RLConstants;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.util.math.vector.Vector2;

public class Car2D {
    public Vector2 position, velocity, tangent;
    public float time, angularVelocity, boost;

    public Car2D(Vector2 position, Vector2 velocity, Vector2 tangent, float angularVelocity, float time, float boost) {
        this.position = position;
        this.velocity = velocity;
        this.tangent = tangent;
        this.angularVelocity = angularVelocity;
        this.time = time;
        this.boost = boost;
    }

    public void step(ControlsOutput input, float dt) {
        this.time += dt;

        var right = this.tangent.cross();
        final float v_f = velocity.dot(this.tangent);
        final float v_l = velocity.dot(right);
        final float w_u = angularVelocity;

        if (input.holdBoost()) {
            this.boost -= CarData.BOOST_CONSUMPTION * dt;
            if (this.boost < 0) {
                this.boost = 0;
                input.withBoost(false);
            }
        }

        Vector2 force = tangent.mul(driveForceForward(input, v_f, v_l, w_u))/*.add(right.mul(driveForceLeft(in, v_f, v_l, w_u)))*/;

        //Vector3 torque = up().mul(driveTorqueUp(in, v_f, w_u));

        velocity = velocity.add(force.mul(dt));
        this.velocity = velocity.mul(
                Math.min(1, CarData.MAX_VELOCITY / velocity.magnitude())
        );
        position = position.add(velocity.mul(dt));

        //angularVelocity = angularVelocity.add(torque.mul(dt));
        //orientation = Matrix3x3.axisToRotation(angularVelocity.mul(dt)).matrixMul(orientation);
    }

    private float driveForceForward(ControlsOutput in, float velocityForward, float velocityLeft, float angularUp) {
        final float driving_speed = DriveManeuver.max_throttle_speed;
        final float braking_force = -3500.0f;
        final float coasting_force = -525.0f;
        final float throttle_threshold = 0.05f;
        final float max_speed = 2275.0f;
        final float min_speed = 3.0f;
        final float braking_threshold = -0.001f;
        final float supersonic_turn_drag = -98.25f;

        final float turn_damping = (-0.07186693033945346f * Math.abs(in.getSteer()) + -0.05545323728191764f * Math.abs(angularUp) + 0.00062552963716722f * Math.abs((float) velocityLeft)) * (float) velocityForward;

        if (in.holdBoost()) {
            if (velocityForward < 0.0f)
                return -braking_force;
            else {
                if (velocityForward < DriveManeuver.max_throttle_speed)
                    return DriveManeuver.boost_acceleration + DriveManeuver.throttleAcceleration(velocityForward) + turn_damping;
                else {
                    if (velocityForward < max_speed)
                        return DriveManeuver.boost_acceleration + turn_damping;
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
                        return in.getThrottle() * DriveManeuver.throttleAcceleration(velocityForward) + turn_damping;
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
        float dt = RLConstants.simulationTickFrequency;
        this.step(new ControlsOutput(), RLConstants.tickFrequency); // We can only influence the next tick, because of latency
        for (float t = 0; t < 3 /*max*/; t += dt) {
            this.step(new ControlsOutput().withThrottle(-direction), dt);

            speed = this.velocity.dot(this.tangent);
            if (Math.abs(speed) < 3)
                break;
            float newDirection = Math.signum(speed);
            if (direction != newDirection)
                break;
        }
    }

    // Copy of the function above, but using the speed controller
    public void simulateGentleFullStop() {
        assert angularVelocity == 0 : "this could get wonky";
        float speed = this.velocity.dot(this.tangent);
        if (Math.abs(speed) < 5)
            return;
        float direction = Math.signum(speed);
        float dt = RLConstants.simulationTickFrequency;
        this.step(new ControlsOutput(), RLConstants.tickFrequency); // We can only influence the next tick, because of latency
        for (float t = 0; t < 3 /*max*/; t += dt) {
            var out = new ControlsOutput();
            DriveManeuver.speedController(dt, out, speed, 0, 0, 0.03f, false);
            this.step(out, dt);

            speed = this.velocity.dot(this.tangent);
            if (Math.abs(speed) < 10)
                break;
            float newDirection = Math.signum(speed);
            if (direction != newDirection)
                break;
        }
    }

    public float simulateDriveTimeForward(float time, boolean useBoost) {
        if (time <= 0)
            return 0;

        float distance = 0;

        float dt = 1f / 30f;
        for (float t = 0; t < time /*max*/; t += dt) {
            this.step(new ControlsOutput().withThrottle(1).withBoost(useBoost), dt);

            distance += this.velocity.mul(dt).magnitude();
        }
        return distance;
    }

    public boolean simulateDriveDistanceForward(float distance, boolean useBoost) {
        return this.simulateDriveDistanceForward(distance, useBoost, 7);
    }

    public boolean simulateDriveDistanceForward(float distance, boolean useBoost, float maxTime) {
        //assert angularVelocity == 0 : "this could get wonky";

        if (distance <= 0)
            return true;

        float dt = 1f / 30f;
        if (distance <= 25)
            dt = RLConstants.simulationTickFrequency;
        for (float t = 0; t < maxTime /*max*/; t += dt) {
            this.step(new ControlsOutput().withThrottle(1).withBoost(useBoost), dt);

            distance -= this.velocity.mul(dt).magnitude();
            if (distance <= 0)
                return true;
            if (distance <= 25)
                dt = RLConstants.simulationTickFrequency;
        }
        return distance <= 0;
    }
}
