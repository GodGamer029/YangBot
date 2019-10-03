package yangbot.input;


import rlbot.flat.BoxShape;
import rlbot.flat.Physics;
import rlbot.flat.Rotator;
import yangbot.manuever.AerialManuver;
import yangbot.manuever.DodgeManuver;
import yangbot.util.ControlsOutput;
import yangbot.vector.Matrix2x2;
import yangbot.vector.Matrix3x3;
import yangbot.vector.Vector2;
import yangbot.vector.Vector3;

/**
 * Basic information about the car.
 * <p>
 * This class is here for your convenience, it is NOT part of the framework. You can change it as much
 * as you want, or delete it.
 */
public class CarData {

    public static float velocity_max = 2300.0f;
    public static float angularVelocity_max = 5.5f;
    /**
     * The orientation of the car
     */
    public final CarOrientation orientation;
    public final Physics physics;
    /**
     * True if the car is showing the supersonic and can demolish enemies on contact.
     * This is a close approximation for whether the car is at max speed.
     */
    public final boolean isSupersonic;
    /**
     * 0 for blue team, 1 for orange team.
     */
    public final int team;
    /**
     * The location of the car on the field. (0, 0, 0) is center field.
     */
    public Vector3 position;
    /**
     * The velocity of the car.
     */
    public Vector3 velocity;
    public Vector3 angularVelocity;
    /**
     * Boost ranges from 0 to 100
     */
    public double boost;
    /**
     * True if the car is driving on the ground, the wall, etc. In other words, true if you can steer.
     */
    public boolean hasWheelContact;
    /**
     * This is not really a car-specific attribute, but it's often very useful to know. It's included here
     * so you don't need to pass around DataPacket everywhere.
     */
    public float elapsedSeconds;

    public Matrix3x3 orientationMatrix;
    public Matrix2x2 orientationDodge;
    public boolean double_jumped = false;
    private ControlsOutput controls = new ControlsOutput();
    private float jump_timer = -1.0f;
    private boolean jumped = false;
    private float dodge_timer = -1.0f;
    private boolean enable_jump_acceleration = false;
    private Vector3 gravity1 = new Vector3(0, 0, -650);
    private Vector2 dodgeDir;
    private Vector3 dodgeTorque;
    public final BoxShape hitbox;

    public CarData(rlbot.flat.PlayerInfo playerInfo, float elapsedSeconds) {
        this.position = new Vector3(playerInfo.physics().location());
        this.velocity = new Vector3(playerInfo.physics().velocity());
        this.orientation = CarOrientation.fromFlatbuffer(playerInfo);
        Rotator r = playerInfo.physics().rotation();
        this.orientationMatrix = Matrix3x3.eulerToRotation(new Vector3(r.pitch(), r.yaw(), r.roll()));
        this.orientationDodge = Matrix2x2.fromRotation(playerInfo.physics().rotation().yaw());
        this.boost = playerInfo.boost();
        this.isSupersonic = playerInfo.isSupersonic();
        this.team = playerInfo.team();
        this.hasWheelContact = playerInfo.hasWheelContact();
        this.elapsedSeconds = elapsedSeconds;
        this.physics = playerInfo.physics();
        this.angularVelocity = new Vector3(playerInfo.physics().angularVelocity());
        this.hitbox = playerInfo.hitbox();
    }

    @SuppressWarnings("CopyConstructorMissesField")
    public CarData(CarData o) {
        this.position = new Vector3(o.position);
        this.velocity = new Vector3(o.velocity);
        this.orientation = new CarOrientation(new Vector3(o.orientation.noseVector), new Vector3(o.orientation.roofVector));
        this.orientationMatrix = new Matrix3x3(o.orientationMatrix);
        this.orientationDodge = new Matrix2x2(o.orientationDodge);
        this.boost = o.boost;
        this.isSupersonic = o.isSupersonic;
        this.team = o.team;
        this.hasWheelContact = o.hasWheelContact;
        this.elapsedSeconds = o.elapsedSeconds;
        this.physics = null;
        this.angularVelocity = new Vector3(o.angularVelocity);
        this.hitbox = o.hitbox;
    }

