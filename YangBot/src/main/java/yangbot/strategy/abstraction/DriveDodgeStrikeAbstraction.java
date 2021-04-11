package yangbot.strategy.abstraction;

import org.jetbrains.annotations.NotNull;
import yangbot.input.*;
import yangbot.input.interrupt.BallTouchInterrupt;
import yangbot.input.interrupt.InterruptManager;
import yangbot.optimizers.graders.Grader;
import yangbot.optimizers.graders.OffensiveGrader;
import yangbot.path.builders.SegmentedPath;
import yangbot.util.AdvancedRenderer;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

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
    public static final float MAX_STRIKE_HEIGHT = 230f /*max jump height*/ + 66 /* height of car nose when tilted up*/ + BallData.COLLISION_RADIUS * 0.45f /*we can hit the lower half*/;
    public State state;

    private BallTouchInterrupt ballTouchInterrupt = null;
    public final DodgeStrikeAbstraction strikeAbstraction;
    public final DriveAbstraction driveAbstraction;
    public float jumpBeforeStrikeDelay = 0.3f;
    public Vector3 originalTargetBallPos = null;
    public boolean debugMessages = true;

    public DriveDodgeStrikeAbstraction(@NotNull SegmentedPath path) {
        this(path, new OffensiveGrader());
    }

    public DriveDodgeStrikeAbstraction(@NotNull SegmentedPath path, @NotNull Grader grader) {
        this.state = State.DRIVE;

        this.driveAbstraction = new DriveAbstraction(path);
        this.strikeAbstraction = new DodgeStrikeAbstraction(grader);
    }

    // Run methods over and over again to bait the runtime into optimizing them
    public static void prepJit() {
        var simCar = new CarData(new Vector3(0, 0, 30), new Vector3(0, 0, 200), new Vector3(), Matrix3x3.identity());
        var rng = ThreadLocalRandom.current();
        var simBall = new ImmutableBallData(new Vector3(0, 200, 200), new Vector3(), new Vector3()).makeMutable();
        for (int i = 0; i < 200; i++) {
            if (rng.nextBoolean())
                simCar.position = new Vector3(0, 0, 30).add(rng.nextFloat() * 80 - 40, rng.nextFloat() * 80 - 40, rng.nextFloat() * 80 - 40);
            else
                simCar.position = simBall.position.add(rng.nextFloat() * 80 - 40, rng.nextFloat() * 80 - 40, rng.nextFloat() * 80 - 40);
            simBall.collide(simCar, 0);
        }
        simCar.hasWheelContact = false;
        simCar.boost = 100;
        for (int i = 0; i < 250; i++) {
            var ctrl = new ControlsOutput();
            ctrl.withPitch(rng.nextFloat() * 2 - 1);
            ctrl.withYaw(rng.nextFloat() * 2 - 1);
            ctrl.withRoll(rng.nextFloat() * 2 - 1);
            ctrl.withBoost(rng.nextBoolean());
            simCar.step(ctrl, RLConstants.tickFrequency);
        }
        /*curGameData.update(simCar, simBall, List.of(simCar), RLConstants.gravity.z, RLConstants.tickFrequency, new DummyRenderer(0));
        List<YangBallPrediction.YangPredictionFrame> frames = new ArrayList<>();
        for(int i = 0; i < 60 * 4; i++){
            frames.add(new YangBallPrediction.YangPredictionFrame(i * RLConstants.tickFrequency * 2, i * RLConstants.tickFrequency * 2, simBall));
        }
        curGameData.setBallPrediction(YangBallPrediction.from(frames, RLConstants.tickFrequency * 2));
        for(int i = 0; i < 2; i++){
            var strik = new StrikeAbstraction(new Curve(), new OffensiveGrader());
            strik.arrivalTime = 0.6f;
            strik.jumpBeforeStrikeDelay = 0.7f;
            strik.state = State.STRIKE;
            strik.maxJumpDelay = 1f;
            strik.jumpDelayStep = 0.1f;
            strik.strikeDodge.timer = 0.1f;
            strik.step(RLConstants.tickFrequency, new ControlsOutput());
        }*/
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
            if (predBall.isPresent()) {
                renderer.drawCentered3dCube(Color.DARK_GRAY, predBall.get().ballData.position, BallData.COLLISION_RADIUS * 2);
            }
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
                    float dist = (float) car.position.flatten().add(car.velocity.flatten().mul(delay)).distance(futureBallPos) - BallData.COLLISION_RADIUS - car.hitbox.getAverageHitboxExtent();
                    if (dist > 450) {
                        if (debugMessages)
                            System.out.println(car.playerIndex + ": Quitting strike because nowhere near hitting dist=" + dist + " delay=" + delay + " carp=" + car.position + " carv=" + car.velocity + " ballp=" + futureBallPos);

                        return RunState.FAILED;
                    }
                }
                break;
            case STRIKE:
                return this.strikeAbstraction.step(dt, controlsOutput);
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

    enum State {
        DRIVE,
        STRIKE
    }
}
