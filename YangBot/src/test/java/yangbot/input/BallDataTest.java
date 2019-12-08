package yangbot.input;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.openjdk.jmh.runner.options.VerboseMode;
import yangbot.vector.Matrix3x3;
import yangbot.vector.Vector3;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BallDataTest {

    @Test
    public void launchBenchmark() throws RunnerException {
        if (getClass().getSimpleName().contains("_jmhType")) // Fix because JMH doesnt like gradle for some reason
            return;
        Options opt = new OptionsBuilder()
                .include(this.getClass().getName() + ".*")
                .mode(Mode.AverageTime)
                .timeUnit(TimeUnit.MICROSECONDS)
                .warmupTime(TimeValue.seconds(1))
                .warmupIterations(6)
                .measurementTime(TimeValue.milliseconds(200))
                .measurementIterations(4)
                .threads(1)
                .forks(0)
                .shouldFailOnError(true)
                .shouldDoGC(true)
                .verbosity(VerboseMode.NORMAL)
                .build();

        new Runner(opt).run();
    }

    @Benchmark
    public void collide(BenchmarkState state, Blackhole bh) {
        assertNotNull(state.ballState.collide(state.carState));
    }

    @State(Scope.Thread)
    public static class BenchmarkState {
        BallData ballState;
        CarData carState;

        @Setup(Level.Trial)
        public void initialize() {
            ballState = new BallData(new Vector3(0, 0, BallData.COLLISION_RADIUS), new Vector3(), new Vector3());
            carState = new CarData(new Vector3(0, 10, 20), new Vector3(20, 10, 0), new Vector3(50, 20, 100), Matrix3x3.identity());
        }
    }
}