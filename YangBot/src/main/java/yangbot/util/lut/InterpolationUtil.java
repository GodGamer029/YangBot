package yangbot.util.lut;

import java.util.function.ToDoubleBiFunction;

public class InterpolationUtil {
    public static float bilinear(ToDoubleBiFunction<Integer, Integer> indexToValue, float x, float y) {
        final int x1 = (int) x;
        final int y1 = (int) y;
        final int x2 = (int) Math.ceil(x);
        final int y2 = (int) Math.ceil(y);

        final double Q11 = indexToValue.applyAsDouble(x1, y1);
        final double Q12 = indexToValue.applyAsDouble(x1, y2);
        final double Q21 = indexToValue.applyAsDouble(x2, y1);
        final double Q22 = indexToValue.applyAsDouble(x2, y2);

        x -= x1;
        y -= y1;

        assert x >= 0 && x <= 1;
        assert y >= 0 && y <= 1;

        return (float) (Q11 * (1 - x) * (1 - y) + Q21 * x * (1 - y) + Q12 * (1 - x) * y + Q22 * x * y);
    }
}
