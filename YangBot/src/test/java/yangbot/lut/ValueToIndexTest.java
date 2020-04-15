package yangbot.lut;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import yangbot.util.Range;
import yangbot.util.lut.ValueHelper;

public class ValueToIndexTest {

    @Test
    public void boi() {
        ValueHelper vh = new ValueHelper(new Range(100, 2300), 22);

        assert vh.getIndexForValue(100) == 0;
        assert vh.getIndexForValue(2300) == 22;

        Assertions.assertEquals(vh.getValueForIndex(0), 100, 0.001f);
        Assertions.assertEquals(vh.getValueForIndex(22), 2300, 0.001f);
    }
}
