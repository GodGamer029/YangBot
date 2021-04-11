package yangbot.strategy.abstraction;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.GameData;
import yangbot.input.ImmutableBallData;
import yangbot.optimizers.DodgeStrikeOptimizer;
import yangbot.optimizers.graders.Grader;
import yangbot.strategy.manuever.DodgeManeuver;
import yangbot.util.AdvancedRenderer;

public class DodgeStrikeAbstraction extends Abstraction {

    public final DodgeManeuver strikeDodge;

    public DodgeStrikeOptimizer optimizer;
    public float strikeCalcDelay = 0.03f; // delay before strike is solved
    public boolean debugMessages = true;
    private float terminateAt = -1;

    public DodgeStrikeAbstraction(Grader grader) {
        this.strikeDodge = new DodgeManeuver();
        this.strikeDodge.delay = 0.4f;
        this.strikeDodge.duration = 0.2f;
        this.optimizer = new DodgeStrikeOptimizer();
        this.optimizer.customGrader = grader;
    }

    public void setExpectedBallHitTime(float time) {
        this.optimizer.expectedBallHitTime = time;
    }

    private void solveGoodStrike() {
        this.optimizer.solveGoodStrike(this.getGameData(), this.strikeDodge);

        this.terminateAt = this.optimizer.dodgeCollisionTime;
    }

    @Override
    protected RunState stepInternal(float dt, ControlsOutput controlsOutput) {
        final GameData gameData = this.getGameData();
        final CarData car = gameData.getCarData();
        final ImmutableBallData ball = gameData.getBallData();
        final AdvancedRenderer renderer = gameData.getAdvancedRenderer();

        if (!this.optimizer.solvedGoodStrike && !car.hasWheelContact && this.strikeDodge.timer >= this.strikeCalcDelay)
            this.solveGoodStrike();

        this.strikeDodge.step(dt, controlsOutput);

        if (ball.hasBeenTouched() && ball.getLatestTouch().playerIndex == car.playerIndex && car.elapsedSeconds - ball.getLatestTouch().gameSeconds - this.strikeDodge.timer < -0.1f) {
            this.strikeDodge.setDone();
            this.terminateAt = Math.min(this.terminateAt, car.elapsedSeconds + 0.2f);
            if (debugMessages)
                System.out.println(" - Ball touched, quitting strike soon");
        }

        if (this.strikeDodge.isDone() && car.elapsedSeconds > this.terminateAt) {
            if (debugMessages)
                System.out.println(car.playerIndex + ": Quitting strike because strikeDodge isDone: " + this.strikeDodge.timer + " " + this.strikeDodge.delay + " " + this.strikeDodge.duration + " ");

            return RunState.DONE;
        }

        if (this.optimizer.strikeSolved && car.elapsedSeconds > this.terminateAt + 0.2f) {
            if (debugMessages)
                System.out.println(car.playerIndex + ": Quitting strike because terminateAt: " + this.terminateAt);

            return RunState.DONE;
        }

        return RunState.CONTINUE;
    }

    @Override
    public boolean canInterrupt() {
        return this.strikeDodge.timer == 0 || this.strikeDodge.isDone();
    }

}
