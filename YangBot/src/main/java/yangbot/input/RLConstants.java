package yangbot.input;

import yangbot.vector.Vector2;

public class RLConstants {

    public static final float ballRadius = 92.75f;
    public static final float carHeight = 36.16f;
    public static final float carElevation = 17.01f;

    public static int tickRate = 120;
    public static float tickSpeed = 1f / tickRate;

    public static boolean isPosNearWall(Vector2 pos) {
        float x = Math.abs(pos.x);
        float y = Math.abs(pos.y);

        if (x >= 2700 && x <= 3850) {
            // https://www.wolframalpha.com/input/?i=Line+between+%283850%2C+3850%29+%282700%2C+4950%29
            float val = 173250f / 23f - (22f * x) / 23f;
            if (val <= y)
                return true;
        }

        return x >= 3820 || y >= 4920;
    }
}
