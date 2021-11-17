package yangbot.strategy.manuever;

import yangbot.input.*;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector3;

import java.util.Objects;

public class AerialManeuver extends Maneuver {

    public static final float throttle_acceleration = 66.66667f;
    public static final float boost_airthrottle_acceleration = DriveManeuver.boost_acceleration + throttle_acceleration;

    public float arrivalTime = 0.0f;
    public Vector3 target = null;

    private Matrix3x3 target_orientation = null;
    public boolean jumping = true;
    public final DodgeManeuver doubleJump;
    private final TurnManeuver turnManuver;
    private float boostTime = 0;
    public float timeNeededForLastMinuteReorient = -1;
    private float timeoutComputeLastMinuteReorient = 0f;
    public boolean doDoubleJump = true;

    private static final float j_speed = DodgeManeuver.speed;
    private static final float j_acceleration = DodgeManeuver.acceleration;
    private static final float j_duration = DodgeManeuver.max_duration;
    private static final float angle_threshold = (float)Math.PI * 0.075f;

    public void setTarget_orientation(Matrix3x3 target_orientation) {
        this.target_orientation = target_orientation;
        this.timeNeededForLastMinuteReorient = -1;
    }

    public AerialManeuver() {
        this.turnManuver = new TurnManeuver();
        this.doubleJump = new DodgeManeuver();
        this.doubleJump.duration = this.doubleJump.delay = 0.20f;
    }

    public static Vector3 getDeltaX(CarData car, Vector3 target, float arrivalTime) {
        return getDeltaX(car, target, arrivalTime, -1, true);
    }

    public static Vector3 getDeltaX(CarData car, Vector3 target, float arrivalTime, float jdur, boolean doDoubleJump) {
        // jdur = remaining jump accel time
        final Vector3 gravity = GameData.current().getGravity();

        float T = arrivalTime - car.elapsedSeconds;

        Vector3 xf = car.position
                .add(car.velocity.mul(T))
                .add(gravity.mul(T * T * 0.5f));

        if (car.hasWheelContact || jdur > 0) {
            if (jdur == -1 || car.hasWheelContact)
                jdur = j_duration;
            float tau = MathUtils.clip(jdur, 0, j_duration);

            if (jdur == j_duration) // full acceleration still available, we are still on ground
                xf = xf.add(car.up().mul(j_speed * T));
            xf = xf.add(car.up().mul(j_acceleration * tau * (T - 0.5f * tau)));
            if(doDoubleJump)
                xf = xf.add(car.up().mul(j_speed * (T - tau)));
        } else if (!car.doubleJumped && doDoubleJump) {
            xf = xf.add(car.up().mul(j_speed * (T)));
        }
        return target.sub(xf);
    }

    @Override
    public void step(float dt, ControlsOutput controlsOutput) {
        final float reorient_distance = 50.0f;

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
            this.doubleJump.delay = this.doDoubleJump ? this.doubleJump.duration : 0;
            float tau = j_duration - doubleJump.timer;

            if (doubleJump.timer == 0.0f) {
                //vf = vf.add(car.up().mul(j_speed));
                xf = xf.add(car.up().mul(j_speed * T));
            }

            xf = xf.add(car.up().mul(j_acceleration * tau * (T - 0.5f * tau)));

            if(this.doDoubleJump)
                xf = xf.add(car.up().mul(j_speed * (T - tau)));

            ControlsOutput output = new ControlsOutput();
            doubleJump.step(dt, output);
            controlsOutput.withJump(output.holdJump());
            if (doubleJump.isDone())
                jumping = false;
        } else
            controlsOutput.withJump(false);

        Vector3 delta_x = target.sub(xf);
        Vector3 direction = delta_x.normalized();
        if(this.arrivalTime - car.elapsedSeconds < 1.5f){
            this.timeoutComputeLastMinuteReorient -= dt;
            if((this.timeoutComputeLastMinuteReorient < 0 || this.timeNeededForLastMinuteReorient < 0) && this.target_orientation != null){
                var simTurn = new TurnManeuver();
                simTurn.target = this.target_orientation;
                simTurn.maxErrorOrientation = 0.1f;
                simTurn.maxErrorAngularVelocity = 0.15f;
                this.timeNeededForLastMinuteReorient = MathUtils.clip(simTurn.simulate(car).elapsedSeconds + 0.05f, 0.2f, 1.5f);
                if(this.arrivalTime - car.elapsedSeconds - this.timeNeededForLastMinuteReorient < 0.3f)
                    this.timeoutComputeLastMinuteReorient = 0.05f;
                else
                    this.timeoutComputeLastMinuteReorient = 0.1f;
            }
        }

