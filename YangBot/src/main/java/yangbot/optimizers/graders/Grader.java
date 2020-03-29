package yangbot.optimizers.graders;

import yangbot.input.GameData;

public abstract class Grader {

    public abstract boolean isImproved(GameData gameData);

}
