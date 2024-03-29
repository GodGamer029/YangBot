package yangbot.input;


import com.google.flatbuffers.FlatBufferBuilder;
import rlbot.flat.Rotator;
import rlbot.gamestate.CarState;
import yangbot.cpp.FlatCarData;
import yangbot.cpp.FlatPhysics;
import yangbot.input.playerinfo.BotInfo;
import yangbot.input.playerinfo.PlayerInfo;
import yangbot.input.playerinfo.PlayerInfoManager;
import yangbot.strategy.manuever.AerialManeuver;
import yangbot.strategy.manuever.DodgeManeuver;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.util.hitbox.YangCarHitbox;
import yangbot.util.math.Car2D;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Matrix2x2;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

/**
 * Basic information about the car.
 * <p>
 * This class is here for your convenience, it is NOT part of the framework. You can change it as much
 * as you want, or delete it.
 */
public class CarData {

    public static final float MAX_VELOCITY = 2300.0f;
    public static final float MAX_ANGULAR_VELOCITY = 5.5f;
    public static final float MASS = 180f;
    public static final Matrix3x3 INERTIA;
    public static final Matrix3x3 INV_INERTIA;
    public static final float BOOST_CONSUMPTION = 33.3f;

    static {
        Matrix3x3 inertia;
        inertia = new Matrix3x3();

        inertia.assign(0, 0, 751f);
        inertia.assign(1, 1, 1334f);
        inertia.assign(2, 2, 1836f);

        inertia = inertia.elementwiseMul(MASS);

        INERTIA = inertia;
        INV_INERTIA = INERTIA.invert();
    }

    public final int team; // 0 for blue team, 1 for orange team.

    public Vector3 position;
    public Vector3 velocity;
    public Vector3 angularVelocity;
    public Matrix3x3 orientation;

    public float boost;
    public boolean hasWheelContact;
    public float elapsedSeconds;

    public final YangWheelInfo wheelInfo = YangWheelInfo.octane();
    public final YangCarHitbox hitbox;
    public boolean doubleJumped;
    public boolean jumped;
    public int goalsScored = 0;

    public final boolean isDemolished;
    public final boolean isBot;
    public final String name;
    public final String strippedName;
    public int playerIndex;
    public ControlsOutput lastControllerInputs = new ControlsOutput();

    // Variables used exclusively in step functions
    private Vector3 dodgeTorque;
    public float jumpTimer = -1.0f;
    public float dodgeTimer = -1.0f;
    public boolean enableJumpAcceleration = false;

    public CarData(rlbot.flat.PlayerInfo playerInfo, float elapsedSeconds, int index) {
        this.position = new Vector3(playerInfo.physics().location());
        this.velocity = new Vector3(playerInfo.physics().velocity());
        Rotator r = playerInfo.physics().rotation();
        this.orientation = Matrix3x3.eulerToRotation(new Vector3(r.pitch(), r.yaw(), r.roll()));
        this.boost = playerInfo.boost();
        this.team = playerInfo.team();
        this.hasWheelContact = playerInfo.hasWheelContact();
        this.elapsedSeconds = elapsedSeconds;
        this.angularVelocity = new Vector3(playerInfo.physics().angularVelocity());
        this.hitbox = new YangCarHitbox(playerInfo.hitbox(), new Vector3(playerInfo.hitboxOffset()), this.orientation);
        this.isBot = playerInfo.isBot();
        this.name = playerInfo.name();
        this.playerIndex = index;
        this.doubleJumped = playerInfo.doubleJumped();
        this.jumped = playerInfo.jumped();
        this.goalsScored = playerInfo.scoreInfo().goals();
        this.isDemolished = playerInfo.isDemolished();

        if (isBot && name.endsWith("(" + (playerIndex + 1) + ")"))
            strippedName = name.substring(0, name.length() - 3).toLowerCase();
        else
            strippedName = name.toLowerCase();
    }

    public CarData(CarData o) {
        this.team = o.team;

        this.position = new Vector3(o.position);
        this.velocity = new Vector3(o.velocity);
        this.angularVelocity = new Vector3(o.angularVelocity);
        this.orientation = new Matrix3x3(o.orientation);

        this.boost = o.boost;
        this.hasWheelContact = o.hasWheelContact;
        this.elapsedSeconds = o.elapsedSeconds;

        this.hitbox = o.hitbox.withOrientation(this.orientation); // Clone Hitbox
        this.doubleJumped = o.doubleJumped;
        this.jumped = o.jumped;

        this.isDemolished = o.isDemolished;
        this.isBot = o.isBot;
        this.name = o.name;
        this.strippedName = o.strippedName;
        this.playerIndex = o.playerIndex;
        this.goalsScored = o.goalsScored;
    }

