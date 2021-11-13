package yangbot.optimizers.graders;

import yangbot.input.GameData;
import yangbot.optimizers.model.ModelUtils;
import yangbot.util.Tuple;

public class ValueNetworkGrader extends Grader {

    private float lastError = 1;
    private float bestPrediction = -1;
    public boolean careAboutCars = true;
    public boolean usedAdvancedValuation = false;

    @Override
    public boolean isImproved(GameData gameData) {
        float myTeam = gameData.getCarData().team;
        var simBall = gameData.getBallData();
        var ball = simBall;
        float pred;
        if(careAboutCars && gameData.getAllCars().size() >= 1 && gameData.getAllCars().stream().anyMatch(c -> !c.isDemolished)){
            pred = 0.8f * ModelUtils.gameStateToPrediction(gameData, false, true) + 0.2f * ModelUtils.ballToPrediction(ball).getKey();
            this.usedAdvancedValuation = true;
        }else
            pred = ModelUtils.ballToPrediction(ball).getKey();
        float error = Math.abs(pred - myTeam);
        if (error < lastError) {
            lastError = error;
            bestPrediction = pred;
            return true;
        }
        return false;
    }

    @Override
    public String getAdditionalInfo() {
        return "lastError=" + lastError + " t=" + (bestPrediction != -1 ? bestPrediction : -1)+" usedAdvValue="+this.usedAdvancedValuation;
    }

    @Override
    public float requiredBallPredLength() {
        return 0;
    }
}
