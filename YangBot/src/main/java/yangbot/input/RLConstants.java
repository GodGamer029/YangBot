package yangbot.input;

import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

public class RLConstants {

    public static final float carElevation = 17.01f;

    public static final float goalDistance = 5120f; // Distance from center to goal
    public static final float goalCenterToPost = 892.755f;
    public static final float goalHeight = 642.775f;

    public static final float arenaHeight = 2044;
    public static final float arenaWidth = 8192;
    public static final float arenaHalfWidth = arenaWidth / 2;
    public static final float arenaLength = 10240;
    public static final float arenaHalfLength = arenaLength / 2;

    public static Vector3 gravity = new Vector3(0, 0, -650);

    public static int tickRate = 120;
    public static float tickFrequency = 1f / tickRate;

    public static int simulationTickRate = 60;
    public static float simulationTickFrequency = 1f / simulationTickRate;

    public static boolean isPosNearWall(Vector2 pos, float tolerance) {
        float x = Math.abs(pos.x) + tolerance;
        float y = Math.abs(pos.y);

        if (x >= 2700 - tolerance && x <= 3850 + tolerance) {
            // https://www.wolframalpha.com/input/?i=Line+between+%283850%2C+3850%29+%282700%2C+4950%29
            float val = 173250f / 23f - (22f * x) / 23f;
            if (val <= y + tolerance)
                return true;
        }

        return x >= 3820 || y + tolerance >= 4920;
    }

    public static boolean isOutOfBounds(Vector3 pos){
        if(pos.z < 0 || pos.z > RLConstants.arenaHeight)
            return true;

        float x = Math.abs(pos.x);
        float y = Math.abs(pos.y);

        if(x - 50 >= RLConstants.arenaHalfWidth)
            return true;

        if (x - 200 >= 2700) {
            // https://www.wolframalpha.com/input/?i=Line+between+%283850%2C+3850%29+%282700%2C+4950%29
            float val = 173250f / 23f - (22f * (x - 200)) / 23f;
            if (val <= y - 200)
                return true;
        }

        if(y - 50 > RLConstants.goalDistance){
            if(isPosOnBackWall(pos, 0))
                return true;
            if(y > RLConstants.goalDistance + 1000)
                return true;
        }

        return false;
    }

    public static boolean isPosOnBackWall(Vector3 pos, float tolerance){
        return (Math.abs(pos.y) > RLConstants.arenaHalfLength - tolerance) &&
                (Math.abs(pos.x) > RLConstants.goalCenterToPost - tolerance || pos.z > RLConstants.goalHeight - tolerance);

    }
}
