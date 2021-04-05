package yangbot.strategy.advisor;

import yangbot.input.*;
import yangbot.util.PosessionUtil;
import yangbot.util.Tuple;
import yangbot.util.math.vector.Vector2;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class RotationAdvisor {

    public static boolean isInfrontOfBall(CarData car, ImmutableBallData ball) {
        return !isAheadOfBall(car, ball);
    }

    public static boolean isAheadOfBall(CarData car, ImmutableBallData ball) {
        if (car.hitbox.getClosestPointOnHitbox(car.position, ball.position).distance(ball.position) - BallData.COLLISION_RADIUS < 80)
            return false;

        if (Math.signum(ball.position.y - car.position.y) == car.getTeamSign())
            return true;

        final Vector2 enemyGoal = new Vector2(0, -car.getTeamSign() * (RLConstants.goalDistance + 1000));

        return car.position.flatten().distance(enemyGoal) + 50 < ball.position.flatten().distance(enemyGoal);
    }

    public static boolean isHeadedBack(CarData car) {
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

        var possession = PosessionUtil.getPossession(gameData, gameData.getBallPrediction());
        if (possession.isEmpty()) {
            // mate has posession
        } else {
            // value
        }

        if (teammates.size() <= 1) {
            //System.out.println("Posession: " + (possession.isEmpty() ? "empty" : possession.get()));
            if (possession.isEmpty() || possession.get() <= -0.1f) {

                return Advice.RETREAT;
            }

            if (possession.get() < 0.2f) // Enemy has more posession
                return Advice.IDLE;
            return Advice.ATTACK;
        }


        long numberOfCarsAbleToDefend = teammates.stream()
                .filter(c -> !isAheadOfBall(c, ballData))
                .filter(c -> c.position.distance(ballData.position) > 300)
                .count();

        // Find car with least time to ball
        Optional<CarData> attackingCarOptional = teammates.stream()
                .filter(c -> c.getPlayerInfo().isActiveShooter())
                .filter(c -> isInfrontOfBall(c, ballData))
                .filter(c -> !isHeadedBack(c))
                .filter(c -> {
                    // Bypass for small distances
                    if (localCar.position.flatten().distance(ballData.position.flatten()) < 500)
                        return true;

                    float yDist = Math.abs(localCar.position.y - ballData.position.y);
                    float xDist = Math.abs(localCar.position.x - ballData.position.x);

                    return yDist > xDist * 0.75f; // Attack from middle, not side
                })
                .map(c -> new Tuple<>(c, c.position.distance(ballData.position) / Math.max(50, (c.forward().flatten().dot(c.velocity.flatten()) + 50))))
                .min(Comparator.comparingDouble(Tuple::getValue))
                .map(Tuple::getKey); // Convert back to CarData

        if (attackingCarOptional.isEmpty())
            return Advice.IDLE;

        final CarData attackingCar = attackingCarOptional.get();

        // TODO: recognize passing plays
        if (numberOfCarsAbleToDefend <= 1) {
            if (attackingCar.playerIndex == localCar.playerIndex) {
                if (Math.abs(localCar.position.y - ballData.position.y) < 1000)
                    return Advice.RETREAT;
                return Advice.IDLE;
            } else
                return Advice.RETREAT;
        }

        return attackingCar.playerIndex == localCar.playerIndex ? Advice.ATTACK : Advice.PREPARE_FOR_PASS;
    }

    public enum Advice {
        RETREAT,
        ATTACK,
        PREPARE_FOR_PASS,
        IDLE,
        NO_ADVICE
    }
}
