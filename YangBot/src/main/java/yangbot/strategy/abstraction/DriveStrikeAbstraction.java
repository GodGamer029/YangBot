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
import yangbot.path.builders.SegmentedPath;
import yangbot.strategy.manuever.DodgeManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.Range;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/*
    Does 3 things:
    - Drive a short path to intersection point
    - Jump if needed
    - Hit the ball

    Planning a strike should never be resource intensive
 */
public class DriveStrikeAbstraction extends Abstraction {

    public float arrivalTime = 0;
    public boolean strikeSolved = false;
    private final DodgeManeuver strikeDodge;
    public float dodgeCollisionTime = 0;
    private SegmentedPath path;
    public State state;
    private boolean solvedGoodStrike = false;
    public Grader customGrader;
    private BallTouchInterrupt ballTouchInterrupt = null;

    public boolean debugMessages = true;
    public float maxJumpDelay = 0.6f;
    public float jumpDelayStep = 0.15f;
    public float jumpBeforeStrikeDelay = 0.3f;
    public Vector3 originalTargetBallPos = null;

    private YangBallPrediction hitPrediction;
    private CarData hitCar;
    private BallData hitBall;
    private Vector3 contactPoint;
    private boolean doesHitNotInvolveDodge;

    public DriveStrikeAbstraction(SegmentedPath path) {
        this(path, new OffensiveGrader());
    }

    public DriveStrikeAbstraction(SegmentedPath path, @NotNull Grader grader) {
        this.state = State.DRIVE;

        this.strikeDodge = new DodgeManeuver();
        this.strikeDodge.delay = 0.4f;
        this.strikeDodge.duration = 0.2f;

        this.path = path;

        this.customGrader = grader;
    }

    public DriveStrikeAbstraction(@NotNull Grader customGrader) {
        this.state = State.STRIKE;

        this.strikeDodge = new DodgeManeuver();
        this.strikeDodge.delay = 0.4f;
        this.strikeDodge.duration = 0.2f;

        this.customGrader = customGrader;
    }

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
        assert this.path != null;
        assert this.jumpBeforeStrikeDelay > 0.15f : "jumpBeforeStrikeDelay should be longer than what is needed to calculate a dodge";

        final GameData gameData = this.getGameData();
        final CarData car = gameData.getCarData();
        final ImmutableBallData ball = gameData.getBallData();
        YangBallPrediction ballPrediction = gameData.getBallPrediction();
        final AdvancedRenderer renderer = gameData.getAdvancedRenderer();

        if (this.ballTouchInterrupt == null)
            this.ballTouchInterrupt = InterruptManager.get().getBallTouchInterrupt(-1);

