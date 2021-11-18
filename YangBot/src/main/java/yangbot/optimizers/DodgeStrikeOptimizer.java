package yangbot.optimizers;

import yangbot.cpp.YangBotJNAInterop;
import yangbot.input.*;
import yangbot.optimizers.graders.Grader;
import yangbot.strategy.manuever.DodgeManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.Range;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;
import yangbot.util.scenario.ScenarioUtil;

import java.awt.*;
import java.util.List;
import java.util.function.BiPredicate;

public class DodgeStrikeOptimizer {

    private static final float SIMULATION_DT = RLConstants.tickFrequency;

    public DodgeManeuver strikeDodge;
    public Grader customGrader;
    public boolean debugMessages = true;
    public boolean strikeSolved = false;
    public float expectedBallHitTime = -1;

    public float maxJumpDelay = 0.6f;
    public float jumpDelayStep = 0.1f;

    public float dodgeCollisionTime = 0;
    public boolean solvedGoodStrike = false;
    private YangBallPrediction hitPrediction;
    private CarData hitCar;
    private BallData hitBall;
    private Vector3 contactPoint;
    private boolean doesHitNotInvolveDodge;

    private GameData gameData;

    private boolean evaluateCollisionState(CarData simCar, BallData simBall, StrikeStatistics statistics) {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();

        statistics.didHitBall = true;
        final boolean isNonDodgeHit = (!simCar.doubleJumped || simCar.dodgeTimer < 0.05f);
        if (isNonDodgeHit && statistics.hadNonDodgeHit)
            return false;

        statistics.numHits++;

        statistics.hadNonDodgeHit = true;
        statistics.didHitBallAfterDodge |= !isNonDodgeHit;

        // Did it hit with the wheels?
        {
            assert simBall.latestTouch != null && simBall.latestTouch.position != null;
            var rel = simBall.latestTouch.position.sub(simCar.position); // car -> contact
            float localContactF = rel.dot(simCar.hitbox.getOrientation().forward());
            rel = simCar.hitbox.removeOffset(rel); // hitbox center -> contact
            float localHitboxContactZ = simCar.hitbox.getOrientation().up().dot(rel);

            final var wheelInfo = car.wheelInfo;
            final var frontAxle = wheelInfo.get(1, 0);
            final var backAxle = wheelInfo.get(-1, 0);
            if (localHitboxContactZ < -simCar.hitbox.hitboxLengths.mul(0.5f).z && Range.isInRange(localContactF, backAxle.localPos.x - backAxle.radius - 1, frontAxle.localPos.x + frontAxle.radius + 1)) {
                statistics.numWheelHits++;
                return false;
            }
        }

        YangBallPrediction simBallPred;
        if (this.customGrader.requiredBallPredLength() > 0)
            simBallPred = YangBotJNAInterop.getBallPrediction(simBall, this.customGrader.requiredBallPredLength() > 1 ? 60 : 120, this.customGrader.requiredBallPredLength());
        else
            simBallPred = YangBallPrediction.empty();

        final GameData tempGameData = new GameData(0L);
        tempGameData.update(simCar, new ImmutableBallData(simBall), List.of(simCar), gameData.getGravity().z, RLConstants.tickFrequency, null, simBallPred);

        statistics.numGraderCalls++;

        if (!this.customGrader.isImproved(tempGameData))
            return false;
        this.hitPrediction = simBallPred;
        return true;
    }

