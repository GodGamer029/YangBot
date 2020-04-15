package yangbot.util.lut;

import java.util.function.ToIntFunction;

public class ValueToIndexFunction implements ToIntFunction<Float> {

    private final ValueHelper v1;

    public ValueToIndexFunction(ValueHelper v1) {
        this.v1 = v1;
    }

    @Override
    public int applyAsInt(Float v1) {
        return this.v1.getIndexForValue(v1);
    }
}
