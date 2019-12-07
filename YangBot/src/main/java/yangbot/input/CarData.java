package yangbot.input;


import com.google.flatbuffers.FlatBufferBuilder;
import rlbot.flat.Physics;
import rlbot.flat.Rotator;
import yangbot.cpp.FBSCarData;
import yangbot.manuever.AerialManeuver;
import yangbot.manuever.DodgeManeuver;
import yangbot.manuever.DriveManeuver;
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

    public static final float velocity_max = 2300.0f;
    public static final float angularVelocity_max = 5.5f;
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
    public final YangHitbox hitbox;
    private ControlsOutput controls = new ControlsOutput();
    private float jump_timer = -1.0f;
    public boolean doubleJumped;
    private float dodge_timer = -1.0f;
    private boolean enable_jump_acceleration = false;
    private Vector2 dodgeDir;
    private Vector3 dodgeTorque;
    public boolean jumped;
    public final boolean isBot;
    public final String name;
    public final String strippedName;
    public final int playerIndex;

    public CarData(rlbot.flat.PlayerInfo playerInfo, float elapsedSeconds, int index) {
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
        this.hitbox = new YangHitbox(playerInfo.hitbox(), this.orientationMatrix);
        this.isBot = playerInfo.isBot();
        this.name = playerInfo.name();
        this.playerIndex = index;
        this.doubleJumped = playerInfo.doubleJumped();
        this.jumped = playerInfo.jumped();

        if (isBot && name.endsWith("(" + (playerIndex + 1) + ")"))
            strippedName = name.substring(0, name.length() - 3).toLowerCase();
        else
            strippedName = name.toLowerCase();

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
        this.isBot = o.isBot;
        this.name = o.name;
        this.playerIndex = o.playerIndex;
        this.strippedName = o.strippedName;
        this.jumped = o.jumped;
        this.doubleJumped = o.doubleJumped;
    }

    public void apply(FlatBufferBuilder builder) {
        FBSCarData.addAngularVelocity(builder, this.angularVelocity.toYangbuffer(builder));
        FBSCarData.addElapsedSeconds(builder, this.elapsedSeconds);
        FBSCarData.addEulerRotation(builder, this.orientationMatrix.toEuler().toYangbuffer(builder));
        FBSCarData.addOnGround(builder, this.hasWheelContact);
        FBSCarData.addVelocity(builder, this.velocity.toYangbuffer(builder));
        FBSCarData.addPosition(builder, this.position.toYangbuffer(builder));
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

    private float driveForceForward(ControlsOutput in, float dt) {
        final float driving_speed = 1450.0f;
        final float braking_force = -3500.0f;
        final float coasting_force = -525.0f;
        final float throttle_threshold = 0.05f;
        final float throttle_force = 1550.0f;
        final float max_speed = 2275.0f;
        final float min_speed = 10.0f;
        final float boost_force = 1500.0f;
        final float steering_torque = 25.75f;
        final float braking_threshold = -0.001f;
        final float supersonic_turn_drag = -98.25f;

        final float v_f = (float) velocity.dot(forward());
        final float v_l = (float) velocity.dot(left());
        final float w_u = (float) angularVelocity.dot(up());

        final float dir = Math.signum(v_f);
        final float speed = Math.abs(v_f);

        final float turn_damping = (-0.07186693033945346f * Math.abs(in.getSteer()) + -0.05545323728191764f * Math.abs(w_u) + 0.00062552963716722f * Math.abs(v_l)) * v_f;

        if (in.holdBoost()) {
            if (v_f < 0.0f)
                return -braking_force;
            else {
                if (v_f < driving_speed)
                    return max_speed - v_f;
                else {
                    if (v_f < max_speed)
                        return AerialManeuver.boost_acceleration + turn_damping;
                    else
                        return supersonic_turn_drag * Math.abs(w_u);
                }
            }
        } else { // Not boosting
            if ((in.getThrottle() * Math.signum(v_f) <= braking_threshold) &&
                    Math.abs(v_f) > min_speed) {
                return braking_force * Math.signum(v_f);
                // not braking
            } else {
                // coasting
                if (Math.abs(in.getThrottle()) < throttle_threshold && Math.abs(v_f) > min_speed) {
                    return coasting_force * Math.signum(v_f) + turn_damping;
                    // accelerating
                } else {
                    if (Math.abs(v_f) > driving_speed) {
                        return turn_damping;
                    } else {
                        return in.getThrottle() * (throttle_force - Math.abs(v_f)) + turn_damping;
                    }
                }
            }
        }
    }

    private float driveForceLeft(ControlsOutput in, float dt) {
        final float v_f = (float) velocity.dot(forward());
        final float v_l = (float) velocity.dot(left());
        final float w_u = (float) angularVelocity.dot(up());

        return (float) ((1380.4531378f * in.getSteer() + 7.8281188f * in.getThrottle() -
                15.0064029f * v_l + 668.1208332f * w_u) *
                (1.0f - Math.exp(-0.001161f * Math.abs(v_f))));
    }

    private float driveTorqueUp(ControlsOutput in, float dt) {
        final float v_f = (float) velocity.dot(forward());
        final float w_u = (float) angularVelocity.dot(up());

        return 15.0f * (in.getSteer() * DriveManeuver.maxTurningCurvature(Math.abs(v_f)) * v_f - w_u);
    }

    private void driving(ControlsOutput in, float dt) {
        Vector3 force = forward().mul(driveForceForward(in, dt)).add(left().mul(driveForceLeft(in, dt)));

        Vector3 torque = up().mul(driveTorqueUp(in, dt));

        velocity = velocity.add(force.mul(dt));
        position = position.add(velocity.mul(dt));

        angularVelocity = angularVelocity.add(torque.mul(dt));
        orientationMatrix = Matrix3x3.axisToRotation(angularVelocity.mul(dt)).dot(orientationMatrix);
    }

    private void driving_handbrake(ControlsOutput in, float dt) {
        // TODO
    }

    private void jump(ControlsOutput in, float dt) {
        this.velocity = this.velocity.add(
                GameData.current().getGravity()
                        .mul(dt)
                        .add(this.up().mul(DodgeManeuver.speed))
        );
        this.position = this.position.add(this.velocity.mul(dt));

        this.orientationMatrix = Matrix3x3.axisToRotation(this.angularVelocity.mul(dt))
                .dot(this.orientationMatrix);

        this.jump_timer = 0.0f;
        this.jumped = true;
        this.doubleJumped = false;
        this.enable_jump_acceleration = true;
        this.hasWheelContact = false;
    }

    private void air_dodge(ControlsOutput in, float dt) {
        if (Math.abs(in.getPitch()) + Math.abs(in.getRoll()) + Math.abs(in.getYaw()) >= DodgeManeuver.input_threshold) {
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

            this.doubleJumped = true;
            this.dodge_timer = 0.0f;
        } else {
            // double jump
            dodgeTorque = new Vector3(0, 0, 0);

            this.velocity = this.velocity.add(GameData.current().getGravity().mul(dt)).add(this.up().mul(DodgeManeuver.speed));
            this.position = this.position.add(this.velocity.mul(dt));

            this.angularVelocity = this.angularVelocity.add(dodgeTorque.mul(dt));
            this.orientationMatrix = Matrix3x3.axisToRotation(this.angularVelocity.mul(dt)).dot(this.orientationMatrix);

            this.doubleJumped = true;
            this.dodge_timer = 1.01f * DodgeManeuver.torque_time;
        }
    }

    private void aerial_control(ControlsOutput in, float dt) {
        final float J = 10.5f;

        final Vector3 T = new Vector3(-400.f, -130.f, 95.f);
        final Vector3 H = new Vector3(-50.f, -30.f * (1.f - Math.abs(in.getPitch())), -20.f * (1.0f - Math.abs(in.getYaw())));

        Vector3 rpy = new Vector3(in.getRoll(), in.getPitch(), in.getYaw());
        if (in.holdBoost() && boost > 0) {
            this.velocity = this.velocity.add(this.forward().mul((AerialManeuver.boost_acceleration + AerialManeuver.throttle_acceleration) * dt));
            boost--;
        } else {
            this.velocity = this.velocity.add(this.forward().mul(in.getThrottle() * AerialManeuver.throttle_acceleration * dt));
        }

        if (in.holdJump() && enable_jump_acceleration) {
            if (jump_timer < DodgeManeuver.min_duration) {
                this.velocity = this.velocity.add(
                        (
                                this.up()
                                        .mul(0.75f * DodgeManeuver.acceleration)
                                        .sub(this.forward().mul(510.0f))
                        ).mul(dt)
                );
            } else {
                this.velocity = this.velocity.add(this.up().mul(DodgeManeuver.acceleration * dt));
            }
        }

        if (dodge_timer >= DodgeManeuver.z_damping_start && (this.velocity.z < 0.0f || dodge_timer < DodgeManeuver.z_damping_end)) {
            this.velocity = this.velocity.add(new Vector3(0.0f, 0.0f, -this.velocity.z * DodgeManeuver.z_damping));
        }

        if (0.0f <= dodge_timer && dodge_timer <= 0.3f) {
            rpy = new Vector3(rpy.x, 0, rpy.z);
        }

        if (0.0f <= dodge_timer && dodge_timer <= DodgeManeuver.torque_time) {
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
                    //driving(in, dt);
                } else {
                    //driving_handbrake(in, dt);
                }
            }
        } else { // In the Air
            if (
                    in.holdJump() &&
                            !this.controls.holdJump() &&
                            this.jump_timer < DodgeManeuver.timeout &&
                            !this.doubleJumped
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
            if (this.dodge_timer >= DodgeManeuver.torque_time || this.hasWheelContact) {
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

        if (!in.holdJump() || this.jump_timer > DodgeManeuver.max_duration) {
            enable_jump_acceleration = false;
        }

        this.controls.set(in);
    }
}
