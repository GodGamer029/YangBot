package yangbot.util.math.vector;

import java.io.Serializable;
import java.util.Locale;

/**
 * A vector that only knows about x and y components.
 * <p>
 * This class is here for your convenience, it is NOT part of the framework. You can add to it as much
 * as you want, or delete it.
 */
public class Vector2 implements Serializable {

    private static final long serialVersionUID = 4025217084999536712L;

    public final float x;
    public final float y;

    public Vector2(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public Vector2() {
        this.x = 0;
        this.y = 0;
    }

    public Vector2(double x, double y) {
        this.x = (float) x;
        this.y = (float) y;
    }

    public static Vector2 fromAngle(float angle) {
        return new Vector2(Math.cos(angle), Math.sin(angle));
    }

    public float get(int index) {
        if (index == 0)
            return x;
        if (index == 1)
            return y;
        return 0;
    }

    public Vector3 unitVectorWithZ(float z) {
        if (z == 0)
            return new Vector3(this);
        if (isZero())
            return new Vector3(0, 0, Math.signum(z));
        Vector2 normed = normalized();

        double magicBoi = Math.sqrt(1 - z * z);
        return new Vector3(normed.mul(magicBoi), z);
    }

    public Vector2 add(Vector2 other) {
        return new Vector2(x + other.x, y + other.y);
    }

    public Vector2 add(float x1, float y1) {
        return new Vector2(x + x1, y + y1);
    }

    public Vector2 sub(Vector2 other) {
        return new Vector2(x - other.x, y - other.y);
    }

    public Vector2 sub(float x1, float y1) {
        return new Vector2(x - x1, y - y1);
    }

    public Vector2 mul(double scale) {
        return new Vector2(x * scale, y * scale);
    }

    public Vector2 mul(Vector2 o) {
        return new Vector2(x * o.x, y * o.y);
    }

    public Vector2 mul(double sx, double sy) {
        return new Vector2(x * sx, y * sy);
    }

    public Vector2 complexMul(Vector2 o) {
        return new Vector2(x * o.x - y * o.y, x * o.y + y * o.x);
    }

    @SuppressWarnings("SuspiciousNameCombination")
    public Vector2 cross() {
        return new Vector2(-y, x);
    }

    // The smallest rotation needed to make `this` and `o` collinear and point toward the same
    public Vector2 rotationBetween(Vector2 other) {
        var v1 = this.normalized();
        var v2 = other.normalized();

        var s = v1.perp(v2);
        var c = v1.dot(v2);

        return Vector2.fromAngle((float) Math.atan2(s, c));
    }

    public Vector2 scaledToMagnitude(double magnitude) {
        if (isZero()) {
            throw new IllegalStateException("Cannot scale up a vector with length zero!");
        }
        double scaleRequired = magnitude / magnitude();
        return mul(scaleRequired);
    }

    public double distance(Vector2 other) {
        double xDiff = x - other.x;
        double yDiff = y - other.y;
        return Math.sqrt(xDiff * xDiff + yDiff * yDiff);
    }

    public double magnitude() {
        return Math.sqrt(magnitudeSquared());
    }

    public float magnitudeF() {
        return (float)Math.sqrt(magnitudeSquared());
    }

    public double magnitudeSquared() {
        return x * x + y * y;
    }

    public Vector2 normalized() {
        if (isZero()) {
            return new Vector2();
        }
        return this.mul(1.f / magnitude());
    }

    public float perp(Vector2 o) {
        return x * o.y - y * o.x;
    }

    public Vector2 dot(Matrix2x2 mat) {
        float[] vA = new float[2];
        for (int i = 0; i < 2; i++) {
            vA[i] = 0;
            for (int j = 0; j < 2; j++) {
                vA[i] += this.get(j) * mat.get(j, i);
            }
        }
        return new Vector2(vA[0], vA[1]);
    }

    public float dot(Vector2 other) {
        return x * other.x + y * other.y;
    }

    public boolean isZero() {
        return x == 0 && y == 0;
    }

    public double correctionAngle(Vector2 ideal) {
        double currentRad = Math.atan2(y, x);
        double idealRad = Math.atan2(ideal.y, ideal.x);

        if (Math.abs(currentRad - idealRad) > Math.PI) {
            if (currentRad < 0) {
                currentRad += Math.PI * 2;
            }
            if (idealRad < 0) {
                idealRad += Math.PI * 2;
            }
        }

        return idealRad - currentRad;
    }

    public double angleBetween(Vector2 o) {
        return Math.atan2(x * o.y - y * o.x, dot(o));
    }

    public Vector2 rotateBy(double angle) {
        if (angle == 0)
            return this;
        double cosAngle = Math.cos(angle);
        double sinAngle = Math.sin(angle);
        double newX = x * cosAngle - y * sinAngle;
        double newY = x * sinAngle + y * cosAngle;
        return new Vector2(newX, newY);
    }

    public double angle() {
        return Math.atan2(y, x);
    }

    public Vector2 withX(float x) {
        return new Vector2(x, y);
    }

    public Vector2 withY(float y) {
        return new Vector2(x, y);
    }

    public Vector3 withZ(float z) {
        return new Vector3(x, y, z);
    }

    public Vector2 swap() {
        return new Vector2(y, x);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + String.format(Locale.US, "(x=%.2f;y=%.2f)", x, y);
    }
}
