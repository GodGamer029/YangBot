package yangbot.optimizers;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rlbot.gamestate.GameInfoState;
import yangbot.cpp.YangBotCppInterop;
import yangbot.cpp.YangBotJNAInterop;
import yangbot.input.GameData;
import yangbot.input.RLConstants;
import yangbot.optimizers.graders.OffensiveGrader;
import yangbot.optimizers.graders.ValueNetworkGrader;
import yangbot.optimizers.model.ModelUtils;
import yangbot.strategy.manuever.DodgeManeuver;
import yangbot.util.YangBallPrediction;
import yangbot.util.scenario.Scenario;
import yangbot.util.scenario.ScenarioUtil;

import java.awt.*;

public class CarBallCollisionOptimizerPerfTest {

    @BeforeAll
    public static void setup() {
        YangBotCppInterop.init((byte) 0, (byte) 0);
        YangBotJNAInterop.doNothing();
    }

    @Test
    public void testSyntheticSample() {
        var gameData = GameData.current();
    }

    @Test
    public void testModel() {
        String encoded = "eWFuZ3YxOmMoYj05OC4wLHA9KDE2MjQuOTQwLDQzOTEuMDQwLDI3LjEyMCksdj0oLTEyNDEuNTcxLDYwMS43OTEsMzA4LjAyMSksYT0oLTAuMDA3LC0wLjAwMywtMC4zMjEpLG89KC0wLjAxNywyLjY3NSwwLjAwMCkpLGIocD0oMTIzMi42MTAsNDg2NS4xNzAsOTIuNDcwKSx2PSgzMTEuNjIxLC05MTIuNzExLDAuMDAwKSxhPSg1LjA2MiwyLjM2NSwtMi4xODcpKTs";
        var simGameData = GameData.current();
        ScenarioUtil.decodeApplyToGameData(simGameData, encoded);

        var ball = simGameData.getBallData();

        var predicted = ModelUtils.ballToPrediction(ball);
        System.out.println("Predicted s=" + predicted.getKey() + " t=" + predicted.getValue());
    }

    @Test
    public void testSample() {
        String encoded = "eWFuZ3YxOmMoYj05MS4wLHA9KC0yMDExLjk0MCwyNzI1LjIzMCwyNy4xMjApLHY9KC0yODcuNzkxLDEyNDEuMzkxLDMwNy43NjEpLGE9KC0wLjAwMiwwLjAwMCwtMC4wMDApLG89KC0wLjAxNywxLjc5OSwwLjAwMCkpLGIocD0oLTIwODcuMzQwLDMyNDMuODMwLDkzLjEzMCksdj0oMTY4LjQzMSwtMTA5LjIyMSwwLjAwMCksYT0oMS4xOTcsMS44NDYsLTAuMjA2KSk7";
        var simGameData = GameData.current();
        ScenarioUtil.decodeApplyToGameData(simGameData, encoded);

        DodgeStrikeOptimizer optimizer = new DodgeStrikeOptimizer();
        optimizer.expectedBallHitTime = simGameData.getElapsedSeconds() + 0.5f;

        DodgeManeuver strikeDodge = new DodgeManeuver();
        strikeDodge.timer = 0.03f;
        strikeDodge.duration = 0.2f;
        strikeDodge.delay = 0.6f;

        optimizer.maxJumpDelay = 1f;
        optimizer.jumpDelayStep = 0.1f;

        for(int i = 0; i < 10; i++){
            optimizer.customGrader = new ValueNetworkGrader();
            optimizer.solvedGoodStrike = false;
            optimizer.debugMessages = true;
            optimizer.solveGoodStrike(simGameData, strikeDodge);
        }/*

        var start = System.currentTimeMillis();
        for(int i = 0; i < 2000; i++){
            optimizer.customGrader = new ValueNetworkGrader();
            optimizer.solvedGoodStrike = false;
            optimizer.debugMessages = false;
            optimizer.solveGoodStrike(simGameData, strikeDodge);
        }
        var end = System.currentTimeMillis();
        System.out.println("Took "+(end-start)+"ms, per="+((end-start) / 2000f));*/
    }

}
