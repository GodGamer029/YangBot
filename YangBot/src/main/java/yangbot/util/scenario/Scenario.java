package yangbot.util.scenario;

import rlbot.cppinterop.RLBotDll;
import rlbot.gamestate.*;
import yangbot.input.*;
import yangbot.util.AdvancedRenderer;
import yangbot.util.math.vector.Vector3;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class Scenario {

    protected GameState gameState = null;
    protected State state = State.RESET;
    protected float resetTransitionDelay = 0.05f;
    protected float delayedResetTransitionDelay = 1f;
    protected Optional<Consumer<ControlsOutput>> onReset = Optional.empty(), onInit = Optional.empty();
    protected BiFunction<ControlsOutput, Float, RunState> onRun;
    protected Optional<Consumer<Float>> onComplete = Optional.empty();
    private float timer = 0;
    private float lastTime = -1;

    private Scenario() {
    }

    public ControlsOutput processInput(DataPacket input) {
        if (this.lastTime == -1)
            this.lastTime = input.car.elapsedSeconds;
        final float dt = input.car.elapsedSeconds - this.lastTime;
        this.lastTime = input.car.elapsedSeconds;

        final GameData gameData = GameData.current();
        final AdvancedRenderer renderer = AdvancedRenderer.forBotIndex(input.playerIndex);
        gameData.update(input.car, new ImmutableBallData(input.ball), input.allCars, input.gameInfo, dt, renderer);
        ControlsOutput controlsOutput = new ControlsOutput();

        switch (this.state) {
            case INVALID:
                assert false;
            case DELAYED_RESET: {
                if (this.timer < 0)
                    break; // if timer >= 0, fallthrough
                else
                    this.state = State.RESET;
            }
            case RESET: {
                this.timer = 0;
                this.onReset.ifPresent(c -> c.accept(controlsOutput));
                RLBotDll.setGameState(this.gameState.buildPacket());
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
                        this.reset();
                        break;
                    case CONTINUE:
                        break;
                    case DELAYED_RESET:
                        this.state = State.DELAYED_RESET;
                        this.timer = -this.delayedResetTransitionDelay;
                        break;
                    case COMPLETE:
                        this.state = State.INVALID;
                        this.onComplete.ifPresent(c -> c.accept(this.timer));
                        return null; // Signal the scenario loader that we are done
                    default:
                        assert false;
                }
                break;
            }
        }
        this.timer += dt;
        return controlsOutput;
    }

    public void reset() {
        this.state = State.RESET;
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

        public Scenario build() {
            assert scenario.gameState != null;

            // Reset game speed, if not set
            if (this.scenario.gameState.getGameInfoState() == null)
                this.scenario.gameState.withGameInfoState(new GameInfoState().withGameSpeed(1f));
            else if (this.scenario.gameState.getGameInfoState().getGameSpeed() == null)
                this.scenario.gameState.getGameInfoState().withGameSpeed(1f);

            // move ball out of the way if its not needed
            if (this.scenario.gameState.getBallState() == null)
                this.scenario.gameState.withBallState(new BallState().withPhysics(new PhysicsState().withLocation(new Vector3(0, 0, RLConstants.arenaHeight + 300).toDesiredVector()).withVelocity(new DesiredVector3(0f, 0f, 0f))));

            return this.scenario;
        }

        public Builder withGameState(GameState state) {
            this.scenario.gameState = state;
            return this;
        }

        public Builder withReset(Consumer<ControlsOutput> onReset) {
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

        public Builder withOnComplete(Consumer<Float> onComplete) {
            this.scenario.onComplete = Optional.of(onComplete);
            return this;
        }

        public Builder withTransitionDelay(float delay) {
            assert delay >= 0f;
            this.scenario.resetTransitionDelay = delay;
            return this;
        }
    }
}
