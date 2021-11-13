package yangbot.util.scenario;

import rlbot.cppinterop.RLBotDll;
import rlbot.gamestate.*;
import yangbot.cpp.YangBotJNAInterop;
import yangbot.input.*;
import yangbot.util.AdvancedRenderer;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Vector3;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class Scenario {

    protected Function<Integer, Optional<GameState>> gameStateSupplier = null;
    protected State state = State.RESET;
    protected float resetTransitionDelay = 0.05f;
    protected float delayedResetTransitionDelay = 1f;
    protected Optional<BiConsumer<Float, Integer>> onReset = Optional.empty(), onRunComplete = Optional.empty();
    protected Optional<Consumer<ControlsOutput>> onInit = Optional.empty();
    protected BiFunction<ControlsOutput, Float, RunState> onRun;
    protected Optional<Consumer<Integer>> onScenarioComplete = Optional.empty();
    private float timer = 0;
    private float lastTime = -1;
    private int numInvocations = 0;

    private Scenario() {
    }

    public ControlsOutput processInput(DataPacket input) {
        if (this.lastTime == -1)
            this.lastTime = input.car.elapsedSeconds;
        final float dt = MathUtils.closestMultiple(input.car.elapsedSeconds - this.lastTime, RLConstants.tickFrequency);
        this.lastTime = input.car.elapsedSeconds;

        final GameData gameData = GameData.current();
        final AdvancedRenderer renderer = AdvancedRenderer.forBotIndex(input.playerIndex);
        gameData.update(input.car, new ImmutableBallData(input.ball), input.allCars, input.gameInfo, dt, renderer, YangBotJNAInterop.getBallPrediction(input.ball, RLConstants.tickRate, 5));
        ControlsOutput controlsOutput = new ControlsOutput();

        switch (this.state) {
            case INVALID:
                assert false;
            case DELAYED_RESET: {
                if (this.timer < 0)
                    break; // if timer >= 0, fallthrough
                else
                    this.reset();
            }
            case RESET: {
                this.onReset.ifPresent(c -> c.accept(this.timer, this.numInvocations));
                this.timer = 0;
                var targetGameState = this.gameStateSupplier.apply(this.numInvocations);
                if (targetGameState.isEmpty()) {
                    assert this.numInvocations > 0;
                    this.state = State.INVALID;
                    this.onScenarioComplete.ifPresent(c -> c.accept(this.numInvocations - 1));
                    return null; // Signal the scenario loader that we are done
                }
                RLBotDll.setGameState(targetGameState.get().buildPacket());
                this.state = State.INIT;
                break;
            }
            case INIT: {
                if (timer > resetTransitionDelay) {
                    this.timer = 0;
                    this.onInit.ifPresent(c -> c.accept(controlsOutput));
                    this.state = State.RUN;
                } else
                    break;
            }
            case RUN: {
                var runState = this.onRun.apply(controlsOutput, this.timer);
                switch (runState) {
                    case RESET:
                        this.onRunComplete.ifPresent(c -> c.accept(this.timer, this.numInvocations));
                        this.reset();
                        break;
                    case CONTINUE:
                        break;
                    case DELAYED_RESET:
                        this.onRunComplete.ifPresent(c -> c.accept(this.timer, this.numInvocations));
                        this.state = State.DELAYED_RESET;
                        this.timer = -this.delayedResetTransitionDelay;
                        break;
                    case COMPLETE:
                        this.onRunComplete.ifPresent(c -> c.accept(this.timer, this.numInvocations));
                        this.state = State.INVALID;
                        this.onScenarioComplete.ifPresent(c -> c.accept(this.numInvocations));
                        return null; // Signal the scenario loader that we are done
                    default:
                        assert false;
                }
                break;
            }
        }
        if (this.state != State.RESET)
            this.timer += dt; // preserver timer for reset
        return controlsOutput;
    }

    public void reset() {
        this.state = State.RESET;
        this.numInvocations++;
    }

    public enum State {
        DELAYED_RESET,
        RESET,
        INIT,
        RUN,
        INVALID
    }

    public enum RunState {
        CONTINUE,
        RESET,
        DELAYED_RESET,
        COMPLETE
    }

    public static class Builder {
        private final Scenario scenario = new Scenario();

        private GameState staticGameState = null;
        private Function<Integer, Optional<GameState>> dynamicGameState = null;

        public Scenario build() {
            assert dynamicGameState != null || staticGameState != null;
            assert dynamicGameState == null || staticGameState == null;

            if (staticGameState != null) {
                // Reset game speed, if not set
                if (staticGameState.getGameInfoState() == null)
                    staticGameState.withGameInfoState(new GameInfoState().withGameSpeed(1f));
                else if (staticGameState.getGameInfoState().getGameSpeed() == null)
                    staticGameState.getGameInfoState().withGameSpeed(1f);

                // move ball out of the way if its not needed
                if (staticGameState.getBallState() == null)
                    staticGameState.withBallState(new BallState().withPhysics(new PhysicsState().withLocation(new Vector3(0, 0, RLConstants.arenaHeight + 300).toDesiredVector()).withVelocity(new DesiredVector3(0f, 0f, 0f))));

                this.scenario.gameStateSupplier = (num) -> Optional.of(staticGameState);
            } else {
                this.scenario.gameStateSupplier = (num) -> {
                    var stateOpt = dynamicGameState.apply(num);
                    if (stateOpt.isEmpty())
                        return stateOpt;
                    var state = stateOpt.get();
                    if (state.getGameInfoState() == null)
                        state.withGameInfoState(new GameInfoState().withGameSpeed(1f));
                    else if (state.getGameInfoState().getGameSpeed() == null)
                        state.getGameInfoState().withGameSpeed(1f);

                    // move ball out of the way if its not needed
                    if (state.getBallState() == null)
                        state.withBallState(new BallState().withPhysics(new PhysicsState().withLocation(new Vector3(0, 0, RLConstants.arenaHeight + 300).toDesiredVector()).withVelocity(new DesiredVector3(0f, 0f, 0f))));

                    return Optional.of(state);
                };
            }

            return this.scenario;
        }

        public Builder withGameState(GameState state) {
            this.staticGameState = state;
            return this;
        }

        public Builder withDynamicGameState(Function<Integer, Optional<GameState>> stateMaker) {
            this.dynamicGameState = stateMaker;
            return this;
        }

        public Builder withReset(BiConsumer<Float, Integer> onReset) {
            this.scenario.onReset = Optional.of(onReset);
            return this;
        }

        public Builder withInit(Consumer<ControlsOutput> onInit) {
            this.scenario.onInit = Optional.of(onInit);
            return this;
        }

        public Builder withRun(BiFunction<ControlsOutput, Float, RunState> onRun) {
            this.scenario.onRun = onRun;
            return this;
        }

        public Builder withOnComplete(Consumer<Integer> onComplete) {
            this.scenario.onScenarioComplete = Optional.of(onComplete);
            return this;
        }

        public Builder withOnRunComplete(BiConsumer<Float, Integer> onComplete) {
            this.scenario.onRunComplete = Optional.of(onComplete);
            return this;
        }

        public Builder withTransitionDelay(float delay) {
            assert delay >= 0f;
            this.scenario.resetTransitionDelay = delay;
            return this;
        }
    }
}
