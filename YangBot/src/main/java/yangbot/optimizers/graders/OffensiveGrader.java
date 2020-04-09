package yangbot.optimizers.graders;

import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.input.ImmutableBallData;
import yangbot.input.RLConstants;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.vector.Vector2;

import java.util.Optional;

public class OffensiveGrader extends Grader {

    private final float maximumTimeForGoal = 2.5f;

    private float timeToGoal = maximumTimeForGoal;
    private float minDistanceToGoal = 5000;
    private boolean didLandInGoal = false;

    @Override
    public boolean isImproved(GameData gameData) {
        final CarData car = gameData.getCarData();
        final YangBallPrediction simBallPred = gameData.getBallPrediction();

        final int teamSign = car.team * 2 - 1;
        final Vector2 enemyGoal = new Vector2(0, -teamSign * (RLConstants.goalDistance + 1000));

        boolean result = false;

        for (float time = 0; time < Math.min(3, simBallPred.relativeTimeOfLastFrame()); time += RLConstants.simulationTickFrequency * 2) {
            Optional<YangBallPrediction.YangPredictionFrame> dataAtFrame = simBallPred.getFrameAtRelativeTime(time);
            if (dataAtFrame.isEmpty())
                break;
            ImmutableBallData ballAtFrame = dataAtFrame.get().ballData;
            float dist = (float) ballAtFrame.position.distance(enemyGoal.withZ(RLConstants.goalHeight / 2));
            boolean landsInGoal = time <= maximumTimeForGoal && ballAtFrame.makeMutable().isInEnemyGoal(teamSign);

            if (didLandInGoal) { // The ball has to at least land in the goal to be better than the last simulation
                if (!landsInGoal)
                    continue;

                if (time < timeToGoal) {
                    timeToGoal = time;
                    return true;
                }
            } else {
                if (landsInGoal) { // Lands in goal, but last one didn't? Definitely better than the last
                    didLandInGoal = true;
                    timeToGoal = time;
                    return true;
                } else if (dist < minDistanceToGoal && time > 0.2f) { // Check if it's better than the last sim which also didn't score
                    minDistanceToGoal = dist;
                    result = true;
                }
            }
        }
        return result;
    }
}
