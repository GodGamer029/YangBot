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
    public float speedReactionTime = 4 * RLConstants.tickFrequency;

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

        float a_old = DriveManeuver.brake_acceleration;

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
        float maxSpeed = path.maxSpeedAt(currentPos - car.forwardSpeed() * this.speedReactionTime);
        float maxSpeedCalc = DriveManeuver.maxTurningSpeed(path.curvatureAt(currentPos));
        float perc = 100 - (100 * currentPos / this.path.length);
        int yPos = 540;
        renderer.drawString2d(String.format("TimeUntilArrival %.2f", timeUntilArrival), Color.WHITE, new Point(500, yPos += 20), 1, 1);
        renderer.drawString2d(String.format("ReqSpeed %.1f", currentPos / timeUntilArrival), Color.WHITE, new Point(500, yPos += 20), 1, 1);
        renderer.drawString2d(String.format("Current %.1f %.1f", perc, currentPos), Color.WHITE, new Point(500, yPos += 20), 1, 1);
        renderer.drawString2d(String.format("Length %.1f", this.path.length), Color.WHITE, new Point(500, yPos += 20), 1, 1);
        if (this.arrivalTime > 0)
            renderer.drawString2d(String.format("Arriving in %.1fs (%.1fs, s=%.1f)", timeUntilArrival, this.arrivalTime, this.arrivalSpeed), Color.WHITE, new Point(500, yPos += 40), 2, 2);
        else //  Max: %.1f MaxCalc: %.1f
            renderer.drawString2d(String.format("MySpeed %.1fs", car.forwardSpeed(), maxSpeed, maxSpeedCalc), Color.WHITE, new Point(500, yPos += 40), 2, 2);
        //renderer.drawString2d(String.format("Max speed: %.0fuu/s", this.path.maxSpeedAt(this.path.findNearest(car.position))), Color.WHITE, new Point(500, 490), 2, 2);
        renderer.drawString2d(String.format("Max drive: %04.0fuu/s", this.driveManeuver.maximumSpeed), Color.WHITE, new Point(500, yPos += 40), 2, 2);
        renderer.drawString2d(String.format("Min drive: %04.0fuu/s e: %04.0f", this.driveManeuver.minimumSpeed, this.driveManeuver.minimumSpeed - car.forwardSpeed()), Color.WHITE, new Point(500, yPos += 40), 2, 2);
        renderer.drawString2d(String.format("Off path: %.0fuu", distanceOffPath), Color.WHITE, new Point(500, yPos += 40), 2, 2);
        renderer.drawString2d(String.format("Max Speed: %.0fuu/s", maxSpeed), Color.WHITE, new Point(500, yPos += 40), 2, 2);
        if(this.driveManeuver.target != null)
            renderer.drawCentered3dCube(Color.GREEN, this.driveManeuver.target, 55);
        renderer.drawCentered3dCube(Color.RED, path.pointAt(currentPos), 45);

        //System.out.printf("%03f.1f: %04.1f %04.1f %03.1f\n", perc, car.forwardSpeed(), maxSpeed, maxSpeed - car.forwardSpeed());
    }

    @Override
    public void step(float dt, ControlsOutput controlsOutput) {
        final float tReact = 0.25f;

        final GameData gameData = this.getGameData();
        final CarData car = gameData.getCarData();

        if (path.length <= 0) {
            System.err.println("Invalid path");
            this.setIsDone(true);
            throw new IllegalArgumentException("Path has no control points");
        }

        if (path.maxSpeeds.length == 0)
            path.calculateMaxSpeeds(CarData.MAX_VELOCITY, CarData.MAX_VELOCITY, car.boost);

        float currentPredSpeed = Math.max(1, car.velocity.magnitudeF());
        currentPredSpeed += Math.signum(currentPredSpeed) * 0.5f * this.speedReactionTime * (DriveManeuver.throttleAcceleration(currentPredSpeed) + DriveManeuver.boost_acceleration);
        currentPredSpeed = MathUtils.clip(currentPredSpeed, 50, CarData.MAX_VELOCITY);

        CarData simCar = new CarData(car);
        simCar.smartPrediction(0.05f);
        final float predPathDistanceFromTarget = path.findNearest(simCar.position);
        final float truePathDistanceFromTarget = path.findNearest(car.position);
        final float sAhead = predPathDistanceFromTarget - currentPredSpeed * tReact;

        if (sAhead < 0) {
            // we add an invisible point after the curve's tail to keep the car aligned with the end tangent
            var lastPoint = path.pointAt(0);
            var lastTangent = path.tangentAt(0);
            float leeway = currentPredSpeed * 0.4f;

            driveManeuver.target = lastPoint.add(lastTangent.mul(Math.min(leeway, -sAhead)));

        } else
            driveManeuver.target = path.pointAt(sAhead);

        if (this.arrivalTime != -1) {
            final float T_min = RLConstants.tickFrequency * 3;
            final float timeUntilArrival = Math.max(this.arrivalTime - car.elapsedSeconds, T_min);

            if (arrivalSpeed != -1) {
                driveManeuver.minimumSpeed = determineSpeedPlan(predPathDistanceFromTarget, timeUntilArrival, dt, car.forwardSpeed(), this.arrivalSpeed, this.path);
                driveManeuver.maximumSpeed = driveManeuver.minimumSpeed + 5;
            } else {
                driveManeuver.minimumSpeed = CarData.MAX_VELOCITY;
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
            float distFromTarget = path.findNearest(car.position);
            if (distFromTarget <= 50 || this.closeToDestTimeout > 0) { // Trigger a timeout as to not break existing code relying on the more lenient end condition
                this.closeToDestTimeout += dt;

                if (this.closeToDestTimeout >= 0.1f || distFromTarget <= 10f || distFromTarget > 50) {
                    this.setIsDone(true);
                    this.closeToDestTimeout = 0.1f;
                }
            }
        }

        final float maxSpeedAtPathSection = path.maxSpeedAt(truePathDistanceFromTarget - currentPredSpeed * speedReactionTime);

        driveManeuver.maximumSpeed = Math.min(maxSpeedAtPathSection + 5, driveManeuver.maximumSpeed);
        driveManeuver.minimumSpeed = Math.min(driveManeuver.minimumSpeed, driveManeuver.maximumSpeed - 5);

        if (allowBackwardsDriving && car.forward().dot(path.tangentAt(predPathDistanceFromTarget - currentPredSpeed * speedReactionTime)) < 0) {
            float temp = driveManeuver.maximumSpeed;
            driveManeuver.maximumSpeed = driveManeuver.minimumSpeed * -1;
            driveManeuver.minimumSpeed = temp * -1;

            driveManeuver.target = driveManeuver.target.add(car.position.sub(driveManeuver.target).mul(-1));
        }
        driveManeuver.reaction_time = speedReactionTime;
        driveManeuver.step(dt, controlsOutput);
    }
}
