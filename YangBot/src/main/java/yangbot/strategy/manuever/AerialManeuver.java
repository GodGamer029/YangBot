package yangbot.strategy.manuever;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.FoolGameData;
import yangbot.input.GameData;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector3;

public class AerialManeuver extends Maneuver {

    public static final float boost_airthrottle_acceleration = 1060.0f;
    public static final float throttle_acceleration = 66.66667f;

    public float arrivalTime = 0.0f;
    public Vector3 target = null;
    public Matrix3x3 target_orientation = null;
    private boolean jumping = true;
    public final DodgeManeuver doubleJump;
    private final TurnManeuver turnManuver;

    private static final float j_speed = DodgeManeuver.speed;
    private static final float j_acceleration = DodgeManeuver.acceleration;
    private static final float j_duration = DodgeManeuver.max_duration;
    private static final float angle_threshold = 0.3f;

    public AerialManeuver() {
        this.turnManuver = new TurnManeuver();
        this.doubleJump = new DodgeManeuver();
        this.doubleJump.duration = 0.20f;
        this.doubleJump.delay = 0.25f;
    }

    public static Vector3 getDeltaX(CarData car, Vector3 target, float arrivalTime) {
        return getDeltaX(car, target, arrivalTime, -1);
    }

    public static Vector3 getDeltaX(CarData car, Vector3 target, float arrivalTime, float jdur) {
        final GameData gameData = GameData.current();
        final Vector3 gravity = gameData.getGravity();

        float T = arrivalTime - car.elapsedSeconds;

        Vector3 xf = car.position
                .add(car.velocity.mul(T))
                .add(gravity.mul(T * T * 0.5f));

        if (car.hasWheelContact || jdur > 0) {
            if (jdur == -1)
                jdur = j_duration;
            float tau = MathUtils.clip(jdur, 0, 0.2f);

            if (jdur == j_duration)
                xf = xf.add(car.up().mul(j_speed * T));
            xf = xf.add(car.up().mul(j_acceleration * tau * (T - 0.5f * tau)));
            xf = xf.add(car.up().mul(j_speed * (T - tau)));
        } else if (!car.doubleJumped) {
            xf = xf.add(car.up().mul(j_speed * (T)));
        }
        return target.sub(xf);
    }

    @Override
    public void step(float dt, ControlsOutput controlsOutput) {
        final float reorient_distance = 50.0f;
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
            //System.out.println(doubleJump.timer+": "+output.holdJump()+" "+car.doubleJumped);
            if (doubleJump.timer >= doubleJump.delay)
                jumping = false;
        } else
            controlsOutput.withJump(false);

        Vector3 delta_x = target.sub(xf);
        Vector3 direction = delta_x.normalized();

        if (delta_x.magnitude() > reorient_distance || (this.arrivalTime - car.elapsedSeconds > 0.6f && delta_x.magnitude() > 5)) {
            this.turnManuver.target = Matrix3x3.lookAt(direction, new Vector3(0, 0, 1));
        } else {
            if (target_orientation == null || Math.abs(target_orientation.det() - 1f) < 0.01f) {
                this.turnManuver.target = Matrix3x3.lookAt(target.sub(car.position), new Vector3(0, 0, 1));
            } else {
                this.turnManuver.target = target_orientation;
            }
        }
        if (this.doubleJump.timer < 0.15f && this.arrivalTime - car.elapsedSeconds > 0.4f)
            this.turnManuver.target = Matrix3x3.roofTo(direction, new Vector3(0, 0, 1));

        turnManuver.step(dt, null);

        if (jumping_prev && !jumping) {
            controlsOutput.withRoll(0);
            controlsOutput.withPitch(0);
            controlsOutput.withYaw(0);
        } else {
            controlsOutput.withRoll(this.turnManuver.controls.getRoll());
            controlsOutput.withPitch(this.turnManuver.controls.getPitch());
            controlsOutput.withYaw(this.turnManuver.controls.getYaw());

            if (jumping) {
                //controlsOutput.withRoll(controlsOutput.getRoll() * 0);
                //controlsOutput.withYaw(controlsOutput.getYaw() * 0);
                //controlsOutput.withPitch(controlsOutput.getPitch() * 0);
            }
        }

