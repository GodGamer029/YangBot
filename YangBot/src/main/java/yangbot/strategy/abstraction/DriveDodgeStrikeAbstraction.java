package yangbot.strategy.abstraction;

import org.jetbrains.annotations.NotNull;
import yangbot.input.*;
import yangbot.input.interrupt.BallTouchInterrupt;
import yangbot.input.interrupt.InterruptManager;
import yangbot.optimizers.DodgeStrikeOptimizer;
import yangbot.optimizers.graders.Grader;
import yangbot.optimizers.graders.OffensiveGrader;
import yangbot.optimizers.graders.ValueNetworkGrader;
import yangbot.path.builders.SegmentedPath;
import yangbot.strategy.manuever.DodgeManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;
import yangbot.util.scenario.ScenarioUtil;

import java.awt.*;
import java.util.concurrent.ThreadLocalRandom;

/*
    Does 3 things:
    - Drive a short path to intersection point
    - Jump if needed
    - Hit the ball

    Planning a strike should never be resource intensive
 */
public class DriveDodgeStrikeAbstraction extends Abstraction {

    public float arrivalTime = 0;
    public boolean forceJump = false;
    public static final float MAX_STRIKE_HEIGHT = 230f /*max jump height*/ + 66 /* height of car nose when tilted up*/ + BallData.COLLISION_RADIUS * 0.4f /*we can hit the lower half*/ + RLConstants.carElevation;
    public State state;

    private BallTouchInterrupt ballTouchInterrupt = null;
    public final DodgeStrikeAbstraction strikeAbstraction;
    public final DriveAbstraction driveAbstraction;
    public float jumpBeforeStrikeDelay = 0.3f;
    public Vector3 originalTargetBallPos = null;
    public boolean debugMessages = true;

    public DriveDodgeStrikeAbstraction(@NotNull SegmentedPath path) {
        this(path, new ValueNetworkGrader());
    }

    public DriveDodgeStrikeAbstraction(@NotNull SegmentedPath path, @NotNull Grader grader) {
        this.state = State.DRIVE;

        this.driveAbstraction = new DriveAbstraction(path);
        this.strikeAbstraction = new DodgeStrikeAbstraction(grader);
    }

    // Run methods over and over again to bait the runtime into optimizing them
    public static void prepJit() {
        String encoded = "eWFuZ3YxOmMoYj05MS4wLHA9KC0yMDExLjk0MCwyNzI1LjIzMCwyNy4xMjApLHY9KC0yODcuNzkxLDEyNDEuMzkxLDMwNy43NjEpLGE9KC0wLjAwMiwwLjAwMCwtMC4wMDApLG89KC0wLjAxNywxLjc5OSwwLjAwMCkpLGIocD0oLTIwODcuMzQwLDMyNDMuODMwLDkzLjEzMCksdj0oMTY4LjQzMSwtMTA5LjIyMSwwLjAwMCksYT0oMS4xOTcsMS44NDYsLTAuMjA2KSk7";
        var simGameData = GameData.current();
        ScenarioUtil.decodeApplyToGameData(simGameData, encoded);

        DodgeStrikeOptimizer optimizer = new DodgeStrikeOptimizer();
        optimizer.expectedBallHitTime = simGameData.getElapsedSeconds() + 0.5f;

        DodgeManeuver strikeDodge = new DodgeManeuver();
        strikeDodge.timer = 0.03f;
        strikeDodge.duration = 0.2f;
        strikeDodge.delay = 0.6f;

        optimizer.maxJumpDelay = 1f;
        optimizer.jumpDelayStep = 0.1f;

        for(int i = 0; i < 5; i++){
            optimizer.customGrader = new ValueNetworkGrader();
            optimizer.solvedGoodStrike = false;
            optimizer.debugMessages = false;
            optimizer.solveGoodStrike(simGameData, strikeDodge);
        }
    }

