package yangbot.input;

import rlbot.flat.BoxShape;
import rlbot.render.Renderer;
import yangbot.vector.Matrix3x3;
import yangbot.vector.Vector3;

import java.awt.*;

public class YangHitbox {

    public static Vector3 octaneHitboxOffset = new Vector3(13.88f, 0f, 20.75f);

    public final Vector3 hitboxOffset;
    public final Vector3 hitboxLengths;
    private Matrix3x3 orientation;

    private Vector3 permF;
    private Vector3 permL;
    private Vector3 permU;

    public YangHitbox(BoxShape hitbox, Matrix3x3 orientation) {
        this.hitboxLengths = new Vector3(hitbox.length(), hitbox.width(), hitbox.height());
        this.hitboxOffset = octaneHitboxOffset;

        setOrientation(orientation);
    }

    private void setOrientation(Matrix3x3 orientation) {
        this.orientation = orientation;

        permF = this.orientation.forward().mul(this.hitboxLengths.x / 2);
        permL = this.orientation.left().mul(this.hitboxLengths.y / 2);
        permU = this.orientation.up().mul(this.hitboxLengths.z / 2);
    }

    public Vector3 applyOffset(Vector3 p) {
        return p.add(orientation.forward().mul(hitboxOffset.x)).add(orientation.left().mul(hitboxOffset.y)).add(orientation.up().mul(hitboxOffset.z));
    }

    public Vector3 removeOffset(Vector3 permutated) {
        return permutated.sub(orientation.forward().mul(hitboxOffset.x)).sub(orientation.left().mul(hitboxOffset.y)).sub(orientation.up().mul(hitboxOffset.z));
    }

    public Vector3 permutatePoint(Vector3 point, float frontDir, float leftDir, float upDir, float scale) {
        point = applyOffset(point);
        return point.add(permF.mul(frontDir * scale)).add(permL.mul(leftDir * scale)).add(permU.mul(upDir * scale));
    }

    public Vector3 permutatePoint(Vector3 point, float frontDir, float leftDir, float upDir) {
        point = applyOffset(point);
        return point.add(permF.mul(frontDir)).add(permL.mul(leftDir)).add(permU.mul(upDir));
    }

    public Vector3 permutatePoint(Vector3 point, Vector3 direction) {
        return permutatePoint(point, direction.x, direction.y, direction.z);
    }

    public Vector3 getClosestPointOnHitbox(Vector3 hitboxPos, Vector3 point) {
        Vector3 center = this.orientation.dot(hitboxOffset).add(hitboxPos);
        Vector3 halfLengths = this.hitboxLengths.mul(0.5f);

        Vector3 vLocal = point.sub(center).dot(this.orientation);
        vLocal = vLocal.clip(0, -halfLengths.x, halfLengths.x);
        vLocal = vLocal.clip(1, -halfLengths.y, halfLengths.y);
        vLocal = vLocal.clip(2, -halfLengths.z, halfLengths.z);

        return this.orientation.dot(vLocal).add(center);
    }

    public void draw(Renderer renderer, Vector3 p, float scale, Color c) {
        renderer.drawLine3d(c, permutatePoint(p, 1, 1, 1, scale), permutatePoint(p, 1, 1, -1, scale));
        renderer.drawLine3d(c, permutatePoint(p, 1, -1, 1, scale), permutatePoint(p, 1, -1, -1, scale));
        renderer.drawLine3d(c, permutatePoint(p, -1, 1, 1, scale), permutatePoint(p, -1, 1, -1, scale));
        renderer.drawLine3d(c, permutatePoint(p, -1, -1, 1, scale), permutatePoint(p, -1, -1, -1, scale));
        renderer.drawLine3d(c, permutatePoint(p, 1, 1, 1, scale), permutatePoint(p, -1, 1, 1, scale));
        renderer.drawLine3d(c, permutatePoint(p, 1, -1, 1, scale), permutatePoint(p, -1, -1, 1, scale));
        renderer.drawLine3d(c, permutatePoint(p, 1, 1, -1, scale), permutatePoint(p, -1, 1, -1, scale));
        renderer.drawLine3d(c, permutatePoint(p, 1, -1, -1, scale), permutatePoint(p, -1, -1, -1, scale));
        renderer.drawLine3d(c, permutatePoint(p, 1, 1, 1, scale), permutatePoint(p, 1, -1, 1, scale));
        renderer.drawLine3d(c, permutatePoint(p, -1, 1, 1, scale), permutatePoint(p, -1, -1, 1, scale));
        renderer.drawLine3d(c, permutatePoint(p, 1, 1, -1, scale), permutatePoint(p, 1, -1, -1, scale));
        renderer.drawLine3d(c, permutatePoint(p, -1, 1, -1, scale), permutatePoint(p, -1, -1, -1, scale));
    }
}
