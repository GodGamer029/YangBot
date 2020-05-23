package yangbot.strategy;

import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.input.ImmutableBallData;
import yangbot.input.RLConstants;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.vector.Vector2;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class GenericStrategyPlanner extends StrategyPlanner {

    @Override
    protected void planStrategyInternal() {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final YangBallPrediction ballPrediction = gameData.getBallPrediction();

        final int teamSign = car.getTeamSign();
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

        if (awareness <= -0.3f) {
            newDecidedStrategy = new DefendStrategy();
        } else {
            newDecidedStrategy = new OffensiveStrategy();
        }

        this.setDone();
    }
}
