package yangbot.input;

import com.google.flatbuffers.FlatBufferBuilder;
import rlbot.flat.BallInfo;
import rlbot.flat.Physics;
import yangbot.cpp.FlatPhysics;
import yangbot.cpp.YangBotJNAInterop;
import yangbot.util.YangBallPrediction;
import yangbot.util.hitbox.YangHitbox;
import yangbot.util.hitbox.YangSphereHitbox;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector3;

import java.util.ArrayList;
import java.util.List;

public class BallData {
    public static final float RESTITUTION = 0.6f;
    public static final float DRAG = -0.031f; //
    public static final float MAX_VELOCITY = 6000.0f;
    public static final float MAX_ANGULAR = 6.0f;

    public static final float RADIUS = 91.25f;
    public static final float COLLISION_RADIUS = 93.15f;
    public static final float MASS = 30f; // 30
    public static final float INERTIA = 0.375f * MASS * RADIUS * RADIUS;
    public static final float MU = 2;

    public Vector3 position;
    public Vector3 velocity;
    public Vector3 angularVelocity;
    public BallTouch latestTouch;
    public boolean hasBeenTouched;
    public float elapsedSeconds = 0;

    public BallData(final BallInfo ball, float elapsedSeconds) {
        this.position = new Vector3(ball.physics().location());
        this.velocity = new Vector3(ball.physics().velocity());
        this.angularVelocity = new Vector3(ball.physics().angularVelocity());
        this.hasBeenTouched = ball.latestTouch() != null;
        this.latestTouch = this.hasBeenTouched ? new BallTouch(ball.latestTouch()) : null;
        this.elapsedSeconds = elapsedSeconds;
    }

    public BallData(final ImmutableBallData ball) {
        this.position = ball.position;
        this.velocity = ball.velocity;
        this.angularVelocity = ball.angularVelocity;
        this.hasBeenTouched = ball.hasBeenTouched();
        if (this.hasBeenTouched)
            this.latestTouch = ball.getLatestTouch();
        else
            this.latestTouch = null;
        this.elapsedSeconds = ball.elapsedSeconds;
    }

    public BallData(final BallData ball) {
        this.position = ball.position;
        this.velocity = ball.velocity;
        this.angularVelocity = ball.angularVelocity;
        this.hasBeenTouched = ball.hasBeenTouched;
        this.latestTouch = ball.latestTouch;
        this.elapsedSeconds = ball.elapsedSeconds;
    }

    public BallData(Vector3 position, Vector3 velocity, Vector3 angularVelocity) {
        this.position = position;
        this.velocity = velocity;
        this.angularVelocity = angularVelocity;
        this.latestTouch = null;
        this.hasBeenTouched = false;
        this.elapsedSeconds = 0;
    }

    public BallData(final Physics ballPhysics) {
        this.position = new Vector3(ballPhysics.location());
        this.velocity = new Vector3(ballPhysics.velocity());
        this.angularVelocity = new Vector3(ballPhysics.angularVelocity());
        this.hasBeenTouched = false;
        this.latestTouch = null;
    }

    public ImmutableBallData makeImmutable() {
        return new ImmutableBallData(this);
    }

    private float psyonixImpulseScale(float dv) {

        float[][] values = {
                {0.0f, 0.65f},
                {500.0f, 0.65f},
                {2300.0f, 0.55f},
                {4600.0f, 0.30f}
        };

        float input = MathUtils.clip(dv, 0, 4600);

        for (int i = 0; i < values.length; i++) {
            if (values[i][0] <= input && input <= values[i + 1][0]) {
                float u = (input - values[i][0]) / (values[i + 1][0] - values[i][0]);
                return MathUtils.lerp(values[i][1], values[i + 1][1], u);
            }
        }

        return -1;
    }

    public static YangSphereHitbox hitbox() {
        return new YangSphereHitbox(COLLISION_RADIUS);
    }

    public void stepWithCollide(float dt, CarData car) {
        collide(car, 0);
        step(dt);
    }

    public void stepWithCollideChip(float dt, CarData car) {
        FlatPhysics fbsCarData = YangBotJNAInterop.simulateCarBallCollision(car, this, dt).get();
        this.position = new Vector3(fbsCarData.position());
        this.velocity = new Vector3(fbsCarData.velocity());
        this.angularVelocity = new Vector3(fbsCarData.angularVelocity());
    }

    public int makeFlatPhysics(FlatBufferBuilder builder) {
        FlatPhysics.startFlatPhysics(builder);

        FlatPhysics.addAngularVelocity(builder, this.angularVelocity.toYangbuffer(builder));
        FlatPhysics.addVelocity(builder, this.velocity.toYangbuffer(builder));
        FlatPhysics.addPosition(builder, this.position.toYangbuffer(builder));
        FlatPhysics.addElapsedSeconds(builder, this.elapsedSeconds);

        return FlatPhysics.endFlatPhysics(builder);
    }

