package yangbot.strategy.advisor;

import javafx.util.Pair;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.input.ImmutableBallData;
import yangbot.input.RLConstants;
import yangbot.util.math.vector.Vector2;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class RotationAdvisor {

    private static boolean isInfrontOfBall(CarData car, ImmutableBallData ball) {
        return !isAheadOfBall(car, ball);
    }

    private static boolean isAheadOfBall(CarData car, ImmutableBallData ball) {
        if (Math.signum(ball.position.y - car.position.y) == car.getTeamSign())
            return true;

        final Vector2 enemyGoal = new Vector2(0, -car.getTeamSign() * (RLConstants.goalDistance + 1000));

        return car.position.flatten().distance(enemyGoal) + 50 < ball.position.flatten().distance(enemyGoal);
    }

    private static boolean isHeadedBack(CarData car) {
        final Vector2 ownGoal = new Vector2(0, car.getTeamSign() * (RLConstants.goalDistance + 1000));

        final Vector2 carToOwnGoal = ownGoal.sub(car.position.flatten()).normalized();

        if (car.forward().flatten().dot(carToOwnGoal) > 0.3f /*facing own goal*/ && Math.abs(car.angularVelocity.z) < 1 /*not steering*/ && carToOwnGoal.dot(car.velocity.flatten()) > 300 /*on the move*/)
            return true;

        return false;
    }

    public static Advice whatDoIDo(final GameData gameData) {
        final CarData localCar = gameData.getCarData();
        // teammates includes local car
        final List<CarData> teammates = gameData.getAllCars().stream().filter(c -> c.team == localCar.team).collect(Collectors.toList());
        final ImmutableBallData ballData = gameData.getBallData();

        if (isAheadOfBall(localCar, ballData)) {
            // We are ahead of the ball
            return Advice.RETREAT;
        }

        if (teammates.size() <= 1)
            return Advice.ATTACK;

        // Find car with least time to ball
        Optional<CarData> attackingCarOptional = teammates.stream()
                .filter(c -> isInfrontOfBall(c, ballData))
                .filter(c -> !isHeadedBack(c))
                .map(c -> new Pair<>(c, c.position.distance(ballData.position) / (c.forward().flatten().dot(c.velocity.flatten()) + 50)))
                .min(Comparator.comparingDouble(Pair::getValue))
                .map(Pair::getKey); // Convert back to CarData

        if (attackingCarOptional.isEmpty())
            return Advice.IDLE;

        final CarData attackingCar = attackingCarOptional.get();

        // TODO: recognize passing plays

        return attackingCar.playerIndex == localCar.playerIndex ? Advice.ATTACK : Advice.IDLE;
    }

    public enum Advice {
        RETREAT,
        ATTACK,
        PREPARE_FOR_PASS,
        IDLE,
        NO_ADVICE
    }
}
