package yangbot.manuever;

import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.util.ControlsOutput;
import yangbot.vector.Matrix3x3;
import yangbot.vector.Vector3;

public class AerialManuver extends Manuver{

    public static float boost_accel = 1060.0f;
    public static float throttle_accel = 66.66667f;

    public float arrivalTime = 0.0f;

    private boolean jumping = false;
    private DodgeManuver doubleJump;
    private TurnManuver turnManuver;
    public Vector3 target = null;
    public Matrix3x3 target_orientation = null;

    public AerialManuver(){
        this.turnManuver = new TurnManuver();
        this.doubleJump = new DodgeManuver();
        this.doubleJump.duration = 0.20f;
        this.doubleJump.delay = 0.25f;
    }

    @Override
    public boolean isViable() {
        return false;
    }

    @Override
    public void step(float dt, ControlsOutput controlsOutput) {
        final float j_speed = DodgeManuver.speed;
        final float j_accel = DodgeManuver.acceleration;
        final float j_duration = DodgeManuver.max_duration;

        final float reorient_distance = 50.0f;
        final float angle_threshold = 0.3f;
        final float throttle_distance = 50.0f;

        final GameData gameData = this.getGameData();
        final Vector3 gravity = gameData.getGravity();
        final CarData car = gameData.getCarData();
        final BallData ball = gameData.getBallData();

        float T = this.arrivalTime - car.elapsedSeconds;

        Vector3 xf = car.position
                .add(car.velocity.mul(T))
                .add(gravity.mul(T * T * 0.5f));
        Vector3 vf = car.velocity.add(gravity.mul(T));

        boolean jumping_prev = this.jumping;
        if(this.jumping){
            float tau = j_duration - doubleJump.timer;

            if(doubleJump.timer == 0.0f){
                vf = vf.add(car.up().mul(j_speed));
                xf = xf.add(car.up().mul(j_speed * T));
            }

            vf = vf.add(car.up().mul(j_accel * tau));
            xf = xf.add(car.up().mul(j_accel * tau * (T - 0.5f * tau)));

            vf = vf.add(car.up().mul(j_speed));
            xf = xf.add(car.up().mul(j_speed * (T - tau)));

            ControlsOutput output = new ControlsOutput();
            doubleJump.step(dt, output);
            controlsOutput.withJump(output.holdJump());

            if(doubleJump.timer >= doubleJump.delay)
                jumping = false;
        }else
            controlsOutput.withJump(false);

        Vector3 delta_x = target.sub(xf);
        Vector3 direction = delta_x.normalized();

        if(delta_x.magnitude() > reorient_distance)
            this.turnManuver.target = Matrix3x3.lookAt(delta_x, new Vector3(0, 0, 1));
        else{
            if(Math.abs(target_orientation.det() - 1f) < 0.01f){
                this.turnManuver.target = Matrix3x3.lookAt(target.sub(car.position), new Vector3(0, 0, 1));
            }else{
                this.turnManuver.target = target_orientation;
            }
        }

        turnManuver.step(dt, null);

        if(jumping_prev && !jumping){
            controlsOutput.withRoll(0);
            controlsOutput.withPitch(0);
            controlsOutput.withYaw(0);
        }else{
            controlsOutput.withRoll(this.turnManuver.controls.getRoll());
            controlsOutput.withPitch(this.turnManuver.controls.getPitch());
            controlsOutput.withYaw(this.turnManuver.controls.getYaw());
        }

        if(car.forward().angle(direction) < angle_threshold){
            if(delta_x.magnitude() > throttle_distance){
                controlsOutput.withBoost(true);
                controlsOutput.withThrottle(0);
            }else{
                controlsOutput.withBoost(false);
                controlsOutput.withThrottle(0.5f * throttle_accel * T * T);
            }
        }else{
            controlsOutput.withBoost(false);
            controlsOutput.withThrottle(0);
        }

        this.setIsDone(T <= 0);
    }

    @Override
    public CarData simulate(CarData car) {
        CarData carCopy = new CarData(car);
        AerialManuver copy = new AerialManuver();
        copy.target = new Vector3(this.target);
        copy.arrivalTime = this.arrivalTime;
        copy.target_orientation = this.target_orientation;

        float dt = 0.01666f;
        for(float t = dt; t < 6.0f; t += dt){
            ControlsOutput output = new ControlsOutput();
            copy.step(dt, output);
            carCopy.step(output, dt);

            if(copy.isDone())
                break;
        }

        return carCopy;
    }
}