    public CarData(Vector3 position, Vector3 velocity, Vector3 angularVelocity, Matrix3x3 orientation) {
        this.position = position;
        this.velocity = velocity;
        this.angularVelocity = angularVelocity;
        this.orientation = orientation;

        this.team = 0;
        this.isBot = true;
        this.name = "unknown";
        this.strippedName = this.name;
        this.playerIndex = -1;
        this.hitbox = new YangCarHitbox(this.orientation);
        this.isDemolished = false;
    }

    public CarData(CarState carState) {
        this(new Vector3(carState.getPhysics().getLocation()), new Vector3(carState.getPhysics().getVelocity()), new Vector3(carState.getPhysics().getAngularVelocity()), Matrix3x3.eulerToRotation(new Vector3(carState.getPhysics().getRotation())));
        this.boost = carState.getBoostAmount();
        this.hasWheelContact = this.position.z < 7 + RLConstants.carElevation;
    }

    // https://www.desmos.com/calculator/mplxfpbnej
    public static float getJumpHeightAtTime(float time){
        if(time > 1.73 || time <= 0)
            return 0;
        float g = RLConstants.gravity.z;
        float x = MathUtils.clip(time, 0, 0.025f);
        float z = 0.5f * ((DodgeManeuver.acceleration * 0.75f) + g) * x * x + DodgeManeuver.speed * x;

        if(time <= 0.025f)
            return z;
        x = MathUtils.clip(time, 0.025f, 0.2f) - 0.025f;
        z += 0.5f * (DodgeManeuver.acceleration + g) * x * x + DodgeManeuver.speed * x;
        if(time <= 0.2f)
            return z;
        x = time - 0.2f;
        z += 0.5f * g * x * x + ((DodgeManeuver.acceleration + g) * 0.2f + DodgeManeuver.speed) * x;
        return z;
    }

    // From L0laapk3
    // assumes jump duration = 0.2
    public static float getJumpTimeForHeight(float heightIncrease, float gravity) {
        assert gravity == -650; // This code only works for default gravity
        if (heightIncrease <= 74.48f) { // During acceleration part
            return (float) Math.max(0, Math.sqrt(10100 * heightIncrease + 531441) - 729) / 2020f;
        } else {
            return (float) (DodgeManeuver.acceleration - Math.sqrt(Math.max(0, 1888839f - 8125f * heightIncrease))) / 1625f;
        }
    }

    public static float slowdownForceFromSteering(float steer, float vf){
        steer = Math.abs(steer);
        if(steer < 0.01f)
            return 0;
        float x1 = vf * DriveManeuver.maxTurningCurvature(vf);
        float x2 = vf * x1;
        float predAccel = -36.34783f * x1 -0.09389f * x2 + 41.91971f;
        if(predAccel > 0)
            return 0;
        // Scale according to steer
        float s1 = steer * predAccel;
        float s2 = s1 * steer;
        float s3 = s2 * steer;
        return 0.0146f * s1 + 0.83f * s2 + 0.15f * s3;
    }

    public float slowdownForceFromSteering(float steer) {
        float vF = this.forwardSpeed();
        return CarData.slowdownForceFromSteering(steer, vF);
    }

    // airTime ranges between ~1 to ~1.73
    public static float getJumpHoldDurationForTotalAirTime(float airTime, float gravity) {
        assert gravity > 0;
        float d = (float) -Math.sqrt(625 * gravity * gravity - 3645000 * gravity * (400 * airTime * airTime - 20 * airTime + 1) + 2916 * (729000000 * airTime * airTime + 255217000 * airTime + 1252969)) / 1458000 + gravity / 58320 + airTime - 1 / 40f;

        return MathUtils.clip(d, 0.025f, 0.2f);
    }

    public static float driveTorqueUp(ControlsOutput in, float velocityForward, float angularUp) {

        return 15.0f * (in.getSteer() * DriveManeuver.maxTurningCurvature(Math.abs(velocityForward)) * velocityForward - angularUp);
    }

    public float forwardSpeed() {
        return this.velocity.dot(this.forward());
    }

    public int getTeamSign() {
        return this.team * 2 - 1;
    }

    public PlayerInfo getPlayerInfo() {
        return PlayerInfoManager.getFor(this);
    }

    public boolean isGrounded() {
        if (RLConstants.isPosNearWall(this.position.flatten(), 20) || this.position.z >= RLConstants.arenaHeight - 100)
            return this.hasWheelContact;
        else
            return this.position.z <= RLConstants.carElevation + 3;
    }

