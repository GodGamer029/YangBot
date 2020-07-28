package yangbot.optimizers.graders;

import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.input.ImmutableBallData;
import yangbot.input.RLConstants;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.vector.Vector2;

import java.util.Optional;

public class DefensiveGrader extends Grader {

    private boolean isScoring = true;
    private float timeToGoal = 0;
    private float maxDistToOwnGoal = 0;

    @Override
    public boolean isImproved(GameData gameData) {
        final CarData car = gameData.getCarData();
        final YangBallPrediction simBallPred = gameData.getBallPrediction();
        final int teamSign = car.getTeamSign();

        boolean result = false;

        for (float time = 0; time < Math.min(3, simBallPred.relativeTimeOfLastFrame()); time += RLConstants.simulationTickFrequency * 2) {
            Optional<YangBallPrediction.YangPredictionFrame> dataAtFrame = simBallPred.getFrameAtRelativeTime(time);
            if (dataAtFrame.isEmpty())
                break;

            ImmutableBallData ballAtFrame = dataAtFrame.get().ballData;
            boolean landsInGoal = ballAtFrame.makeMutable().isInOwnGoal(teamSign);

            if (landsInGoal) {
                if (time > this.timeToGoal) {
                    this.timeToGoal = time;
                }

                return false;
            }

        }
        // Ball doesn't land in goal

        if (this.isScoring) {
            this.isScoring = false;
            result = true;
        }

        // Next goal: go away from goal asap
        final Vector2 ownGoal = new Vector2(0, teamSign * RLConstants.goalDistance);

        float dist = 0;
        int distSamples = 0;
        // Take some samples for avg. dist to own goal
        for (float time = 0; time < Math.min(2.5f, simBallPred.relativeTimeOfLastFrame()); time += 0.25f) {
            Optional<YangBallPrediction.YangPredictionFrame> dataAtFrame = simBallPred.getFrameAtRelativeTime(time);
            if (dataAtFrame.isEmpty())
                break;

            ImmutableBallData ballAtFrame = dataAtFrame.get().ballData;
            dist += ballAtFrame.position.flatten().distance(ownGoal);
            distSamples++;
        }

        if (distSamples > 0) {
            dist /= distSamples;
            if (dist > this.maxDistToOwnGoal) {
                this.maxDistToOwnGoal = dist;
                result = true;
            }
        }

        //if (this.offensiveGrader.isImproved(gameData)) {
        //    result = true;
        //}

        return result;
    }

    @Override
    public String getAdditionalInfo() {
        return "isScoring=" + this.isScoring + " timeToGoal=" + this.timeToGoal + (this.isScoring ? "" : "maxDistToOwnGoal=" + maxDistToOwnGoal);
    }
}
