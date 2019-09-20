package yangbot.manuever;

import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.util.ControlsOutput;
import yangbot.util.MathUtils;
import yangbot.vector.Matrix3x3;
import yangbot.vector.Vector2;
import yangbot.vector.Vector3;

public class RegularKickoffManuver extends Manuver {

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

    private KickOffState kickOffState = KickOffState.INIT;
    private KickOffLocation kickOffLocation = null;
    private DodgeManuver dodgeManuver;
    private TurnManuver turnManuver;
    private boolean reachedTheBoost = false;
    private boolean doingFlip = false;
    public boolean doSecondFlip = true;

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
        
        if(ball.position.flatten().magnitude() > 5){
            System.out.println("Kickoff done!" + ball.position.flatten().magnitude() + " : "+ball.position.flatten());
            this.setIsDone(true);
            return;
        }
        switch(kickOffState){
            case INIT:
                reachedTheBoost = false;
                doingFlip = false;
                int xPos = Math.abs(Math.round(car.position.x));
                if(xPos == 2048)
                    kickOffLocation = KickOffLocation.CORNER;
                else if(xPos == 256)
                    kickOffLocation = KickOffLocation.MIDDLE;
                else if(xPos == 0)
                    kickOffLocation = KickOffLocation.CENTER;
                else{
                    // System.out.println("Couldnt determine kickoff location: "+car.position.toString()+" xPos: "+xPos);
                    return;
                }

                dodgeManuver = new DodgeManuver();
                turnManuver = new TurnManuver();

                System.out.println("kickoff location: "+kickOffLocation.name());

                controlsOutput.withThrottle(1);
                controlsOutput.withBoost(true);

                if(kickOffLocation == KickOffLocation.CENTER)
                    kickOffState = KickOffState.REACH_BALL;
                else
                    kickOffState = KickOffState.REACH_BOOST;
                break;
            case REACH_BOOST:
                if(kickOffLocation == KickOffLocation.CORNER){
                    controlsOutput.withThrottle(1);
                    if(car.boost >= 13)
                        controlsOutput.withBoost(true);

                    if(doingFlip) {
                        if(dodgeManuver.timer >= dodgeManuver.delay){
                            kickOffState = KickOffState.SECOND_FlIP;
                            dodgeManuver.step(dt, controlsOutput);
                            dodgeManuver.timer = 0;
                        }else
                            dodgeManuver.step(dt, controlsOutput);

                    } else if(Math.abs(car.position.y) < 2400 || reachedTheBoost){
                        if(car.forward().angle(new Vector3(0, -Math.signum(car.position.y), 0)) < Math.PI / 6){
                            System.out.println("Jumping with Angle: "+car.position);
                            doingFlip = true;
                            dodgeManuver.duration = 0.05f;
                            dodgeManuver.delay = 0.10f;
                            dodgeManuver.target = new Vector3(0, car.position.y, car.position.z);
                            dodgeManuver.step(dt, controlsOutput);
                        }else{
                            controlsOutput.withSteer(Math.signum(car.position.y) * Math.signum(car.position.x));
                        }
                        reachedTheBoost = true;
                    }
                }else{
                    controlsOutput.withThrottle(1);
                    controlsOutput.withBoost(true);

                    if(Math.abs(car.position.y) < 2850 || reachedTheBoost){
                        controlsOutput.withBoost(true);
                        reachedTheBoost = true;
                        doingFlip = true;
                        dodgeManuver.duration = 0.1f;
                        dodgeManuver.delay = 0.15f;
                        dodgeManuver.target = null;
                        float carFAngle = (float) car.forward().flatten().normalized().angle();
                        float ballCarAngle = (float) ball.position.flatten().sub(car.position.flatten()).normalized().angle();
                        float newAng = (ballCarAngle - carFAngle) * 3f + ballCarAngle;
                        dodgeManuver.controllerInput = new Vector2(Math.signum(car.position.y) * Math.cos(newAng), Math.signum(car.position.y) * Math.sin(newAng)).normalized();
                        if(dodgeManuver.timer >= dodgeManuver.delay){
                            kickOffState = KickOffState.SECOND_FlIP;
                            dodgeManuver.step(dt, controlsOutput);
                            dodgeManuver.timer = 0;
                        }else
                            dodgeManuver.step(dt, controlsOutput);
                    }else{
                        Vector3 target_local = new Vector3(0, Math.signum(car.position.y) * 2850, 0).sub(car.position).dot(car.orientationMatrix);

                        float angle = (float) Math.atan2(target_local.y, target_local.x);
                        controlsOutput.withSteer(3.0f * angle);
                    }

                }
                break;
            case REACH_BALL:
                if(Math.abs(car.position.y) > 2000)
                    controlsOutput.withBoost(true);
                else if(Math.abs(car.position.y) < 1000){
                    dodgeManuver.duration = 0.1f;
                    dodgeManuver.delay = 0.15f;
                    dodgeManuver.direction = car.forward();
                    dodgeManuver.step(dt, controlsOutput);
                }

                controlsOutput.withThrottle(1);
                break;
            case SECOND_FlIP:
            {
                if(car.boost > 13 && kickOffLocation == KickOffLocation.CORNER)
                    controlsOutput.withBoost(true);

                if((car.hasWheelContact && Math.abs(car.position.x) < 1000) || dodgeManuver.timer > 0){
                    if(!doSecondFlip){
                        this.setIsDone(true);
                        this.kickOffState = KickOffState.INIT;
                        return;
                    }

                    Vector3 closestToBall = null;
                    float closestToBallDistance = (float) car.position.flatten().distance(ball.position.flatten()) * 2f;
                    for(CarData c : gameData.getAllCars()){
                        if(c.team == car.team)
                            continue;
                        float dist = (float) c.position.flatten().distance(ball.position.flatten());
                        if(dist < closestToBallDistance){
                            closestToBallDistance = dist;
                            closestToBall = c.position;
                        }
                    }

                    dodgeManuver.duration = 0.1f;
                    dodgeManuver.delay = 0.15f;
                    dodgeManuver.target = null;
                    dodgeManuver.direction = new Vector3(MathUtils.clip(closestToBall == null ? 0 : closestToBall.sub(ball.position).flatten().x / 50, -1, 1), -Math.signum(car.position.y), 0).normalized();
                    dodgeManuver.step(dt, controlsOutput);
                    if(dodgeManuver.timer >= dodgeManuver.delay){
                        turnManuver.target = Matrix3x3.lookAt(new Vector3(ball.position.add(ball.velocity.mul(dt * 3)).sub(car.position).flatten(), car.position.z).normalized(), new Vector3(0, 0, 1));
                        turnManuver.step(dt, controlsOutput);
                    }
                    if(dodgeManuver.isDone() || dodgeManuver.timer >= 1f){
                        this.setIsDone(true);
                        kickOffState = KickOffState.INIT;
                    }

                }else if(car.hasWheelContact){
                    Vector3 target_local = ball.position.sub(car.position).dot(car.orientationMatrix);

                    float angle = (float) Math.atan2(target_local.y, target_local.x);
                    controlsOutput.withSteer(3.0f * angle);
                } else {
                    if(kickOffLocation == KickOffLocation.CORNER)
                        turnManuver.target = Matrix3x3.lookAt(new Vector3(-0.35f * Math.signum(car.position.x), -Math.signum(car.position.y), 0), new Vector3(0, 0, 1));
                    else
                        turnManuver.target = Matrix3x3.lookAt(new Vector3(0, -Math.signum(car.position.y), 0.3f).normalized(), new Vector3(0, 0, 1));

                    turnManuver.step(dt, controlsOutput);
                    controlsOutput.withJump(false);
                }

                break;
            }
        }
    }

    @Override
    public CarData simulate(CarData car) {
        return null;
    }
}
