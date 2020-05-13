package yangbot.input.playerinfo;

import yangbot.input.CarData;

public class PlayerInfo {

    private final String combName;
    private float inactiveRotatorUntil = -1;
    private CarData currentCarData;

    public PlayerInfo(String name) {
        this.combName = name;
    }

    public void update(CarData car) {
        this.currentCarData = car;
    }

    public void setInactiveRotatorUntil(float time) {
        this.inactiveRotatorUntil = Math.max(this.inactiveRotatorUntil, time);
    }

    public boolean isActiveRotator() {
        return this.currentCarData.elapsedSeconds > this.inactiveRotatorUntil;
    }

}