    public Matrix2x2 getDodgeOrientation() {
        return Matrix2x2.fromRotation(this.orientation.toEuler().y);
    }

    public int makeFlatPhysics(FlatBufferBuilder builder) {
        FlatPhysics.startFlatPhysics(builder);

        FlatPhysics.addAngularVelocity(builder, this.angularVelocity.toYangbuffer(builder));
        FlatPhysics.addEulerRotation(builder, this.orientation.toEuler().toYangbuffer(builder));
        FlatPhysics.addVelocity(builder, this.velocity.toYangbuffer(builder));
        FlatPhysics.addPosition(builder, this.position.toYangbuffer(builder));
        FlatPhysics.addElapsedSeconds(builder, this.elapsedSeconds);

        return FlatPhysics.endFlatPhysics(builder);
    }

    public int makeFlat(FlatBufferBuilder builder) {
        int physOffset = this.makeFlatPhysics(builder);

        FlatCarData.startFlatCarData(builder);

        FlatCarData.addOnGround(builder, this.hasWheelContact);
        FlatCarData.addPhysics(builder, physOffset);

        return FlatCarData.endFlatCarData(builder);
    }

    public Vector3 up() {
        return this.orientation.up();
    }

    public Vector3 forward() {
        return this.orientation.forward();
    }

    public static float driveForceForward(ControlsOutput in, float velocityForward, float velocityLeft, float angularUp) {
        final float driving_speed = DriveManeuver.max_throttle_speed;
        final float braking_force = -3500.0f;
        final float coasting_force = -525.0f;
        final float throttle_threshold = 0.05f;
        final float max_speed = 2275.0f;
        final float min_speed = 3.0f;
        final float braking_threshold = -0.001f;
        final float supersonic_turn_drag = -98.25f;

        final float turn_damping = (-0.07186693033945346f * Math.abs(in.getSteer()) + -0.05545323728191764f * Math.abs(angularUp) + 0.00062552963716722f * Math.abs(velocityLeft)) * velocityForward;

        if (in.holdBoost()) {
            if (velocityForward < 0.0f)
                return -braking_force;
            else {
                if (velocityForward < driving_speed)
                    return DriveManeuver.boost_acceleration + DriveManeuver.throttleAcceleration(velocityForward) + turn_damping;
                else {
                    if (velocityForward < max_speed)
                        return DriveManeuver.boost_acceleration + turn_damping;
                    else
                        return supersonic_turn_drag * Math.abs(angularUp);
                }
            }
        } else { // Not boosting
            if ((in.getThrottle() * Math.signum(velocityForward) <= braking_threshold) &&
                    Math.abs(velocityForward) > min_speed) {
                return braking_force * Math.signum(velocityForward);
                // not braking
            } else {
                // coasting
                if (Math.abs(in.getThrottle()) < throttle_threshold && Math.abs(velocityForward) > min_speed) {
                    return coasting_force * Math.signum(velocityForward) + turn_damping;
                    // accelerating
                } else {
                    if (Math.abs(velocityForward) > driving_speed) {
                        return turn_damping;
                    } else {
                        return in.getThrottle() * DriveManeuver.throttleAcceleration(velocityForward) + turn_damping;
                    }
                }
            }
        }
    }

    public Physics3D toPhysics3d() {
        return new Physics3D(this.position, this.velocity, this.orientation, this.angularVelocity);
    }

    public Physics2D toPhysics2d() {
        return new Physics2D(this.position.flatten(), this.velocity.flatten(), this.orientation.flatten(), this.angularVelocity.dot(this.up()));
    }

    // https://www.wolframalpha.com/input/?i=solve+291.66+%2B+1458.33+d+-+g+x+%3D+0+for+x
    public static float getTimeForMaxJumpHeight(float duration, float gravity) {
        assert gravity < 0;
        assert duration > 0 && duration <= 0.2f;

        return (3 * (9722 + 48611 * duration)) / (-100 * gravity);
    }

    public Car2D toCar2D() {
        return new Car2D(this.position.flatten(), this.velocity.flatten(), this.forward().flatten(), this.up().dot(angularVelocity), this.elapsedSeconds, this.boost);
    }

    public static float driveForceLeft(ControlsOutput in, float velocityForward, float velocityLeft, float angularUp) {

        return (float) ((1380.4531378f * in.getSteer() + 7.8281188f * in.getThrottle() -
                15.0064029f * velocityLeft + 668.1208332f * angularUp) *
                (1.0f - Math.exp(-0.001161f * Math.abs(velocityForward))));
    }

