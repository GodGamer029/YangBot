package yangbot.strategy;

import yangbot.input.*;
import yangbot.input.interrupt.BallTouchInterrupt;
import yangbot.input.interrupt.InterruptManager;
import yangbot.optimizers.graders.DefensiveGrader;
import yangbot.path.Curve;
import yangbot.path.EpicPathPlanner;
import yangbot.strategy.abstraction.StrikeAbstraction;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.strategy.manuever.FollowPathManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.Area2;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class DefendStrategy extends Strategy {

    private State state = State.BALLCHASE;
    private FollowPathManeuver followPathManeuver = new FollowPathManeuver();
    private static float xDefendDist = RLConstants.goalCenterToPost + 400;
    private static float yDefendDist = RLConstants.goalDistance * 0.4f;
    private StrikeAbstraction strikeAbstraction = null;
    private BallTouchInterrupt ballTouchInterrupt = null;

    private boolean planIntercept(YangBallPrediction ballPrediction) {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();

        Curve validPath = null;
        float arrivalTime = 0;
        Vector3 targetBallPos = null;

        float t = 0;

        // Path finder
        do {
            final Optional<YangBallPrediction.YangPredictionFrame> interceptFrameOptional = ballPrediction.getFrameAfterRelativeTime(t);
            if (interceptFrameOptional.isEmpty())
                break;

            final YangBallPrediction.YangPredictionFrame interceptFrame = interceptFrameOptional.get();
            final Vector3 targetPos = interceptFrame.ballData.position;

            var pathPlanner = new EpicPathPlanner()
                    .withStart(car, 10)
                    .withEnd(targetPos.withZ(car.position.z), new Vector3(0, -car.getTeamSign(), 0))
                    .withBallAvoidance(true, car, interceptFrame.absoluteTime, true)
                    .withCreationStrategy(EpicPathPlanner.PathCreationStrategy.SIMPLE);
            Optional<Curve> curveOptional = pathPlanner.plan();

            if (curveOptional.isPresent()) {
                boolean isValid = pathPlanner.isUsingBallAvoidance();
                if (!isValid) {
                    var curv = curveOptional.get();
                    var status = curv.doPathChecking(car, interceptFrame.absoluteTime, ballPrediction);
                    isValid = status.isValid();
                }

                if (isValid) {
                    validPath = curveOptional.get();
                    arrivalTime = interceptFrame.absoluteTime;
                    targetBallPos = targetPos;
                }

                break;
            }

            t = interceptFrame.relativeTime;
            t += RLConstants.simulationTickFrequency * 4; // 15 ticks / s
        } while (t < ballPrediction.relativeTimeOfLastFrame());

        if (validPath == null)
            return false;

        this.strikeAbstraction = new StrikeAbstraction(validPath, new DefensiveGrader());
        this.strikeAbstraction.arrivalTime = arrivalTime;
        this.strikeAbstraction.maxJumpDelay = 0.6f;
        this.strikeAbstraction.jumpDelayStep = 0.15f;
        float zDiff = targetBallPos.z - car.position.z;
        if (zDiff < 50)
            this.strikeAbstraction.jumpBeforeStrikeDelay = 0.3f;
        else
            this.strikeAbstraction.jumpBeforeStrikeDelay = MathUtils.clip(CarData.getJumpTimeForHeight(zDiff, gameData.getGravity().z), 0.3f, 0.6f);

        this.state = State.FOLLOW_PATH_STRIKE;
        return true;
    }

    @Override
    protected void planStrategyInternal() {
        //System.out.println("Planning strategy: "+this.state);
        this.ballTouchInterrupt = InterruptManager.get().getBallTouchInterrupt(-1);
        // How critical is the situation?
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final ImmutableBallData ball = gameData.getBallData();
        final YangBallPrediction ballPrediction = gameData.getBallPrediction();

        if (this.checkReset(1f)) {
            //System.out.println("Quitting because check reset");
            return;
        }

        if (!car.hasWheelContact || car.position.z > 100) {
            this.setDone();
            //System.out.println("Quitting because wheel contact");
            return;
        }

        int teamSign = car.team * 2 - 1;

        // Own goaling
        {
            Optional<YangBallPrediction.YangPredictionFrame> firstConcedingGoalFrame = ballPrediction.getFramesBeforeRelative(4f)
                    .stream()
                    .filter((f) -> (int) Math.signum(f.ballData.position.y) == teamSign && f.ballData.makeMutable().isInOwnGoal(teamSign))
                    .findFirst();

            if (firstConcedingGoalFrame.isPresent()) { // We getting scored on
                final YangBallPrediction.YangPredictionFrame frameConceding = firstConcedingGoalFrame.get();

                YangBallPrediction framesBeforeGoal = ballPrediction.getBeforeRelative(frameConceding.relativeTime);
                if (framesBeforeGoal.frames.size() == 0) {
                    return;
                }

                framesBeforeGoal = YangBallPrediction.from(framesBeforeGoal.frames.stream()
                        .filter((frame) -> frame.ballData.position.z < 250)
                        .collect(Collectors.toList()), framesBeforeGoal.tickFrequency);

                if (!this.planIntercept(framesBeforeGoal))
                    this.state = State.BALLCHASE;

                return;
            }
        }

        // Defend area
        {
            var rend = new AdvancedRenderer(50);
            rend.startPacket();

            final Area2 defendArea = new Area2(List.of(
                    new Vector2(-xDefendDist, RLConstants.goalDistance * teamSign),
                    new Vector2(xDefendDist, RLConstants.goalDistance * teamSign),
                    new Vector2(xDefendDist * 1.7f, (RLConstants.goalDistance - yDefendDist) * teamSign),
                    new Vector2(-xDefendDist * 1.7f, (RLConstants.goalDistance - yDefendDist) * teamSign)
            ));

            Optional<YangBallPrediction.YangPredictionFrame> ballInDefendAreaFrame = ballPrediction.getFramesBeforeRelative(3f)
                    .stream()
                    .filter((f) -> f.ballData.position.z < 1000 && defendArea.contains(f.ballData.position.flatten()))
                    .findFirst();

            if (ballInDefendAreaFrame.isPresent()) { // We getting scored on
                final YangBallPrediction.YangPredictionFrame frameAreaEnter = ballInDefendAreaFrame.get();

                //rend.drawCentered3dCube(Color.GREEN, ball.position, BallData.RADIUS + 15);
                //rend.drawCentered3dCube(Color.YELLOW, frameAreaEnter.ballData.position, BallData.RADIUS + 5);

                YangBallPrediction framesBeforeAreaEnter = ballPrediction.getBeforeRelative(frameAreaEnter.relativeTime + 0.5f);
                if (framesBeforeAreaEnter.frames.size() == 0) {
                    rend.finishAndSendIfDifferent();
                    return;
                }

                framesBeforeAreaEnter = YangBallPrediction.from(framesBeforeAreaEnter.frames.stream()
                        .filter((frame) -> frame.ballData.position.z < 250)
                        .collect(Collectors.toList()), framesBeforeAreaEnter.tickFrequency);

                rend.finishAndSendIfDifferent();
                if (this.planIntercept(framesBeforeAreaEnter))
                    return;
            }
        }

        if (car.position.y * teamSign > RLConstants.goalDistance * 0.7f || car.position.distance(ball.position) < 400)
            state = State.BALLCHASE; // We are in a defensive position
        else
            state = State.ROTATE; // We are in a bad position, lets go to our goal
    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {
        if (this.state == State.FOLLOW_PATH_STRIKE) {
            if (this.reevaluateStrategy(this.strikeAbstraction.canInterrupt() ? 0.7f : 1.6f))
                return;
        } else {
            if (this.reevaluateStrategy((state == State.FOLLOW_PATH) ? 1.2f : 0.3f))
                return; // Return if we are done
        }

        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final AdvancedRenderer renderer = gameData.getAdvancedRenderer();
        final YangBallPrediction ballPrediction = gameData.getBallPrediction();

        final int teamSign = car.getTeamSign();

        final Area2 defendArea = new Area2(List.of(
                new Vector2(-xDefendDist, RLConstants.goalDistance * teamSign),
                new Vector2(xDefendDist, RLConstants.goalDistance * teamSign),
                new Vector2(xDefendDist * 1.7f, (RLConstants.goalDistance - yDefendDist) * teamSign),
                new Vector2(-xDefendDist * 1.7f, (RLConstants.goalDistance - yDefendDist) * teamSign)
        ));
        defendArea.draw(renderer, 50);

        switch (state) {
            case FOLLOW_PATH: {
                this.followPathManeuver.path.draw(renderer);
                this.followPathManeuver.step(dt, controlsOutput);

                if (this.reevaluateStrategy(this.ballTouchInterrupt))
                    return;

                if (this.followPathManeuver.isDone()) {
                    this.reevaluateStrategy(0.05f);
                    return;
                }

                final float distanceOffPath = (float) car.position.flatten().distance(this.followPathManeuver.path.pointAt(this.followPathManeuver.path.findNearest(car.position)).flatten());

                if (distanceOffPath > 100 && this.reevaluateStrategy(0.25f))
                    return;

                final Vector2 ownGoal = new Vector2(0, teamSign * RLConstants.goalDistance);
                final float distanceCarToDefend = (float) car.position.add(car.velocity.mul(0.3f)).flatten().distance(ownGoal);
                final float distanceBallToDefend = (float) ballPrediction.getFrameAtRelativeTime(0.5f).get().ballData.position.flatten().distance(ownGoal);

                if (distanceCarToDefend + 300 > distanceBallToDefend && (this.followPathManeuver.arrivalTime < 0 || this.followPathManeuver.arrivalTime - car.elapsedSeconds > 0.4f)) {
                    this.followPathManeuver.arrivalSpeed = 2200;
                } else {
                    // Arrival in 0.4s?
                    if (Math.max(1, car.position.distance(this.followPathManeuver.path.pointAt(0))) / Math.max(1, car.velocity.flatten().magnitude()) < 0.4f)
                        this.followPathManeuver.arrivalSpeed = DriveManeuver.max_throttle_speed - 10;
                    else
                        this.followPathManeuver.arrivalSpeed = -1;
                }
                break;
            }
            case FOLLOW_PATH_STRIKE:
                this.strikeAbstraction.step(dt, controlsOutput);
                if (this.strikeAbstraction.isDone()) {
                    this.reevaluateStrategy(0f);
                }

                break;
            case ROTATE: {
                Optional<Curve> pathOpt = new EpicPathPlanner()
                        .withStart(car)
                        .withEnd(new Vector3(0, RLConstants.goalDistance * 0.9f * teamSign, car.position.z), new Vector3(0, teamSign, 0))
                        .withBallAvoidance(true, car, -1, false)
                        .plan();

                assert pathOpt.isPresent() : "Path should be present when no ball avoidance is required";

                this.followPathManeuver.path = pathOpt.get();
                this.followPathManeuver.arrivalTime = -1;
                this.state = State.FOLLOW_PATH;
            }
            case BALLCHASE: {
                if (this.reevaluateStrategy(this.ballTouchInterrupt))
                    return;
                DefaultStrategy.smartBallChaser(dt, controlsOutput);
                break;
            }
            default:
                assert false;
                break;
        }
    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.empty();
    }

    @Override
    public String getAdditionalInformation() {
        return "State: " + this.state;
    }

    enum State {
        ROTATE,
        FOLLOW_PATH,
        FOLLOW_PATH_STRIKE,
        BALLCHASE
    }
}
