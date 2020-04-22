package yangbot.strategy.abstraction;

import org.jetbrains.annotations.NotNull;
import rlbot.cppinterop.RLBotDll;
import rlbot.flat.QuickChatSelection;
import yangbot.cpp.YangBotJNAInterop;
import yangbot.input.*;
import yangbot.input.interrupt.BallTouchInterrupt;
import yangbot.input.interrupt.InterruptManager;
import yangbot.optimizers.graders.Grader;
import yangbot.optimizers.graders.OffensiveGrader;
import yangbot.path.Curve;
import yangbot.strategy.manuever.DodgeManeuver;
import yangbot.strategy.manuever.FollowPathManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
import java.util.ArrayList;
import java.util.Optional;

/*
    Does 3 things:
    - Drive a short path to intersection point
    - Jump if needed
    - Hit the ball

    Planning a strike should never be resource intensive
 */
public class StrikeAbstraction extends Abstraction {

    public float arrivalTime = 0;
    public boolean strikeSolved = false;
    private DodgeManeuver strikeDodge;
    private FollowPathManeuver followPath;
    private State state;
    private boolean solvedGoodStrike = false;
    public Grader customGrader;
    private BallTouchInterrupt ballTouchInterrupt = null;

    private static boolean debugMessages = false;
    public float maxJumpDelay = 0.6f;
    public float jumpDelayStep = 0.15f;
    public float jumpBeforeStrikeDelay = 0.3f;

    public StrikeAbstraction(Curve path) {
        this(path, new OffensiveGrader());
    }

    public StrikeAbstraction(Curve path, @NotNull Grader grader) {
        this.state = State.DRIVE;

        this.strikeDodge = new DodgeManeuver();
        this.strikeDodge.delay = 0.4f;
        this.strikeDodge.duration = 0.2f;

        this.followPath = new FollowPathManeuver();
        this.followPath.path = path;

        this.customGrader = grader;
    }

    public StrikeAbstraction(@NotNull Grader customGrader) {
        this.state = State.STRIKE;

        this.strikeDodge = new DodgeManeuver();
        this.strikeDodge.delay = 0.4f;
        this.strikeDodge.duration = 0.2f;

        this.customGrader = customGrader;
    }