    public BotInfo getBotFamilyInfo() {
        return PlayerInfoManager.getForBotFamily(this);
    }

    public Vector3 right() {
        return this.orientation.right();
    }

    private void driving_handbrake(ControlsOutput in, float dt) {
        // TODO
    }

    private void driving(ControlsOutput in, float dt) {
        final float v_f = velocity.dot(forward());
        final float v_l = velocity.dot(right());
        final float w_u = angularVelocity.dot(up());

        Vector3 force = forward().mul(driveForceForward(in, v_f, v_l, w_u)).add(right().mul(driveForceLeft(in, v_f, v_l, w_u)));

        Vector3 torque = up().mul(driveTorqueUp(in, v_f, w_u));

        velocity = velocity.add(force.mul(dt));
        position = position.add(velocity.mul(dt));

        angularVelocity = angularVelocity.add(torque.mul(dt));
        orientation = Matrix3x3.axisToRotation(angularVelocity.mul(dt)).matrixMul(orientation);
    }

    private void air_dodge(ControlsOutput in, float dt) {
        if (Math.abs(in.getPitch()) + Math.abs(in.getRoll()) + Math.abs(in.getYaw()) >= DodgeManeuver.input_threshold) {
            // directional dodge

            float vf = this.velocity.dot(this.forward());
            float scalar = Math.abs(vf) / CarData.MAX_VELOCITY;

            Vector2 dodgeDir = new Vector2(-in.getPitch(), in.getYaw()).normalized();

            Vector3 dodge_torque_local = new Vector3(new Vector2(dodgeDir.x * 224.0f, dodgeDir.y * 260.0f).cross());
            this.dodgeTorque = this.orientation.dot(dodge_torque_local);

            if (Math.abs(dodgeDir.x) < 0.1f) dodgeDir = new Vector2(0.0f, dodgeDir.y);
            if (Math.abs(dodgeDir.y) < 0.1f) dodgeDir = new Vector2(dodgeDir.x, 0.0f);

            boolean backward_dodge;
            if (Math.abs(vf) < 100.0f) {
                backward_dodge = dodgeDir.x < 0.0f;
            } else {
                backward_dodge = (dodgeDir.x >= 0.0f) != (vf > 0.0f);
            }

            Vector2 dv = dodgeDir.mul(500.0f);

            if (backward_dodge) {
                dv = dv.mul((16f / 15f) * (1f + 1.5f * scalar), 1);
            }
            dv = dv.mul(1, (1.0f + 0.9f * scalar));

            this.velocity = this.velocity.add(GameData.current().getGravity().mul(dt)).add(new Vector3(getDodgeOrientation().dot(dv)));
            this.position = this.position.add(this.velocity.mul(dt));

            this.angularVelocity = this.angularVelocity.add(dodgeTorque.mul(dt));
            this.orientation = Matrix3x3.axisToRotation(this.angularVelocity.mul(dt)).matrixMul(this.orientation);

            this.doubleJumped = true;
            this.dodgeTimer = 0.0f;
        } else {
            // double jump
            dodgeTorque = new Vector3(0, 0, 0);

            this.velocity = this.velocity.add(GameData.current().getGravity().mul(dt)).add(this.up().mul(DodgeManeuver.speed));
            this.position = this.position.add(this.velocity.mul(dt));

            this.angularVelocity = this.angularVelocity.add(dodgeTorque.mul(dt));
            this.orientation = Matrix3x3.axisToRotation(this.angularVelocity.mul(dt)).matrixMul(this.orientation);

            this.doubleJumped = true;
            this.dodgeTimer = 1.01f * DodgeManeuver.torque_time;
        }
    }

