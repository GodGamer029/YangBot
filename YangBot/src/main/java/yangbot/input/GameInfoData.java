package yangbot.input;

import rlbot.flat.GameInfo;

public class GameInfoData {

    public final float gameSpeed, gameTimeRemaining, secondsElapsed;
    public final boolean isMatchEnded, isKickoffPause, isRoundActive, isUnlimitedTime, isOvertime;

    public GameInfoData(GameInfo gameInfo) {
        this.gameSpeed = gameInfo.gameSpeed();
        this.gameTimeRemaining = gameInfo.gameTimeRemaining();
        this.secondsElapsed = gameInfo.secondsElapsed();

        this.isMatchEnded = gameInfo.isMatchEnded();
        this.isKickoffPause = gameInfo.isKickoffPause();
        this.isRoundActive = gameInfo.isRoundActive();
        this.isUnlimitedTime = gameInfo.isUnlimitedTime();
        this.isOvertime = gameInfo.isOvertime();
    }
}
