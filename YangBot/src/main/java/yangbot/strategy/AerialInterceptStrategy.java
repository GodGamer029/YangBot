package yangbot.strategy;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.GameData;
import yangbot.manuever.AerialManeuver;
import yangbot.prediction.YangBallPrediction;
import yangbot.vector.Vector3;

import java.util.Optional;

public class AerialInterceptStrategy extends Strategy {

    private AerialManeuver aerialManeuver = null;

    @Override
    protected void planStrategyInternal() {
        aerialManeuver = new AerialManeuver();

        GameData gameData = GameData.current();
        CarData carData = gameData.getCarData();

        if (!carData.hasWheelContact) {
            this.setDone();
            return;
        }

        YangBallPrediction ballPrediction = gameData.getBallPrediction();

        for (YangBallPrediction.YangPredictionFrame frame : ballPrediction.frames) {

            Vector3 pos = frame.ballData.position;
            if (pos.z < 300)
                continue;
            if (frame.absoluteTime - carData.elapsedSeconds < 0.2f)
                continue;


        }

    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {

    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.of(new DefaultStrategy());
    }
}
