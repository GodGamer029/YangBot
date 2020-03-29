package yangbot.strategy.manuever.kickoff;

import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.input.ImmutableBallData;

public class KickoffTester {

    public static boolean isKickoff() {
        GameData g = GameData.current();
        final ImmutableBallData ball = g.getBallData();
        if (g.getCarData().elapsedSeconds - GameData.timeOfMatchStart >= 3)
            return false;
        return (ball.velocity.flatten().isZero() && ball.position.flatten().isZero());
    }

    public static KickOffLocation getKickoffLocation(CarData car) {
        int xPos = Math.abs(Math.round(car.position.x));
        if (xPos >= 2040 && xPos <= 2056)
            return KickOffLocation.CORNER;
        else if (xPos >= 250 && xPos <= 262)
            return KickOffLocation.MIDDLE;
        else if (xPos == 0)
            return KickOffLocation.CENTER;
        else {
            return KickOffLocation.UNKNOWN;
        }
    }

    public enum KickOffLocation {
        CORNER,
        MIDDLE,
        CENTER,
        UNKNOWN
    }
}
