package yangbot.input.interrupt;

import yangbot.input.BallTouch;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InterruptManager {

    private static final Map<Long, InterruptManager> interruptManagerMap = new ConcurrentHashMap<>();
    private static float lastBallTouchUpdate = -1;
    private List<WeakReference<Interrupt>> interrupts;

    public InterruptManager(Long aLong) {
        interrupts = Collections.synchronizedList(new ArrayList<>());
    }

    public static InterruptManager get() {
        interruptManagerMap.computeIfAbsent(Thread.currentThread().getId(), InterruptManager::new);
        return interruptManagerMap.get(Thread.currentThread().getId());
    }

    public static void ballTouchInterrupt(BallTouch ballTouch) {
        if (ballTouch.gameSeconds != InterruptManager.lastBallTouchUpdate)
            InterruptManager.lastBallTouchUpdate = ballTouch.gameSeconds;
        else
            return;
        for (var entry : interruptManagerMap.entrySet()) {
            var mgr = entry.getValue();

            mgr.getInterrupts().forEach((inter) -> {
                var boi = inter.get();
                if (boi != null && boi.getClass() == BallTouchInterrupt.class) {
                    ((BallTouchInterrupt) boi).interrupt(ballTouch);
                }
            });
        }
    }

    public List<WeakReference<Interrupt>> getInterrupts() {
        return interrupts;
    }

    public void registerInterrupt(Interrupt interrupt) {
        this.interrupts.add(new WeakReference<>(interrupt));
    }

    public BallTouchInterrupt getBallTouchInterrupt(int carIgnoreIndex) {
        BallTouchInterrupt b = new BallTouchInterrupt(carIgnoreIndex);
        this.registerInterrupt(b);
        return b;
    }


}
