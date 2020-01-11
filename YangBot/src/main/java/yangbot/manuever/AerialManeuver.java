package yangbot.manuever;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.FoolGameData;
import yangbot.input.GameData;
import yangbot.vector.Matrix3x3;
import yangbot.vector.Vector3;

public class AerialManeuver extends Maneuver {

    public static final float boost_acceleration = 1060.0f;
    public static final float throttle_acceleration = 66.66667f;

    public float arrivalTime = 0.0f;
    public Vector3 target = null;
    public Matrix3x3 target_orientation = null;
    private boolean jumping = true;
    private final DodgeManeuver doubleJump;
    private final TurnManeuver turnManuver;

    public AerialManeuver() {
        this.turnManuver = new TurnManeuver();
        this.doubleJump = new DodgeManeuver();
        this.doubleJump.duration = 0.20f;
        this.doubleJump.delay = 0.25f;
    }

    @Override
    public boolean isViable() {
        return false;
    }

    @Override
    public void step(float dt, ControlsOutput controlsOutput) {
        final float j_speed = DodgeManeuver.speed;
        final float j_acceleration = DodgeManeuver.acceleration;
        final float j_duration = DodgeManeuver.max_duration;

        final float reorient_distance = 50.0f;
        final float angle_threshold = 0.3f;
        final float throttle_distance = 50.0f;

        final GameData gameData = this.getGameData();
        final Vector3 gravity = gameData.getGravity();
        final CarData car = gameData.getCarData();

        this.turnManuver.fool(gameData);
        this.doubleJump.fool(gameData);

        final float T = this.arrivalTime - car.elapsedSeconds;

        Vector3 xf = car.position
                .add(car.velocity.mul(T))
                .add(gravity.mul(T * T * 0.5f));

        boolean jumping_prev = this.jumping;
        if (this.jumping) {
            float tau = j_duration - doubleJump.timer;

            if (doubleJump.timer == 0.0f) {
                //vf = vf.add(car.up().mul(j_speed));
                xf = xf.add(car.up().mul(j_speed * T));
            }

            //vf = vf.add(car.up().mul(j_accel * tau));
            xf = xf.add(car.up().mul(j_acceleration * tau * (T - 0.5f * tau)));

            //vf = vf.add(car.up().mul(j_speed));
            xf = xf.add(car.up().mul(j_speed * (T - tau)));

            ControlsOutput output = new ControlsOutput();
            doubleJump.step(dt, output);
            controlsOutput.withJump(output.holdJump());

            if (doubleJump.timer >= doubleJump.delay)
                jumping = false;
        } else
            controlsOutput.withJump(false);

        Vector3 delta_x = target.sub(xf);
        Vector3 direction = delta_x.normalized();

        if (delta_x.magnitude() > reorient_distance) {
            this.turnManuver.target = Matrix3x3.lookAt(delta_x, new Vector3(0, 0, 1));
        } else {
            if (target_orientation == null || Math.abs(target_orientation.det() - 1f) < 0.01f) {
                this.turnManuver.target = Matrix3x3.lookAt(target.sub(car.position), new Vector3(0, 0, 1));
            } else {
                this.turnManuver.target = target_orientation;
            }
        }

        turnManuver.step(dt, null);

        if (jumping_prev && !jumping) {
            controlsOutput.withRoll(0);
            controlsOutput.withPitch(0);
            controlsOutput.withYaw(0);
        } else {
            controlsOutput.withRoll(this.turnManuver.controls.getRoll());
            controlsOutput.withPitch(this.turnManuver.controls.getPitch());
            controlsOutput.withYaw(this.turnManuver.controls.getYaw());
        }

        if (car.forward().angle(direction) < angle_threshold) {
            if (delta_x.magnitude() > throttle_distance) {
                controlsOutput.withBoost(true);
                controlsOutput.withThrottle(0);
            } else {
                controlsOutput.withBoost(false);
                controlsOutput.withThrottle(0.5f * throttle_acceleration * T * T);
            }
        } else {
            controlsOutput.withBoost(false);
            controlsOutput.withThrottle(0);
        }

        this.setIsDone(T <= 0);
    }

    @Override
    public CarData simulate(CarData car) {
        CarData carCopy = new CarData(car);
        AerialManeuver fakeAerial = new AerialManeuver();
        fakeAerial.target = this.target;
        fakeAerial.arrivalTime = this.arrivalTime;
        fakeAerial.target_orientation = this.target_orientation;
        fakeAerial.jumping = car.hasWheelContact;
        FoolGameData foolGameData = GameData.current().fool();

        float dt = 0.01666f;
        Vector3 lastPos = null;
        for (float t = dt; t < 5.0f; t += dt) {
            ControlsOutput output = new ControlsOutput();
            foolGameData.foolCar(carCopy);
            fakeAerial.fool(foolGameData);
            fakeAerial.step(dt, output);
            carCopy.step(output, dt);


            if (fakeAerial.isDone())
                break;
        }

        return carCopy;
    }
}
