package yangbot.util.lut;

import yangbot.util.math.vector.Vector2;

import java.io.Serializable;

public class PowerslideEntry implements Serializable {

    private static final long serialVersionUID = -8936164411204386333L;

    public final float time;
    public final float finalSpeed;
    public final Vector2 finalPos;

    public PowerslideEntry(float time, float finalSpeed, Vector2 finalPos) {
        this.time = time;
        this.finalSpeed = finalSpeed;
        this.finalPos = finalPos;
    }
}
