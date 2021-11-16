package yangbot.util;

import yangbot.util.math.MathUtils;

import java.io.Serializable;
import java.util.Iterator;

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

    public static Range of(float start, float end) {
        return new Range(start, end);
    }

    public float interpolate(float t) {
        return MathUtils.lerp(start, end, t);
    }

    public int numSteps(float stepBy) {
        assert Math.abs(stepBy) > 0;
        float diff = end - start;
        assert Math.signum(diff) == Math.signum(stepBy);

        return (int) Math.ceil(diff / stepBy) + 1;
    }

    public float step(int step, int numSteps) {
        assert numSteps >= 2 : numSteps;
        assert step >= 0 && step < numSteps : step;

        return MathUtils.lerp(start, end, (float) step / (numSteps - 1));
    }

    public float stepBy(int step, float by) {
        if (step == 0 || by == 0)
            return start;
        assert by > 0;

        float val = start + step * by;
        return MathUtils.clip(val, start, end);
    }

    public StepIter stepBy(float stepBy) {
        assert stepBy > 0;
        return new StepIter(stepBy);
    }

    public StepIter stepWith(int numSteps) {
        assert numSteps > 1 : "steps need to at least include start & end";
        return new StepIter(numSteps);
    }

    public static boolean isInRange(float val, float start, float end) {
        if(start > end){
            float temp = start;
            start = end;
            end = temp;
        }
        return val >= start && val <= end;
    }

    @Override
    public String toString() {
        return "Range(" + start + " - " + end + ")";
    }

    public class StepIter implements Iterator<Float> {
        private final int numSteps;
        private final float stepBy;
        private int curStep = -1;

        private StepIter(float stepBy) {
            this.stepBy = stepBy;
            this.numSteps = Range.this.numSteps(stepBy);
        }

        private StepIter(int numSteps) {
            this.numSteps = numSteps;
            this.stepBy = (end - start) / (numSteps - 1);
        }

        @Override
        public boolean hasNext() {
            return curStep < numSteps - 1;
        }

        @Override
        public Float next() {
            assert hasNext();

            return Range.this.stepBy(++curStep, this.stepBy);
        }

        public Float current() {
            return Range.this.stepBy(curStep, this.stepBy);
        }

        public void reset() {
            this.curStep = -1;
        }
    }
}