        switch (this.state) {
            case DRIVE:

                if (this.ballTouchInterrupt.hasInterrupted())
                    return RunState.DONE;

                this.path.draw(renderer);
                this.path.step(dt, controlsOutput);

                if (this.path.shouldReset(car)) {
                    if (debugMessages)
                        System.out.println("Quitting strike because path reset");

                    return RunState.DONE;
                }

                if (this.arrivalTime - car.elapsedSeconds < this.jumpBeforeStrikeDelay || this.path.isDone()) {
                    this.state = State.STRIKE;
                    float delay = MathUtils.clip(this.arrivalTime - car.elapsedSeconds, 0.1f, 2f);

                    Vector2 futureBallPos = ballPrediction.getFrameAtRelativeTime(delay).get().ballData.position.flatten();
                    float dist = (float) car.position.flatten().add(car.velocity.flatten().mul(delay)).distance(futureBallPos) - BallData.COLLISION_RADIUS - car.hitbox.getAverageHitboxExtent();
                    if (dist > 450) {
                        if (debugMessages)
                            System.out.println("Quitting strike because nowhere near hitting dist=" + dist + " ");

                        return RunState.DONE;
                    }
                }
                break;
            case STRIKE:
                if (!this.solvedGoodStrike && this.strikeDodge.timer >= Math.min(0.20f, 0.05f + RLConstants.gameLatencyCompensation)) {
                    this.solvedGoodStrike = true;

                    // Make 120hz ball prediction if necessary
                    if (ballPrediction.tickFrequency > RLConstants.tickFrequency * 1.5f) {
                        ballPrediction = YangBotJNAInterop.getBallPrediction(ball.makeMutable(), RLConstants.tickRate);
                    }

                    Optional<YangBallPrediction.YangPredictionFrame> ballFrameAtArrivalOptional = ballPrediction.getFrameAtRelativeTime(this.jumpBeforeStrikeDelay - this.strikeDodge.timer);
                    assert ballFrameAtArrivalOptional.isPresent();

                    YangBallPrediction.YangPredictionFrame ballFrameAtArrival = ballFrameAtArrivalOptional.get();
                    Vector3 ballAtArrival = ballFrameAtArrival.ballData.position;
                    //if(this.originalTargetBallPos != null)
                    //    ballAtArrival = this.originalTargetBallPos;

                    float T = Math.min(ballPrediction.relativeTimeOfLastFrame() - 0.1f, (float) ((ballAtArrival.sub(car.position).magnitude() - BallData.COLLISION_RADIUS - car.hitbox.getMinHitboxExtent()) / MathUtils.clip(car.velocity.magnitude() + 100, 400, CarData.MAX_VELOCITY)));
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
                        Vector3 carAtArrival = car.position
                                .add(car.velocity.mul(T))
                                .add(gameData.getGravity().mul(T * T * 0.5f));
                        Vector2 direction = ballAtArrival.flatten().sub(carAtArrival.flatten()).normalized();
                        strikeDodge.direction = direction;

                        final float simDt = RLConstants.tickFrequency;

                        long ms = System.currentTimeMillis();
                        int simulationCount = 0;
                        boolean didHitBall = false;
                        boolean didHitBallAfterDodge = false;
                        int numGraderCalls = 0;
                        int numWheelHits = 0;
                        int numHits = 0;

                        for (float duration = Math.max(0.1f, this.strikeDodge.timer); duration <= 0.2f; duration = duration < 0.2f ? 0.2f : 999f) {
                            float jumpDelayStep = this.jumpDelayStep;
                            float angleDiffStep = (float) (Math.PI * 0.15f);
                            if (duration < 0.2f) { // We really only need a duration < 0.2 if we want to jump
                                jumpDelayStep *= 2;
                                angleDiffStep *= 3;
                            }
                            for (float delay = duration + 0.05f; delay <= (duration < 0.2f ? 0.3f : this.maxJumpDelay); delay += jumpDelayStep) {
                                boolean hadNonDodgeHit = false;
                                for (float angleDiff = (float) (Math.PI * -0.8f); angleDiff < (float) (Math.PI * 0.8f); angleDiff += angleDiffStep) {
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
                                    if (delay >= 0.2f) {
                                        simDodge.enablePreorient = true;
                                        Vector3 orientDir = ballAtArrival.sub(carAtArrival).normalized().add(0, 0, 0).normalized();
                                        Vector2 velDir = car.velocity.flatten().normalized();
                                        Vector3 comb = orientDir.flatten().add(velDir).normalized().withZ(orientDir.z).normalized();
                                        simDodge.preorientOrientation = Matrix3x3.lookAt(comb, new Vector3(0, 0, 1));
                                    }

                                    FoolGameData foolGameData = GameData.current().fool();
                                    Vector3 simContact = null;

                                    float timeEnd = T + 0.2f;
                                    // Simulate ball - car collision
                                    for (float time = 0; time < timeEnd; time += simDt) {
                                        ControlsOutput simControls = new ControlsOutput();

                                        foolGameData.foolCar(simCar);
                                        simDodge.fool(foolGameData);

                                        simDodge.step(simDt, simControls);

                                        simCar.step(simControls, simDt);
                                        // Don't let the car escape the arena
                                        if (simCar.position.x >= RLConstants.arenaHalfWidth)
                                            break;
                                        if (simCar.position.y >= RLConstants.arenaHalfLength)
                                            break;
                                        if (simCar.position.z < RLConstants.carElevation - 5)
                                            break;

                                        simContact = simBall.collide(simCar, 0);

                                        if (simBall.hasBeenTouched)
                                            break;

                                        // + = hit earlier (car will arrive before ball when executed)
                                        // - = hit later (ball will arrive before car when executed)
                                        // The right parameter is different depending on random stuff, its probably latency between the framework and rocket league
                                        Optional<YangBallPrediction.YangPredictionFrame> frameOptional = ballPrediction.getFrameAtRelativeTime(time + RLConstants.tickFrequency);
                                        if (frameOptional.isEmpty())
                                            break;
                                        simBall = frameOptional.get().ballData.makeMutable();
                                        assert simBall.velocity.magnitude() < BallData.MAX_VELOCITY * 1.5f : "Got faulty ball: " + simBall.toString();

                                        simBall.hasBeenTouched = false;
                                    }

                                    // Evaluate post-collision ball state
                                    if (simBall.hasBeenTouched) {

                                        didHitBall = true;
                                        final boolean isNonDodgeHit = (!simCar.doubleJumped || simCar.dodgeTimer < 0.05f);
                                        if (isNonDodgeHit && hadNonDodgeHit)
                                            continue;

                                        numHits++;

                                        hadNonDodgeHit = true;
                                        didHitBallAfterDodge |= !isNonDodgeHit;

                                        // Did it hit with the wheels?
                                        {
                                            assert simContact != null;
                                            var rel = simContact.sub(simCar.position); // car -> contact
                                            float localContactF = (float) rel.dot(simCar.hitbox.getOrientation().forward());
                                            rel = simCar.hitbox.removeOffset(rel); // hitbox center -> contact
                                            float localHitboxContactZ = (float) simCar.hitbox.getOrientation().up().dot(rel);

                                            final var wheelInfo = car.wheelInfo;
                                            final var frontAxle = wheelInfo.get(1, 0);
                                            final var backAxle = wheelInfo.get(-1, 0);
                                            if (localHitboxContactZ < -simCar.hitbox.hitboxLengths.mul(0.5f).z && Range.isInRange(localContactF, backAxle.localPos.x - backAxle.radius - 1, frontAxle.localPos.x + frontAxle.radius + 1)) {
                                                //if(debugMessages)
                                                //    System.out.println("Stopped because hit with wheels");
                                                numWheelHits++;
                                                continue;
                                            }
                                        }

                                        YangBallPrediction simBallPred = YangBotJNAInterop.getBallPrediction(simBall, 60);

                                        boolean applyDodgeSettings = false;
                                        final GameData tempGameData = new GameData(0L);
                                        tempGameData.update(simCar, new ImmutableBallData(simBall), new ArrayList<>(), gameData.getGravity().z, dt, renderer);
                                        tempGameData.setBallPrediction(simBallPred);

                                        numGraderCalls++;

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

                                            if (isNonDodgeHit)
                                                this.strikeDodge.delay = 9999;

                                            this.strikeSolved = true;
                                            this.hitPrediction = simBallPred;
                                            this.hitCar = simCar;
                                            this.hitBall = simBall;
                                            this.contactPoint = simContact;
                                            this.dodgeCollisionTime = simCar.elapsedSeconds + car.elapsedSeconds;
                                            this.doesHitNotInvolveDodge = isNonDodgeHit;
                                        }
                                    }
                                }
                            }
                        }

                        if (this.strikeSolved) { // Found shot

                            if (debugMessages) {
                                System.out.println(car.playerIndex + ": >> Optimized dodgeManeuver: delay=" + this.strikeDodge.delay + " duration=" + this.strikeDodge.duration + " grader=" + this.customGrader.getClass().getSimpleName() + " doesHitNotInvolveDodge=" + doesHitNotInvolveDodge);

                                //RLBotDll.setGameState(new GameState().withGameInfoState(new GameInfoState().withGameSpeed(0.1f)).buildPacket());
                            }

                        } else { // Couldn't satisfy grader
                            RLBotDll.sendQuickChat(car.playerIndex, true, QuickChatSelection.Information_TakeTheShot);
                            strikeDodge.direction = null;
                            strikeDodge.duration = 0;
                            strikeDodge.delay = 9999;
                            strikeDodge.setDone();
                            System.out.println(car.playerIndex + ": >>> Could not satisfy grader, aborting... (Grader: " + this.customGrader.getClass().getSimpleName() + ", didHitBall=" + didHitBall + ", didHitBallAfterDodge=" + didHitBallAfterDodge + ", numHits=" + numHits + ", numGraderCalls=" + numGraderCalls + ", numWheelHits=" + numWheelHits + ")");
                            System.out.println(car.playerIndex + ": > Additional grader info: " + this.customGrader.getAdditionalInfo());
                        }
                        if (this.debugMessages) {

                            System.out.println(car.playerIndex + ": > With parameters: maxJumpDelay=" + this.maxJumpDelay + " jumpBeforeStrikeDelay=" + this.jumpBeforeStrikeDelay + " jumpDelayStep=" + this.jumpDelayStep);

                        }

                        System.out.println(car.playerIndex + ":  > Strike planning took: " + (System.currentTimeMillis() - ms) + "ms with " + simulationCount + " simulations at: " + car.elapsedSeconds);
                    }
                }

                //if (!strikeDodge.isDone())
                strikeDodge.step(dt, controlsOutput);

                if (ball.hasBeenTouched() && ball.getLatestTouch().playerIndex == car.playerIndex && car.elapsedSeconds - ball.getLatestTouch().gameSeconds - strikeDodge.timer < -0.1f) {
                    strikeDodge.setDone();
                    float originalCollTime = this.dodgeCollisionTime;
                    this.dodgeCollisionTime = Math.min(this.dodgeCollisionTime, car.elapsedSeconds + 0.2f);
                    if (this.doesHitNotInvolveDodge)
                        this.dodgeCollisionTime = car.elapsedSeconds;
                    if (debugMessages)
                        System.out.println(" - Ball touched, quitting strike soon (prev=" + originalCollTime + ", now=" + this.dodgeCollisionTime + ")");
                }

                if (strikeDodge.isDone() && car.elapsedSeconds > this.dodgeCollisionTime) {
                    if (debugMessages) {
                        System.out.println("Quitting strike because strikeDodge isDone: " + strikeDodge.timer + " " + strikeDodge.delay + " " + strikeDodge.duration + " ");
                        //RLBotDll.setGameState(new GameState().withGameInfoState(new GameInfoState().withGameSpeed(1f)).buildPacket());
                    }

                    return RunState.DONE;
                } else if (this.strikeSolved && car.elapsedSeconds > this.dodgeCollisionTime + 0.2f) {
                    if (debugMessages) {
                        System.out.println("Quitting strike because dodgeCollisionTime over: " + this.dodgeCollisionTime + " rel=" + (this.hitCar.elapsedSeconds));
                        //RLBotDll.setGameState(new GameState().withGameInfoState(new GameInfoState().withGameSpeed(1f)).buildPacket());
                    }

                    return RunState.DONE;
                } else if (this.strikeSolved) {
                    /*
                    this.hitPrediction = simBallPred;
                                            this.hitCar = simCar;
                                            this.hitBall = simBall;
                                            this.contactPoint = simContact;
                     */

                    this.hitPrediction.draw(renderer, Color.YELLOW, 2);
                    this.hitCar.hitbox.draw(renderer, this.hitCar.position, 1, Color.ORANGE);
                    renderer.drawCentered3dCube(Color.BLACK, this.hitBall.position, BallData.COLLISION_RADIUS * 2);
                    renderer.drawCentered3dCube(Color.GREEN, this.contactPoint, 30);

                    if (this.originalTargetBallPos != null) {
                        renderer.drawCentered3dCube(Color.PINK, this.originalTargetBallPos, BallData.COLLISION_RADIUS * 2);
                    }
                }

                break;
        }

        return RunState.CONTINUE;
    }

    @Override
    public boolean canInterrupt() {
        if (state == State.STRIKE && !this.strikeDodge.isDone())
            return false;

        return super.canInterrupt();
    }

    enum State {
        DRIVE,
        STRIKE
    }
}