        float reorientUntil = 0.6f;
        if(this.timeNeededForLastMinuteReorient >= 0)
            reorientUntil = this.timeNeededForLastMinuteReorient;

        if (delta_x.magnitude() > reorient_distance || (this.arrivalTime - car.elapsedSeconds > reorientUntil && delta_x.magnitude() > 3))
            this.turnManuver.target = Matrix3x3.lookAt(direction, this.jumping ? new Vector3(0, 0, 1) : car.up());
        else
            this.turnManuver.target = Objects.requireNonNullElseGet(this.target_orientation,
                    () -> Matrix3x3.lookAt(target.sub(car.position), car.up()));

        if (jumping_prev && !jumping) {
            controlsOutput.withRoll(0);
            controlsOutput.withPitch(0);
            controlsOutput.withYaw(0);
        } else {
            turnManuver.step(dt, controlsOutput);
        }

        if (car.forward().angle(direction) < ((this.jumping ? 2 : 1) * angle_threshold)) {
            float s = delta_x.dot(car.forward());
            float boostDt = Math.max(0, 0.1f - this.boostTime);
            boostDt += RLConstants.tickFrequency * 2;
            float delta_v = s / (T - RLConstants.tickFrequency * 0.5f);
            if (delta_v > AerialManeuver.boost_airthrottle_acceleration * boostDt) {
                controlsOutput.withBoost(true);
                controlsOutput.withThrottle(0);
                boostTime += dt;
            } else {
                boostTime = 0;
                controlsOutput.withBoost(false);
                controlsOutput.withThrottle(MathUtils.clip(delta_v / (2 * throttle_acceleration * dt), -1, 1));
            }
        } else {
            boostTime = 0;
            controlsOutput.withBoost(false);
            controlsOutput.withThrottle(0);
        }

        this.setIsDone(T <= 0);
    }

    public boolean isViable(CarData car, float maxBoostUse) {
        var gravity = GameData.current().getGravity();
        maxBoostUse = Math.min(maxBoostUse, car.boost + 5);
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
            simTurn.target = Matrix3x3.lookAt(dir, car.up());
            simTurn.maxErrorOrientation = 0.2f;
            simTurn.maxErrorAngularVelocity = 1f;
            turnTime = MathUtils.clip(simTurn.simulate(car).elapsedSeconds, 0.05f, 1.5f);
        }

        float timeBoostStart = turnTime + 0.05f;
        if (timeBoostStart > T)
            return false;

        float reqAccel = (2 * (float) deltaX.magnitude()) / ((T - timeBoostStart) * (T - timeBoostStart));
        float ratio = reqAccel / boost_airthrottle_acceleration;

        float timeBoostStop = T - (T - timeBoostStart) * (float) Math.sqrt(1 - MathUtils.clip(ratio, 0, 1));

        var velocityEstimate = vf.add(dir.mul(boost_airthrottle_acceleration * (timeBoostStop - timeBoostStart)));
        float boostEstimate = (timeBoostStop - timeBoostStart) * CarData.BOOST_CONSUMPTION;

        boolean isPossible = velocityEstimate.magnitude() < Math.max(CarData.MAX_VELOCITY - 25, car.velocity.magnitude()) && // max speed
                boostEstimate < maxBoostUse * 0.95f &&                      // boost use
                ratio < 1f;                                               // acceleration possible

        //System.out.printf("aerial conditions isPoss=%s vel=%.1f boost=%.2f/%.2f ratio=%.3f tBoostStart=%.2f tBoostEnd=%.2f deltaMag=%.2f L=%.2f"+System.lineSeparator(),
        //            isPossible, velocityEstimate.magnitude(), boostEstimate, maxBoostUse * 0.95f, ratio, timeBoostStart, timeBoostStop, (float)deltaX.magnitude(), ((T - timeBoostStart) * (T - timeBoostStart)));

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
