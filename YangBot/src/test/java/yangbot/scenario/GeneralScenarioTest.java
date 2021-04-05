package yangbot.scenario;

import org.junit.jupiter.api.Test;
import rlbot.gamestate.CarState;
import rlbot.gamestate.GameInfoState;
import rlbot.gamestate.GameState;
import rlbot.gamestate.PhysicsState;
import yangbot.input.GameData;
import yangbot.input.RLConstants;
import yangbot.util.math.vector.Vector3;
import yangbot.util.scenario.Scenario;
import yangbot.util.scenario.ScenarioLoader;
import yangbot.util.scenario.ScenarioUtil;

public class GeneralScenarioTest {

    @Test
    public void sampleScenario() {
        Scenario s = new Scenario.Builder()
                .withTransitionDelay(0.1f)
                .withGameState(new GameState()
                        .withGameInfoState(new GameInfoState().withGameSpeed(1f))
                        .withCarState(0, new CarState()))
                .withInit(c -> {

                })
                .withRun((output, timer) -> {
                    final var gameData = GameData.current();
                    final var car = gameData.getCarData();
                    float dt = gameData.getDt();

                    return timer > 1f ? Scenario.RunState.COMPLETE : Scenario.RunState.CONTINUE;
                })
                .withOnComplete((f) -> {

                })
                .build();

        ScenarioLoader.loadScenario(s);
        assert ScenarioLoader.get().waitToCompletion(4000);
    }

    @Test
    public void testGameStateEncoder() {
        Scenario s = new Scenario.Builder()
                .withTransitionDelay(0.1f)
                .withGameState(new GameState()
                        .withGameInfoState(new GameInfoState().withGameSpeed(1f))
                        .withCarState(0, new CarState()
                                .withBoostAmount(100f)
                                .withPhysics(new PhysicsState()
                                        .withVelocity(new Vector3().toDesiredVector())
                                        .withLocation(new Vector3(0, 0, RLConstants.carElevation + 1).toDesiredVector())
                                        .withRotation(new Vector3(1, 0, 0).toDesiredRotation())
                                        .withAngularVelocity(new Vector3().toDesiredVector())
                                )))
                .withRun((output, timer) -> {
                    final var gameData = GameData.current();
                    final var car = gameData.getCarData();
                    float dt = gameData.getDt();

                    if (timer >= 0.1f && timer < 1f) {
                        output.withBoost(true);
                        output.withThrottle(1);
                    }

                    return timer > 1f ? Scenario.RunState.COMPLETE : Scenario.RunState.CONTINUE;
                })
                .withOnComplete((f) -> {
                    System.out.println(ScenarioUtil.getEncodedGameState(GameData.current()));
                })
                .build();

        ScenarioLoader.loadScenario(s);
        assert ScenarioLoader.get().waitToCompletion(4000);
    }

    @Test
    public void testGameStateDecoder() {
        Scenario s = new Scenario.Builder()
                .withTransitionDelay(0.1f)
                .withGameState(ScenarioUtil.encodedGameStateToGameState("eWFuZ3YxOmMocD0oNjU1LjU0LC0yLjQ4LDUwMCksdj0oMTQwNy43OCwtNS4wNSwwLjI4KSxhPSgtMC4wMCwwLjAwLDAuMDApLG89KC0wLjAxLC0wLjAwLDAuMDApKSxiKHA9KDAuMDAsMC4wMCwxNTAwKSx2PSgwLjAwLDAuMDAsLTUwKSxhPSgwLjAwLDAuMDAsMC4wMCkpOw=="))
                .withRun((output, timer) -> {

                    return timer > 1f ? Scenario.RunState.COMPLETE : Scenario.RunState.CONTINUE;
                })
                .withOnComplete((f) -> {

                })
                .build();

        ScenarioLoader.loadScenario(s);
        assert ScenarioLoader.get().waitToCompletion(4000);
    }
}
