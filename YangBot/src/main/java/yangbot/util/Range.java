package yangbot.util;

public class Range {

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
}
