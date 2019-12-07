package yangbot.strategy;

import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.prediction.YangBallPrediction;
import yangbot.vector.Vector3;

public class AfterKickoffStrategy extends StrategyPlanner {

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

        switch (kickoffQuality) {
            case GOOD:
                newDecidedStrategy = new OffensiveStrategy();
                break;
            case BAD:
                newDecidedStrategy = new DefendStrategy();
                break;
            case NEUTRAL:
                newDecidedStrategy = new NeutralStrategy();
                break;
            default:
                assert false;
        }
        this.setDone();
    }

    enum KickoffQuality {
        BAD,
        GOOD,
        NEUTRAL
    }
}
