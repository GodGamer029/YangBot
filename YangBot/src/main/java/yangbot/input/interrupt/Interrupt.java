package yangbot.input.interrupt;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class Interrupt {

    private AtomicBoolean interrupted = new AtomicBoolean(false);
    private boolean isActive = false;

    public boolean hasInterrupted() {
        return interrupted.getAndSet(false);
    }

    public void interrupt() {
        if (this.isActive)
            this.interrupted.set(true);
    }

    public void activate() {
        this.isActive = true;
        this.interrupted.set(false);
    }

    public void deactivate() {
        this.isActive = false;
        this.interrupted.set(false);
    }
}
