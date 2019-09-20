package yangbot.manuever;

import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.util.ControlsOutput;
import yangbot.vector.Matrix3x3;
import yangbot.vector.Vector2;
import yangbot.vector.Vector3;

public class RecoverToGroundManuver extends Manuver {

    public Vector2 orientationTarget = new Vector2(0, 0);
    private TurnManuver turnManuver;;
    public RecoverToGroundManuver() {

        turnManuver = new TurnManuver();
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

        if (orientationTarget.isZero()){
            orientationTarget = car.forward().flatten();
            if(orientationTarget.isZero())
                orientationTarget = car.velocity.flatten();
            orientationTarget = orientationTarget.normalized();
        }

        if (car.hasWheelContact){
            this.setIsDone(true);
        }else{
            turnManuver.target = Matrix3x3.lookAt(new Vector3(orientationTarget, 0), new Vector3(0, 0, 1));
            turnManuver.step(dt, controlsOutput);
        }
    }

    @Override
    public CarData simulate(CarData car) {
        return null;
    }
}
