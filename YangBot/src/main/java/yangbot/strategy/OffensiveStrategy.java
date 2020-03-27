package yangbot.strategy;

import yangbot.abstraction.StrikeAbstraction;
import yangbot.cpp.YangBotJNAInterop;
import yangbot.input.*;
import yangbot.prediction.Curve;
import yangbot.prediction.EpicPathPlanner;
import yangbot.prediction.YangBallPrediction;
import yangbot.util.AdvancedRenderer;
import yangbot.util.hitbox.YangCarHitbox;
import yangbot.util.math.Line2;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class OffensiveStrategy extends Strategy {

    private StrikeAbstraction strikeAbstraction = new StrikeAbstraction(null);
    private State state = State.IDK;
    private boolean hasSolvedGoodHit = false;
    private YangBallPrediction hitPrediction = null;
    private CarData hitCar = null;
    private Vector3 contactPoint = null;
    private Vector3 contactNormal = null;
    private BallData hitBall = null;
    private YangCarHitbox hitboxAtBallHit;
    private Vector3 positionAtBallHit;
    private float lastBallHit = 0;
    private float randomDodgeStart = 0.3f;
    private Strategy suggestedStrat = null;

    @Override
    protected void planStrategyInternal() {
        GameData gameData = GameData.current();
        CarData car = gameData.getCarData();
        ImmutableBallData ball = gameData.getBallData();
        YangBallPrediction ballPrediction = gameData.getBallPrediction();

        //RLBotDll.setGameState(new GameState().withGameInfoState(new GameInfoState().withGameSpeed(1f)).buildPacket());

        // Don't replan when we are close to hitting the ball
        /*if (this.state == State.FOLLOW_PATH_STRIKE && this.followPathManeuver.arrivalTime > car.elapsedSeconds && this.followPathManeuver.arrivalTime - car.elapsedSeconds < 0.5f)
            return;*/

        if (this.checkReset(1.5f))
            return;

        this.hasSolvedGoodHit = false;
        this.state = State.IDK;

        final int teamSign = car.team * 2 - 1;

        if (!car.hasWheelContact) {
            this.setDone();
            return;
        }

        if (DribbleStrategy.isViable()) {
            suggestedStrat = new DribbleStrategy();
        }

        // Make sure we dont hit the ball back to our goal
        if (Math.signum(ball.position.y - car.position.y) == -teamSign) {
            List<YangBallPrediction.YangPredictionFrame> strikeableFrames = ballPrediction.getFramesBetweenRelative(0.15f, 1.75f)
                    .stream()
                    .filter((frame) -> Math.signum(frame.ballData.position.y - (car.position.y + car.velocity.y * frame.relativeTime * 0.6f)) == -teamSign) // Ball is closer to enemy goal than to own
                    .filter((frame) -> (frame.ballData.position.z <= BallData.COLLISION_RADIUS + 80 || RLConstants.isPosNearWall(frame.ballData.position.flatten(), BallData.COLLISION_RADIUS * 1.5f)) && !frame.ballData.makeMutable().isInAnyGoal())
                    .collect(Collectors.toList());

            if (strikeableFrames.size() > 0) {
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

                if (validPath != null) {
                    state = State.FOLLOW_PATH_STRIKE;
                    this.strikeAbstraction = new StrikeAbstraction(validPath);
                    this.strikeAbstraction.arrivalTime = arrivalTime;
                    return;
                }
            }
        }


    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {
        if (this.reevaluateStrategy(this.hasSolvedGoodHit ? 1.2f : 0.7f))
            return;
        GameData gameData = GameData.current();
        CarData car = gameData.getCarData();
        final ImmutableBallData ball = gameData.getBallData();
        AdvancedRenderer renderer = gameData.getAdvancedRenderer();
        YangBallPrediction ballPrediction = gameData.getBallPrediction();

        final int teamSign = car.team * 2 - 1;
        final Vector2 enemyGoal = new Vector2(0, -teamSign * (RLConstants.goalDistance + 200));

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
            case IDK:
                DefaultStrategy.smartBallChaser(dt, controlsOutput);
                break;
            case FOLLOW_PATH_STRIKE:
                this.strikeAbstraction.step(dt, controlsOutput);
                if (this.strikeAbstraction.isDone())
                    this.reevaluateStrategy(0);

                break;
        }
    }

    enum State {
        IDK,
        FOLLOW_PATH_STRIKE
    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.ofNullable(suggestedStrat);
    }
}
