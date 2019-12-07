package yangbot.input;

import rlbot.flat.BallInfo;
import rlbot.flat.Physics;
import yangbot.vector.Vector3;

public class BallData {
    public static final float DRAG = -0.0305f;
    public static final float MAX_VELOCITY = 4000.0f;
    public static final float MAX_ANGULAR = 6.0f;

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

    public BallData(final Physics ballPhysics) {
        this.position = new Vector3(ballPhysics.location());
        this.velocity = new Vector3(ballPhysics.velocity());
        this.angularVelocity = new Vector3(ballPhysics.angularVelocity());
        this.hasBeenTouched = false;
        this.latestTouch = null;
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
}
