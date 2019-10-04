package yangbot.util;

import yangbot.vector.Vector3;

public class Ray {
    public final Vector3 start;
    public final Vector3 direction;

    public Ray(Vector3 start, Vector3 direction) {
        this.start = start;
        this.direction = direction;
    }
}
