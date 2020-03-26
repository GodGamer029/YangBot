package yangbot.manuever;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.GameData;
import yangbot.input.ImmutableBallData;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

public class RecoverToGroundManeuver extends Maneuver {

    public Vector2 orientationTarget = new Vector2(0, 0);
    private final TurnManeuver turnManeuver;

    public RecoverToGroundManeuver() {
        turnManeuver = new TurnManeuver();
    }

    @Override
    public void step(float dt, ControlsOutput controlsOutput) {
        final GameData gameData = this.getGameData();
        final Vector3 gravity = gameData.getGravity();
        final CarData car = gameData.getCarData();
        final ImmutableBallData ball = gameData.getBallData();

        if (orientationTarget.isZero()) {
            orientationTarget = car.forward().flatten();
            if (orientationTarget.isZero())
                orientationTarget = car.velocity.flatten();
            orientationTarget = orientationTarget.normalized();
        }

        if (car.hasWheelContact) {
            this.setIsDone(true);
        } else {
            turnManeuver.target = Matrix3x3.lookAt(new Vector3(orientationTarget, 0), new Vector3(0, 0, 1));
            turnManeuver.step(dt, controlsOutput);
        }
    }

    @Override
    public CarData simulate(CarData car) {
        return null;
    }
}
