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
    private float maxDistToGoal = 0;

    @Override
    public boolean isImproved(GameData gameData) {
        final CarData car = gameData.getCarData();
        final YangBallPrediction simBallPred = gameData.getBallPrediction();

        final int teamSign = car.getTeamSign();
        final Vector2 ownGoal = new Vector2(0, teamSign * (RLConstants.goalDistance + 1000));

        boolean result = false;

        float minDistToGoal = 9999999;
        for (float time = 0; time < Math.min(3, simBallPred.relativeTimeOfLastFrame()); time += RLConstants.simulationTickFrequency * 2) {
            Optional<YangBallPrediction.YangPredictionFrame> dataAtFrame = simBallPred.getFrameAtRelativeTime(time);
            if (dataAtFrame.isEmpty())
                break;

            ImmutableBallData ballAtFrame = dataAtFrame.get().ballData;
            float dist = (float) ballAtFrame.position.distance(ownGoal.withZ(RLConstants.goalHeight / 2));
            boolean landsInGoal = ballAtFrame.makeMutable().isInOwnGoal(teamSign);

            if (landsInGoal)
                return false;

            if (dist < minDistToGoal)
                minDistToGoal = dist;
        }
        // Ball doesn't land in goal

        if (this.isScoring) {
            this.isScoring = false;
            result = true;
        }

        if (minDistToGoal > this.maxDistToGoal) {
            this.maxDistToGoal = minDistToGoal;
            result = true;
        }

        return result;
    }
}
