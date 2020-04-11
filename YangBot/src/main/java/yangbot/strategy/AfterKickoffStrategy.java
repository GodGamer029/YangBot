package yangbot.strategy;

import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.input.ImmutableBallData;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.vector.Vector3;

public class AfterKickoffStrategy extends StrategyPlanner {

    private KickoffQuality kickoffQuality = KickoffQuality.GOOD;

    @Override
    public void planStrategyInternal() {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final ImmutableBallData ball = gameData.getBallData();

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
            kickoffQuality = KickoffQuality.GOOD;

        switch (kickoffQuality) {
            case GOOD:
                newDecidedStrategy = new OffensiveStrategy();
                break;
            case BAD:
                newDecidedStrategy = new DefendStrategy();
                break;
            default:
                assert false;
        }
        this.setDone();
    }

    enum KickoffQuality {
        BAD,
        GOOD
    }
}
