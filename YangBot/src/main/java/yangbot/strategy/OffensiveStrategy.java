package yangbot.strategy;

import yangbot.cpp.YangBotJNAInterop;
import yangbot.input.*;
import yangbot.optimizers.graders.OffensiveGrader;
import yangbot.path.Curve;
import yangbot.path.EpicPathPlanner;
import yangbot.strategy.abstraction.StrikeAbstraction;
import yangbot.strategy.advisor.RotationAdvisor;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.strategy.manuever.FollowPathManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.YangBallPrediction;
import yangbot.util.hitbox.YangCarHitbox;
import yangbot.util.math.Line2;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class OffensiveStrategy extends Strategy {

    private StrikeAbstraction strikeAbstraction = new StrikeAbstraction(new OffensiveGrader());
    private FollowPathManeuver followPathManeuver = new FollowPathManeuver();
    private State state = State.INVALID;
    private YangBallPrediction hitPrediction = null;
    private CarData hitCar = null;
    private Vector3 contactPoint = null;
    private Vector3 contactNormal = null;
    private BallData hitBall = null;
    private YangCarHitbox hitboxAtBallHit;
    private Vector3 positionAtBallHit;
    private float lastBallHit = 0;
    private Strategy suggestedStrat = null;
    private int idleDirection = 1;
    private RotationAdvisor.Advice lastAdvice = RotationAdvisor.Advice.NO_ADVICE;

    private void planStrategyAttack() {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final int teamSign = car.getTeamSign();
        final ImmutableBallData ball = gameData.getBallData();
        final YangBallPrediction ballPrediction = gameData.getBallPrediction();

        /*if (DribbleStrategy.isViable()) {
            suggestedStrat = new DribbleStrategy();
            this.setDone();
            return;
        }*/

        // Make sure we don't hit the ball back to our goal
        assert Math.signum(ball.position.y - car.position.y) == -teamSign : "We should be in front of the ball, not ahead car: " + car.position + " ball: " + ball.position;

        final boolean allowWallHits = false;

        List<YangBallPrediction.YangPredictionFrame> strikeableFrames = ballPrediction.getFramesBetweenRelative(0.15f, 1.75f)
                .stream()
                .filter((frame) -> Math.signum(frame.ballData.position.y - (car.position.y + car.velocity.y * frame.relativeTime * 0.6f)) == -teamSign) // Ball is closer to enemy goal than to own
                .filter((frame) -> (frame.ballData.position.z <= BallData.COLLISION_RADIUS + 80 /*|| (allowWallHits && RLConstants.isPosNearWall(frame.ballData.position.flatten(), BallData.COLLISION_RADIUS * 1.5f))*/)
                        && !frame.ballData.makeMutable().isInAnyGoal())
                .collect(Collectors.toList());

        if (strikeableFrames.size() == 0) {
            this.state = State.BALLCHASE;
            return;
        }


        Curve validPath = null;
        float arrivalTime = 0;

        float maxT = strikeableFrames.get(strikeableFrames.size() - 1).relativeTime - RLConstants.simulationTickFrequency * 2;
        float t = strikeableFrames.get(0).relativeTime;

        YangBallPrediction strikePrediction = YangBallPrediction.from(strikeableFrames, RLConstants.tickFrequency);

        final Vector2 enemyGoal = new Vector2(0, -teamSign * (RLConstants.goalDistance + 1000));
        final float goalCenterToPostDistance = RLConstants.goalCenterToPost - BallData.COLLISION_RADIUS * 2;
        assert goalCenterToPostDistance > 100; // Could fail with smaller goals
        assert enemyGoal.x == 0; // Could fail with custom goals
        final Line2 enemyGoalLine = new Line2(enemyGoal.sub(goalCenterToPostDistance, 0), enemyGoal.add(goalCenterToPostDistance, 0));
        final Vector3 startPosition = car.position.add(car.velocity.mul(RLConstants.tickFrequency * 2));
        final Vector3 startTangent = car.forward().mul(3).add(car.velocity.normalized()).normalized();

        float oldPathArrival = this.strikeAbstraction.arrivalTime;
        if (oldPathArrival < car.elapsedSeconds || oldPathArrival > strikeableFrames.get(strikeableFrames.size() - 1).absoluteTime)
            oldPathArrival = -1;
        else
            oldPathArrival -= car.elapsedSeconds;

        // Path finder
        while (t < maxT) {
            final Optional<YangBallPrediction.YangPredictionFrame> interceptFrameOptional = strikePrediction.getFrameAtRelativeTime(t);
            if (interceptFrameOptional.isEmpty())
                break;

            if (Math.abs(oldPathArrival - t) < 0.1f) // Be extra precise, we are trying to rediscover our last path
                t += RLConstants.simulationTickFrequency * 1;
            else if (t > 1.5f) // Speed it up, not as important
                t += RLConstants.simulationTickFrequency * 4;
            else // default
                t += RLConstants.simulationTickFrequency * 2;

            final var interceptFrame = interceptFrameOptional.get();

            if (interceptFrame.ballData.velocity.magnitude() > 4000)
                continue;

            final Vector3 targetBallPos = interceptFrame.ballData.position;

            final Vector2 closestScoringPosition = enemyGoalLine.closestPointOnLine(targetBallPos.flatten());
            final Vector3 ballTargetToGoalTarget = closestScoringPosition.sub(targetBallPos.flatten()).normalized().withZ(0);

            Vector3 ballHitTarget = targetBallPos.sub(ballTargetToGoalTarget.mul(BallData.COLLISION_RADIUS + car.hitbox.permutatePoint(new Vector3(), 1, 0, 0).magnitude()));
            if (!RLConstants.isPosNearWall(ballHitTarget.flatten(), 10))
                ballHitTarget = ballHitTarget.withZ(RLConstants.carElevation);
            final Vector3 carToDriveTarget = ballHitTarget.sub(startPosition).normalized();
            final Vector3 endTangent = carToDriveTarget.mul(4).add(ballTargetToGoalTarget).withZ(0).normalized();

            Curve currentPath;
            Optional<Curve> curveOptional = Optional.empty();
            if ((RLConstants.isPosNearWall(startPosition.flatten(), 100) || RLConstants.isPosNearWall(ballHitTarget.flatten(), 50)) && startPosition.distance(ballHitTarget) > 400)
                curveOptional = YangBotJNAInterop.findPath(startPosition, startTangent, ballHitTarget, endTangent, 25);

            if (curveOptional.isPresent())
                currentPath = curveOptional.get();
            else {
                currentPath = new EpicPathPlanner()
                        .withStart(startPosition, startTangent)
                        .withEnd(ballHitTarget, endTangent.mul(-1))
                        .plan().get();
            }

            if (currentPath.length == 0 || Float.isNaN(currentPath.length) || currentPath.points.size() == 0)
                continue;

            // Check if path is valid
            {
                currentPath.calculateMaxSpeeds(CarData.MAX_VELOCITY, CarData.MAX_VELOCITY);

                Curve.PathCheckStatus pathStatus = currentPath.doPathChecking(car, interceptFrame.absoluteTime, ballPrediction);

                if (pathStatus.isValid()) {
                    validPath = currentPath;
                    arrivalTime = interceptFrame.absoluteTime;
                    break;
                } else if (curveOptional.isEmpty() && currentPath.length < 700) {
                    //System.out.println("Short path was deemed impossible: leng=" + currentPath.length + " stat=" + pathStatus.pathStatus.name() + " speed=" + pathStatus.speedNeeded);
                }
            }
        }

        if (validPath == null) {
            this.state = State.BALLCHASE;
            return;
        }

        this.state = State.FOLLOW_PATH_STRIKE;
        this.strikeAbstraction = new StrikeAbstraction(validPath);
        this.strikeAbstraction.arrivalTime = arrivalTime;
    }

    private void planStrategyRetreat() {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final int teamSign = car.getTeamSign();
        final ImmutableBallData ball = gameData.getBallData();
        final YangBallPrediction ballPrediction = gameData.getBallPrediction();

        // Go back to where the ball is headed,
        Vector3 endPos = ball.position.add(ball.velocity.mul(0.6f)).withZ(RLConstants.carElevation).add(0, teamSign * 1000, 0);
        endPos = endPos.withX(endPos.x * 0.7f); // slightly centered
        Vector3 endTangent = new Vector3(0, teamSign, 0)
                .add(car.forward())
                .add(ball.velocity.normalized())
                .normalized();

        Optional<Curve> optionalCurve = new EpicPathPlanner()
                .withStart(car.position, car.forward())
                .withEnd(endPos, endTangent)
                .withBallAvoidance(true, car, -1, false)
                //.withCreationStrategy(EpicPathPlanner.PathCreationStrategy.NAVMESH)
                .plan();

        if (optionalCurve.isPresent()) {
            this.state = State.ROTATE;
            this.followPathManeuver = new FollowPathManeuver();
            this.followPathManeuver.arrivalTime = car.elapsedSeconds + Math.min(1.5f, Math.max(0.3f, optionalCurve.get().length / (DriveManeuver.max_throttle_speed - 50)));
            this.followPathManeuver.path = optionalCurve.get();
        } else
            this.state = State.GO_BACK_SOMEHOW; // Possible if navmesh hasn't loaded yet
    }

    @Override
    protected void planStrategyInternal() {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final int teamSign = car.getTeamSign();
        final ImmutableBallData ball = gameData.getBallData();
        final YangBallPrediction ballPrediction = gameData.getBallPrediction();
        this.state = State.INVALID;

        if (this.checkReset(1.5f))
            return;

        if (!car.hasWheelContact) {
            this.setDone();
            return;
        }

        final RotationAdvisor.Advice rotationAdvice = RotationAdvisor.whatDoIDo(gameData);
        this.lastAdvice = rotationAdvice;
        switch (rotationAdvice) {
            case PREPARE_FOR_PASS:
            case IDLE:
                this.state = State.IDLE;
                return;
            case ATTACK:
                this.planStrategyAttack();
                return;
            case RETREAT: // Ignore
                break;
            default:
                System.out.println("Unhandled Advice: " + rotationAdvice);
                this.state = State.IDLE;
        }

        // Ball is closer to own goal / out of position
        if (rotationAdvice == RotationAdvisor.Advice.RETREAT ||
                Math.signum((ball.position.y + ball.velocity.y * 0.4f) - (car.position.y + car.velocity.y * 0.2f) + teamSign * 100) == teamSign) {

            this.planStrategyRetreat();
            return;
        }
    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {
        if (this.reevaluateStrategy(this.strikeAbstraction.strikeSolved ? 1.2f : 0.7f))
            return;
        assert this.state != State.INVALID : "Invalid state! Last advice: " + this.lastAdvice;

        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final ImmutableBallData ball = gameData.getBallData();
        final AdvancedRenderer renderer = gameData.getAdvancedRenderer();
        final YangBallPrediction ballPrediction = gameData.getBallPrediction();

        final int teamSign = car.team * 2 - 1;
        final Vector2 ownGoal = new Vector2(0, teamSign * (RLConstants.goalDistance + 100));

        if (this.hitPrediction != null) {
            this.hitPrediction.draw(renderer, Color.MAGENTA, 0);
            this.hitCar.hitbox.draw(renderer, this.hitCar.position, 1, Color.GREEN);
            renderer.drawCentered3dCube(Color.GREEN, this.contactPoint, 10);
            renderer.drawLine3d(Color.MAGENTA, this.contactPoint, this.contactPoint.add(this.contactNormal.mul(100)));

            if (ball.hasBeenTouched()) {
                BallTouch latestTouch = ball.getLatestTouch();
                if (latestTouch.gameSeconds > this.lastBallHit) {
                    this.lastBallHit = latestTouch.gameSeconds;
                    this.hitboxAtBallHit = car.hitbox;
                    this.positionAtBallHit = car.position;
                }
                if (this.lastBallHit > 0) {
                    this.hitboxAtBallHit.draw(renderer, this.positionAtBallHit, 1, Color.BLUE);

                }
                renderer.drawCentered3dCube(Color.BLUE, latestTouch.position, 10);
                renderer.drawLine3d(Color.RED, latestTouch.position, latestTouch.position.add(latestTouch.normal.mul(100)));
            }

            renderer.drawCentered3dCube(Color.BLUE, this.hitBall.position, BallData.RADIUS * 2);
            renderer.drawCentered3dCube(Color.CYAN, ball.position, BallData.RADIUS * 2);
            car.hitbox.draw(renderer, car.position, 1, Color.BLUE);
        }

        switch (this.state) {
            case GO_BACK_SOMEHOW: {
                DriveManeuver.steerController(controlsOutput, car, ownGoal.withZ(0));
                break;
            }
            case BALLCHASE: {
                DefaultStrategy.smartBallChaser(dt, controlsOutput);
                break;
            }
            case IDLE: {
                // Go back to ball if we are completely out of the play (demolished probably)
                if (car.position.flatten().distance(ball.position.flatten()) > RLConstants.arenaLength * 0.8f) {
                    DefaultStrategy.smartBallChaser(dt, controlsOutput);
                    break;
                }

                // Move left and right on the field, always keeping a bit of distance to the ball
                if (Math.abs(car.position.x) > RLConstants.arenaHalfWidth * 0.2f && Math.signum(car.position.x) == this.idleDirection)
                    this.idleDirection *= -1;

                // Hover around the middle area
                Vector3 idleTarget = new Vector3(this.idleDirection * RLConstants.arenaHalfWidth, 0, 0);

                Vector2 futureBallPos = ball.position.flatten().add(ball.velocity.flatten().mul(0.5f));
                var frameOpt = ballPrediction.getFrameAtRelativeTime(0.5f);
                if (frameOpt.isPresent())
                    futureBallPos = frameOpt.get().ballData.position.flatten();

                idleTarget = idleTarget.withY(MathUtils.lerp(futureBallPos.y, ownGoal.y, 0.3f));

                DriveManeuver.steerController(controlsOutput, car, idleTarget);
                DriveManeuver.speedController(dt, controlsOutput, (float) car.forward().dot(car.velocity), DriveManeuver.max_throttle_speed - 10f, CarData.MAX_VELOCITY, 0.5f);

                break;
            }
            case ROTATE: {
                if (car.position.z > 300) {
                    DefaultStrategy.smartBallChaser(dt, controlsOutput);
                } else {
                    this.followPathManeuver.step(dt, controlsOutput);
                    //this.followPathManeuver.draw(renderer, car);
                    this.followPathManeuver.path.draw(renderer);
                    if (this.followPathManeuver.isDone())
                        this.reevaluateStrategy(0);
                }

                break;
            }
            case FOLLOW_PATH_STRIKE: {
                this.strikeAbstraction.step(dt, controlsOutput);
                if (this.strikeAbstraction.isDone())
                    this.reevaluateStrategy(0);

                break;
            }
        }
    }

    @Override
    public String getAdditionalInformation() {
        return "State: " + this.state;
    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.ofNullable(suggestedStrat);
    }

    enum State {
        IDLE,
        FOLLOW_PATH_STRIKE,
        ROTATE,
        GO_BACK_SOMEHOW,
        BALLCHASE,
        INVALID
    }
}
