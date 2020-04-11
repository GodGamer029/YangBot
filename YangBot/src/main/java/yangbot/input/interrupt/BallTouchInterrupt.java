package yangbot.input.interrupt;

import yangbot.input.BallTouch;

public class BallTouchInterrupt extends Interrupt {

    private int carHideIndex = -1;

    public BallTouchInterrupt(int carHideIndex) {
        this.carHideIndex = carHideIndex;
        this.activate();
    }

    public void interrupt(BallTouch ballTouch) {
        if (ballTouch.playerIndex != this.carHideIndex)
            this.interrupt();
    }
}