    @Override
    protected RunState stepInternal(float dt, ControlsOutput controlsOutput) {
        assert this.arrivalTime > 0;
        assert this.jumpBeforeStrikeDelay > 0.15f || this.forceJump : "jumpBeforeStrikeDelay should be longer than what is needed to calculate a dodge";

        final GameData gameData = this.getGameData();
        final CarData car = gameData.getCarData();
        YangBallPrediction ballPrediction = gameData.getBallPrediction();
        final AdvancedRenderer renderer = gameData.getAdvancedRenderer();

        if (this.ballTouchInterrupt == null)
            this.ballTouchInterrupt = InterruptManager.get().getBallTouchInterrupt(-1);

        if (this.originalTargetBallPos != null) {
            renderer.drawCentered3dCube(Color.PINK, this.originalTargetBallPos, BallData.COLLISION_RADIUS * 2);

            //var lolBallPred = YangBotJNAInterop.getBallPrediction(gameData.getBallData().makeMutable(), 120);
            var predBall = ballPrediction.getFrameAtAbsoluteTime(this.arrivalTime);
            predBall.ifPresent(yangPredictionFrame ->
                    renderer.drawCentered3dCube(Color.DARK_GRAY, yangPredictionFrame.ballData.position, BallData.COLLISION_RADIUS * 2));
        }

        switch (this.state) {
            case DRIVE:
                if (this.ballTouchInterrupt.hasInterrupted()) {
                    if (debugMessages)
                        System.out.println("Quitting strike because ballTouchInterrupt");
                    return RunState.DONE;
                }

                var runState = this.driveAbstraction.step(dt, controlsOutput);
                if (runState == RunState.FAILED) {
                    if (debugMessages)
                        System.out.println("Quitting strike because DriveAbstraction failed");
                    return RunState.FAILED;
                }

                if (this.arrivalTime - car.elapsedSeconds < this.jumpBeforeStrikeDelay || this.driveAbstraction.isDone()) {
                    this.state = State.STRIKE;
                    this.strikeAbstraction.setExpectedBallHitTime(this.arrivalTime);
                    float delay = MathUtils.clip(this.arrivalTime - car.elapsedSeconds, 0.1f, 2f);
                    if (this.forceJump)
                        break;

                    Vector2 futureBallPos = ballPrediction.getFrameAtRelativeTime(delay).get().ballData.position.flatten();
                    float dist = (float) car.position.flatten().distance(futureBallPos) - BallData.COLLISION_RADIUS - car.hitbox.getDiagonalExtent();
                    if (dist / delay > MathUtils.clip(car.velocity.flatten().magnitudeF() + 700, 0, CarData.MAX_VELOCITY + 200)) {
                        if (debugMessages)
                            System.out.println(car.playerIndex + ": Quitting strike because nowhere near hitting dist=" + dist + " delay=" + delay + " carp=" + car.position + " carv=" + car.velocity + " ballp=" + futureBallPos);
                        return RunState.FAILED;
                    }
                }
                break;
            case STRIKE:
                var rs = this.strikeAbstraction.step(dt, controlsOutput);;
                if (rs.isFail() && debugMessages)
                    System.out.println(car.playerIndex + ": Quitting strike, strikeAbstraction failed ");

                return rs;
        }

        return RunState.CONTINUE;
    }

    @Override
    public boolean canInterrupt() {
        if (state == State.STRIKE && !this.strikeAbstraction.canInterrupt())
            return false;

        if (state == State.DRIVE && !this.driveAbstraction.canInterrupt())
            return false;

        return super.canInterrupt();
    }

    @Override
    public void draw(AdvancedRenderer renderer) {
        switch (state){
            case DRIVE -> {
                this.driveAbstraction.draw(renderer);
            }
            case STRIKE -> {
                this.strikeAbstraction.draw(renderer);
            }
        }
    }

    enum State {
        DRIVE,
        STRIKE
    }
}