    public Vector3 up() {
        return new Vector3(this.orientationMatrix.get(0, 2), this.orientationMatrix.get(1, 2), this.orientationMatrix.get(2, 2));
    }

    public Vector3 forward() {
        return new Vector3(this.orientationMatrix.get(0, 0), this.orientationMatrix.get(1, 0), this.orientationMatrix.get(2, 0));
    }

    public Vector3 left() {
        return new Vector3(this.orientationMatrix.get(0, 1), this.orientationMatrix.get(1, 1), this.orientationMatrix.get(2, 1));
    }

    private void driving(ControlsOutput in, float dt) {
        // TODO
    }

    private void driving_handbrake(ControlsOutput in, float dt) {
        // TODO
    }

    private void jump(ControlsOutput in, float dt) {
        this.velocity = this.velocity.add(
                GameData.current().getGravity()
                        .mul(dt)
                        .add(this.up().mul(DodgeManuver.speed))
        );
        this.position = this.position.add(this.velocity.mul(dt));

        this.orientationMatrix = Matrix3x3.axisToRotation(this.angularVelocity.mul(dt))
                .dot(this.orientationMatrix);

        this.jump_timer = 0.0f;
        this.jumped = true;
        this.double_jumped = false;
        this.enable_jump_acceleration = true;
        this.hasWheelContact = false;
    }

    private void air_dodge(ControlsOutput in, float dt) {
        if (Math.abs(in.getPitch()) + Math.abs(in.getRoll()) + Math.abs(in.getYaw()) >= DodgeManuver.input_threshold) {
            // directional dodge

            float vf = (float) this.velocity.dot(this.forward());
            float s = Math.abs(vf) / CarData.velocity_max;

            this.dodgeDir = new Vector2(-in.getPitch(), in.getYaw()).normalized();

            Vector3 dodge_torque_local = new Vector3(new Vector2(this.dodgeDir.x * 224.0f, this.dodgeDir.y * 260.0f).cross());
            this.dodgeTorque = this.orientationMatrix.dot(dodge_torque_local);

            if (Math.abs(this.dodgeDir.x) < 0.1f) this.dodgeDir = new Vector2(0.0f, this.dodgeDir.y);
            if (Math.abs(this.dodgeDir.y) < 0.1f) this.dodgeDir = new Vector2(this.dodgeDir.x, 0.0f);

            boolean backward_dodge;
            if (Math.abs(vf) < 100.0f) {
                backward_dodge = this.dodgeDir.x < 0.0f;
            } else {
                backward_dodge = (this.dodgeDir.x >= 0.0f) != (vf > 0.0f);
            }

            Vector2 dv = this.dodgeDir.mul(500.0f);

            if (backward_dodge) {
                dv.x += (16.0f / 15.0f) * (1.0f + 1.5f * s);
            }
            dv.y *= (1.0f + 0.9f * s);

            this.velocity = this.velocity.add(GameData.current().getGravity().mul(dt)).add(new Vector3(orientationDodge.dot(dv)));
            this.position = this.position.add(this.velocity.mul(dt));

            this.angularVelocity = this.angularVelocity.add(dodgeTorque.mul(dt));
            this.orientationMatrix = Matrix3x3.axisToRotation(this.angularVelocity.mul(dt)).dot(this.orientationMatrix);

            this.double_jumped = true;
            this.dodge_timer = 0.0f;
        } else {
            // double jump
            dodgeTorque = new Vector3(0, 0, 0);

            this.velocity = this.velocity.add(GameData.current().getGravity().mul(dt)).add(this.up().mul(DodgeManuver.speed));
            this.position = this.position.add(this.velocity.mul(dt));

            this.angularVelocity = this.angularVelocity.add(dodgeTorque.mul(dt));
            this.orientationMatrix = Matrix3x3.axisToRotation(this.angularVelocity.mul(dt)).dot(this.orientationMatrix);

            this.double_jumped = true;
            this.dodge_timer = 1.01f * DodgeManuver.torque_time;
        }
    }

