package yangbot.util;

import org.junit.jupiter.api.Test;
import yangbot.input.CarData;
import yangbot.util.math.Car1D;

public class MathPerfTest {

    @Test
    public void car1dTest() {
        float tmp = 0;

        for (int i = 0; i < 10000; i++) { // JIT
            tmp += Car1D.simulateDriveDistanceForwardAccel(800, (float) Math.random() * CarData.MAX_VELOCITY, (float) Math.random() * 100).distanceTraveled;
        }
        try {
            Thread.sleep(500);
        } catch (Exception e) {

        }


        long ns = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            tmp += Car1D.simulateDriveDistanceForwardAccel(800, (float) Math.random() * CarData.MAX_VELOCITY, (float) Math.random() * 100).distanceTraveled;
        }
        ns = System.nanoTime() - ns;
        System.out.println("Took: " + (ns * 0.000001f / 1000));
    }

}
