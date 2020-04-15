package yangbot.util.lut;

import yangbot.util.Range;
import yangbot.util.math.MathUtils;

public class ValueHelper {
    private final Range valueRange;
    private final int numIndices;

    public ValueHelper(Range valueRange, int numIndices) {
        this.valueRange = valueRange;
        this.numIndices = numIndices;

        assert numIndices >= 2;
    }

    public int getIndexForValue(float value) {
        return (int) MathUtils.remapClip(value, this.valueRange.start, this.valueRange.end, 0, this.numIndices);
    }

    public float getValueForIndex(int ind) {
        assert ind >= 0 && ind <= numIndices;
        return MathUtils.remap(ind, 0, this.numIndices, this.valueRange.start, this.valueRange.end);
    }

    public int getMaxIndex() {
        return this.getIndexForValue(this.valueRange.end);
    }
}