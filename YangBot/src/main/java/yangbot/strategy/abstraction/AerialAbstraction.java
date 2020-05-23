package yangbot.strategy.abstraction;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.GameData;
import yangbot.input.RLConstants;
import yangbot.strategy.manuever.AerialManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector3;

import java.awt.*;

public class AerialAbstraction extends Abstraction {

    private static final float MAX_TIME_GROUND_ADJUSTMENTS = 0.6f;
    private final AerialManeuver aerialManeuver;
    public Vector3 targetPos;
    public float arrivalTime = 0;
    private float timer = 0;
    private State state;

    public AerialAbstraction() {
        this.state = State.DRIVE;
        this.aerialManeuver = new AerialManeuver();
        this.aerialManeuver.target = this.targetPos;
    }

    // Does a full simulation of the flight path including ground preparations
    public static boolean isViable(CarData carData, Vector3 target, float absoluteArrival) {
        float t = 0;
        final float dt = RLConstants.tickFrequency;
        final float relativeArrival = absoluteArrival - carData.elapsedSeconds;

        var simCar = new CarData(carData);

        if (simCar.hasWheelContact) { // Do on-ground adjustments
            var deltaX = AerialManeuver.getDeltaX(simCar, target, absoluteArrival);
            var deltaXLocal = deltaX.dot(simCar.orientation);
            do {
                float vF = simCar.forwardSpeed();
                var controls = new ControlsOutput();
                // Align the car in-plane
                float /*P*/ angle = (float) Math.atan2(deltaXLocal.y, deltaXLocal.x);
                if (deltaXLocal.x < 0)
                    angle = (float) Math.atan2(deltaXLocal.y, -deltaXLocal.x);

                controls.withSteer(MathUtils.clip(angle, -1f, 1f));

                // Only alter velocity if we are aligned, or our velocity is too high
                if (Math.abs(angle) < Math.PI * 0.2f || (simCar.velocity.magnitude() > 1800 && Math.abs(angle) < Math.PI * 0.5f && Math.abs(deltaXLocal.x) > 800))
                    controls.withThrottle(deltaXLocal.x / 200);
                else if (simCar.velocity.magnitude() < 300)
                    controls.withThrottle(Math.signum(deltaXLocal.x));

                float forwardForce = CarData.driveForceForward(controls, vF, 0, 0);
                var upTorque = simCar.up().mul(CarData.driveTorqueUp(controls, vF, (float) simCar.angularVelocity.dot(simCar.up())));
                simCar.velocity = simCar.velocity.add(simCar.forward().mul(forwardForce).mul(dt)).withZ(0);
                simCar.position = simCar.position.add(simCar.velocity.mul(dt));
                simCar.angularVelocity = simCar.angularVelocity.add(upTorque.mul(dt));
                simCar.orientation = Matrix3x3.axisToRotation(simCar.angularVelocity.mul(dt)).matrixMul(simCar.orientation);
                simCar.elapsedSeconds += dt;

                t += dt;

                deltaX = AerialManeuver.getDeltaX(simCar, target, absoluteArrival);
                deltaXLocal = deltaX.dot(simCar.orientation);
            } while (Math.abs(deltaXLocal.x) >= 200 && t < MAX_TIME_GROUND_ADJUSTMENTS && t < relativeArrival);
        }
        if (t >= relativeArrival)
            return false;

        var simAerial = new AerialManeuver();
        simAerial.arrivalTime = absoluteArrival;
        simAerial.target = target;

        return simAerial.isViable(simCar, 90);
    }

    @Override
    protected RunState stepInternal(float dt, ControlsOutput controlsOutput) {
        this.timer += dt;
        final GameData gameData = this.getGameData();
        final CarData car = gameData.getCarData();

        var deltaX = AerialManeuver.getDeltaX(car, this.targetPos, arrivalTime);

        switch (this.state) {
            case DRIVE:
                var deltaXLocal = deltaX.dot(car.orientation);

                // Align the car in-plane
                float /*P*/ angle = (float) Math.atan2(deltaXLocal.y, deltaXLocal.x);
                if (deltaXLocal.x < 0)
                    angle = (float) Math.atan2(deltaXLocal.y, -deltaXLocal.x);

                controlsOutput.withSteer(MathUtils.clip(angle, -1f, 1f));

                // Only alter velocity if we are aligned, or our velocity is too high
                if (Math.abs(angle) < Math.PI * 0.2f || (car.velocity.magnitude() > 1800 && Math.abs(angle) < Math.PI * 0.5f && Math.abs(deltaXLocal.x) > 800))
                    controlsOutput.withThrottle(deltaXLocal.x / 200);
                else if (car.velocity.magnitude() < 300)
                    controlsOutput.withThrottle(Math.signum(deltaXLocal.x));

                if (Math.abs(deltaXLocal.x) < 200 || this.timer > MAX_TIME_GROUND_ADJUSTMENTS) {
                    this.state = State.FLY;
                    // fallthrough to FLY case
                } else
                    return RunState.CONTINUE;

            case FLY:

                this.aerialManeuver.target = this.targetPos;
                this.aerialManeuver.arrivalTime = this.arrivalTime;
                this.aerialManeuver.target_orientation = Matrix3x3.lookAt(this.targetPos.sub(car.position), car.up());
                this.aerialManeuver.step(dt, controlsOutput);
                if (this.aerialManeuver.isDone() || car.elapsedSeconds > this.arrivalTime + 0.1f)
                    return RunState.DONE;
                return RunState.CONTINUE;
        }
        return RunState.DONE;
    }

    public void draw(AdvancedRenderer renderer) {
        var car = GameData.current().getCarData();
        renderer.drawString2d("State: " + this.state.name(), Color.WHITE, new Point(400, 300), 2, 2);
        var bean = AerialManeuver.getDeltaX(car, new Vector3(), arrivalTime).mul(-1);
        renderer.drawCentered3dCube(Color.RED, bean, 50);
        renderer.drawCentered3dCube(Color.RED, bean, 120);

        renderer.drawCentered3dCube(Color.GREEN, targetPos, 50);
        renderer.drawCentered3dCube(Color.GREEN, targetPos, 120);

        var deltaX = AerialManeuver.getDeltaX(car, this.targetPos, arrivalTime);
        //float D = car.angularVelocity.dot(car.orientation).z;
        //renderer.drawString2d("D: "+D, Color.WHITE, new Point(400, 330), 2, 2);
        renderer.drawLine3d(Color.RED, car.position, car.position.add(deltaX.normalized().mul(100)));
    }

    enum State {
        DRIVE,
        FLY
    }
}
