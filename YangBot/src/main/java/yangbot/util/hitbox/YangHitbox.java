package yangbot.util.hitbox;

import rlbot.render.Renderer;
import yangbot.vector.Vector3;

import java.awt.*;

// Class that solely describes the shape and orientation of the hitbox, not the position
public abstract class YangHitbox {

    public abstract void draw(Renderer renderer, Vector3 p, float scale, Color c);

    public abstract Vector3 getClosestPointOnHitbox(Vector3 hitboxPos, Vector3 point);
}
