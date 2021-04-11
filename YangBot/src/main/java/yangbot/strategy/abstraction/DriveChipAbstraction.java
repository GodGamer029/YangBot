package yangbot.strategy.abstraction;

import org.jetbrains.annotations.NotNull;
import yangbot.input.*;
import yangbot.input.interrupt.BallTouchInterrupt;
import yangbot.input.interrupt.InterruptManager;
import yangbot.path.builders.SegmentedPath;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.Line2;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

import java.awt.*;

public class DriveChipAbstraction extends Abstraction {

    public static final float MAX_CHIP_HEIGHT = 30 + BallData.COLLISION_RADIUS; // magic value for now, should probably be using ball radius and car hitbox to calculate
    public static final float OPTIMAL_Z = 25; // Optimize to hit at this z pos
    private static final float SWITCH_TO_CHIP_TIME = 0.4f;
    public final DriveAbstraction driveAbstraction;
    public float arrivalTime = 0;
    public State state;
    public Vector3 originalTargetBallPos = null;
    public boolean debugMessages = true;
    private Vector3 driveTarget;
    private DriveManeuver driveManeuver;
    private BallTouchInterrupt ballTouchInterrupt = null;
    private Vector3 updatedTargetBallPos = null;

    public DriveChipAbstraction(@NotNull SegmentedPath path) {
        this.driveAbstraction = new DriveAbstraction(path);
        this.state = State.DRIVE;
    }

    public static float getBallExtension(float ballHeight) {
        var car = GameData.current().getCarData();
        final float CAR_BALL_IMPACT_Z = RLConstants.carElevation + car.hitbox.hitboxOffset.z + car.hitbox.hitboxLengths.z * 0.5f;
        final float r = BallData.COLLISION_RADIUS; // Calculate where our car will be able to touch the ball horizontally
        final float ballCarDiff = CAR_BALL_IMPACT_Z - ballHeight;

        if (Math.abs(ballCarDiff) > r)
            return -1;
        return (float) Math.sqrt(r * r - ballCarDiff * ballCarDiff);
    }

