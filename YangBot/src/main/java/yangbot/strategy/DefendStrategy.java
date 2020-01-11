package yangbot.strategy;

import yangbot.input.*;
import yangbot.manuever.DodgeManeuver;
import yangbot.manuever.DriveManeuver;
import yangbot.manuever.FollowPathManeuver;
import yangbot.optimizers.path.AvoidObstacleInPathUtil;
import yangbot.prediction.Curve;
import yangbot.prediction.YangBallPrediction;
import yangbot.util.AdvancedRenderer;
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
                                        AvoidObstacleInPathUtil.applyBallCollisionFix(pathStatus, controlPoints, currentPath, tries);
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
                            AvoidObstacleInPathUtil.applyBallCollisionFix(status, controlPoints, nextPath, tries);
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

                followPathManeuver.draw(renderer, car);
            }

            if ((state == State.FOLLOW_PATH_STRIKE && (!car.hasWheelContact || followPathManeuver.isDone() || followPathManeuver.arrivalTime - car.elapsedSeconds <= dodgeManeuver.delay + RLConstants.tickFrequency))) {
                dodgeManeuver.target = ballPrediction.getFrameAtRelativeTime(dodgeManeuver.delay - dodgeManeuver.timer + 0.1f).get().ballData.position;
                dodgeManeuver.step(dt, controlsOutput);
            }
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
