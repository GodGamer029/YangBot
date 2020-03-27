package yangbot.manuever;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.GameData;
import yangbot.input.RLConstants;
import yangbot.prediction.Curve;
import yangbot.util.AdvancedRenderer;
import yangbot.util.math.MathUtils;

import java.awt.*;

public class FollowPathManeuver extends Maneuver {

    public Curve path = null;
    public float arrivalTime = -1;
    public float arrivalSpeed = -1;
    float expected_error;
    float expected_speed;
    public DriveManeuver driveManeuver;
    public boolean allowBackwardsDriving = false;

    public FollowPathManeuver() {
        driveManeuver = new DriveManeuver();
    }

    @Override
    public void step(float dt, ControlsOutput controlsOutput) {
        final float tReact = 0.3f;

        final GameData gameData = this.getGameData();
        final CarData car = gameData.getCarData();

        if (path.length <= 0) {
            System.err.println("Invalid path");
            this.setIsDone(true);
            throw new IllegalArgumentException("Path has no control points");
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

            if (arrivalSpeed != -1) {
                driveManeuver.minimumSpeed = determineSpeedPlan(pathDistanceFromTarget, timeUntilArrival, dt, car);
                driveManeuver.maximumSpeed = driveManeuver.minimumSpeed;
            } else {
                driveManeuver.minimumSpeed = pathDistanceFromTarget / timeUntilArrival;
                driveManeuver.maximumSpeed = driveManeuver.minimumSpeed;
            }

            this.setIsDone(timeUntilArrival <= T_min);
        } else {
            if (arrivalSpeed != -1) {
                driveManeuver.minimumSpeed = arrivalSpeed;
                driveManeuver.maximumSpeed = arrivalSpeed;
            } else {
                driveManeuver.minimumSpeed = DriveManeuver.max_throttle_speed - 10;
                driveManeuver.maximumSpeed = CarData.MAX_VELOCITY;
            }

            this.setIsDone(pathDistanceFromTarget <= 50f);
        }

        final float maxSpeedAtPathSection = path.maxSpeedAt(pathDistanceFromTarget - currentSpeed * (4f * dt));

        driveManeuver.maximumSpeed = Math.min(maxSpeedAtPathSection, driveManeuver.maximumSpeed);
        driveManeuver.minimumSpeed = Math.min(driveManeuver.minimumSpeed, driveManeuver.maximumSpeed);

        if (allowBackwardsDriving && car.forward().dot(path.tangentAt(pathDistanceFromTarget - currentSpeed * (4f * dt))) < 0) {
            float temp = driveManeuver.maximumSpeed;
            driveManeuver.maximumSpeed = driveManeuver.minimumSpeed * -1;
            driveManeuver.minimumSpeed = temp * -1;

            driveManeuver.target = driveManeuver.target.add(car.position.sub(driveManeuver.target).mul(-1));
        }

        boolean enableSlide = false;

        if (Math.abs(maxSpeedAtPathSection) < 100) { // Very likely to be stuck in a turn that is impossible
            driveManeuver.minimumSpeed = 100;
            driveManeuver.maximumSpeed = 200;
            enableSlide = true;
        }

        driveManeuver.step(dt, controlsOutput);
        if (enableSlide) {
            controlsOutput.withSlide();
            controlsOutput.withSteer(Math.signum(controlsOutput.getSteer()));
        }
    }

    public float getDistanceOffPath(CarData car) {
        float currentPos = this.path.findNearest(car.position);
        return (float) car.position.flatten().distance(this.path.pointAt(currentPos).flatten());
    }

    public void draw(AdvancedRenderer renderer, CarData car) {
        float currentPos = this.path.findNearest(car.position);
        float distanceOffPath = this.getDistanceOffPath(car);
        float perc = 100 - (100 * currentPos / this.path.length);
        renderer.drawString2d(String.format("Current %.1f", perc), Color.WHITE, new Point(500, 410), 1, 1);
        renderer.drawString2d(String.format("Length %.1f", this.path.length), Color.WHITE, new Point(500, 430), 1, 1);
        if (this.arrivalTime > 0)
            renderer.drawString2d(String.format("Arriving in %.1fs", this.arrivalTime - car.elapsedSeconds), Color.WHITE, new Point(500, 450), 2, 2);
        else
            renderer.drawString2d(String.format("Speed %.1fs", car.velocity.magnitude()), Color.WHITE, new Point(500, 450), 2, 2);
        //renderer.drawString2d(String.format("Max speed: %.0fuu/s", this.path.maxSpeedAt(this.path.findNearest(car.position))), Color.WHITE, new Point(500, 490), 2, 2);
        renderer.drawString2d(String.format("Max drive: %.0fuu/s", this.driveManeuver.maximumSpeed), Color.WHITE, new Point(500, 490), 2, 2);
        renderer.drawString2d(String.format("Min drive: %.0fuu/s", this.driveManeuver.minimumSpeed), Color.WHITE, new Point(500, 530), 2, 2);
        renderer.drawString2d(String.format("Off path: %.0fuu", distanceOffPath), Color.WHITE, new Point(500, 570), 2, 2);
    }

    public float distanceError(float s0, float T, float dt, float v0, float vT, float aT) {
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

    public float determineSpeedPlan(float s, float T, float dt, CarData car) {
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
        expected_speed = MathUtils.interpolateQuadratic(v0, vf, a, 0.04f, T);

        return expected_speed;
    }

    @Override
    public CarData simulate(CarData car) {
        return null;
    }
}
