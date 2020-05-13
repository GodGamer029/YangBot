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
    private float minDistToGoal = 100000;
    private boolean didLandInGoal = false;

    @Override
    public boolean isImproved(GameData gameData) {
        final CarData car = gameData.getCarData();
        final YangBallPrediction simBallPred = gameData.getBallPrediction();

        final int teamSign = car.team * 2 - 1;

        for (float time = 0; time < Math.min(3, simBallPred.relativeTimeOfLastFrame()); time += RLConstants.simulationTickFrequency * 2) {
            Optional<YangBallPrediction.YangPredictionFrame> dataAtFrame = simBallPred.getFrameAtRelativeTime(time);
            if (dataAtFrame.isEmpty())
                break;
            ImmutableBallData ballAtFrame = dataAtFrame.get().ballData;

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
                }
            }
        }

        if (didLandInGoal)
            return false;

        float dist = 0;
        int distSamples = 0;
        final Vector2 enemyGoal = new Vector2(0, -teamSign * (RLConstants.goalDistance + 100));
        // Take some samples for avg. dist to goal
        for (float time = 0; time < Math.min(1.80f, simBallPred.relativeTimeOfLastFrame()); time += 0.25f) {
            Optional<YangBallPrediction.YangPredictionFrame> dataAtFrame = simBallPred.getFrameAtRelativeTime(time);
            if (dataAtFrame.isEmpty())
                break;

            ImmutableBallData ballAtFrame = dataAtFrame.get().ballData;
            dist += ballAtFrame.position.flatten().distance(enemyGoal);
            distSamples++;
        }

        if (distSamples > 0) {
            dist /= distSamples;
            if (dist < this.minDistToGoal) {
                this.minDistToGoal = dist;
                return true;
            }
        }
        return false;
    }
}
