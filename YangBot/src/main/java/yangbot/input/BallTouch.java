package yangbot.input;


import rlbot.flat.Touch;
import yangbot.util.math.vector.Vector3;

/**
 * Basic information about the ball's latest touch.
 * <p>
 * This class is here for your convenience, it is NOT part of the framework. You can change it as much
 * as you want, or delete it.
 */
public class BallTouch {
    public final Vector3 position;
    public final Vector3 normal;
    public final String playerName;
    public final float gameSeconds;
    public final int playerIndex;
    public final int team;

    public BallTouch(final Touch touch) {
        this.position = new Vector3(touch.location());
        this.normal = new Vector3(touch.normal());
        this.playerName = touch.playerName();
        this.gameSeconds = touch.gameSeconds();
        this.playerIndex = touch.playerIndex();
        this.team = touch.team();
    }

    public BallTouch(Vector3 position, Vector3 normal, float gameSeconds) {
        this.position = position;
        this.normal = normal;
        this.playerName = "";
        this.gameSeconds = gameSeconds;
        this.playerIndex = -1;
        this.team = -1;
    }
}