    private void aerial_control(ControlsOutput in, float dt) {
        final float J = 10.5f;

        final Vector3 T = new Vector3(-400.f, -130.f, 95.f);
        final Vector3 H = new Vector3(-50.f, -30.f * (1.f - Math.abs(in.getPitch())), -20.f * (1.0f - Math.abs(in.getYaw())));

        Vector3 rpy = new Vector3(in.getRoll(), in.getPitch(), in.getYaw());
        if (in.holdBoost() && boost > 0) {
            this.velocity = this.velocity.add(this.forward().mul((AerialManuver.boost_accel + AerialManuver.throttle_accel) * dt));
            boost--;
        } else {
            this.velocity = this.velocity.add(this.forward().mul(in.getThrottle() * AerialManuver.throttle_accel * dt));
        }

        if (in.holdJump() && enable_jump_acceleration) {
            if (jump_timer < DodgeManuver.min_duration) {
                this.velocity = this.velocity.add(
                        (
                                this.up()
                                        .mul(0.75f * DodgeManuver.acceleration)
                                        .sub(this.forward().mul(510.0f))
                        ).mul(dt)
                );
            } else {
                this.velocity = this.velocity.add(this.up().mul(DodgeManuver.acceleration * dt));
            }
        }

        if (dodge_timer >= DodgeManuver.z_damping_start && (this.velocity.z < 0.0f || dodge_timer < DodgeManuver.z_damping_end)) {
            this.velocity = this.velocity.add(new Vector3(0.0f, 0.0f, -this.velocity.z * DodgeManuver.z_damping));
        }

        if (0.0f <= dodge_timer && dodge_timer <= 0.3f) {
            rpy = new Vector3(rpy.x, 0, rpy.z);
        }

        if (0.0f <= dodge_timer && dodge_timer <= DodgeManuver.torque_time) {
            this.angularVelocity = this.angularVelocity.add(dodgeTorque.mul(dt));
        } else {
            Vector3 w_local = this.angularVelocity.dot(this.orientationMatrix);
            this.angularVelocity = this.angularVelocity.add(
                    this.orientationMatrix
                            .dot(T
                                    .mul(rpy)
                                    .add(H.mul(w_local))
                            )
                            .mul(dt / J)
            );
        }
        this.velocity = this.velocity.add(GameData.current().getGravity().mul(dt));
        this.position = this.position.add(this.velocity.mul(dt));
        this.orientationMatrix = Matrix3x3.axisToRotation(this.angularVelocity.mul(dt)).dot(this.orientationMatrix);
    }

    public void step(ControlsOutput in, float dt) {
        if (this.hasWheelContact) { // On Ground
            if (in.holdJump()) {
                jump(in, dt);
            } else {
                if (!in.holdHandbrake()) {
                    driving(in, dt);
                } else {
                    driving_handbrake(in, dt);
                }
            }
        } else { // In the Air
            if (
                    in.holdJump() &&
                            !this.controls.holdJump() &&
                            this.jump_timer < DodgeManuver.timeout &&
                            !this.double_jumped
            ) {
                air_dodge(in, dt);
            } else {
                aerial_control(in, dt);
            }
        }
        // if the velocities exceed their maximum values, scale them back
        this.velocity.div(Math.max(1.0f, this.velocity.magnitude() / CarData.velocity_max));
        this.angularVelocity.div(Math.max(1.0f, this.angularVelocity.magnitude() / CarData.angularVelocity_max));

        this.elapsedSeconds += dt;

        if (this.dodge_timer >= 0.0f) {
            if (this.dodge_timer >= DodgeManuver.torque_time || this.hasWheelContact) {
                this.dodge_timer = -1.0f;
            } else {
                this.dodge_timer += dt;
            }
        }

        if (this.jump_timer >= 0.0f) {
            if (this.hasWheelContact) {
                this.jump_timer = -1.0f;
            } else {
                this.jump_timer += dt;
            }
        }

        if (!in.holdJump() || this.jump_timer > DodgeManuver.max_duration) {
            enable_jump_acceleration = false;
        }

        this.controls.set(in);
    }
}
