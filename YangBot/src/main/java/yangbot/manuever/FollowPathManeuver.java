package yangbot.manuever;

import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.prediction.Curve;
import yangbot.util.ControlsOutput;
import yangbot.vector.Vector3;

public class FollowPathManeuver extends Maneuver {

    public Curve path;
    public float arrivalTime = -1;
    public float arrivalSpeed = -1;
    float expected_error;
    float expected_speed;
    private DriveManeuver driveManeuver;

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
        final GameData gameData = this.getGameData();
        final Vector3 gravity = gameData.getGravity();
        final CarData car = gameData.getCarData();
        final BallData ball = gameData.getBallData();

        dt = Math.max(dt, 1f / 120f);
        if (path.length <= 0) {
            System.err.println("Invalid path");
            this.setIsDone(true);
            return;
        }

        if (path.maxSpeeds.length == 0)
            path.calculateMaxSpeeds(DriveManeuver.max_speed, DriveManeuver.max_speed);

        float tReact = 0.3f;

        float speed = (float) car.velocity.dot(car.forward());

        float s = path.findNearest(car.position);

        float sAhead = (float) (s - Math.max(car.velocity.magnitude(), 500f) * tReact);

        driveManeuver.target = path.pointAt(sAhead);

        if (arrivalTime != -1) {
            float T_min = 0.008f;
            float T = Math.max(arrivalTime - car.elapsedSeconds, T_min);

            if (arrivalSpeed != -1)
                driveManeuver.speed = determine_speed_plan(s, T, dt, car);
            else
                driveManeuver.speed = s / T;

            this.setIsDone(T <= T_min);
        } else {
            if (arrivalSpeed != -1)
                driveManeuver.speed = arrivalSpeed;
            else
                driveManeuver.speed = DriveManeuver.max_throttle_speed;

            this.setIsDone(s <= 50f);
        }

        driveManeuver.speed = Math.min(driveManeuver.speed, path.maxSpeedAt(s - 4f * speed * dt));

        driveManeuver.step(dt, controlsOutput);
    }

    float interpolate_quadratic(float v0, float vT, float aT, float t, float T) {
        float tau = t / T;
        float dv = aT * T;
        return v0 * (tau - 1.0f) * (tau - 1.0f) +
                dv * (tau - 1.0f) * tau +
                vT * (2.0f - tau) * tau;
    }

    float distance_error(float s0, float T, float dt, float v0, float vT, float aT) {
        int num_steps = (int) (T / dt);
        float s = s0;
        float v = v0;
        float v_prev = v0;

        for (int i = 0; i < num_steps; i++) {
            float t = (((float) i) / ((float) num_steps)) * T;
            float v_ideal = interpolate_quadratic(v0, vT, aT, t, T);
            float v_limit = path.maxSpeedAt(s - v_prev * dt);

            v_prev = v;
            v = Math.min(v_ideal, v_limit);

            s -= 0.5f * (v + v_prev) * dt;
        }

        return s;
    }

    float determine_speed_plan(float s, float T, float dt, CarData car) {
        float v0 = (float) car.velocity.magnitude();
        float vf = arrivalSpeed;

        float a = DriveManeuver.boost_acceleration + DriveManeuver.throttle_acceleration(vf);
        float error = distance_error(s, T, dt, v0, vf, a);

        float a_old = -DriveManeuver.brake_acceleration;

        float error_old = distance_error(s, T, dt, v0, vf, a_old);

        // try to find the right arrival acceleration
        // using a few iterations of secant method
        for (int i = 0; i < 16; i++) {
            if (Math.abs(error) < 0.5f || Float.isInfinite(a) || Float.isNaN(error)) break;

            float new_a = (a_old * error - a * error_old) / (error - error_old);

            a_old = a;
            a = new_a;

            error_old = error;
            error = distance_error(s, T, dt, v0, vf, a);

        }

        expected_error = error;
        expected_speed = interpolate_quadratic(v0, vf, a, DriveManeuver.reaction_time, T);
        //if(Float.isNaN(expected_speed))
        //    System.out.println("Expected speed isNan. v0="+v0+" vf="+vf+" a="+a+" error_old="+error_old);
        return expected_speed;
    }

    @Override
    public CarData simulate(CarData car) {
        return null;
    }
}
