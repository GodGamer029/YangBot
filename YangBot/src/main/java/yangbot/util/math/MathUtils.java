package yangbot.util.math;

import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

public class MathUtils {

    public static float lerp(float a, float b, float t) {
        assert t >= 0 && t <= 1 : "t=" + t;
        return a * (1.0f - t) + b * t;
    }

    public static double lerp(double a, double b, double t) {
        assert t >= 0 && t <= 1 : "t=" + t;
        return a * (1.0f - t) + b * t;
    }

    public static Vector3 lerp(Vector3 a, Vector3 b, double t) {
        assert t >= 0 && t <= 1 : "t=" + t;
        return a.mul(1.0f - t).add(b.mul(t));
    }

    public static double clip(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float clip(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static Vector3 getSurfaceVelocityOfPoint(Vector3 point, Vector3 centerOfRotation, Vector3 angularVelocity, Vector3 velocity) {
        return velocity.add(angularVelocity.mul(point.sub(centerOfRotation)));
    }

    public static float interpolateQuadratic(float v0, float vT, float aT, float t, float T) {
        float tau = t / T;
        float dv = aT * T;
        return v0 * (tau - 1.0f) * (tau - 1.0f) +
                dv * (tau - 1.0f) * tau +
                vT * (2.0f - tau) * tau;
    }

    public static double speedbot_steer(double angle) {
        double value = clip(Math.pow(10 * angle + Math.signum(angle), 3) / 20, -1, 1);
        if (Math.abs(value) <= 0.1)
            value *= 0.5f;
        return value;
    }

    public static float normAngle(float angle) {
        while (angle > Math.PI)
            angle -= 2 * Math.PI;
        while (angle < -Math.PI)
            angle += 2 * Math.PI;

        return angle;
    }

    public static float remapClip(float value, float rangeStart, float rangeEnd, float newRangeStart, float newRangeEnd) {
        return remap(MathUtils.clip(value, rangeStart, rangeEnd), rangeStart, rangeEnd, newRangeStart, newRangeEnd);
    }

    public static float remap(float value, float rangeStart, float rangeEnd, float newRangeStart, float newRangeEnd) {
        value -= rangeStart;
        value /= rangeEnd - rangeStart; // now mapped between 0 - 1
        value *= newRangeEnd - newRangeStart;
        value += newRangeStart;
        return value;
    }

    public static float distance(float v1, float v2) {
        return Math.abs(v1 - v2);
    }

    public static boolean floatsAreEqual(float value1, float value2) {
        return Float.floatToIntBits(value1) == Float.floatToIntBits(value2);
    }

    public static boolean floatsAreEqual(float value1, float value2, double delta) {
        return floatsAreEqual(value1, value2) || Math.abs(value1 - value2) <= delta;
    }

    public static boolean doublesAreEqual(double value1, double value2) {
        return Double.doubleToLongBits(value1) == Double.doubleToLongBits(value2);
    }

    public static boolean doublesAreEqual(double value1, double value2, double delta) {
        return doublesAreEqual(value1, value2) || Math.abs(value1 - value2) <= delta;
    }

    // In radians
    public static double findAngle(Vector2 p1, Vector2 p2, Vector2 pivot) {
        // Normalize
        p1 = p1.sub(pivot).normalized();
        p2 = p2.sub(pivot).normalized();

        return Math.acos(p1.dot(p2));
    }
}
