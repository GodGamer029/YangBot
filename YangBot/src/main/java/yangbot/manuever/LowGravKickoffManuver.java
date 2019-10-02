package yangbot.manuever;

import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.util.ControlsOutput;
import yangbot.vector.Matrix3x3;
import yangbot.vector.Vector3;

public class LowGravKickoffManuver extends Manuver {

    private AerialManuver aerialManuver;

    public LowGravKickoffManuver() {
        this.aerialManuver = new AerialManuver();
    }

    @Override
    public boolean isViable() {
        return false;
    }

    @Override
    public void step(float dt, ControlsOutput controlsOutput) {
        final GameData gameData = this.getGameData();
        final BallData ball = gameData.getBallData();
        final CarData car = gameData.getCarData();

        if (!ball.velocity.isZero()) {
            this.setIsDone(true);
            return;
        }


        if (aerialManuver.target == null) { // Find target

            boolean foundTarget = false;
            for (float f = 1; f < 7; f += 1 / 30f) {
                aerialManuver.target = ball.position;
                aerialManuver.arrivalTime = f + car.elapsedSeconds;
                CarData carData = aerialManuver.simulate(car);

                if (carData.position.distance(aerialManuver.target) <= 200) {
                    System.out.println("Found kickoff at " + f);
                    foundTarget = true;
                    break;
                }
            }
            if (!foundTarget) {
                System.out.println("Cant find target kick off");
                aerialManuver.target = null;
                return;
            }
        }

        aerialManuver.target_orientation = Matrix3x3.lookAt(ball.position.sub(car.position).normalized(), new Vector3(0, 0, 1));
        aerialManuver.step(dt, controlsOutput);
        if (aerialManuver.isDone())
            this.setIsDone(true);
    }

    @Override
    public CarData simulate(CarData car) {
        return null;
    }
}
