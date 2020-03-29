package yangbot.strategy.manuever.kickoff;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.GameData;
import yangbot.input.ImmutableBallData;
import yangbot.strategy.manuever.AerialManeuver;
import yangbot.strategy.manuever.Maneuver;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector3;

public class LowGravityKickoffManeuver extends Maneuver {

    private final AerialManeuver aerialManeuver;

    public LowGravityKickoffManeuver() {
        this.aerialManeuver = new AerialManeuver();
    }

    @Override
    public void step(float dt, ControlsOutput controlsOutput) {
        final GameData gameData = this.getGameData();
        final ImmutableBallData ball = gameData.getBallData();
        final CarData car = gameData.getCarData();

        if (!ball.velocity.isZero()) {
            this.setIsDone(true);
            return;
        }


        if (aerialManeuver.target == null) { // Find target

            boolean foundTarget = false;
            for (float f = 1; f < 7; f += 1 / 30f) {
                aerialManeuver.target = ball.position;
                aerialManeuver.arrivalTime = f + car.elapsedSeconds;
                CarData carData = aerialManeuver.simulate(car);

                if (carData.position.distance(aerialManeuver.target) <= 200) {
                    System.out.println("Found kickoff at " + f);
                    foundTarget = true;
                    break;
                }
            }
            if (!foundTarget) {
                System.out.println("Cant find target kick off");
                aerialManeuver.target = null;
                return;
            }
        }

        aerialManeuver.target_orientation = Matrix3x3.lookAt(ball.position.sub(car.position).normalized(), new Vector3(0, 0, 1));
        aerialManeuver.step(dt, controlsOutput);
        if (aerialManeuver.isDone())
            this.setIsDone(true);
    }

    @Override
    public CarData simulate(CarData car) {
        return null;
    }
}
