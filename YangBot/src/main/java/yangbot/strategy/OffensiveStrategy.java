package yangbot.strategy;

import yangbot.cpp.YangBotCppInterop;
import yangbot.cpp.YangBotJNAInterop;
import yangbot.input.*;
import yangbot.manuever.DodgeManeuver;
import yangbot.manuever.FollowPathManeuver;
import yangbot.prediction.Curve;
import yangbot.prediction.YangBallPrediction;
import yangbot.util.AdvancedRenderer;
import yangbot.util.hitbox.YangCarHitbox;
import yangbot.vector.Vector2;
import yangbot.vector.Vector3;

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

    @Override
    protected void planStrategyInternal() {
        GameData gameData = GameData.current();
        CarData car = gameData.getCarData();
        YangBallPrediction ballPrediction = gameData.getBallPrediction();
        //RLBotDll.setGameState(new GameState().withGameInfoState(new GameInfoState().withGameSpeed(1f)).buildPacket());

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

        boolean isOutOfPosition = false;

        if (isOutOfPosition) {

            return;
        }

        List<YangBallPrediction.YangPredictionFrame> strikeableFrames = ballPrediction.getFramesBetweenRelative(0.1f, 1.75f)
                .stream()
                .filter((frame) -> frame.ballData.position.z <= BallData.COLLISION_RADIUS / 2 + 200 || RLConstants.isPosNearWall(frame.ballData.position.flatten(), BallData.COLLISION_RADIUS * 1.5f))
                .collect(Collectors.toList());

        if (strikeableFrames.size() > 0) {
            Curve validPath = null;
            float arrivalTime = 0;

            float maxT = strikeableFrames.get(strikeableFrames.size() - 1).relativeTime - RLConstants.simulationTickFrequency * 2;
            float t = strikeableFrames.get(0).relativeTime;

            YangBallPrediction strikePrediction = YangBallPrediction.from(strikeableFrames, RLConstants.tickFrequency);

            // Path finder
            while (t < maxT) {
                final Optional<YangBallPrediction.YangPredictionFrame> interceptFrameOptional = strikePrediction.getFrameAtRelativeTime(t);
                if (!interceptFrameOptional.isPresent())
                    break;

                t += RLConstants.simulationTickFrequency * 2;
                if (t > 2) // Speed it up, not as important
                    t += RLConstants.simulationTickFrequency * 2;

                final YangBallPrediction.YangPredictionFrame interceptFrame = interceptFrameOptional.get();
                final Vector3 targetPos = interceptFrame.ballData.position;
                final Vector2 enemyGoal = new Vector2(0, -teamSign * (RLConstants.goalDistance + 1000));
                Vector3 targetToGoal = enemyGoal.sub(targetPos.flatten()).withZ(0).normalized();

                targetToGoal = targetToGoal.add(targetPos.sub(car.position).normalized().mul(3)).div(4);
                final Vector3 groundedTargetPos = targetPos.sub(targetToGoal.mul(BallData.COLLISION_RADIUS));
                final Vector3 startPosition = car.position.add(car.velocity.mul(RLConstants.tickFrequency * 2)).sub(car.forward().mul(80));

                Curve currentPath;

                Optional<Curve> curveOptional = YangBotJNAInterop.findPath(startPosition, car.forward().mul(3).add(car.velocity.normalized()).normalized(), groundedTargetPos, targetToGoal, 100);
                if (curveOptional.isPresent())
                    currentPath = curveOptional.get();
                else {
                    System.out.println("Falling back to simple path");
                    List<Curve.ControlPoint> controlPoints = new ArrayList<>();
                    // Construct Path
                    {
                        controlPoints.add(new Curve.ControlPoint(car.position.add(car.forward().mul(10)), car.forward()));
                        controlPoints.add(new Curve.ControlPoint(groundedTargetPos, targetToGoal.mul(-1)));

                        currentPath = new Curve(controlPoints);
                    }
                }

                if (currentPath.length == 0 || currentPath.points.size() == 0)
                    continue;

                // Check if path is valid
                {
                    currentPath.calculateMaxSpeeds(CarData.MAX_VELOCITY, CarData.MAX_VELOCITY);
                    Curve.PathCheckStatus pathStatus = currentPath.doPathChecking(car, interceptFrame.absoluteTime, ballPrediction);
                    if (pathStatus.isValid()) {
                        validPath = currentPath;
                        arrivalTime = interceptFrame.absoluteTime;
                        break;
                    }
                }
            }

            if (validPath != null) {
                state = State.FOLLOW_PATH_STRIKE;
                dodgeManeuver = new DodgeManeuver();
                dodgeManeuver.delay = 0.3f;
                dodgeManeuver.duration = 0.1f;
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
            this.hitPrediction.draw(renderer, Color.MAGENTA, 2);
            this.hitCar.hitbox.draw(renderer, this.hitCar.position, 1, Color.GREEN);
            renderer.drawCentered3dCube(Color.GREEN, this.contactPoint, 10);
            renderer.drawLine3d(Color.MAGENTA, this.contactPoint, this.contactPoint.add(this.contactNormal.mul(100)));

            if (ball.hasBeenTouched) {
                if (ball.latestTouch.gameSeconds > this.lastBallHit) {
                    this.lastBallHit = ball.latestTouch.gameSeconds;
                    this.hitboxAtBallHit = car.hitbox;
                    this.positionAtBallHit = car.position;
                }
                if (this.lastBallHit > 0) {
                    this.hitboxAtBallHit.draw(renderer, this.positionAtBallHit, 1, Color.BLUE);

                }
                renderer.drawCentered3dCube(Color.BLUE, ball.latestTouch.position, 10);
                renderer.drawLine3d(Color.RED, ball.latestTouch.position, ball.latestTouch.position.add(ball.latestTouch.normal.mul(100)));
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
                followPathManeuver.path.draw(renderer);
                followPathManeuver.draw(renderer, car);

                if (followPathManeuver.arrivalTime - car.elapsedSeconds < dodgeManeuver.delay - RLConstants.tickFrequency || this.hasSolvedGoodHit) {
                    if (!this.hasSolvedGoodHit && !car.hasWheelContact && this.dodgeManeuver.timer >= 0.05f) {
                        this.hasSolvedGoodHit = true;
                        YangBallPrediction.YangPredictionFrame ballFrameAtArrival = ballPrediction.getFrameAtRelativeTime(dodgeManeuver.delay - dodgeManeuver.timer + RLConstants.tickFrequency).get();
                        final Vector3 ballAtArrival = ballFrameAtArrival.ballData.position;
                        float T = (float) ((ballAtArrival.flatten().sub(car.position.flatten()).magnitude() - BallData.COLLISION_RADIUS) / car.velocity.flatten().magnitude());
                        if (T > 0.5f || T <= RLConstants.tickFrequency) {
                            dodgeManeuver.direction = null;
                            dodgeManeuver.target = ballAtArrival;
                        } else {

                            dodgeManeuver.target = null;
                            Vector3 carAtArrival = car.position.add(car.velocity.mul(T - RLConstants.tickFrequency * 2));
                            Vector2 direction = ballAtArrival.flatten().sub(carAtArrival.flatten());
                            dodgeManeuver.direction = direction;

                            final float simDt = RLConstants.tickFrequency;

                            float minDistanceToGoal = (float) ballAtArrival.flatten().distance(enemyGoal);
                            boolean didLandInGoal = false;
                            float timeToGoal = -1;

                            long ns = System.nanoTime();
                            long ms = System.currentTimeMillis();
                            Vector3 ballVelBeforeColl = new Vector3();
                            Vector3 ballAngBeforeColl = new Vector3();
                            int tim = 0;
                            for (float duration = Math.max(0.05f, this.dodgeManeuver.timer); duration <= 0.2f; duration += 0.05f) { // 0.1f, 0.15f, 0.2f
                                for (float delay = duration + 0.05f; delay <= 0.6f; delay += 0.1f) {
                                    for (float angleDiff = (float) (Math.PI / -2f); angleDiff < (float) (Math.PI / 2f); angleDiff += 0.25f) {
                                        tim++;
                                        CarData simCar = new CarData(car);
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

                                        FoolGameData foolGameData = GameData.current().fool();
                                        Vector3 simContact = null;

                                        Vector3 ang = null;
                                        Vector3 vel = null;

                                        for (float time = 0; time < T + 0.1f; time += simDt) {
                                            ControlsOutput simControls = new ControlsOutput();

                                            foolGameData.foolCar(simCar);
                                            simDodge.fool(foolGameData);

                                            simDodge.step(simDt, simControls);

                                            simCar.step(simControls, simDt);

                                            if (simBall.collidesWith(simCar)) {
                                                vel = simBall.velocity;
                                                ang = simBall.angularVelocity;
                                                simBall.collide(simCar);
                                                simBall.hasBeenTouched = true;
                                            }

                                            //simContact = simBall.collide(simCar);
                                            simContact = new Vector3();
                                            if (simBall.hasBeenTouched)
                                                break;

                                            // + = hit earlier
                                            // - = hit later
                                            // The right parameter is different depending on how fast this algorithm performs, currently it takes about 17-21ms, a little more than 2 physics ticks
                                            Optional<YangBallPrediction.YangPredictionFrame> frameOptional = ballPrediction.getFrameAtRelativeTime(time + 1f * RLConstants.tickFrequency);
                                            if (!frameOptional.isPresent())
                                                break;
                                            simBall = frameOptional.get().ballData.makeMutable();
                                            if (simBall.velocity.magnitude() > BallData.MAX_VELOCITY * 1.5f)
                                                System.out.println("Got faulty ball: " + simBall.toString());
                                            simBall.hasBeenTouched = false;
                                        }

                                        if (simBall.hasBeenTouched) {
                                            if (!simCar.doubleJumped || simCar.dodgeTimer <= simDt * 3)
                                                continue;
                                            YangBallPrediction simBallPred = YangBotCppInterop.getBallPrediction(simBall, 120);
                                            //YangBallPrediction simBallPred = simBall.makeBallPrediction(1f/120f, 3);
                                            boolean applyDodgeSettings = false;

                                            for (float time = 0; time < simBallPred.relativeTimeOfLastFrame(); time += 0.1f) {
                                                Optional<YangBallPrediction.YangPredictionFrame> dataAtFrame = simBallPred.getFrameAtRelativeTime(time);
                                                if (!dataAtFrame.isPresent())
                                                    break;
                                                ImmutableBallData ballAtFrame = dataAtFrame.get().ballData;
                                                float dist = (float) ballAtFrame.position.flatten().distance(enemyGoal);
                                                boolean landsInGoal = ballAtFrame.makeMutable().isInGoal(teamSign);

                                                if (landsInGoal) {
                                                    if (!didLandInGoal) {
                                                        didLandInGoal = true;
                                                        timeToGoal = time;
                                                        applyDodgeSettings = true;
                                                        break;
                                                    } else if (time < timeToGoal) {
                                                        timeToGoal = time;
                                                        applyDodgeSettings = true;
                                                        break;
                                                    }
                                                } else if (dist < minDistanceToGoal && time > 0.75f) {
                                                    minDistanceToGoal = dist;
                                                    applyDodgeSettings = true;
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

                                                ballAngBeforeColl = ang;
                                                ballVelBeforeColl = vel;
                                            }
                                        }
                                    }
                                }
                            }
                            if (this.hitPrediction != null) {
                                //RLBotDll.setGameState(new GameState().withGameInfoState(new GameInfoState().withGameSpeed(0.1f)).buildPacket());
                            }

                            System.out.println("Offensive shot planning took: " + (System.currentTimeMillis() - ms) + "ms with " + tim + " simulations");
                        }
                    }

                    dodgeManeuver.step(dt, controlsOutput);

                    if (ball.hasBeenTouched && ball.latestTouch.playerIndex == car.playerIndex && car.elapsedSeconds - ball.latestTouch.gameSeconds - dodgeManeuver.timer < -0.1f)
                        dodgeManeuver.setDone();
                    if (dodgeManeuver.isDone())
                        this.reevaluateStrategy(0.3f);
                    return;
                } else if (car.hasWheelContact) {
                    followPathManeuver.step(dt, controlsOutput);
                    float distanceOffPath = (float) car.position.flatten().distance(followPathManeuver.path.pointAt(followPathManeuver.path.findNearest(car.position)).flatten());
                    if (followPathManeuver.isDone()) {
                        this.reevaluateStrategy(0.3f);
                        System.out.println("Path manuver done");
                        return;
                    } else if (distanceOffPath > 100) {
                        System.out.println("Off path: " + distanceOffPath);
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
        return Optional.empty();
    }
}
