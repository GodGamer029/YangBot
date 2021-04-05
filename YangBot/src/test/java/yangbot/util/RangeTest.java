package yangbot.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RangeTest {

    @Test
    public void rangeTest() {
        Range r = new Range(0, 100);
        Assertions.assertEquals(0, r.interpolate(0));
        Assertions.assertEquals(50, r.interpolate(0.5f));
        Assertions.assertEquals(100, r.interpolate(1));

        Assertions.assertEquals(3, r.numSteps(50));
        Assertions.assertEquals(3, r.numSteps(55));
        Assertions.assertEquals(2, r.numSteps(100));

        Assertions.assertEquals(0, r.step(0, 3));
        Assertions.assertEquals(50, r.step(1, 3));
        Assertions.assertEquals(100, r.step(2, 3));

        Assertions.assertEquals(0, r.stepBy(0, 55));
        Assertions.assertEquals(55, r.stepBy(1, 55));
        Assertions.assertEquals(100, r.stepBy(2, 55));

    }

}
