package yangbot.strategy.manuever;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.GameData;
import yangbot.input.RLConstants;
import yangbot.path.Curve;
import yangbot.util.AdvancedRenderer;
import yangbot.util.math.MathUtils;

import java.awt.*;

public class FollowPathManeuver extends Maneuver {

    public Curve path = null;
    public float arrivalTime = -1;
    public float arrivalSpeed = -1;
    //float expected_error;
    //float expected_speed;
    public DriveManeuver driveManeuver;
    public boolean allowBackwardsDriving = false;
    public float speedReactionTime = 0.04f;

    public FollowPathManeuver() {
        driveManeuver = new DriveManeuver();
    }

    private float closeToDestTimeout = 0;

    public static float distanceError(float s0, float T, float dt, float v0, float vT, float aT, Curve path) {
        int num_steps = (int) (T / dt);
        float s = s0;
        float v = v0;
        float v_prev = v0;

        for (int i = 0; i < num_steps; i++) {
            float t = (((float) i) / ((float) num_steps)) * T;
            float v_ideal = MathUtils.interpolateQuadratic(v0, vT, aT, t, T);
            float v_limit = CarData.MAX_VELOCITY;
            if (path != null)
                v_limit = path.maxSpeedAt(s - v_prev * dt);

            v_prev = v;
            v = Math.min(v_ideal, v_limit);

            s -= 0.5f * (v + v_prev) * dt;
        }

        return s;
    }

    public static float determineSpeedPlan(float distToTarget, float T, float dt, float v0, float vf, Curve path) {

        float a = DriveManeuver.boost_acceleration + DriveManeuver.throttleAcceleration(vf);
        float error = distanceError(distToTarget, T, dt, v0, vf, a, path);

        float a_old = -DriveManeuver.brake_acceleration;

        float error_old = distanceError(distToTarget, T, dt, v0, vf, a_old, path);

        // try to find the right arrival acceleration
        // using a few iterations of secant method
        for (int i = 0; i < 16; i++) {
            if (Math.abs(error) < 0.5f || Float.isInfinite(a) || Float.isNaN(error)) break;

            float new_a = (a_old * error - a * error_old) / (error - error_old);

            a_old = a;

            a = new_a;

            error_old = error;
            error = distanceError(distToTarget, T, dt, v0, vf, a, path);
        }

        //expected_error = error;
        var expected_speed = MathUtils.interpolateQuadratic(v0, vf, a, 0.04f, T);

        return expected_speed;
    }

    public float getDistanceOffPath(CarData car) {
        float currentPos = this.path.findNearest(car.position);
        return (float) car.position.flatten().distance(this.path.pointAt(currentPos).flatten());
    }

    public void draw(AdvancedRenderer renderer, CarData car) {
        float currentPos = this.path.findNearest(car.position);
        float distanceOffPath = this.getDistanceOffPath(car);
        final float timeUntilArrival = Math.max(this.arrivalTime - car.elapsedSeconds, 0.01f);
        float perc = 100 - (100 * currentPos / this.path.length);
        int yPos = 560;
        renderer.drawString2d(String.format("Speed %.1f", currentPos / timeUntilArrival), Color.WHITE, new Point(500, yPos += 20), 1, 1);
        renderer.drawString2d(String.format("Current %.1f", perc), Color.WHITE, new Point(500, yPos += 20), 1, 1);
        renderer.drawString2d(String.format("Length %.1f", this.path.length), Color.WHITE, new Point(500, yPos += 20), 1, 1);
        if (this.arrivalTime > 0)
            renderer.drawString2d(String.format("Arriving in %.1fs (%.1fs)", timeUntilArrival, this.arrivalTime), Color.WHITE, new Point(500, yPos += 40), 2, 2);
        else
            renderer.drawString2d(String.format("Speed %.1fs", car.velocity.magnitude()), Color.WHITE, new Point(500, yPos += 40), 2, 2);
        //renderer.drawString2d(String.format("Max speed: %.0fuu/s", this.path.maxSpeedAt(this.path.findNearest(car.position))), Color.WHITE, new Point(500, 490), 2, 2);
        renderer.drawString2d(String.format("Max drive: %.0fuu/s", this.driveManeuver.maximumSpeed), Color.WHITE, new Point(500, yPos += 40), 2, 2);
        renderer.drawString2d(String.format("Min drive: %.0fuu/s", this.driveManeuver.minimumSpeed), Color.WHITE, new Point(500, yPos += 40), 2, 2);
        renderer.drawString2d(String.format("Off path: %.0fuu", distanceOffPath), Color.WHITE, new Point(500, yPos += 40), 2, 2);
    }

