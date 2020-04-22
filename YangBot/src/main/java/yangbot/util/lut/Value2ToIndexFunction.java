package yangbot.util.lut;

import yangbot.util.Tuple;

import java.io.Serializable;
import java.util.function.ToIntFunction;

public class Value2ToIndexFunction implements ToIntFunction<Tuple<Float, Float>>, Serializable {

    private static final long serialVersionUID = 7217380017864590402L;

    private final ValueHelper v1;
    private final ValueHelper v2;

    public Value2ToIndexFunction(ValueHelper v1, ValueHelper v2) {
        this.v1 = v1;
        this.v2 = v2;
    }

    public ValueHelper getValueHelper1() {
        return v1;
    }

    public ValueHelper getValueHelper2() {
        return v2;
    }

    public int i2ToIndex(int i1, int i2) {
        return i1 + i2 * (1 + this.v1.getMaxIndex());
    }

    @Override
    public int applyAsInt(Tuple<Float, Float> vn) {
        return this.i2ToIndex(this.v1.getIndexForValue(vn.getKey()), this.v2.getIndexForValue(vn.getValue()));
    }
}