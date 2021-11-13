package yangbot.strategy.manuever;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.GameData;
import yangbot.input.RLConstants;
import yangbot.util.Tuple;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Vector3;

public class DriveManeuver extends Maneuver {

    public static final float min_speed = 10f;
    public static final float max_throttle_speed = 1410.0f;
    public static final float boost_acceleration = 991.667f;
    public static final float brake_acceleration = -3500.0f;
    public static final float coasting_acceleration = brake_acceleration * 0.15f;
    public float reaction_time = 0.04f;

    public Vector3 target = null;
    public float minimumSpeed = 1400f;
    public float maximumSpeed = CarData.MAX_VELOCITY;
    public boolean allowBoost = true;

    public DriveManeuver() {
    }

    public DriveManeuver(Vector3 driveTarget) {
        this.target = driveTarget;
    }

    public static Tuple<Float /*arrival time*/, Float/*end speed*/> driveArriveAt(float v0, float s, float T, boolean allowBoost) {
        assert v0 >= 0;
        v0 = MathUtils.clip(v0, 0, CarData.MAX_VELOCITY);
        float dt = RLConstants.simulationTickFrequency;
        ControlsOutput controlsOutput = new ControlsOutput();
        float t = 0;
        while (s > 0) {
            float targetSpeed = s / (T - t);
            if (t >= T)
                targetSpeed = CarData.MAX_VELOCITY;

            DriveManeuver.speedController(dt, controlsOutput, v0, targetSpeed, targetSpeed, 0.04f, allowBoost);
            v0 += CarData.driveForceForward(controlsOutput, v0, 0, 0) * dt;
            v0 = MathUtils.clip(v0, 0, CarData.MAX_VELOCITY);
            s -= v0 * dt;
            t += dt;
        }

        return new Tuple<>(t, v0);
    }

    public void setSpeed(float speed) {
        this.minimumSpeed = this.maximumSpeed = speed;
    }

    public void setSpeed(float min, float max) {
        this.minimumSpeed = min;
        this.maximumSpeed = max;
    }

    public static float maxTurningSpeed(float curvature) { // Curvature -> Max speed
        final int n = 6;

        final float[][] values = {
                {0.00088f, 2300.0f},
                {0.00110f, 1750.0f},
                {0.00138f, 1500.0f},
                {0.00235f, 1000.0f},
                {0.00398f, 500.0f},
                {0.00690f, 0.0f}
        };

        final float input = Math.abs(curvature);

        if (input <= values[0][0])
            return values[0][1];
        if (input >= values[n - 1][0])
            return values[n - 1][1];

        for (int i = 0; i < (n - 1); i++) {
            if (values[i][0] <= input && input <= values[i + 1][0]) {
                float u = (input - values[i][0]) / (values[i + 1][0] - values[i][0]);
                return MathUtils.lerp(values[i][1], values[i + 1][1], u);
            }
        }
        assert false : input;

        return -1.0f;
    }

    public static float maxTurningCurvature(float v) {
        final int n = 6;

        float[][] values = {
                {0.0f, 0.00690f},
                {500.0f, 0.00398f},
                {1000.0f, 0.00235f},
                {1500.0f, 0.00138f},
                {1750.0f, 0.00110f},
                {2300.0f, 0.00088f}
        };

        float input = Math.abs(v);
        if (input <= values[0][0])
            return values[0][1];
        if (input >= values[n - 1][0])
            return values[n - 1][1];

        for (int i = 0; i < (n - 1); i++) {
            if (values[i][0] <= input && input <= values[i + 1][0]) {
                float u = (input - values[i][0]) / (values[i + 1][0] - values[i][0]);
                return MathUtils.lerp(values[i][1], values[i + 1][1], u);
            }
        }
        assert false;
        return -1;
    }

    public static float steerAngle(float v) {
        final int n = 6;

        float[][] values = {
                {0.0f, 30.571f},
                {500.0f, 18.294f},
                {1000.0f, 10.430f},
                {1500.0f, 6.056f},
                {1750.0f, 4.874f},
                {3000.0f, 1.989f}
        };

        float input = Math.abs(v);
        if (input <= values[0][0])
            return values[0][1];
        if (input >= values[n - 1][0])
            return values[n - 1][1];

        for (int i = 0; i < (n - 1); i++) {
            if (values[i][0] <= input && input <= values[i + 1][0]) {
                float u = (input - values[i][0]) / (values[i + 1][0] - values[i][0]);
                return MathUtils.lerp(values[i][1], values[i + 1][1], u);
            }
        }
        assert false;
        return -1;
    }

