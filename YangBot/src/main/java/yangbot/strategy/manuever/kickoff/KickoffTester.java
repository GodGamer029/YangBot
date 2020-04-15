package yangbot.strategy.manuever.kickoff;

import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.input.ImmutableBallData;
import yangbot.util.Tuple;
import yangbot.util.math.MathUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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
            return KickOffLocation.OFF_CENTER;
        else if (MathUtils.floatsAreEqual(xPos, 0, 1))
            return KickOffLocation.CENTER;
        else {
            return KickOffLocation.UNKNOWN;
        }
    }

    public static boolean shouldGoForKickoff(CarData localCar, List<CarData> teammates, BallData ballData) {
        if (teammates.size() <= 1) // Only 1 bot
            return true;

        assert teammates.contains(localCar);

        List<Tuple<CarData, /*distance to ball*/Double>> temp = new ArrayList<>(teammates)
                .stream()
                .map((c) -> new Tuple<>(c, c.position.distance(ballData.position)))
                .sorted(Comparator.comparingDouble(Tuple::getValue)) // Least distance to ball goes
                .limit(2)
                .collect(Collectors.toList());

        Tuple<CarData, Double> first = temp.get(0);
        Tuple<CarData, Double> second = temp.get(1);

        if (first.getKey().playerIndex != localCar.playerIndex && second.getKey().playerIndex != localCar.playerIndex)
            return false; // We are not any of the closest 2 cars

        if (MathUtils.doublesAreEqual(first.getValue(), second.getValue(), 1)) {
            // Distance is equal for both cars? Left goes!
            temp.sort(Comparator.comparingDouble(p -> p.getKey().position.x * p.getKey().getTeamSign()));
        }

        return temp.get(0).getKey().playerIndex == localCar.playerIndex;
    }

    public enum KickOffLocation {
        CORNER,
        OFF_CENTER,
        CENTER,
        UNKNOWN
    }
}
