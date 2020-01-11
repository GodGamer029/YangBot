package yangbot.input;

import rlbot.flat.Physics;
import yangbot.vector.Vector3;

public class ImmutableBallData {

    public final Vector3 position;
    public final Vector3 velocity;
    public final Vector3 angularVelocity;
    public final BallTouch latestTouch;
    public final boolean hasBeenTouched;
    public final float elapsedSeconds;

    public ImmutableBallData(final Physics ballPhysics, float elapsedSeconds) {
        this.position = new Vector3(ballPhysics.location());
        this.velocity = new Vector3(ballPhysics.velocity());
        this.angularVelocity = new Vector3(ballPhysics.angularVelocity());
        this.hasBeenTouched = false;
        this.latestTouch = null;
        this.elapsedSeconds = elapsedSeconds;
    }

    public ImmutableBallData(final BallData ball) {
        this.position = new Vector3(ball.position);
        this.velocity = new Vector3(ball.velocity);
        this.angularVelocity = new Vector3(ball.angularVelocity);
        this.hasBeenTouched = ball.hasBeenTouched;
        this.latestTouch = ball.latestTouch;
        this.elapsedSeconds = ball.elapsedSeconds;
    }

    public BallData makeMutable() {
        return new BallData(this);
    }
}
