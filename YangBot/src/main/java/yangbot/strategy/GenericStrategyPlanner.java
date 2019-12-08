package yangbot.strategy;

import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.input.RLConstants;
import yangbot.prediction.YangBallPrediction;
import yangbot.vector.Vector2;

public class GenericStrategyPlanner extends StrategyPlanner {
    @Override
    protected void planStrategyInternal() {
        GameData gameData = GameData.current();
        CarData car = gameData.getCarData();

        int teamSign = car.team * 2 - 1;

        float awareness = 0;
        int counter = 0;

        Vector2 myGoal = new Vector2(0, teamSign * RLConstants.goalDistance);
        Vector2 attackingGoal = new Vector2(0, -teamSign * RLConstants.goalDistance);

        float distanceCarToDefend = (float) car.position.flatten().distance(myGoal);
        float distanceCarToAttack = (float) car.position.flatten().distance(attackingGoal);

        YangBallPrediction ballPrediction = gameData.getBallPrediction();
        for (YangBallPrediction.YangPredictionFrame frame : ballPrediction.getFramesBeforeRelative(3f)) {
            counter++;
            if (Math.signum(frame.ballData.position.y) == teamSign) { // On defensive side
                if (Math.abs(frame.ballData.position.y) > RLConstants.goalDistance * 0.5f && Math.signum(frame.ballData.velocity.y) == teamSign) {
                    newDecidedStrategy = new DefendStrategy();
                    this.setDone();
                    break;
                }
                awareness--;
            } else { // On attacking side
                awareness++;
            }

            float distanceBallToDefend = (float) frame.ballData.position.flatten().distance(myGoal);
            float distanceBallToAttack = (float) frame.ballData.position.flatten().distance(attackingGoal);

            if (distanceBallToDefend < distanceCarToDefend)
                awareness -= 0.5f;

            if (distanceBallToAttack < distanceCarToAttack)
                awareness += 0.2f;
        }

        awareness /= counter;

        if (awareness >= 0.5f) {
            newDecidedStrategy = new OffensiveStrategy();
        } else if (awareness <= 0.5f) {
            newDecidedStrategy = new DefendStrategy();
        } else {
            newDecidedStrategy = new NeutralStrategy();
        }

        this.setDone();
    }
}
