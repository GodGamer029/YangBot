package yangbot.manuever;

import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.util.ControlsOutput;
import yangbot.util.MathUtils;
import yangbot.vector.Vector3;

public class DriveToPointManuver extends Manuver {

    public Vector3 targetPosition = null;
    public float targetVelocity = 0;

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

        float steer;
        float throttle;
        boolean boost = false;

        // Steer
        {
            double angle = targetPosition
                    .sub(car.position)
                    .dot(car.orientationMatrix)
                    .flatten()
                    .angle();
            steer = (float) MathUtils.clip(MathUtils.speedbot_steer(angle),-1, 1);

            if(Math.abs(angle) > Math.PI / 2)
                controlsOutput.withSlide(true);
        }

        // Throttle
        {
            final float velocityForward = (float) car.velocity.dot(car.forward());
            final float currentVelocity = (float) car.velocity.flatten().magnitude();
            final float velocityDifference = targetVelocity - currentVelocity;
            final float brakingForcePerTick = -DriveManuver.brake_accel * dt;
            final float ticksWithBrakingForce = velocityDifference / brakingForcePerTick;
            final Vector3 carForwardDirection = car.forward();
            boolean behindTarget = false;

            float lastDistance = (float) car.position.flatten().distance(targetPosition.flatten());
            if(ticksWithBrakingForce >= 1 && Math.abs(steer) < 0.15f){ // We need to brake at least 1 tick
                Vector3 tempPos = car.position;
                Vector3 tempVelocity = car.velocity;

                tempPos = tempPos.add(tempVelocity.mul(dt));

                int ticksIterating = (int) ticksWithBrakingForce;
                for(int i = 0; i < ticksIterating; i++){
                    if(i > 100) // Stop here to prevent looping to infinity
                        break;

                    float vfTemp = (float) tempVelocity.dot(carForwardDirection);

                    Vector3 actingForce;

                    if(Math.abs(vfTemp) > DriveManuver.min_speed && -1 * Math.signum(vfTemp) < 0)
                        actingForce = carForwardDirection.mul(-DriveManuver.brake_accel);
                    else
                        actingForce = carForwardDirection.mul(-DriveManuver.coasting_accel);

                    tempVelocity = tempVelocity.add(actingForce.mul(dt));
                    if(tempVelocity.dot(carForwardDirection) <= 0)
                        break; // We are going backwards, no need to check if we get to reach the target

                    tempPos = tempPos.add(tempVelocity.mul(dt));

                    float curDistance = (float) tempPos.flatten().distance(targetPosition.flatten());
                    if(curDistance <= lastDistance && curDistance >= 5){ // Compare against last Distance
                        lastDistance = curDistance; // We are getting closer to the target
                    }else{
                        behindTarget = true;  // Distance between us and target increases
                        break;
                    }
                }

                if(behindTarget || lastDistance <= 30){
                    throttle = -1;
                    boost = false;
                }else{
                    throttle = 1;
                    if(lastDistance >= 200)
                        boost = true;
                }
            }else{
                if(lastDistance < 30)
                    throttle = velocityDifference / DriveManuver.throttle_accel(velocityForward);
                else
                    throttle = 1;
                if(targetVelocity > DriveManuver.max_throttle_speed || velocityDifference > DriveManuver.throttle_accel(velocityForward))
                    boost = true;
            }
        }

        controlsOutput
                .withThrottle(throttle)
                .withSteer(steer)
                .withBoost(boost);
    }

    @Override
    public CarData simulate(CarData car) {
        return null;
    }
}
