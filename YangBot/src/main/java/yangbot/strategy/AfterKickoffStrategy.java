package yangbot.strategy;

import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.prediction.YangBallPrediction;
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

        YangBallPrediction ballPrediction = gameData.getBallPrediction();
        for (YangBallPrediction.YangPredictionFrame frame : ballPrediction.frames) {
            if (frame.absoluteTime > car.elapsedSeconds + 2) {
                predictedPos = frame.ballData.position;
                break;
            }
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
