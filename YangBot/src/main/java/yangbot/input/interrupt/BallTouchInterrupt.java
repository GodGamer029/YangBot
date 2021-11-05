package yangbot.input.interrupt;

import yangbot.input.BallTouch;

public class BallTouchInterrupt extends Interrupt {

    private int carHideIndex;

    public BallTouchInterrupt(int carHideIndex) {
        this.carHideIndex = carHideIndex;
        this.activate();
    }

    public void interrupt(BallTouch ballTouch) {
        if (ballTouch.playerIndex != this.carHideIndex)
            this.interrupt();
    }
}
