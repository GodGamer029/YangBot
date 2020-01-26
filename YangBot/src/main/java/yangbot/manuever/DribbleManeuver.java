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

        Optional<YangBallPrediction.YangPredictionFrame> frameOptional = ballPrediction.getFramesBetweenRelative(0, 2f).stream().filter((f) -> f.ballData.position.z <= zThreshold && f.ballData.position.z > BallData.COLLISION_RADIUS * 1.2f && f.ballData.velocity.z <= 0).findFirst();


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

        if (!car.isGrounded() || !isViable()) {
            this.setDone();
            return;
        }

        float zThreshold = car.hitbox.permutatePoint(car.position, 0, 0, 1).z + BallData.COLLISION_RADIUS;
        Optional<YangBallPrediction.YangPredictionFrame> frameOptional = ballPrediction.getFramesBetweenRelative(RLConstants.tickFrequency, 2f).stream().filter((f) -> f.ballData.position.z <= zThreshold && f.ballData.velocity.z < 0).findFirst();
        if (frameOptional.isPresent()) {
            YangBallPrediction.YangPredictionFrame frame = frameOptional.get();
            Vector3 driveTarget = frame.ballData.position.withZ(car.position.z);

            // Ball Control
            {
                Vector2 ballVel = ball.velocity.flatten();
                float ballSpeed = (float) ballVel.magnitude();

                float forwardDisplacement = 0;
                float leftDisplacement = 0;
                // Throttle
                {
                    renderer.drawString2d("BallSpeed: " + ballSpeed, Color.WHITE, new Point(500, 700), 2, 2);
                    if (ballSpeed < 1000)
                        forwardDisplacement = -1f;
                    else if (ballSpeed > 2000)
                        forwardDisplacement = 1;
                    else
                        forwardDisplacement = MathUtils.lerp(-0.8f, 0.8f, (ballSpeed - 1000f) / 400f);
                }


                // Steer
                if (ballVel.magnitude() > 10) {
                    final int teamSign = car.team * 2 - 1;
                    final Vector2 enemyGoal = new Vector2(0, -teamSign * (RLConstants.goalDistance + 200));
                    Vector2 target = enemyGoal;
                    float angle = MathUtils.clip((float) ball.velocity.flatten().normalized().correctionAngle(enemyGoal.sub(ball.position.flatten()).normalized()), -1, 1);
                    leftDisplacement = -angle;
                }


                float scaler = Math.max(1, Math.abs(forwardDisplacement) + Math.abs(leftDisplacement));
                driveTarget = driveTarget.add(car.hitbox.permF.mul(forwardDisplacement / scaler));
                driveTarget = driveTarget.add(car.hitbox.permL.mul(leftDisplacement));
            }

            if (car.position.distance(driveTarget) < 150) {
                driveManeuver.target = driveTarget.add(ball.velocity.flatten().mul(0.2f).withZ(0));
                if (car.position.distance(driveTarget) < 30)
                    driveManeuver.minimumSpeed = (float) ball.velocity.flatten().magnitude();
                else
                    driveManeuver.minimumSpeed = (float) car.position.distance(driveTarget) / Math.max(RLConstants.tickFrequency, frame.relativeTime);

                driveManeuver.reaction_time = 0.1f;
                driveManeuver.maximumSpeed = driveManeuver.minimumSpeed + 25;
                driveManeuver.minimumSpeed += 5;
                driveManeuver.allowBoostForLowSpeeds = false;
                driveManeuver.step(dt, controlsOutput);
                if (controlsOutput.holdBoost())
                    System.out.println(driveManeuver.minimumSpeed + ", " + car.position.distance(driveTarget) + ", " + frame.relativeTime);
            } else {
                if (followPathManeuver == null || Math.abs(followPathManeuver.arrivalTime - frame.absoluteTime) > 0.1f || followPathManeuver.arrivalTime - car.elapsedSeconds <= 0) {
                    followPathManeuver = new FollowPathManeuver();
                    List<Curve.ControlPoint> controlPoints = new ArrayList<>();

                    controlPoints.add(new Curve.ControlPoint(car.position, car.forward()));
                    controlPoints.add(new Curve.ControlPoint(driveTarget, car.forward().mul(-1)));

                    Curve currentPath = new Curve(controlPoints);
                    followPathManeuver.path = currentPath;
                    followPathManeuver.arrivalTime = frame.absoluteTime;

                }
                followPathManeuver.step(dt, controlsOutput);
                followPathManeuver.draw(renderer, car);
                followPathManeuver.path.draw(renderer);
                renderer.drawCentered3dCube(Color.YELLOW, frame.ballData.position.withZ(zThreshold), 50);
            }
        }
    }

    @Override
    public CarData simulate(CarData car) {
        return null;
    }

}
