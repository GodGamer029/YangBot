package yangbot.manuever;

import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.input.RLConstants;
import yangbot.prediction.Curve;
import yangbot.util.ControlsOutput;
import yangbot.util.MathUtils;

public class FollowPathManeuver extends Maneuver {

    public Curve path;
    public float arrivalTime = -1;
    public float arrivalSpeed = -1;
    float expected_error;
    float expected_speed;
    public DriveManeuver driveManeuver;

    public FollowPathManeuver() {
        path = new Curve();
        driveManeuver = new DriveManeuver();
    }

    @Override
    public boolean isViable() {
        return false;
    }

    @Override
    public void step(float dt, ControlsOutput controlsOutput) {
        final float tReact = 0.3f;

        final GameData gameData = this.getGameData();
        final CarData car = gameData.getCarData();

        if (path.length <= 0) {
            System.err.println("Invalid path");
            this.setIsDone(true);
            return;
        }

        if (path.maxSpeeds.length == 0)
            path.calculateMaxSpeeds(CarData.MAX_VELOCITY, CarData.MAX_VELOCITY);

        final float currentSpeed = (float) car.velocity.dot(car.forward());

        final float pathDistanceFromTarget = path.findNearest(car.position);
        final float sAhead = (float) (pathDistanceFromTarget - Math.max(car.velocity.magnitude(), 500f) * tReact);

        driveManeuver.target = path.pointAt(sAhead);

        if (arrivalTime != -1) {
            final float T_min = RLConstants.tickFrequency;
            final float timeUntilArrival = Math.max(arrivalTime - car.elapsedSeconds, T_min);

            if (arrivalSpeed != -1)
                driveManeuver.speed = determineSpeedPlan(pathDistanceFromTarget, timeUntilArrival, dt, car);
            else
                driveManeuver.speed = pathDistanceFromTarget / timeUntilArrival;

            this.setIsDone(timeUntilArrival <= T_min);
        } else {
            if (arrivalSpeed != -1)
                driveManeuver.speed = arrivalSpeed;
            else
                driveManeuver.speed = DriveManeuver.max_throttle_speed;

            this.setIsDone(pathDistanceFromTarget <= 50f);
        }

        final float maxSpeedAtPathSection = path.maxSpeedAt(pathDistanceFromTarget - currentSpeed * (4f * dt));

        driveManeuver.speed = Math.min(driveManeuver.speed, maxSpeedAtPathSection);
        boolean enableSlide = false;

        if (Math.abs(maxSpeedAtPathSection) < 50) { // Very likely to be stuck in a turn that is impossible
            driveManeuver.speed = 200;
            enableSlide = true;
        }

        driveManeuver.step(dt, controlsOutput);
        if (enableSlide) {
            controlsOutput.withSlide();
            controlsOutput.withSteer(Math.signum(controlsOutput.getSteer()));
        }
    }

    float distanceError(float s0, float T, float dt, float v0, float vT, float aT) {
        int num_steps = (int) (T / dt);
        float s = s0;
        float v = v0;
        float v_prev = v0;

        for (int i = 0; i < num_steps; i++) {
            float t = (((float) i) / ((float) num_steps)) * T;
            float v_ideal = MathUtils.interpolateQuadratic(v0, vT, aT, t, T);
            float v_limit = path.maxSpeedAt(s - v_prev * dt);

            v_prev = v;
            v = Math.min(v_ideal, v_limit);

            s -= 0.5f * (v + v_prev) * dt;
        }

        return s;
    }

    float determineSpeedPlan(float s, float T, float dt, CarData car) {
        float v0 = (float) car.velocity.magnitude();
        float vf = arrivalSpeed;

        float a = DriveManeuver.boost_acceleration + DriveManeuver.throttleAcceleration(vf);
        float error = distanceError(s, T, dt, v0, vf, a);

        float a_old = -DriveManeuver.brake_acceleration;

        float error_old = distanceError(s, T, dt, v0, vf, a_old);

        // try to find the right arrival acceleration
        // using a few iterations of secant method
        for (int i = 0; i < 16; i++) {
            if (Math.abs(error) < 0.5f || Float.isInfinite(a) || Float.isNaN(error)) break;

            float new_a = (a_old * error - a * error_old) / (error - error_old);

            a_old = a;
            a = new_a;

            error_old = error;
            error = distanceError(s, T, dt, v0, vf, a);
        }

        expected_error = error;
        expected_speed = MathUtils.interpolateQuadratic(v0, vf, a, DriveManeuver.reaction_time, T);

        return expected_speed;
    }

    @Override
    public CarData simulate(CarData car) {
        return null;
    }
}
