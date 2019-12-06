package yangbot.manuever;

import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.util.ControlsOutput;
import yangbot.vector.Matrix3x3;
import yangbot.vector.Vector2;
import yangbot.vector.Vector3;

public class RegularKickoffManeuver extends Maneuver {

    @SuppressWarnings("WeakerAccess")
    public boolean doSecondFlip = true;
    private KickOffState kickOffState = KickOffState.INIT;
    private KickOffLocation kickOffLocation = null;
    private DodgeManeuver dodgeManeuver;
    private TurnManeuver turnManeuver;
    private boolean reachedTheBoost = false;
    private boolean doingFlip = false;
    private float timer = 0;

    public static boolean isKickoff() {
        GameData g = GameData.current();
        BallData ball = g.getBallData();
        if (g.getCarData().elapsedSeconds - GameData.timeOfMatchStart >= 3)
            return false;
        return (ball.velocity.flatten().isZero() && ball.position.flatten().isZero());
    }

    @Override
    public boolean isViable() {
        return false;
    }

    @Override
    public void step(float dt, ControlsOutput controlsOutput) {
        final GameData gameData = this.getGameData();
        final Vector3 gravity = gameData.getGravity();
        final CarData car = gameData.getCarData();
        final BallData ball = gameData.getBallData();

        timer += dt;

        if (ball.position.flatten().magnitude() > 5) {
            //System.out.println("Kickoff done!" + ball.position.flatten().magnitude() + " : "+ball.position.flatten());
            this.setIsDone(true);
            return;
        }
        switch (kickOffState) {
            case INIT:
                reachedTheBoost = false;
                doingFlip = false;
                int xPos = Math.abs(Math.round(car.position.x));
                if (xPos >= 2040 && xPos <= 2056)
                    kickOffLocation = KickOffLocation.CORNER;
                else if (xPos >= 250 && xPos <= 262)
                    kickOffLocation = KickOffLocation.MIDDLE;
                else if (xPos == 0)
                    kickOffLocation = KickOffLocation.CENTER;
                else {
                    this.setIsDone(true);
                    // System.out.println("Couldn't determine kickoff location: "+car.position.toString()+" xPos: "+xPos);
                    return;
                }

                dodgeManeuver = new DodgeManeuver();
                turnManeuver = new TurnManeuver();

                //System.out.println("kickoff location: "+kickOffLocation.name());

                controlsOutput.withThrottle(1);
                controlsOutput.withBoost(true);

                if (kickOffLocation == KickOffLocation.CENTER)
                    kickOffState = KickOffState.REACH_BALL;
                else
                    kickOffState = KickOffState.REACH_BOOST;
                break;
            case REACH_BOOST:
                if (kickOffLocation == KickOffLocation.CORNER) {
                    controlsOutput.withThrottle(1);
                    if (car.boost >= 13)
                        controlsOutput.withBoost(true);

                    if (doingFlip) {
                        if (dodgeManeuver.timer >= dodgeManeuver.delay && !car.hasWheelContact) {
                            dodgeManeuver.step(dt, controlsOutput);
                            if (controlsOutput.holdJump()) {
                                kickOffState = KickOffState.SECOND_FlIP;
                                dodgeManeuver = new DodgeManeuver();
                            }
                        } else
                            dodgeManeuver.step(dt, controlsOutput);

                    } else if (Math.abs(car.position.y) < 2400 || reachedTheBoost) {
                        if (car.forward().angle(new Vector3(0, -Math.signum(car.position.y), 0)) < Math.PI / 6) {
                            //System.out.println("Jumping with Angle: "+car.position);
                            doingFlip = true;
                            dodgeManeuver.duration = 0.f;
                            dodgeManeuver.delay = 0.10f;
                            dodgeManeuver.target = new Vector3(0, car.position.y, car.position.z);
                            dodgeManeuver.step(dt, controlsOutput);
                        } else {
                            controlsOutput.withSteer(Math.signum(car.position.y) * Math.signum(car.position.x));
                        }
                        reachedTheBoost = true;
                    }
                } else {
                    controlsOutput.withThrottle(1);
                    controlsOutput.withBoost(true);

                    if (Math.abs(car.position.y) < 2850 || reachedTheBoost) {
                        controlsOutput.withBoost(true);
                        reachedTheBoost = true;
                        doingFlip = true;
                        dodgeManeuver.duration = 0.1f;
                        dodgeManeuver.delay = 0.15f;
                        dodgeManeuver.target = null;
                        float carFAngle = (float) car.forward().flatten().normalized().angle();
                        float ballCarAngle = (float) ball.position.flatten().sub(car.position.flatten()).normalized().angle();
                        float newAng = (ballCarAngle - carFAngle) * 3f + ballCarAngle;
                        dodgeManeuver.controllerInput = new Vector2(Math.signum(car.position.y) * Math.cos(newAng), Math.signum(car.position.y) * Math.sin(newAng)).normalized();
                        if (dodgeManeuver.timer >= dodgeManeuver.delay) {
                            kickOffState = KickOffState.SECOND_FlIP;
                            dodgeManeuver.step(dt, controlsOutput);

                            dodgeManeuver = new DodgeManeuver();
                        } else
                            dodgeManeuver.step(dt, controlsOutput);
                    } else {
                        Vector3 target_local = new Vector3(0, Math.signum(car.position.y) * 2850, 0).sub(car.position).dot(car.orientationMatrix);

                        float angle = (float) Math.atan2(target_local.y, target_local.x);
                        controlsOutput.withSteer(3.0f * angle);
                    }

                }
                break;
            case REACH_BALL:
                if (Math.abs(car.position.y) > 2000)
                    controlsOutput.withBoost(true);
                else if (Math.abs(car.position.y) < 1000) {
                    dodgeManeuver.duration = 0.1f;
                    dodgeManeuver.delay = 0.15f;
                    dodgeManeuver.direction = car.forward().flatten();
                    dodgeManeuver.step(dt, controlsOutput);
                }

                controlsOutput.withThrottle(1);
                break;
            case SECOND_FlIP: {
                if (car.boost > 13 && kickOffLocation == KickOffLocation.CORNER)
                    controlsOutput.withBoost(true);

                if ((car.hasWheelContact && (Math.abs(car.position.x) < 150 || Math.abs(car.position.x + car.velocity.x * dt) < 150)) || dodgeManeuver.timer > 0) {
                    if (!doSecondFlip) {
                        this.setIsDone(true);
                        this.kickOffState = KickOffState.INIT;
                        return;
                    }

                    // TODO
                    Vector3 closestToBall = null;
                    float closestToBallDistance = (float) car.position.flatten().distance(ball.position.flatten()) * 2f;
                    for (CarData c : gameData.getAllCars()) {
                        if (c.team == car.team)
                            continue;
                        float dist = (float) c.position.flatten().distance(ball.position.flatten());
                        if (dist < closestToBallDistance) {
                            closestToBallDistance = dist;
                            closestToBall = c.position.add(c.velocity.mul(dt * 2));
                        }

                    }
                    //dodgeManeuver.direction = ball.position.flatten().sub(car.position.add(car.velocity.mul(dt * MathUtils.clip(closestToBall == null ? 100 : 100 - Math.abs(closestToBall.sub(ball.position).x), 1000, 1000)).x, 0, 0).flatten()).normalized();

                    dodgeManeuver.duration = 0.02f;
                    dodgeManeuver.delay = 0.1f;
                    dodgeManeuver.target = null;
                    dodgeManeuver.direction = ball.position.flatten().sub(car.position.add(car.velocity.mul(dt * 1000).x, 0, 0).flatten()).normalized();

                    dodgeManeuver.step(dt, controlsOutput);

                    if (dodgeManeuver.timer >= dodgeManeuver.delay) {
                        turnManeuver.target = Matrix3x3.lookAt(new Vector3(ball.position.add(ball.velocity.mul(dt * 3)).sub(car.position).flatten(), car.position.z).normalized(), new Vector3(0, 0, 1));
                        //turnManeuver.step(dt, controlsOutput);
                    }
                    if (dodgeManeuver.isDone() || dodgeManeuver.timer >= 1f) {
                        this.setIsDone(true);
                        kickOffState = KickOffState.INIT;
                    }

                } else if (car.hasWheelContact) {
                    if (Math.abs(car.position.x + car.velocity.x * dt) < Math.abs(car.position.x) && Math.abs(car.position.x + car.velocity.x * dt) > 200f) {
                        controlsOutput.withSlide(true);
                        controlsOutput.withSteer((float) car.forward().flatten().correctionAngle(ball.position.flatten().sub(car.position.flatten().withX(ball.position.x))));
                    } else {
                        Vector3 target_local = ball.position.sub(car.position).dot(car.orientationMatrix);

                        float angle = (float) Math.atan2(target_local.y, target_local.x);
                        controlsOutput.withSteer(3.0f * angle);
                    }

                    controlsOutput.withThrottle(1);
                } else {
                    if (turnManeuver.target == null) {
                        if (kickOffLocation == KickOffLocation.CORNER)
                            turnManeuver.target = Matrix3x3.lookAt(new Vector3(-0.18f * Math.signum(car.position.x), -Math.signum(car.position.y), 0), new Vector3(0, 0, 1));
                        else
                            turnManeuver.target = Matrix3x3.lookAt(new Vector3(0, -Math.signum(car.position.y), 0.3f).normalized(), new Vector3(0, 0, 1));
                    }

                    turnManeuver.step(dt, controlsOutput);
                    controlsOutput.withSlide(true);
                    controlsOutput.withJump(false);
                    controlsOutput.withThrottle(1f);
                }

                break;
            }
        }

        if (timer > 4)
            this.setIsDone(true);
    }

    @Override
    public CarData simulate(CarData car) {
        return null;
    }

    enum KickOffState {
        INIT,
        REACH_BOOST,
        REACH_BALL,
        SECOND_FlIP
    }

    enum KickOffLocation {
        CORNER,
        MIDDLE,
        CENTER
    }
}
