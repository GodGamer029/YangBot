package yangbot.strategy;

import yangbot.input.*;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.vector.Vector3;

import java.util.Optional;

public class DefaultStrategy extends Strategy {

    private Strategy newDecidedStrategy = null;
    private int jumpFromWallTick = 0;

    public static void driveToPos(ControlsOutput controlsOutput, Vector3 pos, boolean allowBoost){
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        DriveManeuver.steerController(controlsOutput, car, pos);
        controlsOutput.withThrottle(Math.max(0.05f, (float) (pos.flatten().distance(car.position.flatten()) - 100f) / 100f));
        if (Math.abs(controlsOutput.getSteer()) >= 0.95f && car.angularVelocity.magnitude() < 2f)
            controlsOutput.withSlide(true);
        if(allowBoost && Math.abs(controlsOutput.getSteer()) < 0.1 && controlsOutput.getThrottle() > 0.8f)
            controlsOutput.withBoost();
    }

    public static void smartBallChaser(float dt, ControlsOutput controlsOutput, boolean allowBoost) {
        final GameData gameData = GameData.current();
        final ImmutableBallData ball = gameData.getBallData();
        YangBallPrediction ballPrediction = gameData.getBallPrediction();
        final CarData car = gameData.getCarData();

        Vector3 futureBallPos = ballPrediction.getFrameAtRelativeTime((float) Math.min(0.5f, car.position.flatten().distance(ball.position.flatten()) / (car.velocity.flatten().magnitude() + 50))).get().ballData.position;

        if (!RLConstants.isPosNearWall(futureBallPos.flatten(), BallData.COLLISION_RADIUS + 10) || futureBallPos.z > RLConstants.arenaHeight * 0.8f || futureBallPos.z < 0)
            futureBallPos = futureBallPos.withZ(0);

        if (Math.abs(car.position.y) > RLConstants.goalDistance) {
            futureBallPos = new Vector3(0, 0, 0);
        }

        DefaultStrategy.driveToPos(controlsOutput, futureBallPos.withZ(0), allowBoost);
    }

    @Override
    public void planStrategyInternal() {
        GameData gameData = GameData.current();
        final ImmutableBallData ball = gameData.getBallData();
        CarData car = gameData.getCarData();

        if (!car.hasWheelContact) {
            newDecidedStrategy = new RecoverStrategy();
            this.setDone();
            return;
        }
        newDecidedStrategy = new GenericStrategyPlanner();
        this.setDone();
    }

    @Override
    public void stepInternal(float dt, ControlsOutput controlsOutput) {
        // Chase the ball like a pro

        GameData gameData = GameData.current();
        final ImmutableBallData ball = gameData.getBallData();
        final CarData car = gameData.getCarData();

        if (!car.hasWheelContact) {
            if (jumpFromWallTick > 0) {
                jumpFromWallTick--;
                controlsOutput.withJump(true);
                return;
            } else {
                newDecidedStrategy = new RecoverStrategy();
                this.setDone();
                return;
            }
        } else {
            jumpFromWallTick = 0;
            // On the ceiling
            if (car.position.z > 200 && car.up().dot(new Vector3(0, 0, -1)) > 0.9f) {
                jumpFromWallTick = (int) (0.15f / RLConstants.tickFrequency);
                controlsOutput.withJump(true);
                return;
            }
        }

        DefaultStrategy.smartBallChaser(dt, controlsOutput, false);

        this.setDone();
    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.ofNullable(newDecidedStrategy);
    }
}
