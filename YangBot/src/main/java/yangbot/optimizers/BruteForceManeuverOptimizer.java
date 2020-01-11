package yangbot.optimizers;

import java.util.function.Function;

public class BruteForceManeuverOptimizer<T extends Optimizeable> extends ManeuverOptimizer<T> {


    public BruteForceManeuverOptimizer(T maneuver) {
        super(maneuver);
    }

    // Steps = Number of values evaluated
    public void addVariable(String name, float lowerBound, float upperBound, float steps) {

    }

    @Override
    public void optimize(Function<T, Float> f) {

    }
}
