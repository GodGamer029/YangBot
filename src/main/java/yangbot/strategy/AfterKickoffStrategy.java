package yangbot.strategy;

import rlbot.cppinterop.RLBotDll;
import rlbot.flat.BallPrediction;
import rlbot.flat.PredictionSlice;
import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.util.AdvancedRenderer;
import yangbot.util.ControlsOutput;
import yangbot.vector.Vector3;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AfterKickoffStrategy extends Strategy {

    enum KickoffQuality {
        BAD,
        GOOD,
        NEUTRAL
    };

    private static List<Strategy> suggestedStrat = null;
    private KickoffQuality kickoffQuality = KickoffQuality.NEUTRAL;

    @Override
    public void planStrategy() {
        GameData gameData = GameData.current();
        CarData car = gameData.getCarData();
        BallData ball = gameData.getBallData();

        Vector3 predictedPos = ball.position;
        try{
            BallPrediction pred = RLBotDll.getBallPrediction();
            for (int i = 0; i < pred.slicesLength(); i += 4) {
                PredictionSlice slice = pred.slices(i);
                if (slice.gameSeconds() > car.elapsedSeconds + 2) {
                    predictedPos = new Vector3(slice.physics().location());
                    break;
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        int ballInFavorOfTeam = predictedPos.y > 0 ? 0 : 1;
        int ourTeam = car.team;
        float distanceFromMiddle = Math.abs(predictedPos.y);

        if(distanceFromMiddle > 500){
            if(ballInFavorOfTeam == ourTeam)
                kickoffQuality = KickoffQuality.GOOD;
            else
                kickoffQuality = KickoffQuality.BAD;
        }else
            kickoffQuality = KickoffQuality.NEUTRAL;
    }

    @Override
    public void step(float dt, ControlsOutput controlsOutput) {
        AdvancedRenderer rend = new AdvancedRenderer(9384);
        rend.startPacket();

        rend.drawString2d("Kickoff: "+kickoffQuality.name(), Color.WHITE, new Point(10, 510), 1, 1);

        rend.finishAndSendIfDifferent();
    }

    @Override
    public List<Strategy> suggestStrategy() {
        return suggestedStrat;
    }

    static {
        suggestedStrat = new ArrayList<>();
        suggestedStrat.add(new DefaultStrategy());
        suggestedStrat = Collections.unmodifiableList(suggestedStrat);
    }
}
