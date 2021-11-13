package yangbot.util.math;

import yangbot.util.Tuple;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

import java.util.function.Function;

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

    public static Vector2 lerp(Vector2 a, Vector2 b, double t) {
        assert t >= 0 && t <= 1 : "t=" + t;
        return a.mul(1.0f - t).add(b.mul(t));
    }

    public static float minAbs(float val, float min) {
        assert min >= 0;

        return Math.signum(val) * Math.min(min, Math.abs(val));
    }

    public static double clip(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float clip(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static int clip(int value, int min, int max) {
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

    public static float closestMultiple(float value, float base){
        float factor = value / base;
        return base * Math.round(factor);
    }

    public static float remapClip(float value, float rangeStart, float rangeEnd, float newRangeStart, float newRangeEnd) {
        if(rangeStart == rangeEnd){
            if(value < rangeStart)
                return newRangeStart;
            else if(value > rangeEnd)
                return newRangeEnd;
            else
                return 0.5f * (newRangeStart + newRangeEnd);
        }
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

    // Returns: x | error
    public static Tuple<Float, Float> secantMethod(Function<Float, Float> errorFunc, float x1, float x2) {
        float error = errorFunc.apply(x1);
        float error2 = errorFunc.apply(x2);

        int i = 0;
        for (; i < 16; i++) {
            if (Math.abs(error) < 0.001 || Float.isNaN(error) || Float.isInfinite(error))
                break;

            float newX = (x2 * error - x1 * error2) / (error - error2);
            x2 = x1;
            x1 = newX;

            error2 = error;
            error = errorFunc.apply(newX);
        }
        return new Tuple<>(x1, error);
    }

    public static Tuple<Float, Float> smartBisectionMethod(Function<Float, Float> errorFunc, float x1, float x2) {
        float error1 = errorFunc.apply(x1);
        float error2 = errorFunc.apply(x2);

        assert Math.signum(error1) != Math.signum(error2);

        if (error1 > error2) {
            float temp = x1, tempE = error1;
            x1 = x2;
            error1 = error2;
            x2 = temp;
            error2 = tempE;
        }

        int i = 0;

        //System.out.println("######");
        float lastCompE = 0;
        boolean failure = false;
        for (; i < 24; i++) {
            if (Math.abs(error1) < 0.001 || Float.isNaN(error1) || Float.isInfinite(error1))
                break;
            if (Math.abs(error2) < 0.001 || Float.isNaN(error2) || Float.isInfinite(error2))
                break;

            //System.out.println("x1="+x1+" x2="+x2+" e1="+error1+" e2="+error2);
            float newX;
            if (failure) {
                // regula falsi failure correction
                newX = (x1 + x2) / 2;
                failure = false;
            } else
                newX = x1 + (error1 * (x2 - x1)) / (error1 - error2);

            float newE = errorFunc.apply(newX);

            if (Math.signum(newE) == Math.signum(lastCompE))
                failure = true;

            lastCompE = newE;

            if (newE <= 0) {
                x1 = newX;
                error1 = newE;
            } else {
                x2 = newX;
                error2 = newE;
            }
        }
        assert !Float.isNaN(error1) && !Float.isInfinite(error1);
        assert !Float.isNaN(error2) && !Float.isInfinite(error2);

        if (Math.abs(error1) < Math.abs(error2))
            return new Tuple<>(x1, error1);
        else
            return new Tuple<>(x2, error2);
    }
}
