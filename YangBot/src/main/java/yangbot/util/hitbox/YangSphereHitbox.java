package yangbot.util.hitbox;

import rlbot.render.Renderer;
import yangbot.util.math.vector.Vector3;

import java.awt.*;

public class YangSphereHitbox extends YangHitbox {

    public final float radius;

    public YangSphereHitbox(float radius) {
        this.radius = radius;
    }

    @Override
    public void draw(Renderer renderer, Vector3 p, float scale, Color c) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public Vector3 getClosestPointOnHitbox(Vector3 hitboxPos, Vector3 point) {
        return point
                .sub(hitboxPos)
                .normalized()
                .mul(radius)
                .add(hitboxPos);
    }

    public boolean collidesWith(Vector3 myPosition, YangSphereHitbox sphereHitbox, Vector3 theirPosition) {
        return myPosition.sub(theirPosition).magnitudeSquared() < ((this.radius + sphereHitbox.radius) * (this.radius + sphereHitbox.radius));
    }
}
