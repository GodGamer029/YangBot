package yangbot.manuever;

import yangbot.input.*;
import yangbot.prediction.Curve;
import yangbot.prediction.YangBallPrediction;
import yangbot.util.AdvancedRenderer;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DribbleManeuver extends Maneuver {

    public Vector2 direction = null;
    private final DriveManeuver driveManeuver;
    private FollowPathManeuver followPathManeuver = null;

    public DribbleManeuver() {
        driveManeuver = new DriveManeuver();
    }

    @Override
    public boolean isViable() {
        GameData gameData = GameData.current();
        CarData car = gameData.getCarData();

        if (!car.isGrounded())
            return false;

        YangBallPrediction ballPrediction = gameData.getBallPrediction();

        float zThreshold = car.hitbox.permutatePoint(car.position, 0, 0, 1).z + BallData.COLLISION_RADIUS;

        Optional<YangBallPrediction.YangPredictionFrame> frameOptional = ballPrediction.getFramesBetweenRelative(0, 3f).stream().filter((f) -> f.ballData.position.z <= zThreshold && f.ballData.position.z > zThreshold - 20 && f.ballData.velocity.z <= 0 && !RLConstants.isPosNearWall(f.ballData.position.flatten(), 50)).findFirst();

        return frameOptional.isPresent();
    }

    @Override
    public void step(float dt, ControlsOutput controlsOutput) {
        final GameData gameData = this.getGameData();
        final Vector3 gravity = gameData.getGravity();
        final CarData car = gameData.getCarData();
        final ImmutableBallData ball = gameData.getBallData();
        final YangBallPrediction ballPrediction = gameData.getBallPrediction();
        final AdvancedRenderer renderer = gameData.getAdvancedRenderer();

        final int teamSign = car.team * 2 - 1;
        final Vector2 enemyGoal = new Vector2(0, -teamSign * (RLConstants.goalDistance + 100));

        if (!car.isGrounded() || !isViable()) {
            this.setDone();
            return;
        }

        float zThreshold = car.hitbox.permutatePoint(car.position, 0, 0, 1).z + BallData.COLLISION_RADIUS + 5;
        Optional<YangBallPrediction.YangPredictionFrame> frameOptional = ballPrediction.getFramesBetweenRelative(RLConstants.tickFrequency, 3f).stream().filter((f) -> f.ballData.position.z <= zThreshold && f.ballData.velocity.z <= 10).findFirst();
        if (frameOptional.isPresent()) {
            YangBallPrediction.YangPredictionFrame frame = frameOptional.get();
            renderer.drawCentered3dCube(Color.YELLOW, frame.ballData.position, BallData.RADIUS + 30);
            Vector3 driveTarget = frame.ballData.position.withZ(car.position.z);
            Vector2 ballTarget = enemyGoal;

            // Ball Control
            {
                Vector2 ballVel = ball.velocity.flatten();
                float ballSpeed = (float) ballVel.magnitude();

                float forwardDisplacement = 0;
                float leftDisplacement = 0;

                // Steer
                if (ballVel.magnitude() > 10) {
                    Vector2 target = enemyGoal;
                    float angle = (float) ball.velocity.flatten().normalized().correctionAngle(enemyGoal.sub(ball.position.flatten()).normalized());
                    leftDisplacement = MathUtils.clip((float) Math.pow(-angle * 2, 3), -0.8f, 0.8f);
                }

                // Throttle
                {
                    float lowSpeedLimit = 1000;
                    float highSpeedLimit = 1300;

                    if (car.boost < 30) {
                        lowSpeedLimit -= 300;
                        highSpeedLimit -= 200;
                    }

                    if (Math.abs(leftDisplacement) < 0.1f) {
                        lowSpeedLimit *= 2;
                        highSpeedLimit *= 2;
                    }

                    renderer.drawString2d("BallSpeed: " + ballSpeed, Color.WHITE, new Point(500, 700), 2, 2);
                    if (ballSpeed < lowSpeedLimit * 0.6f - 100)
                        forwardDisplacement = -1.1f;
                    else if (ballSpeed < lowSpeedLimit)
                        forwardDisplacement = -0.8f;
                    else if (ballSpeed > highSpeedLimit)
                        forwardDisplacement = 0.8f;
                    else
                        forwardDisplacement = MathUtils.remap(ballSpeed, lowSpeedLimit, highSpeedLimit, -0.8f, 0.8f);
                }

                float scaler = Math.max(1, Math.abs(forwardDisplacement) + Math.abs(leftDisplacement));
                driveTarget = driveTarget.add(car.hitbox.permF.mul(forwardDisplacement / scaler));
                driveTarget = driveTarget.add(car.hitbox.permL.mul(leftDisplacement));

                renderer.drawString2d("F: " + (forwardDisplacement / scaler) + "\nL: " + leftDisplacement, Color.WHITE, new Point(500, 750), 2, 2);
            }

            if (car.position.distance(driveTarget) < 150) {
                driveManeuver.target = driveTarget.add(ball.velocity.flatten().mul(0.2f).withZ(0));
                driveManeuver.minimumSpeed = (float) car.position.distance(driveTarget) / Math.max(RLConstants.tickFrequency, frame.relativeTime);

                if (car.position.distance(driveTarget) < 50)
                    driveManeuver.minimumSpeed = (float) MathUtils.lerp(driveManeuver.minimumSpeed, ball.velocity.flatten().magnitude(), 0.3f);

                //driveManeuver.reaction_time = 0.1f;
                driveManeuver.maximumSpeed = driveManeuver.minimumSpeed * 1.01f;
                driveManeuver.allowBoostForLowSpeeds = false;
                driveManeuver.step(dt, controlsOutput);
                //if (controlsOutput.holdBoost())
                //    System.out.println(driveManeuver.minimumSpeed + ", " + car.position.distance(driveTarget) + ", " + frame.relativeTime);
            } else {
                if (followPathManeuver == null || Math.abs(followPathManeuver.arrivalTime - frame.absoluteTime) > 0.1f || followPathManeuver.arrivalTime - car.elapsedSeconds <= 0 || followPathManeuver.getDistanceOffPath(car) > 50) {
                    followPathManeuver = new FollowPathManeuver();

                    final Vector3 startPosition = car.position.add(car.forward().mul(10));
                    final Vector3 startTangent = car.forward();
                    final Vector3 endPosition = driveTarget;
                    final Vector3 endTangent = car.forward().mul(-1);

                    List<Curve.ControlPoint> controlPoints = new ArrayList<>();

                    controlPoints.add(new Curve.ControlPoint(startPosition, startTangent));
                    controlPoints.add(new Curve.ControlPoint(endPosition, endTangent.mul(-1)));

                    Curve currentPath = new Curve(controlPoints);
                    /*if(startPosition.distance(endPosition) > 200 && currentPath.tangentAt(Math.max(0, currentPath.findNearest(car.position) - 5)).dot(car.forward()) < 0){ // Following the path will probably fail, we can't drive backwards
                        Optional<Curve> curveOptional = YangBotJNAInterop.findPath(startPosition, startTangent, endPosition, endTangent, 15);
                        if(curveOptional.isPresent())
                            currentPath = curveOptional.get();
                    }*/

                    Curve.PathCheckStatus pathCheckStatus = currentPath.doPathChecking(car, frame.absoluteTime - 0.1f, null);
                    if (!pathCheckStatus.isValid() && !this.isViable()) {
                        this.setDone();
                        return;
                    }

                    followPathManeuver.path = currentPath;
                    followPathManeuver.arrivalTime = frame.absoluteTime;
                }
                followPathManeuver.step(dt, controlsOutput);
                followPathManeuver.draw(renderer, car);
                followPathManeuver.path.draw(renderer);

            }
        }
    }

    @Override
    public CarData simulate(CarData car) {
        return null;
    }

}
