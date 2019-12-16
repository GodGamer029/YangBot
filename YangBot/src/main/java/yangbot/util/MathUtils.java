package yangbot.util;

import yangbot.vector.Vector3;

public class MathUtils {

    public static float lerp(float a, float b, float t) {
        return a * (1.0f - t) + b * t;
    }

    public static double lerp(double a, double b, double t) {
        return a * (1.0f - t) + b * t;
    }

    public static Vector3 lerp(Vector3 a, Vector3 b, double t) {
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

    // The following isn't really useful, just writing down my thoughts
    public double getSpeedInDirection(Vector3 velocity, Vector3 direction) {
        return velocity.dot(direction);
    }

    public Vector3 getVelocityInDirection(Vector3 velocity, Vector3 direction) {
        return direction.mul(getSpeedInDirection(velocity, direction));
    }
}