    private boolean testParameters(float delay, float duration, Vector2 direction, Matrix3x3 preorientMatrix, float T, BiPredicate<CarData, BallData> onCollide, YangBallPrediction ballPrediction) {
        assert !ballPrediction.isEmpty();
        final CarData car = gameData.getCarData();
        final ImmutableBallData ball = gameData.getBallData();

        CarData simCar = new CarData(car);
        simCar.hasWheelContact = false;
        simCar.elapsedSeconds = 0;
        simCar.jumpTimer = this.strikeDodge.timer;
        simCar.enableJumpAcceleration = true;
        simCar.lastControllerInputs.withJump(true);

        BallData simBall = new BallData(ball);
        simBall.hasBeenTouched = false;
        DodgeManeuver simDodge = new DodgeManeuver(this.strikeDodge);
        simDodge.reportProblems = false;
        simDodge.delay = delay;
        simDodge.duration = duration;
        simDodge.direction = direction;
        simDodge.timer = this.strikeDodge.timer;

        if (preorientMatrix != null) {
            simDodge.preorientOrientation = preorientMatrix;
            simDodge.enablePreorient = true;
        }

        FoolGameData foolGameData = GameData.current().fool();
        Vector3 simContact = null;

        float timeEnd = T + 0.3f;
        // Simulate ball - car collision
        for (float time = 0; time < timeEnd; time += SIMULATION_DT) {
            ControlsOutput simControls = new ControlsOutput();

            foolGameData.foolCar(simCar);
            simDodge.fool(foolGameData);

            simDodge.step(SIMULATION_DT, simControls);

            simCar.step(simControls, SIMULATION_DT);
            // Don't let the car escape the arena
            if (Math.abs(simCar.position.x) >= RLConstants.arenaHalfWidth)
                break;
            if (Math.abs(simCar.position.y) >= RLConstants.arenaHalfLength)
                break;
            if (simCar.position.z < RLConstants.carElevation - 5)
                break;

            simContact = simBall.collide(simCar, -3);

            if (simBall.hasBeenTouched)
                break;

            // + = hit earlier (car will arrive before ball when executed)
            // - = hit later (ball will arrive before car when executed)
            // The right parameter is different depending on random stuff, its probably latency between us, the framework and rocket league
            var frameOptional = ballPrediction.getFrameAtRelativeTime(time + RLConstants.tickFrequency);
            if (frameOptional.isEmpty()) {
                // this is pretty terrible ngl
                break;
            }

            simBall = frameOptional.get().ballData.makeMutable();
            assert simBall.velocity.magnitude() < BallData.MAX_VELOCITY * 1.2f : "Got faulty ball: " + simBall;

            simBall.hasBeenTouched = false;
        }

        if (!simBall.hasBeenTouched){
            //System.out.println("miss with delay="+simDodge.delay+" dur="+simDodge.duration);
            return false;
        }

        //System.out.println("hit  with delay="+simDodge.delay+" dur="+simDodge.duration);

        // Evaluate post-collision ball state
        if (onCollide.test(simCar, simBall)) {
            this.strikeDodge.delay = simDodge.delay;
            this.strikeDodge.target = null;
            this.strikeDodge.direction = simDodge.direction;
            this.strikeDodge.duration = simDodge.duration;
            this.strikeDodge.enablePreorient = simDodge.enablePreorient;
            this.strikeDodge.preorientOrientation = simDodge.preorientOrientation;
            this.strikeDodge.controllerInput = simDodge.controllerInput;

            if (!simCar.doubleJumped)
                this.strikeDodge.delay = 9999;

            this.strikeSolved = true;
            this.hitCar = simCar;
            this.hitBall = simBall;
            this.contactPoint = simContact;
            this.dodgeCollisionTime = simCar.elapsedSeconds + car.elapsedSeconds;
            this.doesHitNotInvolveDodge = !simCar.doubleJumped;
            return true;
        }
        return false;
    }