    public boolean isInAnyGoal() {
        return Math.abs(this.position.y) > BallData.RADIUS + RLConstants.goalDistance;
    }

    public boolean isInEnemyGoal(int teamSign) {
        return isInAnyGoal() && Math.signum(this.position.y) == -teamSign;
    }

    public boolean isInOwnGoal(int teamSign) {
        return isInAnyGoal() && Math.signum(this.position.y) == teamSign;
    }

    public void step(float dt) {
        // https://github.com/samuelpmish/RLUtilities/blob/master/src/simulation/ball.cc#L36
        this.velocity = velocity.add(
                velocity.mul(BallData.DRAG)
                        .add(RLConstants.gravity)
                        .mul(dt)
        );
        this.position = position.add(velocity.mul(dt));

        this.angularVelocity = angularVelocity.mul(
                Math.min(1, BallData.MAX_ANGULAR / angularVelocity.magnitude())
        );
        this.velocity = velocity.mul(
                Math.min(1, BallData.MAX_VELOCITY / velocity.magnitude())
        );

        this.elapsedSeconds += dt;
    }

    public void stepCollideGround(float dt) {
        // https://github.com/samuelpmish/RLUtilities/blob/master/src/simulation/ball.cc#L36

        if (this.position.z <= BallData.COLLISION_RADIUS + 0.001f) {
            this.velocity = velocity.add(
                    velocity.mul(BallData.DRAG)
                            .add(RLConstants.gravity)
                            .mul(dt));

            // collides
            var p = this.position.withZ(0);
            var n = new Vector3(0, 0, 1);

            var L = p.sub(this.position);

            float m_reduced = 1f / ((1f / BallData.MASS) + L.dot(L) / INERTIA);

            var v_perp = n.mul(Math.min(this.velocity.dot(n), 0));
            var v_para = velocity.sub(v_perp).sub(L.crossProduct(angularVelocity));

            float ratio = (float) Math.max(0, v_perp.magnitude()) / (float) Math.max(v_para.magnitude(), 0.0001f); // 0.0001

            var J_perp = v_perp.mul(-(1f + RESTITUTION) * MASS); // cross of normal
            var J_para = v_para.mul(-Math.min(1, MU * ratio) * m_reduced);
            var J = J_perp.add(J_para);

            this.angularVelocity = this.angularVelocity.add(L.crossProduct(J).div(INERTIA));
            this.velocity = this.velocity.add(J.div(MASS));

            this.position = this.position.add(this.velocity.mul(dt));

            float penetration = COLLISION_RADIUS - this.position.sub(p).dot(n);
            if (penetration > 0)
                this.position = this.position.add(n.mul(1.001f * penetration));
        } else {
            this.velocity = velocity.add(
                    velocity.mul(BallData.DRAG)
                            .add(RLConstants.gravity)
                            .mul(dt)
            );
            this.position = position.add(velocity.mul(dt));
        }

        this.angularVelocity = angularVelocity.mul(
                Math.min(1, BallData.MAX_ANGULAR / angularVelocity.magnitude())
        );
        this.velocity = velocity.mul(
                Math.min(1, BallData.MAX_VELOCITY / velocity.magnitude())
        );

        this.elapsedSeconds += dt;
    }

    public void stepHeatseeket(float dt) {
        int currentState = Math.round(MathUtils.remapClip((float) this.velocity.magnitude(), 3000, 4600, 0, 20));
        float targetVel = MathUtils.remap(currentState, 0, 20, 3000, 4600);

        Vector3 targetPos = new Vector3(0, RLConstants.goalDistance * Math.signum(this.velocity.y), 100);
        float horizVal = MathUtils.remap(currentState, 0, 20, 1, 1.5f);
        float zVal = 1 / 1.5f;
        this.velocity = this.velocity.add(targetPos.sub(this.position).normalized().mul(targetVel * dt).mul(horizVal, horizVal, zVal));
        this.velocity = velocity.mul(Math.min(1, targetVel / velocity.magnitude()));

        this.stepWithCollideChip(dt, new CarData(new Vector3(0, 0, 9999999), new Vector3(), new Vector3(), Matrix3x3.identity()));
    }

    public boolean collidesWith(YangHitbox obb, Vector3 position) {
        if (obb instanceof YangSphereHitbox) {
            return BallData.hitbox().collidesWith(this.position, (YangSphereHitbox) obb, position);
        }
        final Vector3 contactPoint = obb.getClosestPointOnHitbox(position, this.position);
        return contactPoint.sub(this.position).magnitude() < COLLISION_RADIUS;
    }

