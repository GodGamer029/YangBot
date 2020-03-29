package yangbot.strategy.manuever;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.GameData;
import yangbot.input.RLConstants;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Vector3;

public class DriveManeuver extends Maneuver {

    public static final float min_speed = 10f;
    public static final float max_throttle_speed = 1410.0f;
    public static final float boost_acceleration = 991.667f;
    public static final float brake_acceleration = 3500.0f;
    public static final float coasting_acceleration = 525.0f;
    public float reaction_time = 0.04f;

    public Vector3 target = null;
    public float minimumSpeed = 1400f;
    public float maximumSpeed = CarData.MAX_VELOCITY;
    public boolean allowBoostForLowSpeeds = true;

    public static float maxDistance(float currentSpeed, float time) {
        final float drivingSpeed = RLConstants.tickRate <= 60 ? 1238.3954f : 1235.0f;

        if (time <= 0)
            return 0;

        float newSpeed = currentSpeed;
        float dist = 0;
        final float dt = 1f / 60f;

        for (float t = 0; t < time; t += dt) {
            float force = 0;
            {
                if (newSpeed < drivingSpeed)
                    force = CarData.MAX_VELOCITY - newSpeed;
                else if (newSpeed < CarData.MAX_VELOCITY)
                    force = AerialManeuver.boost_acceleration;
            }
            newSpeed += force * dt;
            dist += newSpeed * dt;
        }
        return dist;
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

        final float input = MathUtils.clip(Math.abs(curvature), values[0][0], values[n - 1][0]);

        for (int i = 0; i < (n - 1); i++) {
            if (values[i][0] <= input && input <= values[i + 1][0]) {
                float u = (input - values[i][0]) / (values[i + 1][0] - values[i][0]);
                return MathUtils.lerp(values[i][1], values[i + 1][1], u);
            }
        }

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

        float input = MathUtils.clip(Math.abs(v), 0f, 2300f);

        for (int i = 0; i < (n - 1); i++) {
            if (values[i][0] <= input && input <= values[i + 1][0]) {
                float u = (input - values[i][0]) / (values[i + 1][0] - values[i][0]);
                return MathUtils.lerp(values[i][1], values[i + 1][1], u);
            }
        }

        return -1.0f;
    }

    public static float throttleAcceleration(float v) {
        final int n = 3;
        float[][] values = {
                {0f, 1600f},
                {1400f, 160f},
                {1410f, 0f}
        };

        float input = Math.max(0, Math.min(1410, Math.abs(v)));

        for (int i = 0; i < (n - 1); i++) {
            if (values[i][0] <= input && input < values[i + 1][0]) {
                float u = (input - values[i][0]) / (values[i + 1][0] - values[i][0]);
                return MathUtils.lerp(values[i][1], values[i + 1][1], u);
            }
        }

        return -1.0f;
    }

    public static void speedController(float dt, ControlsOutput output, float currentSpeed, float minimumSpeed, float maximumSpeed, float reactionTime) {
        minimumSpeed = Math.min(minimumSpeed, CarData.MAX_VELOCITY);
        maximumSpeed = Math.min(maximumSpeed, CarData.MAX_VELOCITY);
        assert minimumSpeed <= maximumSpeed : "minimumSpeed (" + minimumSpeed + ") should always be equal or lower the maximumSpeed(" + maximumSpeed + ")";

        float minimumAcceleration = (minimumSpeed - currentSpeed) / reactionTime;
        float maximumAcceleration = (maximumSpeed - currentSpeed) / reactionTime;

        float brake_coast_transition = -(0.45f * brake_acceleration + 0.55f * coasting_acceleration);
        float coasting_throttle_transition = -0.5f * coasting_acceleration;
        float throttle_boost_transition = 1.0f * throttleAcceleration(currentSpeed) + 0.7f * boost_acceleration;

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
            output.withThrottle(Math.max(0.001f, minimumAcceleration / throttleAcceleration(currentSpeed)));
            output.withBoost(false);
        } else {
            output.withThrottle(1.0f);
            output.withBoost(true);
        }
    }

    public static void steerController(ControlsOutput output, CarData car, Vector3 targetPos) {
        Vector3 target_local = targetPos.sub(car.position).dot(car.orientation);

        float angle = (float) Math.atan2(target_local.y, target_local.x);
        output.withSteer(MathUtils.clip(3.0f * angle * (float) Math.signum(car.velocity.dot(car.forward())), -1f, 1f));
    }

    @Override
    public void step(float dt, ControlsOutput controlsOutput) {
        final GameData gameData = this.getGameData();
        final CarData car = gameData.getCarData();

        steerController(controlsOutput, car, this.target);
        speedController(dt, controlsOutput, (float) car.velocity.dot(car.forward()), this.minimumSpeed, this.maximumSpeed, reaction_time);

        if (!allowBoostForLowSpeeds && this.minimumSpeed < DriveManeuver.max_throttle_speed - 20)
            controlsOutput.withBoost(false);

        if (car.position.sub(target).magnitude() < 100.f)
            this.setIsDone(true);
    }

    @Override
    public CarData simulate(CarData car) {
        return null;
    }
}