    public static float throttleAcceleration(float v) {
        /*final int n = 3;
        float[][] values = {
                {0f, 1600f},
                {1400f, 160f},
                {1410f, 0f}
        };*/

        float input = Math.max(0, Math.abs(v));
        if(input >= 1410f)
            return 0;
        if(input >= 1400)
            return MathUtils.remap(input, 1400, 1410, 160, 0);
        return MathUtils.remap(input, 0, 1400, 1600, 160);

        /*for (int i = 0; i < (n - 1); i++) {
            if (values[i][0] <= input && input <= values[i + 1][0]) {
                float u = (input - values[i][0]) / (values[i + 1][0] - values[i][0]);
                return MathUtils.lerp(values[i][1], values[i + 1][1], u);
            }
        }*/

    }

    public static void speedController(float dt, ControlsOutput output, float currentSpeed, float minimumSpeed, float maximumSpeed, float reactionTime, boolean allowBoost){
        DriveManeuver.speedController(dt, output, currentSpeed, minimumSpeed, maximumSpeed, reactionTime, allowBoost, 0);
    }
    public static void speedController(float dt, ControlsOutput output, float currentSpeed, float minimumSpeed, float maximumSpeed, float reactionTime, boolean allowBoost, float additionalAccel) {
        minimumSpeed = Math.min(minimumSpeed, CarData.MAX_VELOCITY);
        maximumSpeed = Math.min(maximumSpeed, CarData.MAX_VELOCITY);
        assert minimumSpeed <= maximumSpeed : "minimumSpeed (" + minimumSpeed + ") should always be equal or lower the maximumSpeed(" + maximumSpeed + ")";

        float minimumAcceleration = (minimumSpeed - currentSpeed) / reactionTime - additionalAccel;
        float maximumAcceleration = (maximumSpeed - currentSpeed) / reactionTime - additionalAccel;

        final float throttleAccel = throttleAcceleration(currentSpeed);

        final float brake_coast_transition = (MathUtils.lerp(brake_acceleration, coasting_acceleration, 0.7f));
        final float coasting_throttle_transition = 0.5f * coasting_acceleration;
        final float throttle_boost_transition = throttleAccel + 0.45f * boost_acceleration;

        //if (car.up().z < 0.7f) {
        //    brake_coast_transition = coasting_throttle_transition = -0.5f * brake_acceleration;
        //}

        if (maximumAcceleration <= brake_coast_transition) { // maximumAcceleration is negative and bigger than coasting would get us, hold brakes
            output.withThrottle(-1.0f);
            output.withBoost(false);
        } else if (maximumAcceleration < coasting_throttle_transition) { // needed acceleration is negative and good for coasting
            output.withThrottle(0);
            output.withBoost(false);
        } else if (minimumAcceleration <= throttle_boost_transition) { // acceleration does not need boost accel
            output.withThrottle(MathUtils.remapClip(minimumAcceleration / throttleAccel, 0, 1, 0.015f, 1));
            output.withBoost(false);
        } else {
            output.withThrottle(1.0f);
            output.withBoost(allowBoost);
        }
    }

    public static void steerController(ControlsOutput output, CarData car, Vector3 targetPos, float multiplier) {
        Vector3 target_local = targetPos.sub(car.position).dot(car.orientation);

        float angle = (float) Math.atan2(target_local.y, target_local.x);
        float angular = car.angularVelocity.dot(car.up());
        float dd = -0.01f;
        float d = -0.06f;
        float p = 5f;
        output.withSteer(MathUtils.clip(
                dd * angular * angular * Math.signum(angular) +
                d * (float)Math.sqrt(Math.abs(angular)) * Math.signum(angular) +
                        multiplier * p * angle * Math.signum(car.velocity.dot(car.forward())), -1f, 1f));
    }

    public static void steerController(ControlsOutput output, CarData car, Vector3 targetPos) {
        steerController(output, car, targetPos, 1);
    }

    @Override
    public void step(float dt, ControlsOutput controlsOutput) {
        final GameData gameData = this.getGameData();
        final CarData car = gameData.getCarData();

        steerController(controlsOutput, car, this.target);
        float steerSlowdown = car.slowdownForceFromSteering(controlsOutput.getSteer()); // include steering slowdown so that we don't brake too much
        //assert Math.signum(steerSlowdown) != Math.signum(car.forwardSpeed());
        speedController(dt, controlsOutput, car.forwardSpeed(), this.minimumSpeed, this.maximumSpeed, reaction_time, this.allowBoost, steerSlowdown);

        if (car.position.sub(target).magnitude() < 100.f)
            this.setIsDone(true);
    }

    @Override
    public CarData simulate(CarData car) {
        return null;
    }
}
