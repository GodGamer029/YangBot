package yangbot.strategy;

import yangbot.input.*;
import yangbot.path.Curve;
import yangbot.path.EpicPathPlanner;
import yangbot.strategy.manuever.DodgeManeuver;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.strategy.manuever.FollowPathManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
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
        final ImmutableBallData ball = gameData.getBallData();
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
            state = State.BALLCHASE; // We are in a defensive position
        else
            state = State.ROTATE; // We are in a bad position, lets go to our goal
    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {
        if (this.reevaluateStrategy((state == State.FOLLOW_PATH || state == State.FOLLOW_PATH_STRIKE) ? 1.2f : 0.2f))
            return; // Return if we are done

        GameData gameData = GameData.current();
        CarData car = gameData.getCarData();
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

                    Optional<Curve> curveOptional = new EpicPathPlanner()
                            .withStart(car.position.add(car.forward().mul(10)), car.forward())
                            .withEnd(targetPos.withZ(car.position.z), new Vector3(0, -teamSign, 0))
                            .withBallAvoidance(true, car, interceptFrame.absoluteTime, true)
                            .plan();

                    if (curveOptional.isPresent()) {
                        validPath = curveOptional.get();
                        arrivalTime = interceptFrame.absoluteTime;
                    }

                    t -= RLConstants.simulationTickFrequency * 4; // 15 ticks / s
                } while (t > 0);

                if (validPath == null) {
                    this.state = State.BALLCHASE;
                    this.reevaluateStrategy(0.1f);
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
                Optional<Curve> pathOpt = new EpicPathPlanner()
                        .withStart(car.position, car.forward())
                        .withEnd(new Vector3(0, RLConstants.goalDistance * 0.9f * teamSign, car.position.z), new Vector3(0, teamSign, 0))
                        .withBallAvoidance(true, car, -1, false)
                        .plan();

                assert pathOpt.isPresent() : "Path should be present when no ball avoidance is required";

                followPathManeuver.path = pathOpt.get();
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
                    // Arrival in 0.4s?
                    if (Math.max(1, car.position.distance(followPathManeuver.path.pointAt(0))) / Math.max(1, car.velocity.flatten().magnitude()) < 0.4f)
                        followPathManeuver.arrivalSpeed = DriveManeuver.max_throttle_speed - 10;
                    else
                        followPathManeuver.arrivalSpeed = -1;
                }

                //followPathManeuver.draw(renderer, car);
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
