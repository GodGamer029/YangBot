package yangbot.util.math;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.strategy.manuever.DriveManeuver;

public class Car1D {

    public final float distanceTraveled, speed, boost, time;

    public Car1D(float distanceTraveled, float speed, float boost, float time) {
        this.distanceTraveled = distanceTraveled;
        this.speed = speed;
        this.boost = boost;
        this.time = time;
    }

    // Returns: time | speed
    public static Car1D simulateDriveDistanceForwardAccel(float distance, float startSpeed, float boost) {
        assert boost <= 100;

        final float totalDist = distance;
        if (distance <= 0)
            return new Car1D(0, startSpeed, boost, 0);
        if (startSpeed >= CarData.MAX_VELOCITY - 1 || (startSpeed >= DriveManeuver.max_throttle_speed - 1 && boost <= 0))
            return new Car1D(totalDist, CarData.MAX_VELOCITY, boost, totalDist / startSpeed);

        float maxTime = 7;

        float dt = 1f / 60;
        for (float t = 0; t < maxTime /*max*/; t += dt) {
            if (startSpeed >= CarData.MAX_VELOCITY - 1 || (startSpeed >= DriveManeuver.max_throttle_speed - 1 && boost <= 0)) {
                if (boost > 0)
                    startSpeed = CarData.MAX_VELOCITY;

                dt = maxTime;
            } else {
                float force = CarData.driveForceForward(new ControlsOutput().withThrottle(1).withBoost(boost > 0), startSpeed, 0, 0);
                if (boost > 0)
                    boost -= CarData.BOOST_CONSUMPTION * dt;

                startSpeed += force * dt;
                startSpeed = Math.min(startSpeed, CarData.MAX_VELOCITY);
            }

            distance -= startSpeed * dt;
            if (distance <= 0) {
                if (distance < -0.001f) {
                    float oldDist = distance + startSpeed * dt;
                    float dPart = oldDist / startSpeed;
                    assert dPart >= 0 && dPart <= dt : dPart + " " + dt + " " + distance + " " + oldDist;
                    float dThrowaway = dt - dPart;
                    t -= dThrowaway;
                    distance = 0;
                }

                return new Car1D(totalDist - distance, startSpeed, boost, t + dt);
            }
        }
        throw new RuntimeException("Max time exceeded");
    }

    // Returns: time | speed
    public static Car1D simulateDriveDistanceSpeedController(float distance, float startSpeed, float targetSpeed, float boost, float steerInput) {
        final float totalDistance = distance;
        if (distance <= 0)
            return new Car1D(0, startSpeed, boost, 0);
        float maxTime = 7;

        assert targetSpeed >= 0 && targetSpeed <= CarData.MAX_VELOCITY;

        float dt = 1f / 60;
        for (float t = 0; t < maxTime /*max*/; t += dt) {
            if (Math.abs(startSpeed - targetSpeed) > 10) {
                var out = new ControlsOutput();
                DriveManeuver.speedController(dt, out, startSpeed, targetSpeed, targetSpeed, 0.03f, true);
                if (boost <= 0)
                    out.withBoost(false);
                out.withSteer(steerInput);
                float force = CarData.driveForceForward(out, startSpeed, 0, 0);
                if (out.holdBoost())
                    boost -= CarData.BOOST_CONSUMPTION * dt;

                startSpeed += force * dt;
                startSpeed = Math.min(startSpeed, CarData.MAX_VELOCITY);
            } else {
                // No need to waste cpu power on a speed controller that maintains speed -.-
                startSpeed = targetSpeed;
                dt = 1 / 10f;
            }

            distance -= startSpeed * dt;
            if (distance <= 0) {
                if (distance < -0.001) {
                    float oldDist = distance + startSpeed * dt;
                    float dPart = oldDist / startSpeed;
                    assert dPart >= 0 && dPart <= dt : dPart + " " + dt;
                    float dThrowaway = dt - dPart;
                    t -= dThrowaway;
                    distance = 0;
                }

                return new Car1D(totalDistance - distance, startSpeed, boost, t + dt);
            }
        }
        throw new RuntimeException("Max time exceeded");
    }

    // Returns: distance | time
    public static Car1D simulateDriveDistanceForSlowdown(float curSpeed, float maxSpeed) {
        if (curSpeed <= maxSpeed)
            return new Car1D(0f, curSpeed, 0, 0);

        assert curSpeed > 100 && maxSpeed > 100 : curSpeed + " " + maxSpeed;

        float maxTime = 1.5f;

        float dist = 0;
        float dt = 1 / 60f;
        for (float t = 0; t < maxTime /*max*/; t += dt) {
            float force = CarData.driveForceForward(new ControlsOutput().withThrottle(-1), curSpeed, 0, 0);
            assert force <= 0;

            dist += curSpeed * dt;
            curSpeed += force * dt;
            if (curSpeed <= maxSpeed) {
                if (curSpeed < maxSpeed) {
                    float oldSpeed = curSpeed - force * dt;
                    float dPart = (maxSpeed - oldSpeed) / force;
                    assert dPart >= 0 && dPart <= dt : dPart + " " + dt;
                    float dThrowaway = dt - dPart;
                    t -= dThrowaway;
                    dist -= oldSpeed * dThrowaway;
                }
                return new Car1D(dist, curSpeed, 0, t + dt);
            }

        }
        assert false;
        return new Car1D(dist, curSpeed, 0, maxTime);
    }

}
