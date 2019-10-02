package yangbot.input.fieldinfo;


import yangbot.vector.Vector3;

/**
 * Representation of one of the boost pads on the field.
 * <p>
 * This class is here for your convenience, it is NOT part of the framework. You can change it as much
 * as you want, or delete it.
 */
public class BoostPad {

    private final Vector3 location;
    private final boolean isFullBoost;
    private boolean isActive;
    private float boostEatenTime = -1;
    private float currentGameTime = 1;

    public BoostPad(Vector3 location, boolean isFullBoost) {
        this.location = location;
        this.isFullBoost = isFullBoost;
    }

    public void setCurrentGameTime(float currentGameTime) {
        this.currentGameTime = currentGameTime;
    }

    public Vector3 getLocation() {
        return location;
    }

    public boolean isFullBoost() {
        return isFullBoost;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        if (!active && isActive)
            boostEatenTime = currentGameTime;
        isActive = active;
    }

    public float boostAvailableIn() {
        return isActive ? 0 : (boostEatenTime + (isFullBoost() ? 10 : 4)) - currentGameTime;
    }
}