    public boolean collidesWith(CarData car) {
        return collidesWith(car.hitbox, car.position);
    }

    public boolean collidesWith(Vector3 spherePosition, float radius) {
        return BallData.hitbox().collidesWith(this.position, new YangSphereHitbox(radius), spherePosition);
    }

    public YangBallPrediction makeBallPrediction(float tickFrequency, float length) {
        assert length <= 5;
        assert tickFrequency > 0;

        List<YangBallPrediction.YangPredictionFrame> ballDataList = new ArrayList<>();
        BallData simBall = new BallData(this);
        for (float t = 0; t <= length; t += tickFrequency) {
            ballDataList.add(new YangBallPrediction.YangPredictionFrame(t + this.elapsedSeconds, t, simBall));
            simBall.stepCollideGround(tickFrequency);
        }
        return YangBallPrediction.from(ballDataList, tickFrequency);
    }

    public Vector3 collide(CarData car, float tolerance) {
        // https://github.com/samuelpmish/RLUtilities/blob/prerelease/src/simulation/ball.cc#L113

        Vector3 contactPoint = car.hitbox.getClosestPointOnHitbox(car.position, this.position);

        Vector3 normal = contactPoint.sub(this.position);
        if (normal.magnitude() < COLLISION_RADIUS + tolerance) {

            this.hasBeenTouched = true;

            this.latestTouch = new BallTouch(contactPoint, normal, this.elapsedSeconds);

            final Vector3 carPosition = car.position;

            final Matrix3x3 L_ball = Matrix3x3.antiSym(normal);
            final Matrix3x3 L_car = Matrix3x3.antiSym(contactPoint.sub(carPosition));
            Vector3 physImpulse = new Vector3();

            // Physics Engine Impulse
            if (true) {
                final Vector3 ballContactNormal = contactPoint.sub(this.position).normalized(); // `Ball -> Contact` direction

                final Matrix3x3 invInertiaCar = car.orientation.matrixMul(
                        CarData.INV_INERTIA.matrixMul(car.orientation.transpose())
                );

                final Matrix3x3 M = Matrix3x3.identity()
                        .elementwiseMul((1.0f / MASS) + (1.0f / CarData.MASS))
                        .sub(L_ball.matrixMul(L_ball).div(INERTIA))
                        .sub(L_car.matrixMul(invInertiaCar.matrixMul(L_car)))
                        .invert();

                final Vector3 deltaV =
                        car.velocity.sub(L_car.dot(car.angularVelocity))
                                .sub(this.velocity.sub(L_ball.dot(this.angularVelocity)));

                physImpulse = M.dot(deltaV);

                // Satisfy the Coulomb friction model
                {
                    final Vector3 impulsePerpendicular = ballContactNormal.mul(Math.min(physImpulse.dot(ballContactNormal), -1));
                    final Vector3 impulseParallel = physImpulse.sub(impulsePerpendicular);

                    final double ratio = impulsePerpendicular.magnitude() / Math.max(impulseParallel.magnitude(), 0.001f);

                    // scale the parallel component of J1 such that the
                    // Coulomb friction model is satisfied
                    physImpulse = impulsePerpendicular.add(
                            impulseParallel.mul(Math.min(1, MU * ratio))
                    );

                }
            }

            Vector3 psyonixImpulse = new Vector3();
            // Psyonix Impulse
            if (true) {

                final Vector3 carForward = car.forward();
                Vector3 contactNormal = this.position.sub(carPosition); // Car to ball
                contactNormal = contactNormal.scaleZ(0.35f);
                contactNormal = contactNormal.sub(
                        carForward
                                .mul(contactNormal.dot(carForward))
                                .mul(0.35f)
                ).normalized();
                final float deltaVelocity = (float) MathUtils.clip(this.velocity.sub(car.velocity).magnitude(), 0, 4600f);
                // https://gyazo.com/a99918c911d15eb51116a5c03872b20d
                psyonixImpulse = contactNormal.mul(MASS * deltaVelocity * psyonixImpulseScale(deltaVelocity));
            }

            this.angularVelocity = this.angularVelocity.add(
                    L_ball
                            .dot(physImpulse)
                            .div(INERTIA)
            );
            this.velocity = this.velocity.add(
                    physImpulse.add(psyonixImpulse)
                            .div(MASS)
            );

            return contactPoint;
        }

        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(this.getClass().getSimpleName());

        sb.append("(\n");
        sb.append("\tposition=" + position.toString() + "\n");
        sb.append("\tvelocity=" + velocity.toString() + "\n");
        sb.append("\tangular=" + angularVelocity.toString() + "\n");
        sb.append(")");

        return sb.toString();
    }
}
