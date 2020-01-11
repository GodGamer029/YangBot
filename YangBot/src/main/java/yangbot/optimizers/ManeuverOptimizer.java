package yangbot.optimizers;

import yangbot.util.math.MathUtils;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.function.Function;

public abstract class ManeuverOptimizer<T extends Optimizeable> {

    protected T maneuver;

    public ManeuverOptimizer(T maneuver) {
        this.maneuver = maneuver;
    }

    // Optimizes ? for lowest output
    public abstract void optimize(Function<T, Float> f);


    public static class OptimizeableParameter implements Iterator<Float> {
        public final String name;
        public final float lowerBound;
        public final float upperBound;
        private float stepsTaken = 0;
        private int steps;

        public OptimizeableParameter(String name, float lowerBound, float upperBound, int steps) {
            this.name = name;
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
            this.steps = steps;

            assert steps > 1;
            assert this.upperBound > this.lowerBound;
        }

        public void updateObject(Object o) {
            assert hasNext();
            try {
                Class<?> clazz = o.getClass();
                Field f = clazz.getField(this.name);
                f.setFloat(o, this.next());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void reset() {
            this.stepsTaken = 0;
        }


        @Override
        public boolean hasNext() {
            return stepsTaken < steps;
        }

        @Override
        public Float next() {
            float stepsTaken = this.stepsTaken;
            this.stepsTaken++;
            if (stepsTaken <= 0)
                return lowerBound;
            if (stepsTaken >= steps)
                return upperBound;

            return MathUtils.lerp(this.lowerBound, this.upperBound, (float) stepsTaken / (steps - 1));
        }
    }
}