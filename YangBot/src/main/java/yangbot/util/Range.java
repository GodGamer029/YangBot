package yangbot.util;

import java.io.Serializable;

public class Range implements Serializable {

    private static final long serialVersionUID = -6171006590187830450L;

    public final float start; // inclusive
    public final float end; // exclusive

    public Range(float start, float end) {
        assert start < end;

        this.start = start;
        this.end = end;
    }

    public Range(float end) {
        this(0, end);
    }

    public boolean contains(float val) {
        return val >= start && val < end;
    }

    public static boolean isInRange(float val, float start, float end) {
        return val >= start && val <= end;
    }

    @Override
    public String toString() {
        return "Range(" + start + " - " + end + ")";
    }
}
