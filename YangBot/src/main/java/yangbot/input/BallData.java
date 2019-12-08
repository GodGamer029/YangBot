package yangbot.input;

import rlbot.flat.BallInfo;
import rlbot.flat.Physics;
import yangbot.util.MathUtils;
import yangbot.vector.Matrix3x3;
import yangbot.vector.Vector3;

public class BallData {
    public static final float DRAG = -0.0305f;
    public static final float MAX_VELOCITY = 4000.0f;
    public static final float MAX_ANGULAR = 6.0f;

    public static final float RADIUS = 91.25f;
    public static final float COLLISION_RADIUS = 93.15f;
    public static final float MASS = 30f;
    public static final float INERTIA = 0.4f * MASS * RADIUS * RADIUS;
    public static final float MU = 2;

    public Vector3 position;
    public Vector3 velocity;
    public Vector3 angularVelocity;
    public final BallTouch latestTouch;
    public final boolean hasBeenTouched;

    public BallData(final BallInfo ball) {
        this.position = new Vector3(ball.physics().location());
        this.velocity = new Vector3(ball.physics().velocity());
        this.angularVelocity = new Vector3(ball.physics().angularVelocity());
        this.hasBeenTouched = ball.latestTouch() != null;
        this.latestTouch = this.hasBeenTouched ? new BallTouch(ball.latestTouch()) : null;
    }

    public BallData(Vector3 position, Vector3 velocity, Vector3 angularVelocity) {
        this.position = position;
        this.velocity = velocity;
        this.angularVelocity = angularVelocity;
        this.latestTouch = null;
        this.hasBeenTouched = false;
    }

    public BallData(final Physics ballPhysics) {
        this.position = new Vector3(ballPhysics.location());
        this.velocity = new Vector3(ballPhysics.velocity());
        this.angularVelocity = new Vector3(ballPhysics.angularVelocity());
        this.hasBeenTouched = false;
        this.latestTouch = null;
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

    public void stepWithCollide(float dt, CarData car) {
        collide(car);
        step(dt);
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
    }

    public Vector3 collide(CarData car) {
        // https://github.com/samuelpmish/RLUtilities/blob/prerelease/src/simulation/ball.cc#L113

        final Vector3 contactPoint = car.hitbox.getClosestPointOnHitbox(car.position, this.position);

        if (contactPoint.sub(this.position).magnitude() < COLLISION_RADIUS) {

            final Matrix3x3 L_ball = Matrix3x3.antiSym(contactPoint.sub(this.position));
            final Matrix3x3 L_car = Matrix3x3.antiSym(contactPoint.sub(car.position));
            Vector3 physImpulse = new Vector3();

            // Physics Engine Impulse
            if (true) {
                final Vector3 ballContactNormal = contactPoint.sub(this.position).normalized(); // `Ball -> Contact` direction

                final Matrix3x3 invInertiaCar = car.orientation.dot(
                        CarData.INV_INERTIA.dot(car.orientation.transpose())
                );

                final Matrix3x3 M = Matrix3x3.identity()
                        .mul((1.0f / MASS) + (1.0f / CarData.MASS))
                        .sub(L_ball.dot(L_ball).div(INERTIA))
                        .sub(L_car.dot(invInertiaCar.dot(L_car)))
                        .invert();

                final Vector3 deltaV = car.velocity
                        .sub(L_car.dot(car.angularVelocity))
                        .sub(this.velocity.sub(L_ball.dot(this.angularVelocity)));

                physImpulse = M.dot(deltaV);

                // Satisfy the Coulomb friction model
                {
                    final Vector3 impulsePerpendicular = ballContactNormal.mul(Math.min(physImpulse.dot(ballContactNormal), -1));
                    final Vector3 impulseParallel = physImpulse.sub(impulsePerpendicular);

                    double ratio = impulsePerpendicular.magnitude() / Math.max(impulseParallel.magnitude(), 0.001f);

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

                Vector3 carForward = car.forward();
                Vector3 contactNormal = this.position.sub(car.position); // Car to ball
                contactNormal = contactNormal.scaleZ(0.35f); // Makes dribbling easier
                contactNormal = contactNormal.sub(
                        carForward
                                .mul(contactNormal.dot(carForward))
                                .mul(0.35f)
                ).normalized();

                float deltaVelocity = (float) MathUtils.clip(this.velocity.sub(car.velocity).magnitude(), 0, 4600f);
                // https://gyazo.com/a99918c911d15eb51116a5c03872b20d
                psyonixImpulse = contactNormal.mul(MASS * deltaVelocity * psyonixImpulseScale(deltaVelocity));
            }

            this.angularVelocity = this.angularVelocity.add(L_ball.dot(physImpulse).div(INERTIA));
            this.velocity = this.velocity.add(physImpulse.add(psyonixImpulse).div(MASS));

            return contactPoint;
        }

        return null;
    }
}
