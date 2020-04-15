package yangbot.util.lut;

import yangbot.util.Tuple;

import java.util.function.ToIntFunction;

public class Value2ToIndexFunction implements ToIntFunction<Tuple<Float, Float>> {

    private final ValueHelper v1;
    private final ValueHelper v2;

    public Value2ToIndexFunction(ValueHelper v1, ValueHelper v2) {
        this.v1 = v1;
        this.v2 = v2;
    }

    @Override
    public int applyAsInt(Tuple<Float, Float> vn) {
        return this.v1.getIndexForValue(vn.getKey()) + this.v2.getIndexForValue(vn.getValue()) * this.v1.getMaxIndex();
    }
}