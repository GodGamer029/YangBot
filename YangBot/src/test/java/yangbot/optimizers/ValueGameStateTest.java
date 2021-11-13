package yangbot.optimizers;

import org.junit.jupiter.api.Test;
import rlbot.gamestate.*;
import yangbot.input.GameData;
import yangbot.optimizers.model.ModelUtils;
import yangbot.util.math.vector.Vector3;
import yangbot.util.scenario.Scenario;
import yangbot.util.scenario.ScenarioLoader;

import java.awt.*;

public class ValueGameStateTest {

    @Test
    public void testValue2Cars(){
        Scenario s = new Scenario.Builder()
                .withTransitionDelay(0.1f)
                .withGameState(new GameState()
                        .withGameInfoState(new GameInfoState().withGameSpeed(1f))
                        .withCarState(0, new CarState())
                        .withBallState(new BallState().withPhysics(new PhysicsState().withLocation(new Vector3().toDesiredVector())))
                )
                .withInit(c -> {

                })
                .withRun((output, timer) -> {
                    final var gameData = GameData.current();
                    final var car = gameData.getCarData();
                    final var ball = gameData.getBallData();
                    final var renderer = gameData.getAdvancedRenderer();
                    float dt = gameData.getDt();

                    if(gameData.getAllCars().size() > 1){
                        float pred = ModelUtils.gameStateToPrediction(gameData, false, false);
                        float imp = ModelUtils.getImportanceOfCar(gameData.getAllCars().stream().filter(c -> c.playerIndex != car.playerIndex).findFirst().get(), ball);
                        renderer.drawString2d(String.format("%01.4f\n%01.4f", pred, imp), Color.WHITE, new Point(300, 300), 2, 2);
                    }

                    return timer > 90f ? Scenario.RunState.COMPLETE : Scenario.RunState.CONTINUE;
                })
                .withOnComplete((f) -> {

                })
                .build();

        ScenarioLoader.loadScenario(s);
        assert ScenarioLoader.get().waitToCompletion(4000);
    }

}
