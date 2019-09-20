package yangbot.manuever;

import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.util.ControlsOutput;
import yangbot.util.MathUtils;
import yangbot.vector.Vector3;

public class DriveManuver extends Manuver {

    public static float max_speed = 2300f;
    public static float min_speed = 10f;
    public static float max_throttle_speed  = 1410.0f;
    public static float boost_accel  = 991.667f;
    public static float brake_accel = 3500.0f;
    public static float coasting_accel  = 525.0f;

    public Vector3 target = null;
    public float speed = 1400f;
    public static float reaction_time = 0.04f;

    @Override
    public boolean isViable() {
        return false;
    }

    public static float maxTurningSpeed(float curvature){
        final int n = 6;

        float[][] values = {
                {0.00088f, 2300.0f},
                {0.00110f, 1750.0f},
                {0.00138f, 1500.0f},
                {0.00235f, 1000.0f},
                {0.00398f,  500.0f},
                {0.00690f,    0.0f}
        };

        float input = MathUtils.clip(Math.abs(curvature), values[0][0], values[n - 1][0]);

        for (int i = 0; i < (n - 1); i++) {
            if (values[i][0] <= input && input <= values[i + 1][0]) {
                float u = (input - values[i][0]) / (values[i + 1][0] - values[i][0]);
                return MathUtils.clip(MathUtils.lerp(values[i][1], values[i + 1][1], u), 0.0f, 2300.0f);
            }
        }

        return -1.0f;
    }

    public static float throttle_accel(float v){
        final int n = 3;
        float[][] values = {
                { 0f, 1600f},
                { 1400f, 160f},
                { 1410f, 0f}
        };

        float input = Math.max(0, Math.min(1410, Math.abs(v)));

        for (int i = 0; i < (n - 1); i++) {
            if (values[i][0] <= input && input < values[i + 1][0]) {
                float u = (input - values[i][0]) / (values[i + 1][0] - values[i][0]);
                return MathUtils.lerp(values[i][1], values[i + 1][1], u);
            }
        }

        return -1.0f;
    }

    private void steer_controller(float dt, ControlsOutput output, CarData car){
        Vector3 target_local = target.sub(car.position).dot(car.orientationMatrix);

        float angle = (float) Math.atan2(target_local.y, target_local.x);
        output.withSteer(MathUtils.clip(3.0f * angle * Math.signum(this.speed), -1f, 1f));
    }

    private void speed_controller(float dt, ControlsOutput output, CarData car){
        float vf = (float) car.velocity.dot(car.forward());

        float acceleration = (speed - vf) / reaction_time;

        float brake_coast_transition = -(0.45f * brake_accel + 0.55f * coasting_accel);
        float coasting_throttle_transition = -0.5f * coasting_accel;
        float throttle_boost_transition = 1.0f * throttle_accel(vf) + 0.5f * boost_accel;

        if(car.up().z < 0.7f){
            brake_coast_transition = coasting_throttle_transition = -0.5f * brake_accel;
        }

        if(acceleration <= brake_coast_transition){
            output.withThrottle(-1.0f);
            output.withBoost(false);
        }else if((brake_coast_transition < acceleration) && (acceleration < coasting_throttle_transition)){
            output.withThrottle(0);
            output.withBoost(false);
        }else if((coasting_throttle_transition <= acceleration) && (acceleration <= throttle_boost_transition)){
            output.withThrottle(Math.max(0.001f, acceleration / throttle_accel(vf)));
            output.withBoost(false);
        }else if(throttle_boost_transition < acceleration){
            output.withThrottle(1.0f);
            output.withBoost(true);
        }

    }

    @Override
    public void step(float dt, ControlsOutput controlsOutput) {
        final GameData gameData = this.getGameData();
        final CarData car = gameData.getCarData();

        steer_controller(dt, controlsOutput, car);
        speed_controller(dt, controlsOutput, car);

        if(car.position.sub(target).magnitude() < 100.f)
            this.setIsDone(true);
    }

    @Override
    public CarData simulate(CarData car) {
        return null;
    }
}
