package yangbot.strategy;

import rlbot.cppinterop.RLBotDll;
import rlbot.cppinterop.RLBotInterfaceException;
import rlbot.flat.BallPrediction;
import rlbot.flat.PredictionSlice;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.manuever.AerialManuver;
import yangbot.util.ControlsOutput;
import yangbot.vector.Vector3;

import java.util.Optional;

public class AerialInterceptStrategy extends Strategy {

    private AerialManuver aerialManuver = null;

    @Override
    protected void planStrategyInternal() {
        aerialManuver = new AerialManuver();

        GameData gameData = GameData.current();
        CarData carData = gameData.getCarData();

        if (!carData.hasWheelContact) {
            this.setDone();
            return;
        }
        try {

            BallPrediction ballPrediction = RLBotDll.getBallPrediction();

            for (int i = 0; i < ballPrediction.slicesLength(); i++) {
                PredictionSlice slice = ballPrediction.slices(i);
                Vector3 pos = new Vector3(slice.physics().location());
                if (pos.z < 300)
                    continue;
                if (slice.gameSeconds() - carData.elapsedSeconds < 0.2f)
                    continue;


            }
        } catch (RLBotInterfaceException e) {
            e.printStackTrace();
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
