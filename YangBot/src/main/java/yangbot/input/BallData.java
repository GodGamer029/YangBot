package yangbot.input;


import rlbot.flat.BallInfo;
import yangbot.vector.Vector3;

/**
 * Basic information about the ball.
 * <p>
 * This class is here for your convenience, it is NOT part of the framework. You can change it as much
 * as you want, or delete it.
 */
public class BallData {
    public Vector3 position;
    public Vector3 velocity;
    @SuppressWarnings({"WeakerAccess", "unused"})
    public Vector3 spin;
    public final BallTouch latestTouch;
    public final boolean hasBeenTouched;

    public BallData(final BallInfo ball) {
        this.position = new Vector3(ball.physics().location());
        this.velocity = new Vector3(ball.physics().velocity());
        this.spin = new Vector3(ball.physics().angularVelocity());
        this.hasBeenTouched = ball.latestTouch() != null;
        this.latestTouch = this.hasBeenTouched ? new BallTouch(ball.latestTouch()) : null;
    }
}