    private void aerial_control(ControlsOutput in, float dt) {
        final float J = 10.5f;

        final Vector3 T = new Vector3(-400.f, -130.f, 95.f);
        final Vector3 H = new Vector3(-50.f, -30.f * (1.f - Math.abs(in.getPitch())), -20.f * (1.0f - Math.abs(in.getYaw())));

        Vector3 rpy = new Vector3(in.getRoll(), in.getPitch(), in.getYaw());
        if (in.holdBoost() && boost > 0) {
            this.velocity = this.velocity.add(this.forward().mul(AerialManeuver.boost_airthrottle_acceleration * dt));
            boost -= CarData.BOOST_CONSUMPTION * dt;
        } else {
            this.velocity = this.velocity.add(this.forward().mul(in.getThrottle() * AerialManeuver.throttle_acceleration * dt));
        }

        if (in.holdJump() && enableJumpAcceleration) {
            if (jumpTimer < DodgeManeuver.min_duration) {
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

        if (dodgeTimer >= DodgeManeuver.z_damping_start && (this.velocity.z < 0.0f || dodgeTimer < DodgeManeuver.z_damping_end)) {
            this.velocity = this.velocity.add(new Vector3(0.0f, 0.0f, -this.velocity.z * DodgeManeuver.z_damping));
        }

        if (0.0f <= dodgeTimer && dodgeTimer <= 0.3f) {
            rpy = rpy.withY(0);
        }

        if (0.0f <= dodgeTimer && dodgeTimer <= DodgeManeuver.torque_time) {
            this.angularVelocity = this.angularVelocity.add(dodgeTorque.mul(dt));
        } else {
            Vector3 w_local = this.angularVelocity.dot(this.orientation);
            this.angularVelocity = this.angularVelocity.add(
                    this.orientation
                            .dot(T
                                    .mul(rpy)
                                    .add(H.mul(w_local))
                            )
                            .mul(dt / J)
            );
        }
        this.velocity = this.velocity.add(RLConstants.gravity.mul(dt));
        this.position = this.position.add(this.velocity.mul(dt));
        this.orientation = Matrix3x3.axisToRotation(this.angularVelocity.mul(dt)).matrixMul(this.orientation);
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
                            !lastControllerInputs.holdJump() &&
                            this.jumpTimer < DodgeManeuver.timeout &&
                            !this.doubleJumped
            ) {
                air_dodge(in, dt);
            } else {
                aerial_control(in, dt);
            }
        }
        // if the velocities exceed their maximum values, scale them back
        this.velocity = this.velocity.div(Math.max(1.0f, this.velocity.magnitude() / CarData.MAX_VELOCITY));
        this.angularVelocity = this.angularVelocity.div(Math.max(1.0f, this.angularVelocity.magnitude() / CarData.MAX_ANGULAR_VELOCITY));

        this.elapsedSeconds += dt;

        if (this.dodgeTimer >= 0.0f) {
            if (this.dodgeTimer >= DodgeManeuver.torque_time || this.hasWheelContact) {
                this.dodgeTimer = -1.0f;
            } else {
                this.dodgeTimer += dt;
            }
        }

        if (this.jumpTimer >= 0.0f) {
            if (this.hasWheelContact) {
                this.jumpTimer = -1.0f;
            } else {
                this.jumpTimer += dt;
            }
        }

        if (!in.holdJump() || this.jumpTimer > DodgeManeuver.max_duration) {
            enableJumpAcceleration = false;
        }

        this.hitbox.setOrientation(this.orientation);
        this.lastControllerInputs = in;
    }

    public void smartPrediction(float time) {
        assert this.hasWheelContact : "cannot do smart prediction if airborne";

        float dt = RLConstants.simulationTickFrequency;
        Vector3 localVel = new Vector3(this.forwardSpeed(), this.right().dot(this.velocity), 0);
        for (float t = 0; t < time; t += dt) {
            if (t < 0.5f)
                this.orientation = Matrix3x3.axisToRotation(this.angularVelocity.mul(dt)).matrixMul(this.orientation);
            this.velocity = this.forward().mul(localVel.x).add(this.right().mul(localVel.y));
            this.position = this.position.add(this.velocity.mul(dt, dt, 0));
            this.elapsedSeconds += dt;
        }

        this.hitbox.setOrientation(this.orientation);
    }

    public Vector3 getPathStartTangent() {
        return this.forward();
        //return Matrix3x3.axisToRotation(this.angularVelocity.mul(0.1f)).matrixMul(this.orientation)
        //        .forward();

    }

    private void jump(ControlsOutput in, float dt) {
        this.velocity = this.velocity.add(
                GameData.current().getGravity().mul(dt)
                        .add(this.up().mul(DodgeManeuver.speed))
        );
        this.position = this.position.add(this.velocity.mul(dt));

        this.orientation = Matrix3x3.axisToRotation(this.angularVelocity.mul(dt))
                .matrixMul(this.orientation);

        this.jumpTimer = 0.0f;
        this.jumped = true;
        this.doubleJumped = false;
        this.enableJumpAcceleration = true;
        this.hasWheelContact = false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(this.getClass().getSimpleName());

        sb.append("(");
        sb.append("\tposition=" + position.toString() + "");
        sb.append("\tvelocity=" + velocity.toString() + "");
        sb.append("\tangular=" + angularVelocity.toString() + "");
        sb.append("\tforward=" + forward().toString() + "");
        sb.append("\tup=" + up().toString() + "");
        sb.append(")");

        return sb.toString();
    }
}
