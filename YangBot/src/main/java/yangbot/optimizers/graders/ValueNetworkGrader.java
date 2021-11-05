package yangbot.optimizers.graders;

import yangbot.input.GameData;
import yangbot.optimizers.model.ModelUtils;
import yangbot.util.Tuple;

public class ValueNetworkGrader extends Grader {

    private float lastError = 1;
    private Tuple<Float, Float> bestPrediction;

    @Override
    public boolean isImproved(GameData gameData) {
        float targetSign = gameData.getCarData().team;
        var simBall = gameData.getBallPrediction().getFrameAtRelativeTime(0.15f);
        var ball = simBall.orElseThrow().ballData;
        var pred = ModelUtils.ballToPrediction(ball.makeMutable());
        float error = Math.abs(pred.getKey() - targetSign);
        if (error < lastError) {
            lastError = error;
            bestPrediction = pred;
            return true;
        }
        return false;
    }

    @Override
    public String getAdditionalInfo() {
        return "lastError=" + lastError + " t=" + (bestPrediction != null ? bestPrediction.getValue() : -1);
    }

    @Override
    public float requiredBallPredLength() {
        return 0.3f;
    }
}
