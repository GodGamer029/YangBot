package yangbot.strategy.manuever;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.GameData;
import yangbot.util.AdvancedRenderer;
import yangbot.util.math.vector.Vector3;

import java.awt.*;

public class DriftControllerManeuver extends Maneuver {

    public Vector3 targetDirection;
    private boolean disableSlide = false;

    @Override
    public void step(float dt, ControlsOutput controlsOutput) {
        final GameData gameData = this.getGameData();
        final CarData car = gameData.getCarData();
        final AdvancedRenderer renderer = gameData.getAdvancedRenderer();

        Vector3 angularLocal = car.angularVelocity.dot(car.orientation).div(CarData.MAX_ANGULAR_VELOCITY);
        Vector3 targetLocal = this.targetDirection.dot(car.orientation);

        if (this.targetDirection.dot(car.forward()) > 0.98f && Math.abs(angularLocal.z) < 0.01f && car.velocity.normalized().dot(this.targetDirection) > 0.97f) {
            this.setDone();
        }

        // Steer
        {
            float P = 0.95f;
            float pComp = ((float) Math.atan2(targetLocal.y, targetLocal.x));
            float D = 1.8f;
            float steer = P * pComp + D * -angularLocal.z;

            /*renderer.drawString2d(String.format("% 6.2f", steer), Color.WHITE, new Point(400, 400), 2, 2);
            renderer.drawString2d(String.format("% 6.2f", P * ((float) Math.atan2(targetLocal.y, targetLocal.x))), Color.WHITE, new Point(400, 430), 2, 2);
            renderer.drawString2d(String.format("% 6.2f", D * -angularLocal.z), Color.WHITE, new Point(400, 460), 2, 2);*/

            if (!disableSlide && Math.abs(steer) > 0.01f && (Math.abs(angularLocal.z) < 0.1f || Math.signum(steer) == Math.signum(angularLocal.z))) {
                controlsOutput.withSteer(Math.signum(steer) * Math.abs(steer * 5));
                if (!disableSlide)
                    controlsOutput.withSlide(true);
            } else {
                disableSlide = true;
                if (car.forward().dot(car.velocity.normalized()) > 0.97f)
                    controlsOutput.withSteer(pComp * 3);
            }

        }

        renderer.drawLine3d(this.isDone() ? Color.GREEN : Color.RED, car.position, car.position.add(this.targetDirection.mul(300)));

        controlsOutput.withThrottle(1f);
    }

}
