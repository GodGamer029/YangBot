package yangbot.util.math;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.Physics2D;
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

    public Car2D(Physics2D phys, float time, float boost) {
        this.position = phys.position;
        this.velocity = phys.velocity;
        this.tangent = phys.forward();
        this.angularVelocity = phys.angularVelocity;
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

        Vector2 force = tangent.mul(CarData.driveForceForward(input, v_f, v_l, w_u))/*.add(right.mul(driveForceLeft(in, v_f, v_l, w_u)))*/;

        //Vector3 torque = up().mul(driveTorqueUp(in, v_f, w_u));

        velocity = velocity.add(force.mul(dt));
        this.velocity = velocity.mul(
                Math.min(1, CarData.MAX_VELOCITY / velocity.magnitude())
        );
        position = position.add(velocity.mul(dt));

        //angularVelocity = angularVelocity.add(torque.mul(dt));
        //orientation = Matrix3x3.axisToRotation(angularVelocity.mul(dt)).matrixMul(orientation);
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

    public float simulateDriveTimeForward(float time, boolean tryUseBoost /*will turn off in step() if no boost available*/) {
        if (time <= 0)
            return 0;

        float distance = 0;

        float dt = 1f / 30f;
        for (float t = 0; t < time /*max*/; t += dt) {
            this.step(new ControlsOutput().withThrottle(1).withBoost(tryUseBoost), dt);

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