    public void solveGoodStrike(GameData gameData, DodgeManeuver strikeDodge) {
        this.strikeDodge = strikeDodge;
        this.gameData = gameData;
        assert !this.solvedGoodStrike;
        assert this.expectedBallHitTime > 0;
        assert this.customGrader != null;
        final CarData car = gameData.getCarData();
        final ImmutableBallData ball = gameData.getBallData();
        YangBallPrediction ballPrediction = YangBotJNAInterop.getBallPrediction(ball.makeMutable(), RLConstants.tickRate, 3);

        if(debugMessages)
            System.out.println(car.playerIndex+": > Solving good strike " + ScenarioUtil.getEncodedGameState(gameData));

        if (this.expectedBallHitTime < car.elapsedSeconds) {
            System.out.println("expectedBallHitTime=" + this.expectedBallHitTime + " car.elapsed=" + car.elapsedSeconds);
            this.expectedBallHitTime = car.elapsedSeconds;
        }

        this.solvedGoodStrike = true;

        Vector3 ballAtArrival; // Grab target ball pos
        {
            var ballFrameAtArrivalOptional = ballPrediction.getFrameAtAbsoluteTime(this.expectedBallHitTime);
            assert ballFrameAtArrivalOptional.isPresent() : ballPrediction.firstFrame().absoluteTime + " " + this.expectedBallHitTime;

            var ballFrameAtArrival = ballFrameAtArrivalOptional.get();
            ballAtArrival = ballFrameAtArrival.ballData.position;
        }

        // Relative time to ball hit, should overestimate
        final float coarseBallHitEstimation = (float) (ballAtArrival.distance(car.position) - BallData.COLLISION_RADIUS - car.hitbox.getMinHitboxExtent()) / MathUtils.clip((float) car.velocity.magnitude() + 100, 200, CarData.MAX_VELOCITY);
        float T = Math.min(ballPrediction.relativeTimeOfLastFrame() - 0.1f, coarseBallHitEstimation);
        T = Math.max(T, this.expectedBallHitTime - car.elapsedSeconds);

        if (T > Math.min(this.expectedBallHitTime - car.elapsedSeconds + 0.5f, DodgeManeuver.timeout) || T <= RLConstants.tickFrequency) {
            this.strikeDodge.direction = null;
            this.strikeDodge.target = null;
            this.strikeDodge.duration = 0f;
            this.strikeDodge.delay = 999;
            this.strikeDodge.setDone();
            if (debugMessages)
                System.out.println("Disabling strikeDodge because T=" + T + " expectedAt=" + (this.expectedBallHitTime - car.elapsedSeconds));
        }

        // Reset strike dodge parameters
        this.strikeDodge.target = null;
        Vector3 carAtArrival = car.position
                .add(car.velocity.mul(T))
                .add(gameData.getGravity().mul(T * T * 0.5f));
        Vector2 carToBallDirection = ballAtArrival.flatten().sub(carAtArrival.flatten()).normalized();
        this.strikeDodge.direction = carToBallDirection;

        // Statistics
        long ms = System.currentTimeMillis();
        final var statistics = new StrikeStatistics();

        Matrix3x3 preorientMatrix = strikeDodge.preorientOrientation;
        if(preorientMatrix == null) {
            Vector3 orientDir = ballAtArrival.sub(carAtArrival).normalized();
            //Vector2 velDir = car.velocity.flatten().normalized();
            Vector3 comb = orientDir.flatten().normalized().withZ(orientDir.z).normalized();
            preorientMatrix = Matrix3x3.lookAt(comb, new Vector3(0, 0, 1));
        }

        float chosenAngleDiff = 0;
        for (float duration = MathUtils.clip(this.strikeDodge.timer, 0.1f, 0.2f); duration <= 0.2f; duration = duration < 0.2f ? 0.2f : 999f) {
            float jumpDelayStep = this.jumpDelayStep;
            float angleDiffStep = (float) (Math.PI * 0.15f);
            if (duration < 0.2f && T > 0.4f) { // We really only need a duration < 0.2 if we want to single jump
                jumpDelayStep *= 2;
                angleDiffStep *= 3;
            }
            for (float delay = duration + 0.05f; delay <= (duration < 0.2f ? 0.3f : this.maxJumpDelay); delay += jumpDelayStep) {
                statistics.hadNonDodgeHit = false;
                for (float angleDiff = (float) (Math.PI * -0.9f); angleDiff < (float) (Math.PI * 0.9f); angleDiff += angleDiffStep) {
                    statistics.simulationCount++;

                    boolean hasImproved = this.testParameters(
                            delay,
                            duration,
                            carToBallDirection.rotateBy(angleDiff),
                            delay >= 0.2f ? preorientMatrix : null,
                            T,
                            (simCar, simBall) -> this.evaluateCollisionState(simCar, simBall, statistics),
                            ballPrediction);
                    if(hasImproved)
                        chosenAngleDiff = angleDiff;
                }
            }
        }

        if (this.strikeSolved) { // Found shot

            if (debugMessages){
                System.out.printf("%d: >> Optimized dodgeManeuver: delay=%.2f duration=%.2f grader=%s doesHitNotInvolveDodge=%s angleDiff=%.3f "+System.lineSeparator(),
                        car.playerIndex, this.strikeDodge.delay, this.strikeDodge.duration, this.customGrader.getClass().getSimpleName(), doesHitNotInvolveDodge, chosenAngleDiff);

                System.out.println(car.playerIndex + ": > Additional grader info: " + this.customGrader.getAdditionalInfo());
            }

        } else { // Couldn't satisfy grader
            this.strikeDodge.direction = null;
            this.strikeDodge.duration = 0;
            this.strikeDodge.delay = 9999;
            this.strikeDodge.setDone();
            System.out.println(car.playerIndex + ": >>> Could not satisfy grader, aborting... (Grader: " + this.customGrader.getClass().getSimpleName() + ", didHitBall=" + statistics.didHitBall + ", didHitBallAfterDodge=" + statistics.didHitBallAfterDodge + ", numHits=" + statistics.numHits + ", numGraderCalls=" + statistics.numGraderCalls + ", numWheelHits=" + statistics.numWheelHits + ", T=" + T + ")");
            System.out.println(car.playerIndex + ": > Additional grader info: " + this.customGrader.getAdditionalInfo());
            System.out.println(car.playerIndex + ": > State info: " + car.toString().replaceAll("\n\t", ",").replaceAll("\n", "") + " ball=" + ball);
        }

        if (this.debugMessages) {
            System.out.println(car.playerIndex + ": > With parameters: maxJumpDelay=" + this.maxJumpDelay + " expectedAt=" + (this.expectedBallHitTime - car.elapsedSeconds) + " jumpDelayStep=" + this.jumpDelayStep + " timer=" + this.strikeDodge.timer + " graderinfo=" + this.customGrader.getAdditionalInfo());
            System.out.println(car.playerIndex + ": > expectedBallHitTime=" + this.expectedBallHitTime + " T=" + T + " numHits=" + statistics.numHits+" wheelHits="+ statistics.numWheelHits);
        }
        if(debugMessages)
            System.out.println(car.playerIndex + ": > Strike planning took: " + (System.currentTimeMillis() - ms) + "ms with " + statistics.simulationCount + " simulations at: " + car.elapsedSeconds);
    }

    public void drawSolvedStrike(AdvancedRenderer renderer) {
        if (this.strikeSolved) {
            this.hitPrediction.draw(renderer, Color.YELLOW, 2);
            this.hitCar.hitbox.draw(renderer, this.hitCar.position, 1, Color.ORANGE);
            renderer.drawCentered3dCube(Color.BLACK, this.hitBall.position, BallData.COLLISION_RADIUS * 2);
            renderer.drawCentered3dCube(Color.GREEN, this.contactPoint, 30);
        }
    }

    private static class StrikeStatistics {
        public int simulationCount = 0;
        public boolean didHitBall = false;
        public boolean didHitBallAfterDodge = false;
        public int numGraderCalls = 0;
        public int numWheelHits = 0;
        public int numHits = 0;
        public boolean hadNonDodgeHit = false;
    }
}
