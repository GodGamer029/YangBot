package yangbot.util;

import rlbot.gamestate.GameState;
import yangbot.input.ControlsOutput;
import yangbot.input.DataPacket;

public class Scenario {

    protected GameState gameState = null;
    protected State state = State.RESET;
    protected State nextState = State.INVALID;
    protected float resetTransitionDelay = 0.05f;
    private float timer = 0;
    private float lastTime = -1;

    private Scenario() {
    }

    ;

    public ControlsOutput processInput(DataPacket input) {
        if (this.lastTime == -1)
            this.lastTime = input.car.elapsedSeconds;
        final float dt = input.car.elapsedSeconds - this.lastTime;
        this.timer += dt;

        switch (this.state) {
            case INVALID: {
                assert false;
            }
            break;
            case RESET: {
                this.timer = -this.resetTransitionDelay;
                this.nextState = State.RUN;
            }
            break;
            case CHANGE_STATE: {
                if (this.timer >= 0) {
                    this.state = this.nextState;
                    this.nextState = State.INVALID;
                    this.timer = 0;
                }
            }
            break;
            case RUN: {
                // TODO ALL THIS
            }
            break;
        }
        return null;
    }

    enum State {
        RESET,
        CHANGE_STATE,
        RUN,
        INVALID
    }

    ;

    public static class Builder {

    }
}
