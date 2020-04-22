package yangbot.util.lut;

import yangbot.util.Range;
import yangbot.util.math.MathUtils;

import java.io.Serializable;

public class ValueHelper implements Serializable {
    private static final long serialVersionUID = 1577305232922242245L;
    private final Range valueRange;
    private final int numIndices;

    public ValueHelper(Range valueRange, int numIndices) {
        this.valueRange = valueRange;
        this.numIndices = numIndices;

        assert numIndices >= 2;
    }

    public int getIndexForValue(float value) {
        float val = getFloatIndexForValue(value);
        if (MathUtils.floatsAreEqual(val, (float) Math.ceil(val), 0.01f))
            return (int) Math.ceil(val);
        return (int) val;
    }

    public float getFloatIndexForValue(float value) {
        return MathUtils.remapClip(value, this.valueRange.start, this.valueRange.end, 0, this.numIndices);
    }

    public float getValueForIndex(int ind) {
        assert ind >= 0 && ind <= numIndices;
        return MathUtils.remap(ind, 0, this.numIndices, this.valueRange.start, this.valueRange.end);
    }

    public float getValueForIndexClip(int ind) {
        if (ind > numIndices)
            ind = numIndices;
        else if (ind < 0)
            ind = 0;
        return this.getValueForIndex(ind);
    }

    public int getMaxIndex() {
        return this.getIndexForValue(this.valueRange.end);
    }

    @Override
    public String toString() {
        return "ValueHelper(range=" + valueRange + ", numIndices=" + numIndices + ", maxIndex=" + getMaxIndex() + ")";
    }
}