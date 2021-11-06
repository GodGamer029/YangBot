package yangbot.optimizers.graders;

import yangbot.input.GameData;
import yangbot.optimizers.model.ModelUtils;
import yangbot.util.Tuple;

public class ValueNetworkGrader extends Grader {

    private float lastError = 1;
    private float bestPrediction = -1;
    public boolean careAboutCars = true;
    private boolean didWRN = false;

    @Override
    public boolean isImproved(GameData gameData) {
        float myTeam = gameData.getCarData().team;
        var simBall = gameData.getBallPrediction().getFrameAtRelativeTime(0.05f);
        var ball = simBall.orElseThrow().ballData;
        float pred;
        if(careAboutCars && gameData.getAllCars().size() > 1 && gameData.getAllCars().stream().anyMatch(c -> c.team != myTeam && !c.isDemolished)){
            if(!didWRN){
                didWRN = true;
                System.out.println("USED NEW GAMSE TSATETET");
            }
            pred = ModelUtils.gameStateToPrediction(gameData);
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
        return "lastError=" + lastError + " t=" + (bestPrediction != -1 ? bestPrediction : -1);
    }

    @Override
    public float requiredBallPredLength() {
        return 0.2f;
    }
}