    @Override
    protected RunState stepInternal(float dt, ControlsOutput controlsOutput) {
        assert this.arrivalTime > 0;

        final GameData gameData = this.getGameData();
        final CarData car = gameData.getCarData();
        YangBallPrediction ballPrediction = gameData.getBallPrediction();
        final AdvancedRenderer renderer = gameData.getAdvancedRenderer();

        if (this.ballTouchInterrupt == null)
            this.ballTouchInterrupt = InterruptManager.get().getBallTouchInterrupt(-1);

        if (this.originalTargetBallPos != null) {
            renderer.drawCentered3dCube(Color.PINK, this.originalTargetBallPos, BallData.COLLISION_RADIUS * 2);
        }
        if (this.updatedTargetBallPos != null) {
            renderer.drawCentered3dCube(Color.MAGENTA, this.updatedTargetBallPos, BallData.COLLISION_RADIUS * 2);
        }

        switch (this.state) {
            case DRIVE: {
                if (this.ballTouchInterrupt.hasInterrupted()) {
                    if (debugMessages)
                        System.out.println("Quitting chip because ballTouchInterrupt");
                    return RunState.FAILED;
                }

                var runState = this.driveAbstraction.step(dt, controlsOutput);
                if (runState == RunState.FAILED) {
                    if (debugMessages)
                        System.out.println("Quitting chip because DriveAbstraction failed");
                    return RunState.FAILED;
                }

                if (this.arrivalTime - 0.1f < car.elapsedSeconds) {
                    if (debugMessages)
                        System.out.println(car.playerIndex + ": Quitting chip because missed time");

                    return RunState.FAILED;
                }

                if (this.arrivalTime - car.elapsedSeconds < SWITCH_TO_CHIP_TIME || this.driveAbstraction.isDone()) {
                    this.state = State.PREPARE_CHIP;
                    float delay = MathUtils.clip(this.arrivalTime - car.elapsedSeconds, 0.1f, 1f);

                    Vector2 futureBallPos = ballPrediction.getFrameAtRelativeTime(delay).get().ballData.position.flatten();
                    CarData simCar = new CarData(car);
                    simCar.smartPrediction(delay);
                    float dist = (float) simCar.position.flatten().distance(futureBallPos) - BallData.COLLISION_RADIUS - car.hitbox.getForwardExtent();
                    if (dist > 200) {
                        if (debugMessages)
                            System.out.println(car.playerIndex + ": Quitting chip because nowhere near hitting dist=" + dist + " delay=" + delay + " carp=" + car.position + " carv=" + car.velocity + " ballp=" + futureBallPos + " simp=" + simCar.position);

                        return RunState.FAILED;
                    }
                }
                break;
            }
            case PREPARE_CHIP: {
                float arrival = this.arrivalTime - car.elapsedSeconds;
                assert arrival > 0;

                var ballStateOpt = ballPrediction.getFrameAtRelativeTime(arrival);
                assert ballStateOpt.isPresent();

                var ballState = ballStateOpt.get();

                if (ballState.ballData.position.z > 500) {
                    if (debugMessages)
                        System.out.println(car.playerIndex + ": Quitting chip because ball way too high bp=" + ballState.ballData.position + " bv=" + ballState.ballData.velocity);

                    return RunState.FAILED;
                }

                final boolean printBallStateDebug = false;
                if (printBallStateDebug) {
                    System.out.println(">>>>>>>>>>>>");
                    System.out.println("Ball state before: t=" + arrival + " p=" + ballState.ballData.position.z + " v=" + ballState.ballData.velocity.z);

                }

                final float MAX_STEP = 0.1f;
                for (int i = 0; i < 8; i++) {
                    float zVel = ballState.ballData.velocity.z;
                    float zPos = ballState.ballData.position.z;
                    if (Math.abs(zVel) < 50 && zPos - BallData.RADIUS < 10) {
                        if (printBallStateDebug)
                            System.out.println("Rolling on ground, quitting");
                        break; // just rolling on the ground, nothing to improve here
                    }

                    if (Math.abs(zVel) > 50 && zVel < 0) { // Still falling towards the ground
                        float dist = zPos - BallData.RADIUS;
                        assert dist >= 0 : "ball predicted to be in the ground?? zPos=" + zPos + " vel=" + zVel + " arrival=" + arrival + " i=" + i;
                        float t = Math.min(MAX_STEP, Math.abs(dist / zVel));

                        var newBallStateOpt = ballPrediction.getFrameAfterRelativeTime(ballState.relativeTime + t);
                        if (newBallStateOpt.isEmpty())
                            break;

                        var newBallState = newBallStateOpt.get();
                        ballState = newBallState;
                        arrival = newBallState.relativeTime;

                        if (printBallStateDebug)
                            System.out.println("optimized falling, continuin p=" + ballState.ballData.position.z + " v=" + ballState.ballData.velocity.z + " addedT=" + t);
                        continue;
                    }
                    if (zVel > 10) { // Bouncing back up
                        float correction = OPTIMAL_Z - (zPos - BallData.RADIUS);
                        if (Math.abs(correction) < 5 || (Math.abs(correction) < 30 && zVel < 150)) {
                            if (printBallStateDebug)
                                System.out.println("very Optimal, quitting " + correction + " zVel=" + zVel);
                            break; // As optimal as we could get
                        }

                        float t = MathUtils.clip(correction / zVel, -MAX_STEP, MAX_STEP);
                        t *= 0.8f; // overshooting is really unnecessary here
                        if (Math.abs(t) < RLConstants.tickFrequency * 1.1f) {
                            if (printBallStateDebug)
                                System.out.println("Optimal, quitting");
                            break; // Optimal
                        }

                        var newBallStateOpt = ballPrediction.getFrameAfterRelativeTime(ballState.relativeTime + t);
                        if (newBallStateOpt.isEmpty())
                            break;

                        var newBallState = newBallStateOpt.get();
                        ballState = newBallState;
                        arrival = newBallState.relativeTime;

                        if (printBallStateDebug)
                            System.out.println("optimized bounce up, continuin p=" + ballState.ballData.position.z + " v=" + ballState.ballData.velocity.z + " addedT=" + t + " correction=" + correction);
                        continue;
                    }
                    break;
                }
                if (printBallStateDebug) {
                    System.out.println("Ball state after: t=" + arrival + " p=" + ballState.ballData.position.z + " v=" + ballState.ballData.velocity.z);
                    System.out.println("#########");
                }


                var predBall = ballState.ballData;
                // Calculate target drive pos
                // TODO: optimize for more goals

                final Vector2 enemyGoal = new Vector2(0, -car.getTeamSign() * (RLConstants.goalDistance + 1000));

                final float goalCenterToPostDistance = RLConstants.goalCenterToPost - BallData.COLLISION_RADIUS * 2 - 50 /* tolerance */;
                assert goalCenterToPostDistance > 100; // Could fail with smaller goals
                assert enemyGoal.x == 0; // Could fail with custom goals
                final Line2 enemyGoalLine = new Line2(enemyGoal.sub(goalCenterToPostDistance, 0), enemyGoal.add(goalCenterToPostDistance, 0));

                final Vector2 closestScoringPosition = enemyGoalLine.closestPointOnLine(predBall.position.flatten());
                final Vector3 ballTargetToGoalTarget = closestScoringPosition.sub(predBall.position.flatten()).normalized().withZ(0);

                var ballExtension = DriveChipAbstraction.getBallExtension(predBall.position.z);
                if (ballExtension == -1)
                    return RunState.FAILED;

                Vector3 ballHitTarget = predBall.position.sub(ballTargetToGoalTarget.mul(ballExtension + car.hitbox.getForwardExtent()));
                ballHitTarget = ballHitTarget.withZ(RLConstants.carElevation);

                //var carBallTangent = predBall.position.flatten().sub(car.position.flatten()).normalized();
                //var ballExt = DriveChipAbstraction.getBallExtension(predBall.position.z);
                //this.driveTarget = predBall.position.sub(carBallTangent.mul(ballExt + car.hitbox.getForwardExtent()).withZ(0)).withZ(RLConstants.carElevation);

                this.updatedTargetBallPos = predBall.position;
                this.driveTarget = ballHitTarget;
                this.driveManeuver = new DriveManeuver(this.driveTarget);
                this.arrivalTime = arrival + car.elapsedSeconds;
                this.state = State.CHIP;
                // fallthrough to CHIP
            }
            case CHIP: {
                if (car.elapsedSeconds > this.arrivalTime + 0.05f /*make sure we actually hit the ball*/) {
                    return RunState.DONE;
                }
                if (this.ballTouchInterrupt.hasInterrupted()) {
                    if (debugMessages)
                        System.out.println("Quitting chip because ballTouchInterrupt");
                    return RunState.DONE;
                }
                if (!car.hasWheelContact) {
                    if (debugMessages)
                        System.out.println("Quitting chip because not on ground");
                    return RunState.FAILED;
                }
                /*var targetSpeed = FollowPathManeuver.determineSpeedPlan(
                        car.position.flatten().distance(this.driveTarget.flatten()),
                        this.arrivalTime - car.elapsedSeconds,
                        RLConstants.tickFrequency,
                        car.forwardSpeed(),
                        )*/
                // use (dist / time) for now
                var targetSpeed = (float) car.position.flatten().distance(this.driveTarget.flatten()) / (this.arrivalTime - car.elapsedSeconds);
                this.driveManeuver.setSpeed(targetSpeed);
                this.driveManeuver.reaction_time = MathUtils.clip(this.arrivalTime - car.elapsedSeconds - 0.05f, 0.04f, 0.1f);
                this.driveManeuver.step(dt, controlsOutput);
                break;
            }
        }

        return RunState.CONTINUE;
    }

    @Override
    public boolean canInterrupt() {
        if (this.state == State.CHIP)
            return false;
        return super.canInterrupt();
    }

    enum State {
        DRIVE,
        PREPARE_CHIP, // re-evaluate target ball position (and time), and switch to chip afterwards
        CHIP // less than a second until impact
    }
}
