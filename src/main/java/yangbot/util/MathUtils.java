package yangbot.util;

import yangbot.vector.Vector3;

public class MathUtils {

    public static float lerp(float a, float b, float t){
        return a * (1.0f - t) + b * t;
    }

    public static double lerp(double a, double b, double t){
        return a * (1.0f - t) + b * t;
    }

    public static Vector3 lerp(Vector3 a, Vector3 b, double t){
        return a.mul(1.0f - t).add(b.mul(t));
    }

    public static double clip(double value, double min, double max){
        return Math.max(min, Math.min(max, value));
    }

    public static float clip(float value, float min, float max){
        return Math.max(min, Math.min(max, value));
    }

    public static float quadraticLerp(float start, float middle, float end, float t){
        t *= 2;
        if(t <= 0)
            return start;
        if(t == 1)
            return middle;
        if(t >= 2)
            return end;
        if(t <= 1){
            return (start - middle) * (t - 1) * (t - 1) + middle;
        }else{
            return (end - middle) * (t - 1) * (t - 1) + middle;
        }
    }

    public static double speedbot_steer(double angle){
        double value = clip( Math.pow(10 * angle + Math.signum(angle), 3) / 20, -1, 1);
        if(Math.abs(value) <= 0.1)
            value *= 0.5f;
        return value;
    }
}
