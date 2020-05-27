package yangbot.input;

import yangbot.util.math.vector.Vector3;

public class YangWheelInfo {

    private final Vector3 frontAxle, backAxle;
    private final float frontWheelRadius, backWheelRadius;

    public YangWheelInfo(Vector3 frontAxle, Vector3 backAxle, float frontWheelRadius, float backWheelRadius) {
        this.frontAxle = frontAxle;
        this.backAxle = backAxle;
        this.frontWheelRadius = frontWheelRadius;
        this.backWheelRadius = backWheelRadius;
    }

    public static YangWheelInfo octane() {
        return new YangWheelInfo(new Vector3(51.25f, 25.90f, -6.00f), new Vector3(-33.75f, 29.50f, -4.3f), 12.50f, 15f);
    }

    public YangWheel get(int forward, int side) {
        assert forward == -1 || forward == 1;
        assert side == -1 || side == 1 || side == 0;

        final Vector3 axle = forward == 1 ? frontAxle : backAxle;
        final float wheelRadius = forward == 1 ? frontWheelRadius : backWheelRadius;

        return new YangWheel(axle.mul(1, side, 1), wheelRadius);
    }

    public static class YangWheel {
        public final Vector3 localPos;
        public final float radius;

        public YangWheel(Vector3 localPos, float radius) {
            this.localPos = localPos;
            this.radius = radius;
        }
    }
}
