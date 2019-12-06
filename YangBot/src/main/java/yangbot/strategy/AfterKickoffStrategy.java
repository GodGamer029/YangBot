package yangbot.strategy;

import rlbot.cppinterop.RLBotDll;
import rlbot.flat.BallPrediction;
import rlbot.flat.PredictionSlice;
import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.util.ControlsOutput;
import yangbot.vector.Vector3;

import java.util.Optional;

public class AfterKickoffStrategy extends Strategy {

    private KickoffQuality kickoffQuality = KickoffQuality.NEUTRAL;

    @Override
    public void planStrategyInternal() {
        GameData gameData = GameData.current();
        CarData car = gameData.getCarData();
        BallData ball = gameData.getBallData();

        Vector3 predictedPos = ball.position;
        try {
            BallPrediction ballPrediction = RLBotDll.getBallPrediction();
            for (int i = 0; i < ballPrediction.slicesLength(); i += 4) {
                PredictionSlice slice = ballPrediction.slices(i);
                if (slice.gameSeconds() > car.elapsedSeconds + 2) {
                    predictedPos = new Vector3(slice.physics().location());
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        int ballInFavorOfTeam = predictedPos.y > 0 ? 0 : 1;
        int ourTeam = car.team;
        float distanceFromMiddle = Math.abs(predictedPos.y);

        if (distanceFromMiddle > 500) {
            if (ballInFavorOfTeam == ourTeam)
                kickoffQuality = KickoffQuality.GOOD;
            else
                kickoffQuality = KickoffQuality.BAD;
        } else
            kickoffQuality = KickoffQuality.NEUTRAL;
    }

    @Override
    public void stepInternal(float dt, ControlsOutput controlsOutput) {
        this.setDone();
    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.of(new DefaultStrategy());
    }

    enum KickoffQuality {
        BAD,
        GOOD,
        NEUTRAL
    }
}
