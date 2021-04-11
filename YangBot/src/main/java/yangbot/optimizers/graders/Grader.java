package yangbot.optimizers.graders;

import yangbot.input.GameData;

public abstract class Grader {

    public abstract boolean isImproved(GameData gameData);

    public String getAdditionalInfo() {
        return "";
    }

    public float requiredBallPredLength() {
        return 5;
    }

}
