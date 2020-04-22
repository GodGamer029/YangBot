package yangbot.util.lut;

import org.jetbrains.annotations.NotNull;
import yangbot.util.Tuple;

import java.util.HashMap;

public class LutManager {

    private static final LutManager INSTANCE = new LutManager();

    private final HashMap<LutIdentifier, ArrayLutTable> lutMap;

    private LutManager() {
        this.lutMap = new HashMap<>();
    }

    public static LutManager get() {
        return INSTANCE;
    }

    public void registerLut(@NotNull LutIdentifier identifier, @NotNull ArrayLutTable lutTable) {
        this.lutMap.put(identifier, lutTable);
    }

    public ArrayLutTable<Tuple<Float, Float>, PowerslideEntry, Value2ToIndexFunction> getDriftLut() {
        var lut = this.lutMap.get(LutIdentifier.DRIFT);
        return lut;
    }

    @NotNull
    public ArrayLutTable getTable(LutIdentifier identifier) {
        return this.lutMap.get(identifier);
    }

    public enum LutIdentifier {
        DRIFT
    }
}
