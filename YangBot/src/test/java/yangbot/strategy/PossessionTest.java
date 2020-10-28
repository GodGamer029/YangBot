package yangbot.strategy;

import org.junit.jupiter.api.Test;
import rlbot.gamestate.BallState;
import rlbot.gamestate.GameInfoState;
import rlbot.gamestate.GameState;
import rlbot.gamestate.PhysicsState;
import yangbot.input.GameData;
import yangbot.util.PosessionUtil;
import yangbot.util.math.vector.Vector3;
import yangbot.util.scenario.Scenario;
import yangbot.util.scenario.ScenarioLoader;

import java.awt.*;

public class PossessionTest {

    @Test
    public void drawPlayerPosession() {
        Scenario s = new Scenario.Builder()
                .withTransitionDelay(0.05f)
                .withGameState(new GameState()
                        .withGameInfoState(new GameInfoState())
                        .withBallState(new BallState()
                                .withPhysics(new PhysicsState()
                                        .withLocation(new Vector3().toDesiredVector())
                                        .withVelocity(new Vector3().toDesiredVector())
                                )
                        ))
                .withRun((output, timer) -> {
                    final var gameData = GameData.current();
                    final var car = gameData.getCarData();
                    float dt = gameData.getDt();
                    var renderer = gameData.getAdvancedRenderer();

                    final var playerCar = gameData.getAllCars().stream().filter(c -> c.isBot == false).findFirst();
                    if (playerCar.isEmpty())
                        return Scenario.RunState.COMPLETE;

                    float time = PosessionUtil.timeToBall(playerCar.get(), gameData.getBallPrediction());
                    renderer.drawString2d(String.format("Time: %.2f", time), Color.WHITE, new Point(300, 300), 2, 2);

                    return gameData.getAllCars().size() <= 1 ? Scenario.RunState.COMPLETE : Scenario.RunState.CONTINUE;
                })
                .build();

        ScenarioLoader.loadScenario(s);
        ScenarioLoader.get().waitToCompletion(0);
    }

}
