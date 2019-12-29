package yangbot.strategy;

import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.input.RLConstants;
import yangbot.manuever.DodgeManeuver;
import yangbot.manuever.DriveManeuver;
import yangbot.manuever.FollowPathManeuver;
import yangbot.prediction.Curve;
import yangbot.prediction.YangBallPrediction;
import yangbot.util.AdvancedRenderer;
import yangbot.util.ControlsOutput;
import yangbot.vector.Vector2;
import yangbot.vector.Vector3;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DefendStrategy extends Strategy {

    private State state = State.BALLCHASE;
    private FollowPathManeuver followPathManeuver = new FollowPathManeuver();
    private DodgeManeuver dodgeManeuver = new DodgeManeuver();
    private Vector3 collision = null;

    @Override
    protected void planStrategyInternal() {
        // How critical is the situation?
        GameData gameData = GameData.current();
        CarData car = gameData.getCarData();
        BallData ball = gameData.getBallData();
        YangBallPrediction ballPrediction = gameData.getBallPrediction();

        if (this.checkReset(0.5f))
            return;

        if (!car.hasWheelContact || car.position.z > 100) {
            this.setDone();
            return;
        }

        int teamSign = car.team * 2 - 1;

        Optional<YangBallPrediction.YangPredictionFrame> firstConcedingGoalFrame = ballPrediction.getFramesBeforeRelative(4f)
                .stream()
                .filter((f) -> (int) Math.signum(f.ballData.position.y) == teamSign && Math.abs(f.ballData.position.y) > RLConstants.goalDistance + BallData.RADIUS && f.ballData.position.z <= 300)
                .findFirst();

        if (firstConcedingGoalFrame.isPresent()) { // We getting scored on
            state = State.GETTING_SCORED_ON_GROUND;
            return;
        }

        if (car.position.y * teamSign > RLConstants.goalDistance * 0.7f || car.position.distance(ball.position) < 400)
            state = State.BALLCHASE;
        else
            state = State.ROTATE;
    }

    private List<Curve.ControlPoint> applyBallCollisionFix(Curve.PathCheckStatus pathCheckStatus, List<Curve.ControlPoint> controlPoints, Curve currentPath, int tries) {
        if (!pathCheckStatus.collidedWithBall)
            return controlPoints;

        // Take the contact point and place a control point in the opposite direction
        Vector3 contactPoint = pathCheckStatus.ballCollisionContactPoint.withZ(controlPoints.get(0).point.z);
        final float distanceAtCollision = currentPath.findNearest(contactPoint);
        Vector3 pathPointAtCollision = currentPath.pointAt(distanceAtCollision);
        Vector3 tangentAtCollision = currentPath.tangentAt(distanceAtCollision);

        Vector3 collisionNormal = contactPoint.sub(pathPointAtCollision);
        Vector3 collisionNormalParallel = tangentAtCollision.mul(collisionNormal.dot(tangentAtCollision));
        Vector3 collisionNormalPerpendicular = collisionNormal.sub(collisionNormalParallel).normalized();

        Vector3 newControlPoint = pathPointAtCollision.add(collisionNormalPerpendicular.mul(BallData.RADIUS * -1.2f * (tries / 2f + 1)).withZ(0)).withZ(controlPoints.get(0).point.z);
        if (tries == 0)
            controlPoints.add(1, new Curve.ControlPoint(newControlPoint, newControlPoint.sub(controlPoints.get(0).point).normalized()));
        else
            controlPoints.set(1, new Curve.ControlPoint(newControlPoint, newControlPoint.sub(controlPoints.get(0).point).normalized()));
        controlPoints.get(2).tangent = controlPoints.get(2).point.sub(controlPoints.get(1).point);
        this.collision = pathCheckStatus.ballCollisionContactPoint;

        return controlPoints;
    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {
        if (this.reevaluateStrategy((state == State.FOLLOW_PATH || state == State.FOLLOW_PATH_STRIKE) ? 1.2f : 0.3f))
            return; // Return if we are done

        GameData gameData = GameData.current();
        CarData car = gameData.getCarData();
        BallData ball = gameData.getBallData();
        AdvancedRenderer renderer = gameData.getAdvancedRenderer();
        YangBallPrediction ballPrediction = gameData.getBallPrediction();

        int teamSign = car.team * 2 - 1;

        switch (state) {
            case GETTING_SCORED_ON_GROUND: {
                final Optional<YangBallPrediction.YangPredictionFrame> firstConcedingGoalFrame = ballPrediction.getFramesBeforeRelative(4f)
                        .stream()
                        .filter((f) -> Math.signum(f.ballData.position.y) == teamSign && Math.abs(f.ballData.position.y) > RLConstants.goalDistance + BallData.RADIUS && f.ballData.position.z <= 300)
                        .findFirst();

                if (!firstConcedingGoalFrame.isPresent()) {
                    this.reevaluateStrategy(0);
                    return;
                }

                final YangBallPrediction.YangPredictionFrame frameConceding = firstConcedingGoalFrame.get();

                final YangBallPrediction framesBeforeGoal = ballPrediction.getBeforeRelative(frameConceding.relativeTime);
                if (framesBeforeGoal.frames.size() == 0) {
                    this.reevaluateStrategy(0);
                    return;
                }

                Curve validPath = null;
                float arrivalTime = 0;

                float t = frameConceding.relativeTime - RLConstants.simulationTickFrequency * 2;

                // Path finder
                do {
                    final Optional<YangBallPrediction.YangPredictionFrame> interceptFrameOptional = framesBeforeGoal.getFrameAtRelativeTime(t);
                    if (!interceptFrameOptional.isPresent())
                        break;

                    final YangBallPrediction.YangPredictionFrame interceptFrame = interceptFrameOptional.get();
                    final Vector3 targetPos = interceptFrame.ballData.position;

                    Curve currentPath;
                    List<Curve.ControlPoint> controlPoints = new ArrayList<>();
                    // Construct Path
                    {
                        controlPoints.add(new Curve.ControlPoint(car.position.add(car.forward().mul(10)), car.forward()));
                        controlPoints.add(new Curve.ControlPoint(targetPos.withZ(car.position.z), new Vector3(0, -teamSign, 0)));

                        currentPath = new Curve(controlPoints);
                    }

                    // Check if path is valid
                    {
                        currentPath.calculateMaxSpeeds(CarData.MAX_VELOCITY, CarData.MAX_VELOCITY);
                        Curve.PathCheckStatus pathStatus = currentPath.doPathChecking(car, interceptFrame.absoluteTime, ballPrediction);
                        if (pathStatus.isValid() || validPath == null) {
                            if (pathStatus.isValid() && pathStatus.collidedWithBall) {
                                for (int tries = 0; tries < 3; tries++) { // Move the path away from the ball
                                    pathStatus = currentPath.doPathChecking(car, -1, ballPrediction);
                                    if (pathStatus.collidedWithBall) { // Colliding with ball
                                        controlPoints = this.applyBallCollisionFix(pathStatus, controlPoints, currentPath, tries);
                                        currentPath = new Curve(controlPoints);
                                    } else
                                        break;
                                }
                            }
                            if (pathStatus.isValid() && !pathStatus.collidedWithBall) {
                                validPath = currentPath;
                                arrivalTime = interceptFrame.absoluteTime;
                            }

                        }
                    }
                    t -= RLConstants.simulationTickFrequency * 4;
                } while (t > 0);

                if (validPath == null) {
                    this.reevaluateStrategy(0);
                    return;
                }
                state = State.FOLLOW_PATH_STRIKE;
                dodgeManeuver = new DodgeManeuver();
                dodgeManeuver.delay = 0.3f;
                dodgeManeuver.duration = 0.1f;
                followPathManeuver.path = validPath;
                followPathManeuver.arrivalTime = arrivalTime;
                break;
            }
            case FOLLOW_PATH:
            case FOLLOW_PATH_STRIKE:
                break;
            case ROTATE: {
                Curve nextPath;
                Curve.PathCheckStatus status;
                // Construct Path
                {
                    List<Curve.ControlPoint> controlPoints = new ArrayList<>();
                    {
                        controlPoints.add(new Curve.ControlPoint(car.position, car.forward()));
                        controlPoints.add(new Curve.ControlPoint(new Vector3(0, RLConstants.goalDistance * 0.9f * teamSign, car.position.z), new Vector3(0, teamSign, 0)));

                        nextPath = new Curve(controlPoints);
                    }

                    for (int tries = 0; tries < 5; tries++) { // Move the path away from the ball
                        status = nextPath.doPathChecking(car, -1, ballPrediction);
                        if (status.collidedWithBall) { // Colliding with ball
                            this.applyBallCollisionFix(status, controlPoints, nextPath, tries);
                            nextPath = new Curve(controlPoints);
                        } else
                            break;
                    }
                }

                followPathManeuver.path = nextPath;
                followPathManeuver.arrivalTime = -1;
                state = State.FOLLOW_PATH;
            }
            case BALLCHASE:
                DefaultStrategy.smartBallChaser(dt, controlsOutput);
                break;
            case HIT_BALL_AWAY:

                break;
            default:
                assert false;
                break;
        }

        if (state == State.FOLLOW_PATH || state == State.FOLLOW_PATH_STRIKE) {
            followPathManeuver.path.draw(renderer);
            followPathManeuver.step(dt, controlsOutput);
            float distanceOffPath = (float) car.position.flatten().distance(followPathManeuver.path.pointAt(followPathManeuver.path.findNearest(car.position)).flatten());
            if (followPathManeuver.isDone())
                this.reevaluateStrategy(0.05f);
            else if (distanceOffPath > 100)
                this.reevaluateStrategy(0.25f);

            if (this.collision != null && !this.collision.isZero()) {
                renderer.drawCentered3dCube(Color.MAGENTA, this.collision, 70);
                renderer.drawCentered3dCube(Color.MAGENTA, this.collision, 170);
            }

            if (state == State.FOLLOW_PATH) {
                Vector2 myGoal = new Vector2(0, teamSign * RLConstants.goalDistance);
                float distanceCarToDefend = (float) car.position.add(car.velocity.mul(0.3f)).flatten().distance(myGoal);
                float distanceBallToDefend = (float) ballPrediction.getFrameAtRelativeTime(0.5f).get().ballData.position.flatten().distance(myGoal);

                if (distanceCarToDefend + 300 > distanceBallToDefend && (followPathManeuver.arrivalTime < 0 || followPathManeuver.arrivalTime - car.elapsedSeconds > 0.4f)) {
                    followPathManeuver.arrivalSpeed = 2200;
                } else {
                    followPathManeuver.arrivalSpeed = DriveManeuver.max_throttle_speed - 10;
                }


            }

            if ((state == State.FOLLOW_PATH_STRIKE && (!car.hasWheelContact || followPathManeuver.isDone() || followPathManeuver.arrivalTime - car.elapsedSeconds <= dodgeManeuver.delay + RLConstants.tickFrequency))) {
                dodgeManeuver.target = ballPrediction.getFrameAtRelativeTime(dodgeManeuver.delay - dodgeManeuver.timer + 0.1f).get().ballData.position;
                dodgeManeuver.step(dt, controlsOutput);
            }

            if (followPathManeuver.arrivalTime > 0)
                renderer.drawString2d(String.format("Arriving in %.1fs", followPathManeuver.arrivalTime - car.elapsedSeconds), Color.WHITE, new Point(500, 450), 2, 2);
            renderer.drawString2d(String.format("Max speed: %.0fuu/s", followPathManeuver.path.maxSpeedAt(followPathManeuver.path.findNearest(car.position))), Color.WHITE, new Point(500, 490), 2, 2);
            renderer.drawString2d(String.format("Max drive: %.0fuu/s", followPathManeuver.driveManeuver.speed), Color.WHITE, new Point(500, 530), 2, 2);
            renderer.drawString2d(String.format("Off path: %.0fuu", distanceOffPath), Color.WHITE, new Point(500, 570), 2, 2);
        }
    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.empty();
    }

    enum State {
        GETTING_SCORED_ON_GROUND,
        ROTATE,
        FOLLOW_PATH,
        FOLLOW_PATH_STRIKE,
        BALLCHASE,
        HIT_BALL_AWAY
    }
}