    @Override
    protected RunState stepInternal(float dt, ControlsOutput controlsOutput) {
        assert arrivalTime > 0;
        assert followPath.path != null;

        this.followPath.arrivalTime = this.arrivalTime;

        final GameData gameData = this.getGameData();
        final CarData car = gameData.getCarData();
        final ImmutableBallData ball = gameData.getBallData();
        final YangBallPrediction ballPrediction = gameData.getBallPrediction();
        final AdvancedRenderer renderer = gameData.getAdvancedRenderer();

        if (this.ballTouchInterrupt == null)
            this.ballTouchInterrupt = InterruptManager.get().getBallTouchInterrupt(-1);

        switch (this.state) {
            case DRIVE:

                if (this.ballTouchInterrupt.hasInterrupted())
                    return RunState.DONE;

                this.followPath.path.draw(renderer, Color.YELLOW);
                //this.followPath.draw(renderer, car);
                this.followPath.step(dt, controlsOutput);

                float distanceOffPath = (float) car.position.flatten().distance(this.followPath.path.pointAt(this.followPath.path.findNearest(car.position)).flatten());
                if (distanceOffPath > 100) {
                    if (debugMessages)
                        System.out.println("Quitting strike because distanceOffPath: " + distanceOffPath);
                    return RunState.DONE;
                }

                if (!car.hasWheelContact)
                    return RunState.DONE;

                if (this.followPath.arrivalTime - car.elapsedSeconds < this.jumpBeforeStrikeDelay || this.followPath.isDone()) {
                    this.state = State.STRIKE;

                    float dist = (float) car.position.flatten().add(car.velocity.flatten().mul(this.jumpBeforeStrikeDelay)).distance(ball.position.flatten()) - BallData.COLLISION_RADIUS - car.hitbox.getAverageHitboxExtent();
                    if (dist > 300)
                        return RunState.DONE;
                }
                break;
            case STRIKE:
                if (!this.solvedGoodStrike && this.strikeDodge.timer >= Math.min(0.20f, 0.05f + RLConstants.gameLatencyCompensation)) {
                    this.solvedGoodStrike = true;

                    Optional<YangBallPrediction.YangPredictionFrame> ballFrameAtArrivalOptional = ballPrediction.getFrameAtRelativeTime(0.4f - strikeDodge.timer + RLConstants.tickFrequency);
                    assert ballFrameAtArrivalOptional.isPresent();

                    YangBallPrediction.YangPredictionFrame ballFrameAtArrival = ballFrameAtArrivalOptional.get();
                    final Vector3 ballAtArrival = ballFrameAtArrival.ballData.position;
                    float T = (float) ((ballAtArrival.flatten().sub(car.position.flatten()).magnitude() - BallData.COLLISION_RADIUS) / car.velocity.flatten().magnitude());
                    if (T > Math.min(this.jumpBeforeStrikeDelay + 0.5f, DodgeManeuver.timeout) || T <= RLConstants.tickFrequency) {
                        strikeDodge.direction = null;
                        strikeDodge.target = null;
                        strikeDodge.duration = 0f;
                        strikeDodge.delay = 999;
                        strikeDodge.setDone();
                        if (debugMessages)
                            System.out.println("Disabling strikeDodge because T=" + T + " jumpBeforeStrikeDelay=" + this.jumpBeforeStrikeDelay);
                    } else {
                        strikeDodge.target = null;
                        Vector3 carAtArrival = car.position.add(car.velocity.mul(T - RLConstants.tickFrequency * 2));
                        Vector2 direction = ballAtArrival.flatten().sub(carAtArrival.flatten()).normalized();
                        strikeDodge.direction = direction;

                        final float simDt = RLConstants.simulationTickFrequency;

                        long ms = System.currentTimeMillis();
                        int simulationCount = 0;

                        for (float duration = Math.max(0.1f, this.strikeDodge.timer); duration <= 0.2f; duration = duration < 0.2f ? 0.2f : 999f) { // 0.1f, 0.15f, 0.2f
                            for (float delay = duration + 0.05f; delay <= this.maxJumpDelay; delay += this.jumpDelayStep) {
                                for (float angleDiff = (float) (Math.PI * -0.8f); angleDiff < (float) (Math.PI * 0.8f); angleDiff += Math.PI * 0.15f) {
                                    simulationCount++;
                                    CarData simCar = new CarData(car);
                                    simCar.hasWheelContact = false;
                                    simCar.elapsedSeconds = 0;
                                    simCar.jumpTimer = this.strikeDodge.timer;
                                    simCar.enableJumpAcceleration = true;
                                    simCar.lastControllerInputs.withJump(true);

                                    BallData simBall = new BallData(ball);
                                    simBall.hasBeenTouched = false;
                                    DodgeManeuver simDodge = new DodgeManeuver(strikeDodge);
                                    simDodge.delay = delay;
                                    simDodge.duration = duration;
                                    simDodge.direction = direction.rotateBy(angleDiff);
                                    simDodge.timer = this.strikeDodge.timer;
                                    if (delay > 0.2f) {
                                        simDodge.enablePreorient = true;
                                        Vector3 dir = ballAtArrival.sub(carAtArrival).normalized().add(0, 0, 0.4f).normalized();
                                        simDodge.preorientOrientation = Matrix3x3.lookAt(dir, new Vector3(0, 0, 1));
                                    }

                                    FoolGameData foolGameData = GameData.current().fool();
                                    //Vector3 simContact = null;

                                    // Simulate ball - car collision
                                    for (float time = 0; time < T + 0.2f; time += simDt) {
                                        ControlsOutput simControls = new ControlsOutput();

                                        foolGameData.foolCar(simCar);
                                        simDodge.fool(foolGameData);

                                        simDodge.step(simDt, simControls);

                                        simCar.step(simControls, simDt);

                                        simBall.collide(simCar, -3);

                                        if (simBall.hasBeenTouched)
                                            break;

                                        // + = hit earlier
                                        // - = hit later
                                        // The right parameter is different depending on how fast this algorithm performs, currently it takes about 17-21ms, a little more than 2 physics ticks
                                        Optional<YangBallPrediction.YangPredictionFrame> frameOptional = ballPrediction.getFrameAtRelativeTime(time + 1.2f * RLConstants.tickFrequency);
                                        if (frameOptional.isEmpty())
                                            break;
                                        simBall = frameOptional.get().ballData.makeMutable();
                                        assert simBall.velocity.magnitude() < BallData.MAX_VELOCITY * 1.5f : "Got faulty ball: " + simBall.toString();

                                        simBall.hasBeenTouched = false;
                                    }

                                    // Evaluate post-collision ball state
                                    if (simBall.hasBeenTouched) {
                                        if (!simCar.doubleJumped || simCar.dodgeTimer <= simDt * 3)
                                            continue;

                                        YangBallPrediction simBallPred = YangBotJNAInterop.getBallPrediction(simBall, 60);
                                        //YangBallPrediction simBallPred = simBall.makeBallPrediction(1f/120f, 3);

                                        boolean applyDodgeSettings = false;
                                        final GameData tempGameData = new GameData(0L);
                                        tempGameData.update(simCar, new ImmutableBallData(simBall), new ArrayList<>(), gameData.getGravity().z, dt, renderer);
                                        tempGameData.setBallPrediction(simBallPred);

                                        if (this.customGrader.isImproved(tempGameData))
                                            applyDodgeSettings = true;

                                        if (applyDodgeSettings) {
                                            this.strikeDodge.delay = simDodge.delay;
                                            this.strikeDodge.target = null;
                                            this.strikeDodge.direction = simDodge.direction;
                                            this.strikeDodge.duration = simDodge.duration;
                                            this.strikeDodge.enablePreorient = simDodge.enablePreorient;
                                            this.strikeDodge.preorientOrientation = simDodge.preorientOrientation;
                                            this.strikeDodge.controllerInput = simDodge.controllerInput;

                                            this.strikeSolved = true;
                                            /*this.hitPrediction = simBallPred;
                                            this.hitCar = simCar;
                                            this.hitBall = simBall;
                                            this.contactPoint = simContact;
                                            this.contactNormal = simBall.position.sub(simContact).normalized();*/
                                        }
                                    }
                                }
                            }
                        }

                        if (this.strikeSolved) { // Found shot
                            //RLBotDll.setGameState(new GameState().withGameInfoState(new GameInfoState().withGameSpeed(0.1f)).buildPacket());
                            if (debugMessages)
                                System.out.println("Optimized dodgeManeuver: delay=" + this.strikeDodge.delay + " duration=" + this.strikeDodge.duration + " ");
                        } else { // Couldn't hit the ball
                            RLBotDll.sendQuickChat(car.playerIndex, true, QuickChatSelection.Information_TakeTheShot);
                            strikeDodge.direction = null;
                            strikeDodge.duration = 0;
                            strikeDodge.delay = 9999;
                            strikeDodge.setDone();
                            System.out.println(car.playerIndex + ": >>> Could not hit ball, aborting... (Grader: " + this.customGrader.getClass().getSimpleName() + ")");
                        }

                        System.out.println(car.playerIndex + ":  > Strike planning took: " + (System.currentTimeMillis() - ms) + "ms with " + simulationCount + " simulations");
                    }
                }

                if (!strikeDodge.isDone())
                    strikeDodge.step(dt, controlsOutput);

                if (ball.hasBeenTouched() && ball.getLatestTouch().playerIndex == car.playerIndex && car.elapsedSeconds - ball.getLatestTouch().gameSeconds - strikeDodge.timer < -0.1f) {
                    strikeDodge.setDone();
                }

                if (strikeDodge.isDone()) {
                    if (debugMessages)
                        System.out.println("Quitting strike because strikeDodge isDone: " + strikeDodge.timer + " " + strikeDodge.delay + " " + strikeDodge.duration);
                    return RunState.DONE;
                }

                break;
        }

        return RunState.CONTINUE;
    }

    @Override
    public boolean canInterrupt() {
        if (state == State.STRIKE && this.strikeDodge.timer > 0 && !this.strikeDodge.isDone())
            return false;

        return super.canInterrupt();
    }

    enum State {
        DRIVE,
        STRIKE
    }
}
