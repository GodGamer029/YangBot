package yangbot.input;

import rlbot.flat.Physics;
import yangbot.cpp.FlatPhysics;
import yangbot.util.math.vector.Vector3;

public class ImmutableBallData {

    public final Vector3 position;
    public final Vector3 velocity;
    public final Vector3 angularVelocity;
    private final BallTouch latestTouch;
    private final boolean hasBeenTouched;
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

    public ImmutableBallData(final FlatPhysics ballPhysics) {
        this.position = new Vector3(ballPhysics.position());
        this.velocity = new Vector3(ballPhysics.velocity());
        this.angularVelocity = new Vector3(ballPhysics.angularVelocity());
        this.hasBeenTouched = false;
        this.latestTouch = null;
        this.elapsedSeconds = ballPhysics.elapsedSeconds();
    }

    public ImmutableBallData(Vector3 position, Vector3 velocity, Vector3 angularVelocity) {
        this.position = position;
        this.velocity = velocity;
        this.angularVelocity = angularVelocity;
        this.latestTouch = null;
        this.hasBeenTouched = false;
        this.elapsedSeconds = 0;
    }

    public boolean hasBeenTouched() {
        return this.hasBeenTouched && this.latestTouch != null;
    }

    public BallTouch getLatestTouch() {
        assert hasBeenTouched();
        return this.latestTouch;
    }

    public BallData makeMutable() {
        return new BallData(this);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "(position=" + this.position + ",velocity=" + this.velocity + ",angularVelocity=" + this.angularVelocity + ",t=" + this.elapsedSeconds + ")";
    }
}
