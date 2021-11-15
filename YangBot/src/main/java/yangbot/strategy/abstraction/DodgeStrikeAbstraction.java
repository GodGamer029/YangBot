package yangbot.strategy.abstraction;

import yangbot.input.*;
import yangbot.optimizers.DodgeStrikeOptimizer;
import yangbot.optimizers.graders.Grader;
import yangbot.strategy.manuever.DodgeManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.math.vector.Matrix3x3;

import java.awt.*;

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

        if (!this.optimizer.solvedGoodStrike && !car.hasWheelContact){
            if(this.strikeDodge.preorientOrientation == null && this.optimizer.expectedBallHitTime > 0) {
                var targetB = gameData.getBallPrediction().getFrameAtAbsoluteTime(this.optimizer.expectedBallHitTime);
                targetB.ifPresent(yangPredictionFrame -> {
                    var predCarZ = car.position.z + CarData.getJumpHeightAtTime(this.optimizer.expectedBallHitTime - car.elapsedSeconds + this.strikeDodge.timer) - CarData.getJumpHeightAtTime(this.strikeDodge.timer);
                    var predContact = yangPredictionFrame.ballData.position;
                    predContact = predContact.add(car.position.withZ(predCarZ).sub(predContact).normalized().mul(BallData.COLLISION_RADIUS));

                    this.strikeDodge.preorientOrientation = Matrix3x3.lookAt(predContact.sub(car.position.withZ(predCarZ)).normalized());
                });

            } else if(this.strikeDodge.timer >= this.strikeCalcDelay)
                this.solveGoodStrike();
        }

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
    public void draw(AdvancedRenderer r) {
        r.drawString2d(String.format("timer=%.02f vz=%.1f", this.strikeDodge.timer, GameData.current().getCarData().velocity.z), Color.WHITE, new Point(500, 400), 2, 2);
        if(this.optimizer.solvedGoodStrike)
            this.optimizer.drawSolvedStrike(r);
    }

    @Override
    public boolean canInterrupt() {
        return this.strikeDodge.timer == 0 || this.strikeDodge.isDone();
    }

}
