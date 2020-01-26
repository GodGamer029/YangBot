package yangbot.strategy;

import rlbot.cppinterop.RLBotDll;
import rlbot.flat.QuickChatSelection;
import rlbot.gamestate.GameInfoState;
import rlbot.gamestate.GameState;
import yangbot.cpp.YangBotJNAInterop;
import yangbot.input.*;
import yangbot.manuever.DodgeManeuver;
import yangbot.manuever.FollowPathManeuver;
import yangbot.prediction.Curve;
import yangbot.prediction.YangBallPrediction;
import yangbot.util.AdvancedRenderer;
import yangbot.util.hitbox.YangCarHitbox;
import yangbot.util.math.Line2;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class OffensiveStrategy extends Strategy {

    private DodgeManeuver dodgeManeuver = new DodgeManeuver();
    private FollowPathManeuver followPathManeuver = new FollowPathManeuver();
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
        YangBallPrediction ballPrediction = gameData.getBallPrediction();

        RLBotDll.setGameState(new GameState().withGameInfoState(new GameInfoState().withGameSpeed(1f)).buildPacket());

        if (this.state == State.FOLLOW_PATH_STRIKE && this.followPathManeuver.arrivalTime > car.elapsedSeconds && this.followPathManeuver.arrivalTime - car.elapsedSeconds < 0.5f)
            return; // Don't replan when we are close to hitting the ball

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


        List<YangBallPrediction.YangPredictionFrame> strikeableFrames = ballPrediction.getFramesBetweenRelative(0.15f, 1.75f)
                .stream()
                .filter((frame) -> frame.ballData.position.z <= BallData.COLLISION_RADIUS + 80 || RLConstants.isPosNearWall(frame.ballData.position.flatten(), BallData.COLLISION_RADIUS * 1.5f))
                .collect(Collectors.toList());

        if (strikeableFrames.size() > 0) {
            Curve validPath = null;
            float arrivalTime = 0;

            float maxT = strikeableFrames.get(strikeableFrames.size() - 1).relativeTime - RLConstants.simulationTickFrequency * 2;
            float t = strikeableFrames.get(0).relativeTime;

            YangBallPrediction strikePrediction = YangBallPrediction.from(strikeableFrames, RLConstants.tickFrequency);

            final Vector2 enemyGoal = new Vector2(0, -teamSign * (RLConstants.goalDistance + 1000));
            final Line2 enemyGoalLine = new Line2(enemyGoal.sub(RLConstants.goalCenterToPost * 0.8f, 0), enemyGoal.add(RLConstants.goalCenterToPost * 0.8f, 0));
            final Vector3 startPosition = car.position.add(car.velocity.mul(RLConstants.tickFrequency * 2));
            final Vector3 startTangent = car.forward().mul(3).add(car.velocity.normalized()).normalized();

            float oldPathArrival = this.followPathManeuver.arrivalTime;
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
                    //System.out.println("Falling back to simple path");
                    List<Curve.ControlPoint> controlPoints = new ArrayList<>();

                    // Construct Path
                    {
                        controlPoints.add(new Curve.ControlPoint(startPosition, startTangent));
                        controlPoints.add(new Curve.ControlPoint(ballHitTarget, endTangent.mul(-1)));

                        currentPath = new Curve(controlPoints);
                    }
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

                        randomDodgeStart = (float) Math.random() * 0.25f + 0.25f;
                        break;
                    } else if (curveOptional.isEmpty() && currentPath.length < 700) {
                        //System.out.println("Short path was deemed impossible: leng=" + currentPath.length + " stat=" + pathStatus.pathStatus.name() + " speed=" + pathStatus.speedNeeded);
                    }
                }
            }

            if (validPath != null) {
                state = State.FOLLOW_PATH_STRIKE;
                dodgeManeuver = new DodgeManeuver();
                dodgeManeuver.delay = 0.4f;
                dodgeManeuver.duration = 0.2f;
                followPathManeuver.path = validPath;
                followPathManeuver.arrivalTime = arrivalTime;
                return;
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
                if (this.followPathManeuver.arrivalTime - car.elapsedSeconds < randomDodgeStart || this.followPathManeuver.isDone() || this.hasSolvedGoodHit) {
                    if (!this.hasSolvedGoodHit && this.dodgeManeuver.timer >= Math.min(0.20f, 0.05f + RLConstants.gameLatencyCompensation)) {

                        this.hasSolvedGoodHit = true;
                        Optional<YangBallPrediction.YangPredictionFrame> ballFrameAtArrivalOptional = ballPrediction.getFrameAtRelativeTime(0.4f - dodgeManeuver.timer + RLConstants.tickFrequency);
                        assert ballFrameAtArrivalOptional.isPresent();

                        YangBallPrediction.YangPredictionFrame ballFrameAtArrival = ballFrameAtArrivalOptional.get();
                        final Vector3 ballAtArrival = ballFrameAtArrival.ballData.position;
                        float T = (float) ((ballAtArrival.flatten().sub(car.position.flatten()).magnitude() - BallData.COLLISION_RADIUS) / car.velocity.flatten().magnitude());
                        if (T > 0.5f || T <= RLConstants.tickFrequency) {
                            dodgeManeuver.direction = null;
                            dodgeManeuver.target = null; // ballAtArrival
                            dodgeManeuver.duration = 0f;
                            dodgeManeuver.delay = 999;
                            dodgeManeuver.setDone();
                        } else {
                            dodgeManeuver.target = null;
                            Vector3 carAtArrival = car.position.add(car.velocity.mul(T - RLConstants.tickFrequency * 2));
                            Vector2 direction = ballAtArrival.flatten().sub(carAtArrival.flatten()).normalized();
                            dodgeManeuver.direction = direction;

                            final float simDt = RLConstants.tickFrequency;

                            float minDistanceToGoal = (float) ballAtArrival.flatten().distance(enemyGoal);
                            boolean didLandInGoal = false;
                            float timeToGoal = -1;

                            long ms = System.currentTimeMillis();
                            int tim = 0;

                            for (float duration = Math.max(0.05f, this.dodgeManeuver.timer); duration <= 0.2f; duration += 0.05f) { // 0.1f, 0.15f, 0.2f
                                for (float delay = duration + 0.05f; delay <= 0.6f; delay += 0.15f) {
                                    for (float angleDiff = (float) (Math.PI * -0.8f); angleDiff < (float) (Math.PI * 0.8f); angleDiff += Math.PI * 0.15f) {
                                        tim++;
                                        CarData simCar = new CarData(car);
                                        simCar.hasWheelContact = false;
                                        simCar.elapsedSeconds = 0;
                                        simCar.jumpTimer = this.dodgeManeuver.timer;
                                        simCar.enableJumpAcceleration = true;
                                        simCar.lastControllerInputs.withJump(true);

                                        BallData simBall = new BallData(ball);
                                        simBall.hasBeenTouched = false;
                                        DodgeManeuver simDodge = new DodgeManeuver(dodgeManeuver);
                                        simDodge.delay = delay;
                                        simDodge.duration = duration;
                                        simDodge.direction = direction.rotateBy(angleDiff);
                                        simDodge.timer = this.dodgeManeuver.timer;
                                        if (delay > 0.2f) {
                                            simDodge.enablePreorient = true;
                                            Vector3 dir = ballAtArrival.sub(carAtArrival).normalized().add(0, 0, 0.4f).normalized();
                                            simDodge.preorientOrientation = Matrix3x3.lookAt(dir, new Vector3(0, 0, 1));
                                        }

                                        FoolGameData foolGameData = GameData.current().fool();
                                        Vector3 simContact = null;

                                        // Simulate ball - car collision
                                        for (float time = 0; time < T + 0.1f; time += simDt) {
                                            ControlsOutput simControls = new ControlsOutput();

                                            foolGameData.foolCar(simCar);
                                            simDodge.fool(foolGameData);

                                            simDodge.step(simDt, simControls);

                                            simCar.step(simControls, simDt);

                                            simContact = simBall.collide(simCar, -3);

                                            if (simBall.hasBeenTouched)
                                                break;

                                            // + = hit earlier
                                            // - = hit later
                                            // The right parameter is different depending on how fast this algorithm performs, currently it takes about 17-21ms, a little more than 2 physics ticks
                                            Optional<YangBallPrediction.YangPredictionFrame> frameOptional = ballPrediction.getFrameAtRelativeTime(time + 1.2f * RLConstants.tickFrequency);
                                            if (frameOptional.isEmpty())
                                                break;
                                            simBall = frameOptional.get().ballData.makeMutable();
                                            assert simBall.velocity.magnitude() < BallData.MAX_VELOCITY * 1.5f : "Got faulty ball: " + simBall.toString();

                                            simBall.hasBeenTouched = false;
                                        }

                                        // Evaluate post-collision ball state
                                        if (simBall.hasBeenTouched) {
                                            if (!simCar.doubleJumped || simCar.dodgeTimer <= simDt * 3)
                                                continue;
                                            YangBallPrediction simBallPred = YangBotJNAInterop.getBallPrediction(simBall, 60);
                                            //YangBallPrediction simBallPred = simBall.makeBallPrediction(1f/120f, 3);
                                            boolean applyDodgeSettings = false;

                                            for (float time = 0; time < Math.min(3, simBallPred.relativeTimeOfLastFrame()); time += RLConstants.simulationTickFrequency * 2) {
                                                Optional<YangBallPrediction.YangPredictionFrame> dataAtFrame = simBallPred.getFrameAtRelativeTime(time);
                                                if (dataAtFrame.isEmpty())
                                                    break;
                                                ImmutableBallData ballAtFrame = dataAtFrame.get().ballData;
                                                float dist = (float) ballAtFrame.position.distance(enemyGoal.withZ(RLConstants.goalHeight / 2));
                                                boolean landsInGoal = ballAtFrame.makeMutable().isInGoal(teamSign);

                                                if (didLandInGoal) { // The ball has to at least land in the goal to be better than the last simulation
                                                    if (!landsInGoal)
                                                        continue;

                                                    if (time < timeToGoal) {
                                                        timeToGoal = time;
                                                        applyDodgeSettings = true;
                                                        break;
                                                    }
                                                } else {
                                                    if (landsInGoal) { // Lands in goal, but last one didn't? Definitely better than the last
                                                        didLandInGoal = true;
                                                        timeToGoal = time;
                                                        applyDodgeSettings = true;
                                                        break;
                                                    } else if (dist < minDistanceToGoal && time > 0.25f) { // Check if it's better than the last sim which also didn't score
                                                        minDistanceToGoal = dist;
                                                        applyDodgeSettings = true;
                                                    }
                                                }
                                            }
                                            if (applyDodgeSettings) {
                                                this.dodgeManeuver.delay = simDodge.delay;
                                                this.dodgeManeuver.target = null;
                                                this.dodgeManeuver.direction = simDodge.direction;
                                                this.dodgeManeuver.duration = simDodge.duration;
                                                this.dodgeManeuver.enablePreorient = simDodge.enablePreorient;
                                                this.dodgeManeuver.preorientOrientation = simDodge.preorientOrientation;
                                                this.dodgeManeuver.controllerInput = simDodge.controllerInput;

                                                this.hitPrediction = simBallPred;
                                                this.hitCar = simCar;
                                                this.hitBall = simBall;
                                                this.contactPoint = simContact;
                                                this.contactNormal = simBall.position.sub(simContact).normalized();
                                            }
                                        }
                                    }
                                }
                            }

                            if (this.hitPrediction != null) { // Found shot
                                //RLBotDll.setGameState(new GameState().withGameInfoState(new GameInfoState().withGameSpeed(0.1f)).buildPacket());

                                if (didLandInGoal)
                                    RLBotDll.sendQuickChat(car.playerIndex, false, QuickChatSelection.Reactions_Calculated);
                                else
                                    RLBotDll.sendQuickChat(car.playerIndex, false, QuickChatSelection.Apologies_Oops);

                            } else { // Couldn't hit the ball
                                RLBotDll.sendQuickChat(car.playerIndex, true, QuickChatSelection.Information_TakeTheShot);
                                dodgeManeuver.direction = null;
                                dodgeManeuver.duration = 0;
                                dodgeManeuver.delay = 9999;
                                dodgeManeuver.setDone();
                            }

                            System.out.println("Offensive shot planning took: " + (System.currentTimeMillis() - ms) + "ms with " + tim + " simulations");
                        }
                    }

                    dodgeManeuver.step(dt, controlsOutput);

                    if (ball.hasBeenTouched() && ball.getLatestTouch().playerIndex == car.playerIndex && car.elapsedSeconds - ball.getLatestTouch().gameSeconds - dodgeManeuver.timer < -0.1f)
                        dodgeManeuver.setDone();
                    if (dodgeManeuver.isDone())
                        this.reevaluateStrategy(0.3f);
                    return;
                } else if (car.isGrounded()) {
                    followPathManeuver.path.draw(renderer);
                    followPathManeuver.draw(renderer, car);
                    followPathManeuver.step(dt, controlsOutput);
                    float distanceOffPath = (float) car.position.flatten().distance(followPathManeuver.path.pointAt(followPathManeuver.path.findNearest(car.position)).flatten());
                    if (followPathManeuver.isDone()) {
                        this.reevaluateStrategy(0.3f);
                        System.out.println("Path manuver done");
                        return;
                    } else if (distanceOffPath > 100) {
                        if (this.reevaluateStrategy(0.25f))
                            return;
                    }
                } else
                    this.reevaluateStrategy(0.2f);
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