        if (car.forward().angle(direction) < ((this.jumping ? 2 : 1) * angle_threshold)) {
            float s = delta_x.dot(car.forward());

            float boostDt = 0.03f; // TODO: find whether we have already gone over the min boost usage time and lower this to 1/120
            float delta_v = s / T;
            if (delta_v > (2 * (AerialManeuver.boost_airthrottle_acceleration + AerialManeuver.throttle_acceleration) * boostDt)) {
                controlsOutput.withBoost(true);
                controlsOutput.withThrottle(0);
            } else {
                controlsOutput.withBoost(false);
                controlsOutput.withThrottle(MathUtils.clip(delta_v / (2 * throttle_acceleration * dt), -1, 1));
            }
        } else {
            controlsOutput.withBoost(false);
            controlsOutput.withThrottle(0);
        }

        this.setIsDone(T <= 0);
    }

    public boolean isViable(CarData car, float maxBoostUse) {
        var gravity = GameData.current().getGravity();
        maxBoostUse = Math.min(maxBoostUse, (float) car.boost + 5);
        float T = this.arrivalTime - car.elapsedSeconds;
        if (T <= 0)
            return false;

        Vector3 xf = car.position
                .add(car.velocity.mul(T))
                .add(gravity.mul(T * T * 0.5f));
        Vector3 vf = car.velocity.add(gravity.mul(T));

        if (car.hasWheelContact) {
            vf = vf.add(car.up().mul(2 * j_speed + j_acceleration * j_duration));
            xf = xf.add(car.up().mul(j_speed * (2 * T - j_duration) + j_acceleration * (T * j_duration - 0.5f * j_duration * j_duration)));
        }

        var deltaX = target.sub(xf);

        var dir = deltaX.normalized();

        float turnTime; // bad estimate
        {
            var simTurn = new TurnManeuver();
            simTurn.target = Matrix3x3.lookAt(dir, new Vector3(0, 0, 1));
            simTurn.maxErrorOrientation = 0.3f;
            simTurn.maxErrorAngularVelocity = 0.5f;
            turnTime = MathUtils.clip(simTurn.simulate(car).elapsedSeconds, 0.05f, 1.5f);
        }

        float timeBoostStart = turnTime + 0.15f; // We don't really start orienting at the start
        if (timeBoostStart > T)
            return false;

        float reqAccel = (2 * (float) deltaX.magnitude()) / ((T - timeBoostStart) * (T - timeBoostStart));
        float ratio = reqAccel / boost_airthrottle_acceleration;

        float timeBoostStop = T - (T - timeBoostStart) * (float) Math.sqrt(1 - MathUtils.clip(ratio, 0, 1));

        var velocityEstimate = vf.add(dir.mul(boost_airthrottle_acceleration * (timeBoostStop - timeBoostStart)));
        float boostEstimate = (timeBoostStop - timeBoostStart) * CarData.BOOST_CONSUMPTION;

        boolean isPossible = velocityEstimate.magnitude() < CarData.MAX_VELOCITY - 50 && // max speed
                boostEstimate < maxBoostUse * 0.95f &&                      // boost use
                ratio < 0.975f;                                               // acceleration possible

        if (isPossible) {
            //System.out.println("Aerial possible: velocityEstimate="+velocityEstimate.magnitude()+" boostEstimate="+boostEstimate+" ratio="+ratio+" reqAccel="+reqAccel+" timeBoostStart="+timeBoostStart+" timeBoostStop="+timeBoostStop);
        }

        return isPossible;

    }

    @Override
    public CarData simulate(CarData car) {
        CarData carCopy = new CarData(car);
        AerialManeuver fakeAerial = new AerialManeuver();
        fakeAerial.target = this.target;
        fakeAerial.arrivalTime = this.arrivalTime;
        fakeAerial.target_orientation = this.target_orientation;
        fakeAerial.jumping = carCopy.hasWheelContact;
        FoolGameData foolGameData = GameData.current().fool();

        float dt = 0.01666f;
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
