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
        String encoded = "eWFuZ3YxOmMoYj05OC4wLHA9KDE2MjQuOTQwLDQzOTEuMDQwLDI3LjEyMCksdj0oLTEyNDEuNTcxLDYwMS43OTEsMzA4LjAyMSksYT0oLTAuMDA3LC0wLjAwMywtMC4zMjEpLG89KC0wLjAxNywyLjY3NSwwLjAwMCkpLGIocD0oMTIzMi42MTAsNDg2NS4xNzAsOTIuNDcwKSx2PSgzMTEuNjIxLC05MTIuNzExLDAuMDAwKSxhPSg1LjA2MiwyLjM2NSwtMi4xODcpKTs";
        var simGameData = GameData.current();
        ScenarioUtil.decodeApplyToGameData(simGameData, encoded);

        DodgeStrikeOptimizer optimizer = new DodgeStrikeOptimizer();
        optimizer.expectedBallHitTime = simGameData.getElapsedSeconds() + 0.2f;

        DodgeManeuver strikeDodge = new DodgeManeuver();
        strikeDodge.timer = 0.03f;
        strikeDodge.duration = 0.2f;
        strikeDodge.delay = 0.6f;

        YangBallPrediction ballPrediction = YangBotJNAInterop.getBallPrediction(simGameData.getBallData().makeMutable(), RLConstants.tickRate, 5);
        optimizer.maxJumpDelay = 0.6f;
        optimizer.jumpDelayStep = 0.1f;
        optimizer.customGrader = new ValueNetworkGrader();
        optimizer.solveGoodStrike(simGameData, strikeDodge);
        optimizer.solvedGoodStrike = false;
        optimizer.customGrader = new ValueNetworkGrader();
        ((ValueNetworkGrader)optimizer.customGrader).careAboutCars = false;
        optimizer.solveGoodStrike(simGameData, strikeDodge);
        optimizer.customGrader = new OffensiveGrader();
        optimizer.solvedGoodStrike = false;
        optimizer.solveGoodStrike(simGameData, strikeDodge);

        Scenario s = new Scenario.Builder()
                .withTransitionDelay(RLConstants.tickFrequency * 4)
                .withGameState(ScenarioUtil.decodeToGameState(encoded).withGameInfoState(new GameInfoState().withGameSpeed(0.2f)))
                .withInit(c -> {

                })
                .withRun((output, timer) -> {
                    final var gameData = GameData.current();
                    final var car = gameData.getCarData();
                    final var ball = gameData.getBallData();
                    final var renderer = gameData.getAdvancedRenderer();
                    float dt = gameData.getDt();
                    car.hitbox.draw(renderer, car.position, 1, Color.YELLOW);
                    ballPrediction.draw(renderer, Color.YELLOW, 5);

                    return timer > 1f ? Scenario.RunState.COMPLETE : Scenario.RunState.CONTINUE;
                })
                .withOnComplete((f) -> {

                })
                .build();
        //ScenarioLoader.loadScenario(s);
        //assert ScenarioLoader.get().waitToCompletion(4000);
    }

}
