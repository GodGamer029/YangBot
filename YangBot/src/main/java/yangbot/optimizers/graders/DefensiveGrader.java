package yangbot.optimizers.graders;

import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.input.ImmutableBallData;
import yangbot.input.RLConstants;
import yangbot.util.YangBallPrediction;

import java.util.Optional;

public class DefensiveGrader extends Grader {

    private boolean isScoring = true;

    private OffensiveGrader offensiveGrader = new OffensiveGrader();

    @Override
    public boolean isImproved(GameData gameData) {
        final CarData car = gameData.getCarData();
        final YangBallPrediction simBallPred = gameData.getBallPrediction();
        final int teamSign = car.getTeamSign();

        boolean result = false;

        for (float time = 0; time < Math.min(3, simBallPred.relativeTimeOfLastFrame()); time += RLConstants.simulationTickFrequency * 4) {
            Optional<YangBallPrediction.YangPredictionFrame> dataAtFrame = simBallPred.getFrameAtRelativeTime(time);
            if (dataAtFrame.isEmpty())
                break;

            ImmutableBallData ballAtFrame = dataAtFrame.get().ballData;
            boolean landsInGoal = ballAtFrame.makeMutable().isInOwnGoal(teamSign);

            if (landsInGoal)
                return false;
        }
        // Ball doesn't land in goal

        if (this.isScoring) {
            this.isScoring = false;
            result = true;
        }

        if (this.offensiveGrader.isImproved(gameData)) {
            result = true;
        }

        return result;
    }
}
