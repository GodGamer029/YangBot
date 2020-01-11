package yangbot.manuever;

import yangbot.input.*;
import yangbot.prediction.YangBallPrediction;
import yangbot.vector.Vector2;
import yangbot.vector.Vector3;

public class DribbleManeuver extends Maneuver {

    public Vector2 direction = null;
    public boolean hasBallPossession = false;
    private BallState state;
    private Vector3 ballPredictionPos = null;
    private Vector3 ballPredictionVelocity = null;
    private float ballArrival = 0;
    private Vector3 impact = null;
    private final RecoverToGroundManeuver recoverToGroundManuver;
    private final DriveToPointManeuver driveToPointManeuver;

    public DribbleManeuver() {
        recoverToGroundManuver = new RecoverToGroundManeuver();
        driveToPointManeuver = new DriveToPointManeuver();
        state = BallState.FIND_BALL;
    }

    @Override
    public boolean isViable() {
        GameData gameData = GameData.current();
        CarData car = gameData.getCarData();

        YangBallPrediction ballPrediction = gameData.getBallPrediction();
        final float ballRadius = RLConstants.ballRadius;
        for (YangBallPrediction.YangPredictionFrame frame : ballPrediction.frames) {
            ImmutableBallData frameBall = frame.ballData;
            Vector3 loc = frameBall.position;
            Vector3 vel = frameBall.velocity;

            if (frame.absoluteTime - car.elapsedSeconds < 0.2f)
                continue;

            if (frame.absoluteTime - car.elapsedSeconds > 3.5f)
                continue;

            if (RLConstants.isPosNearWall(loc.flatten(), 10))
                continue;

            if (loc.z <= ballRadius + 2f && Math.abs(vel.z) <= 1f)
                break;

            if (vel.z < 0 && loc.z - ballRadius > car.position.z + 18f && loc.z - ballRadius < car.position.z + 30f)
                return true;
        }

        return false;
    }

    @Override
    public void step(float dt, ControlsOutput controlsOutput) {
        final GameData gameData = this.getGameData();
        final Vector3 gravity = gameData.getGravity();
        final CarData car = gameData.getCarData();
        final ImmutableBallData ball = gameData.getBallData();

        float vf = (float) car.velocity.dot(car.forward());
        if (state == BallState.FIND_BALL) {

            YangBallPrediction ballPrediction = gameData.getBallPrediction();
            for (YangBallPrediction.YangPredictionFrame frame : ballPrediction.frames) {
                ImmutableBallData frameBall = frame.ballData;
                Vector3 loc = frameBall.position;
                Vector3 vel = frameBall.velocity;

                float sliceSeconds = frame.absoluteTime;
                if (sliceSeconds - (car.elapsedSeconds) < 0.2f)
                    continue;

                if (sliceSeconds - (car.elapsedSeconds) > 3.5f)
                    continue;

                if (loc.z <= RLConstants.ballRadius + 2f && Math.abs(vel.z) <= 1f)
                    break;

                if (RLConstants.isPosNearWall(loc.flatten(), 10))
                    continue;

                if (vel.z < 0 &&
                        loc.z - RLConstants.ballRadius > car.position.z + RLConstants.carElevation &&
                        loc.z - RLConstants.ballRadius < car.position.z + RLConstants.carElevation + 12f) {
                    ballPredictionPos = loc.add(vel.mul(dt));
                    ballPredictionVelocity = vel;
                    ballArrival = sliceSeconds;

                    state = BallState.GOTO_BALL;
                    break;
                }
            }

            if (state != BallState.GOTO_BALL)
                this.setIsDone(true);
        } else if (state == BallState.GOTO_BALL) {

            float dist = (float) car.position.flatten().distance(ballPredictionPos.flatten());
            if (car.elapsedSeconds >= ballArrival || (dist < 15 && ball.position.z - RLConstants.ballRadius > car.position.z + RLConstants.carElevation)) {
                if (dist < 50) {
                    //System.out.println("Starting dribble; "+dist);
                    state = BallState.DRIBBLING;
                } else {
                    //System.out.println("Refinding ball; "+dist);
                    state = BallState.FIND_BALL;
                }
                return;
            }

            YangBallPrediction ballPrediction = gameData.getBallPrediction();
            boolean weFoundTheBall = false;
            for (YangBallPrediction.YangPredictionFrame frame : ballPrediction.frames) {
                ImmutableBallData frameBall = frame.ballData;
                Vector3 loc = frameBall.position;
                Vector3 vel = frameBall.velocity;
                if (vel.z < 0 && loc.z - RLConstants.ballRadius > car.position.z + 17f && loc.z - RLConstants.ballRadius < car.position.z + 40f && Math.abs(ballArrival - frame.absoluteTime) < 0.2f) {
                    weFoundTheBall = true;
                    ballPredictionPos = loc;
                    ballPredictionVelocity = vel;
                    ballArrival = frame.absoluteTime;
                    break;
                }
            }
            if (!weFoundTheBall) {
                state = BallState.FIND_BALL;
                // System.out.println("Couldn't get to ball timeLeft: "+(ballArrival - car.elapsedSeconds));
                return;
            }


            {
                float targetVelocity = Math.max(((float) ballPredictionVelocity.flatten().magnitude() - 30), 0);

                driveToPointManeuver.targetPosition = ballPredictionPos.add(car.forward().mul(-10));
                driveToPointManeuver.targetVelocity = 0;
                driveToPointManeuver.step(dt, controlsOutput);
            }
        } else if (state == BallState.DRIBBLING) {
            float toBallDistance = (float) car.position.flatten().distance(ball.position.flatten());
            if (toBallDistance > 200 || (ball.position.z - RLConstants.ballRadius < car.position.z + 10f && Math.abs(ball.velocity.z) < 0.05f)) {
                state = BallState.FIND_BALL;
                hasBallPossession = false;
                return;
            }

            if (car.hasWheelContact && car.up().angle(new Vector3(0, 0, 1)) < Math.PI / 10) {
                driveToPointManeuver.targetPosition = new Vector3(ball.position.flatten().add(ball.velocity.flatten().mul(dt)), car.position.z);
                driveToPointManeuver.targetVelocity = (float) ball.velocity.flatten().magnitude() - 30f;
                driveToPointManeuver.step(dt, controlsOutput);
            } else {
                hasBallPossession = false;
                if (!car.hasWheelContact) {
                    recoverToGroundManuver.orientationTarget = ball.position.flatten().sub(car.position.flatten()).normalized();
                    recoverToGroundManuver.step(dt, controlsOutput);
                }
                return;
            }
        }

        hasBallPossession = state == BallState.DRIBBLING && ball.position.z - RLConstants.ballRadius > car.position.z + 15f;
    }

    @Override
    public CarData simulate(CarData car) {
        return null;
    }

    enum BallState {
        FIND_BALL,
        GOTO_BALL,
        DRIBBLING
    }
}
