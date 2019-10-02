package yangbot.manuever;

import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.util.ControlsOutput;
import yangbot.vector.Vector2;
import yangbot.vector.Vector3;

public class DodgeManuver extends Manuver {

    public static float max_duration = 0.2f;
    public static float min_duration = 0.025f;
    public static float speed = 291.667f;
    public static float acceleration = 1458.3333f;

    public static float timeout = 1.5f;
    public static float input_threshold = 0.5f;

    public static float z_damping = 0.35f;
    public static float z_damping_start = 0.15f;
    public static float z_damping_end = 0.21f;

    public static float torque_time = 0.65f;
    public static float side_torque = 260.0f;
    public static float forward_torque = 224.0f;

    public float duration = -1;
    public float timer = 0;
    public float delay = -1;
    public Vector3 target = null;
    public Vector2 direction = null;
    public Vector2 controllerInput = null;

    @Override
    public boolean isViable() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void step(float dt, ControlsOutput controlsOutput) {
        final GameData gameData = this.getGameData();
        final Vector3 gravity = gameData.getGravity();
        final CarData car = gameData.getCarData();
        final BallData ball = gameData.getBallData();

        float timeout = 0.9f;

        if (duration >= 0 && timer <= duration)
            controlsOutput.withJump(true);

        float dodge_time = 0;
        if (duration <= 0 && delay <= 0)
            throw new IllegalArgumentException("invalid dodge parameters");
        if (duration <= 0 && delay > 0)
            dodge_time = delay;
        if (duration > 0 && delay <= 0)
            dodge_time = duration + 2.0f * dt;
        if (duration > 0 && delay > 0)
            dodge_time = delay;

        if (timer >= dodge_time && !car.double_jumped && !car.hasWheelContact) {
            Vector2 direction_local = null;

            if ((target == null && direction == null) || (target != null && direction != null))
                direction_local = new Vector2();

            if (target != null && direction == null)
                direction_local = target.sub(car.position)
                        .normalized()
                        .flatten()
                        .dot(car.orientationDodge);

            if (target == null && direction != null)
                direction_local = direction.normalized().dot(car.orientationDodge);

            if (direction_local.magnitude() > 0.0f) {
                float vf = (float) car.velocity.dot(car.forward());
                float s = Math.abs(vf) / CarData.velocity_max;

                boolean backward_dodge;

                if (Math.abs(vf) < 100.f)
                    backward_dodge = (direction_local.x < 0);
                else
                    backward_dodge = (direction_local.x >= 0) != (vf > 0);

                float x = direction_local.x / ((backward_dodge) ? (16.0f / 15.0f) * (1.0f + 1.5f * s) : 1.0f);
                float y = direction_local.y / (1.0f + 0.9f * s);
                direction_local = new Vector2(x, y).normalized();

                controlsOutput.withRoll(0);
                controlsOutput.withPitch(-direction_local.x);
                controlsOutput.withYaw(direction_local.y);

            } else if (controllerInput != null && !controllerInput.isZero()) {
                controlsOutput.withRoll(0);
                controlsOutput.withPitch(controllerInput.y);
                controlsOutput.withYaw(controllerInput.x);
            }
            controlsOutput.withJump(true);
        }

        if (car.double_jumped)
            controlsOutput.withJump(false);

        //if((timer < dodge_time) && preorientation)

        setIsDone(this.timer > timeout);
        this.timer += dt;
    }

    @Override
    public CarData simulate(CarData car) {
        throw new IllegalStateException("Not implemented");
    }
}