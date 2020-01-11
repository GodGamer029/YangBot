package yangbot.util.hitbox;

import rlbot.flat.BoxShape;
import rlbot.render.Renderer;
import yangbot.vector.Matrix3x3;
import yangbot.vector.Vector3;

import java.awt.*;

public class YangCarHitbox extends YangHitbox {

    public static Vector3 octaneHitboxExtents = new Vector3(118.01f, 84.2f, 36.16f);
    public static Vector3 octaneHitboxOffset = new Vector3(13.88f, 0f, 20.75f);

    public final Vector3 hitboxOffset;
    public final Vector3 hitboxLengths;

    private Matrix3x3 orientation;

    private Vector3 permF;
    private Vector3 permL;
    private Vector3 permU;

    public YangCarHitbox(BoxShape hitbox, Vector3 offsets, Matrix3x3 orientation) {
        this.hitboxLengths = new Vector3(hitbox.length(), hitbox.width(), hitbox.height()).mul(1, 1, 1);
        this.hitboxOffset = offsets;

        setOrientation(orientation);
    }

    public YangCarHitbox(Vector3 hitbox, Vector3 offsets, Matrix3x3 orientation) {
        this.hitboxLengths = new Vector3(hitbox);
        this.hitboxOffset = offsets;

        setOrientation(orientation);
    }

    public YangCarHitbox(Matrix3x3 orientation) {
        this.hitboxLengths = octaneHitboxExtents;
        this.hitboxOffset = octaneHitboxOffset;

        setOrientation(orientation);
    }

    public Matrix3x3 getOrientation() {
        return this.orientation;
    }

    public YangCarHitbox withOrientation(Matrix3x3 orientation) {
        return new YangCarHitbox(this.hitboxLengths, this.hitboxOffset, orientation);
    }

    public void setOrientation(Matrix3x3 orientation) {
        this.orientation = orientation;

        this.permF = this.orientation.forward().mul(this.hitboxLengths.x / 2);
        this.permL = this.orientation.left().mul(this.hitboxLengths.y / 2);
        this.permU = this.orientation.up().mul(this.hitboxLengths.z / 2);
    }

    public YangSphereHitbox asSphere(float scale) {
        // Take the distance from the most distant point on the hitbox to the center as radius for the sphere
        return new YangSphereHitbox((float) this.permutatePoint(removeOffset(new Vector3()), 1, 1, 1, scale).magnitude());
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

    @Override
    public Vector3 getClosestPointOnHitbox(Vector3 hitboxPos, Vector3 point) {
        final Vector3 center = this.orientation.dot(hitboxOffset).add(hitboxPos);
        final Vector3 halfLengths = this.hitboxLengths.mul(0.5f);

        Vector3 vLocal = point.sub(center).dot(this.orientation);
        vLocal = vLocal.clip(0, -halfLengths.x, halfLengths.x);
        vLocal = vLocal.clip(1, -halfLengths.y, halfLengths.y);
        vLocal = vLocal.clip(2, -halfLengths.z, halfLengths.z);

        return this.orientation.dot(vLocal).add(center);
    }

    @Override
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

        renderer.drawLine3d(c, permutatePoint(p, 1, 0, -1, scale), permutatePoint(p, 1, 0, 1, scale));
        //renderer.drawLine3d(c, permutatePoint(p, 1, -1, 0, scale), permutatePoint(p, 1, 1, 0, scale));

    }
}
