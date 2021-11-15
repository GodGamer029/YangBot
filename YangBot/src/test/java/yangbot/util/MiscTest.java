package yangbot.util;

import org.junit.jupiter.api.Test;
import rlbot.gamestate.CarState;
import rlbot.gamestate.GameInfoState;
import rlbot.gamestate.GameState;
import yangbot.input.GameData;
import yangbot.util.scenario.Scenario;
import yangbot.util.scenario.ScenarioLoader;

import java.awt.*;

public class MiscTest {

    @Test
    public void printPos() {
        Scenario s = new Scenario.Builder()
                .withTransitionDelay(0.1f)
                .withGameState(new GameState()
                        .withGameInfoState(new GameInfoState().withGameSpeed(1f))
                        .withCarState(0, new CarState()))
                .withInit(c -> {

                })
                .withRun((output, timer) -> {
                    final var gameData = GameData.current();
                    final var carOpt = gameData.getAllCars().stream().filter(c -> c.playerIndex != gameData.getCarData().playerIndex).findFirst();
                    if(carOpt.isEmpty())
                        return Scenario.RunState.CONTINUE;
                    var car = carOpt.get();
                    final var ball = gameData.getBallData();
                    final var renderer = gameData.getAdvancedRenderer();
                    float dt = gameData.getDt();

                    renderer.drawString2d(car.position.y+"", Color.WHITE, new Point(400, 400), 2, 2);

                    return Scenario.RunState.CONTINUE;
                })
                .withOnComplete((f) -> {

                })
                .build();

        ScenarioLoader.loadScenario(s);
        assert ScenarioLoader.get().waitToCompletion(4000);
    }

}
