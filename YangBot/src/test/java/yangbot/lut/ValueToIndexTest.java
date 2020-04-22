package yangbot.lut;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import yangbot.util.Range;
import yangbot.util.Tuple;
import yangbot.util.lut.InterpolationUtil;
import yangbot.util.lut.Value2ToIndexFunction;
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

    @Test
    public void test2() {
        ValueHelper vh1 = new ValueHelper(new Range(100, 2300), 22);
        ValueHelper vh2 = new ValueHelper(new Range(0, 180), 10);
        ;

        var func = new Value2ToIndexFunction(vh1, vh2);

        assert func.applyAsInt(new Tuple<>(vh1.getValueForIndex(0), vh2.getValueForIndex(0))) == 0;
        assert func.applyAsInt(new Tuple<>(vh1.getValueForIndex(1), vh2.getValueForIndex(0))) == 1;
        assert func.applyAsInt(new Tuple<>(vh1.getValueForIndex(0), vh2.getValueForIndex(1))) == 23;
        assert func.applyAsInt(new Tuple<>(100f, 18f)) == 23;

        for (int v1 = 0; v1 <= vh1.getMaxIndex(); v1++) {
            assert vh1.getIndexForValue(vh1.getValueForIndex(v1)) == v1 : v1 + " " + vh1.getFloatIndexForValue(vh1.getValueForIndex(v1));
        }
        for (int v2 = 0; v2 <= vh2.getMaxIndex(); v2++) {
            assert vh2.getIndexForValue(vh2.getValueForIndex(v2)) == v2 : v2 + " " + vh2.getValueForIndex(v2);
        }

        for (int v1 = 0; v1 <= vh1.getMaxIndex(); v1++) {
            for (int v2 = 0; v2 <= vh2.getMaxIndex(); v2++) {
                int funcResult = func.applyAsInt(new Tuple<>(vh1.getValueForIndex(v1), vh2.getValueForIndex(v2)));
                int expectedResult = v1 + v2 * (1 + vh1.getMaxIndex());
                String debugString = v1 + " (" + vh1.getValueForIndex(v1) + " (" + vh1.getIndexForValue(vh1.getValueForIndex(v1)) + ")) " + v2 + " (" + vh2.getValueForIndex(v2) + " (" + vh2.getIndexForValue(vh2.getValueForIndex(v2)) + ")); " + funcResult + " != " + expectedResult;
                assert funcResult == expectedResult : debugString;
            }
        }

    }

    @Test
    public void interp() {
        Assertions.assertEquals(0, InterpolationUtil.bilinear((i1, i2) -> i2 * 5, 0.5f, 0f), 0.001f);
        Assertions.assertEquals(5, InterpolationUtil.bilinear((i1, i2) -> i2 * 5, 0.5f, 1), 0.001f);
        Assertions.assertEquals(2.5f, InterpolationUtil.bilinear((i1, i2) -> i2 * 5, 0.5f, 0.5f), 0.001f);
        Assertions.assertEquals(10 + 1 + 15, InterpolationUtil.bilinear((i1, i2) -> i1 * 2 + i2 * 5, 5.5f, 3f), 0.001f);
    }
}
