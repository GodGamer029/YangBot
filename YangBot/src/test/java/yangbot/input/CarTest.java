package yangbot.input;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CarTest {
    @Test
    public void jumpAirTimeTest() {
        Assertions.assertEquals(0.025f, CarData.getJumpHoldDurationForTotalAirTime(1, 650));
        Assertions.assertEquals(0.2f, CarData.getJumpHoldDurationForTotalAirTime(2, 650));
    }
}
