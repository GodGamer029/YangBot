package yangbot.manuever;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.GameData;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

public class DodgeManeuver extends Maneuver {

    public static final float startTimeout = 1.25f;
    public static final float max_duration = 0.2f;
    public static final float min_duration = 0.025f;
    public static final float speed = 291.667f;
    public static final float acceleration = 1458.3333f;

    public static final float timeout = 1.5f;
    public static final float input_threshold = 0.5f;

    public static final float z_damping = 0.35f;
    public static final float z_damping_start = 0.15f;
    public static final float z_damping_end = 0.21f;

    public static final float torque_time = 0.65f;
    public static final float side_torque = 260.0f;
    public static final float forward_torque = 224.0f;

    public float duration = -1;
    public float timer = 0;
    public float delay = -1;
    public Vector3 target = null;
    public Vector2 direction = null;
    public Vector2 controllerInput = null;

    public boolean enablePreorient = false;
    public Matrix3x3 preorientOrientation = null;
    private TurnManeuver turnManeuver = null;

    public DodgeManeuver() {
        this.turnManeuver = new TurnManeuver();
    }

    public DodgeManeuver(final DodgeManeuver dodge) {
        this.turnManeuver = new TurnManeuver();
        this.duration = dodge.duration;
        this.timer = dodge.timer;
        this.delay = dodge.delay;
        this.target = dodge.target;
        this.direction = dodge.direction;
        this.controllerInput = dodge.controllerInput;
        this.enablePreorient = dodge.enablePreorient;
        this.preorientOrientation = dodge.preorientOrientation;
    }

    @Override
    public boolean isViable() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void step(float dt, ControlsOutput controlsOutput) {
        final GameData gameData = this.getGameData();
        final CarData car = gameData.getCarData();

        float timeout = 0.9f;

        if (duration >= 0 && timer <= duration) {
            controlsOutput.withJump(true);
        }

        float dodge_time = 0;
        if (duration <= 0 && delay <= 0)
            throw new IllegalArgumentException("invalid dodge parameters");
        if (duration <= 0 && delay > 0)
            dodge_time = delay;
        if (duration > 0 && delay <= 0)
            dodge_time = duration + 2.0f * dt;
        if (duration > 0 && delay > 0)
            dodge_time = delay;

        if (timer >= dodge_time && !car.doubleJumped) {
            Vector2 direction_local = null;

            if ((target == null && direction == null) || (target != null && direction != null))
                direction_local = new Vector2();

            if (target != null && direction == null)
                direction_local = target.sub(car.position)
                        .normalized()
                        .flatten()
                        .dot(car.getDodgeOrientation());

            if (target == null && direction != null)
                direction_local = direction.normalized().dot(car.getDodgeOrientation());

            if (direction_local.magnitude() > 0.0f) {
                float vf = (float) car.velocity.dot(car.forward());
                float s = Math.abs(vf) / CarData.MAX_VELOCITY;

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
            } else {
                System.err.println("Dodge maneuver has no direction nor target");
            }
            controlsOutput.withJump(true);
        } else if (!car.isGrounded() && !car.doubleJumped) {
            if (this.enablePreorient) {
                this.turnManeuver.fool(gameData);
                this.turnManeuver.target = this.preorientOrientation;
                this.turnManeuver.step(dt, controlsOutput);
            }
        }

        if (car.doubleJumped)
            controlsOutput.withJump(false);

        if (car.doubleJumped && timer - dodge_time > 0.2f)
            this.setDone();
        else if (this.timer > timeout)
            this.setDone();
        this.timer += dt;
    }

    @Override
    public CarData simulate(CarData car) {
        throw new IllegalStateException("Not implemented");
    }
}