    @Override
    public void step(float dt, ControlsOutput controlsOutput) {
        final float tReact = 0.35f;

        final GameData gameData = this.getGameData();
        final CarData car = gameData.getCarData();

        if (path.length <= 0) {
            System.err.println("Invalid path");
            this.setIsDone(true);
            throw new IllegalArgumentException("Path has no control points");
        }

        if (path.maxSpeeds.length == 0)
            path.calculateMaxSpeeds(CarData.MAX_VELOCITY, CarData.MAX_VELOCITY, true);

        float currentPredSpeed = Math.max(1, car.forwardSpeed());
        currentPredSpeed += Math.signum(currentPredSpeed) * car.velocity.dot(car.right());
        currentPredSpeed = MathUtils.lerp(currentPredSpeed, currentPredSpeed + Math.signum(currentPredSpeed) * 0.04f * (DriveManeuver.throttleAcceleration(currentPredSpeed) + DriveManeuver.boost_acceleration), 0.5f);
        currentPredSpeed = MathUtils.clip(currentPredSpeed, 1, CarData.MAX_VELOCITY);

        final float pathDistanceFromTarget = path.findNearest(car.position);
        final float sAhead = pathDistanceFromTarget - Math.max(currentPredSpeed, 300f) * tReact;

        if (sAhead < 0) {
            // we add an invisible point after the curve's tail to keep the car aligned with the end tangent
            var lastPoint = path.pointAt(0);
            var lastTangent = path.tangentAt(0);
            float leeway = currentPredSpeed * tReact * 1.1f;
            var lerpPoint = lastPoint.add(lastTangent.mul(Math.min(leeway, -sAhead)));

            driveManeuver.target = lerpPoint;
        } else
            driveManeuver.target = path.pointAt(sAhead);

        if (this.arrivalTime != -1) {
            final float T_min = RLConstants.tickFrequency * 3;
            final float timeUntilArrival = Math.max(this.arrivalTime - car.elapsedSeconds, T_min);

            if (arrivalSpeed != -1) {
                driveManeuver.minimumSpeed = determineSpeedPlan(pathDistanceFromTarget, timeUntilArrival, dt, car.forwardSpeed(), this.arrivalSpeed, this.path);
                driveManeuver.maximumSpeed = driveManeuver.minimumSpeed + 5;
            } else {
                driveManeuver.minimumSpeed = pathDistanceFromTarget / timeUntilArrival;
                driveManeuver.maximumSpeed = driveManeuver.minimumSpeed + 5;
            }

            this.setIsDone(timeUntilArrival <= T_min);
        } else {
            if (arrivalSpeed != -1) {
                driveManeuver.minimumSpeed = arrivalSpeed;
                driveManeuver.maximumSpeed = arrivalSpeed + 10;
            } else {
                driveManeuver.minimumSpeed = CarData.MAX_VELOCITY;
                driveManeuver.maximumSpeed = CarData.MAX_VELOCITY;
            }

            if (pathDistanceFromTarget <= 20 || this.closeToDestTimeout > 0) { // Trigger a timeout as to not break existing code relying on the more lenient end condition
                this.closeToDestTimeout += dt;

                if (this.closeToDestTimeout >= 0.05f || pathDistanceFromTarget <= 5f) {
                    this.setIsDone(true);
                    this.closeToDestTimeout = 0.05f;
                }
            }
        }

        final float maxSpeedAtPathSection = path.maxSpeedAt(pathDistanceFromTarget - currentPredSpeed * speedReactionTime);

        driveManeuver.maximumSpeed = Math.min(maxSpeedAtPathSection, driveManeuver.maximumSpeed);
        driveManeuver.minimumSpeed = Math.min(driveManeuver.minimumSpeed, driveManeuver.maximumSpeed);

        if (allowBackwardsDriving && car.forward().dot(path.tangentAt(pathDistanceFromTarget - currentPredSpeed * speedReactionTime)) < 0) {
            float temp = driveManeuver.maximumSpeed;
            driveManeuver.maximumSpeed = driveManeuver.minimumSpeed * -1;
            driveManeuver.minimumSpeed = temp * -1;

            driveManeuver.target = driveManeuver.target.add(car.position.sub(driveManeuver.target).mul(-1));
        }

        if (Math.abs(maxSpeedAtPathSection) < 10) { // Very likely to be stuck in a turn that is impossible
            //driveManeuver.minimumSpeed = 10;
            //driveManeuver.maximumSpeed = 10;
            //enableSlide = true;
        }

        driveManeuver.reaction_time = speedReactionTime;
        driveManeuver.step(dt, controlsOutput);
    }
}
