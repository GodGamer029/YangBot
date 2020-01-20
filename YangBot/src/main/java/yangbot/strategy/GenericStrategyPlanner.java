package yangbot.strategy;

import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.input.ImmutableBallData;
import yangbot.input.RLConstants;
import yangbot.prediction.YangBallPrediction;
import yangbot.util.math.vector.Vector2;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class GenericStrategyPlanner extends StrategyPlanner {

    public static boolean isOutOfPosition(GameData gameData) {
        // TODO
        final CarData car = gameData.getCarData();
        final YangBallPrediction ballPrediction = gameData.getBallPrediction();
        final ImmutableBallData ballData = gameData.getBallData();

        int teamSign = car.team * 2 - 1;
        Vector2 myGoal = new Vector2(0, teamSign * RLConstants.goalDistance);


        return false;
    }

    @Override
    protected void planStrategyInternal() {
        GameData gameData = GameData.current();
        CarData car = gameData.getCarData();
        final ImmutableBallData ball = gameData.getBallData();
        YangBallPrediction ballPrediction = gameData.getBallPrediction();

        int teamSign = car.team * 2 - 1;
        List<CarData> carsInMyTeam = gameData.getAllCars().parallelStream().filter((c) -> c.team == car.team).collect(Collectors.toList());

        float awareness = 0;
        int counter = 0;

        Vector2 myGoal = new Vector2(0, teamSign * RLConstants.goalDistance);
        Vector2 attackingGoal = new Vector2(0, -teamSign * RLConstants.goalDistance);

        float distanceCarToDefend = (float) car.position.flatten().distance(myGoal);
        float distanceCarToAttack = (float) car.position.flatten().distance(attackingGoal);

        for (YangBallPrediction.YangPredictionFrame frame : ballPrediction.getFramesBeforeRelative(3.5f)) {
            counter++;
            float absoluteBallY = Math.abs(frame.ballData.position.y);
            if (Math.signum(frame.ballData.position.y) == teamSign) { // On defensive side
                if (absoluteBallY > RLConstants.goalDistance * 0.8f && Math.signum(frame.ballData.velocity.y) == teamSign) {
                    newDecidedStrategy = new DefendStrategy();
                    this.setDone();
                    return;
                }
                awareness -= Math.min(1, Math.pow(absoluteBallY / RLConstants.goalDistance, 2));
            } else { // On attacking side
                awareness++;
            }

            float distanceBallToDefend = (float) frame.ballData.position.flatten().distance(myGoal);
            float distanceBallToAttack = (float) frame.ballData.position.flatten().distance(attackingGoal);

            if (distanceBallToDefend < distanceCarToDefend)
                awareness -= 0.5f;

            if (distanceBallToAttack < distanceCarToAttack)
                awareness += 0.3f;
        }

        awareness /= counter;

        if (carsInMyTeam.size() > 1) {
            awareness -= 0.1f; // Temporary change for zombie tournament

            ImmutableBallData ballInFuture = ballPrediction.getFrameAtRelativeTime(0.2f).get().ballData;
            Optional<CarData> closestToBallCar = carsInMyTeam.stream()
                    .filter((carData -> carData.playerIndex != car.playerIndex))
                    .min(Comparator.comparingDouble(c -> c.position
                            .add(c.velocity.mul(0.2))
                            .flatten()
                            .distance(ballInFuture.position.flatten())
                    ));
            if (closestToBallCar.isPresent())
                awareness -= 0.3f;
            else
                awareness += 0.2f;
        }

        if (awareness >= 0.5f) {
            newDecidedStrategy = new OffensiveStrategy();
        } else if (awareness <= 0f) {
            newDecidedStrategy = new DefendStrategy();
        } else {
            newDecidedStrategy = new NeutralStrategy();
        }

        //System.out.println("-> Decided on " + newDecidedStrategy.getClass().getSimpleName() + " with awareness=" + awareness);

        this.setDone();
    }
}
