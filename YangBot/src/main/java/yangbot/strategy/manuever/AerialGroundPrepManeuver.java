package yangbot.strategy.manuever;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.GameData;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Vector3;

public class AerialGroundPrepManeuver extends Maneuver {

    public Vector3 targetPos;
    public float arrivalTime = 0;
    private double lastDeltaXMag = -1;

    @Override
    public void step(float dt, ControlsOutput controlsOutput) {
        final GameData g = this.getGameData();
        final CarData car = g.getCarData();

        var deltaX = AerialManeuver.getDeltaX(car, this.targetPos, arrivalTime);

        var deltaXLocal = deltaX.dot(car.orientation);
        // Align the car in-plane
        float /*P*/ angle = (float) Math.atan2(deltaXLocal.y, deltaXLocal.x);
        if (deltaXLocal.x < 0)
            angle = (float) Math.atan2(deltaXLocal.y, -deltaXLocal.x);

        float /*D*/ ang = car.angularVelocity.dot(car.up());

        controlsOutput.withSteer(MathUtils.clip(4 * angle - 0.5f * ang * ang, -1f, 1f));

        // Only alter velocity if we are aligned, or our velocity is too high
        if (Math.abs(angle) < Math.PI * 0.3f || (car.velocity.magnitude() > 1800 && Math.abs(angle) < Math.PI * 0.5f && Math.abs(deltaXLocal.x) > 800))
            controlsOutput.withThrottle(deltaXLocal.x / 200);
        else if (car.velocity.magnitude() < 300)
            controlsOutput.withThrottle(Math.signum(deltaXLocal.x));
        else
            controlsOutput.withThrottle(0.03f);

        double deltaMag = deltaXLocal.div(Math.max(0.1f, this.arrivalTime - car.elapsedSeconds)).magnitude();
        boolean deltaCond = false;
        double da = 0;
        if(this.lastDeltaXMag >= 0 && dt > 0){
            da = Math.abs((deltaMag - this.lastDeltaXMag) / dt);
            deltaCond = da < AerialManeuver.boost_airthrottle_acceleration;
        }
        this.lastDeltaXMag = deltaMag;
        //if(!g.isFoolGameData() && false)
        //    System.out.printf("deltaMag=%.1f t=%.2f angle=%.2f da=%.1f vF=%.1f"+System.lineSeparator(),
        //        deltaX.magnitude(), this.arrivalTime - car.elapsedSeconds, Math.abs(angle) / Math.PI, da, car.forwardSpeed());

        if (Math.abs(angle) < Math.PI * 0.15f && deltaCond) {
            this.setDone();
        }
    }
}
