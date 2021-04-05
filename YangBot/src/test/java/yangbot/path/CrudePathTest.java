package yangbot.path;

import org.junit.jupiter.api.Test;
import rlbot.cppinterop.RLBotDll;
import rlbot.gamestate.CarState;
import rlbot.gamestate.GameInfoState;
import rlbot.gamestate.GameState;
import rlbot.gamestate.PhysicsState;
import yangbot.MainClass;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.input.RLConstants;
import yangbot.path.builders.SegmentedPath;
import yangbot.path.graphbased.CrudeGraphMethod;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector3;
import yangbot.util.scenario.Scenario;
import yangbot.util.scenario.ScenarioLoader;

public class CrudePathTest {

    private SegmentedPath path = null;
    private CrudeGraphMethod crude;
    private boolean initiatePath = false;
    private float endTime = 40;

    @Test
    public void testCrude() {
        MainClass.loadLut();

        Scenario s = new Scenario.Builder()
                .withTransitionDelay(0.1f)
                .withGameState(new GameState()
                        .withGameInfoState(new GameInfoState().withGameSpeed(1f))
                        .withCarState(0, new CarState()))
                .withInit(c -> {
                    Thread t = new Thread(() -> {
                        crude = new CrudeGraphMethod(new Vector3(2000, 3000, RLConstants.carElevation), new Vector3(0, -1, 0));

                        CarData simCar = new CarData(
                                new Vector3(0, 0, RLConstants.carElevation),
                                new Vector3(0, -2000, 0),
                                new Vector3(),
                                Matrix3x3.lookAt(new Vector3(0, -1, 0))
                        );
                        simCar.boost = 100;
                        long ms = System.currentTimeMillis();
                        var pathFound = crude.findPath(simCar);
                        System.out.println("Found path: " + pathFound.isPresent() + " in " + (System.currentTimeMillis() - ms) + "ms");
                        if (pathFound.isEmpty())
                            endTime = 0;
                        assert pathFound.isPresent() : "No path found!!!";
                        RLBotDll.setGameState(new GameState().withGameInfoState(new GameInfoState().withGameSpeed(0.5f)).withCarState(0, new CarState()
                                .withPhysics(new PhysicsState()
                                        .withLocation(simCar.position.add(simCar.velocity.mul(-0.1f)).toDesiredVector())
                                        .withVelocity(simCar.velocity.toDesiredVector())
                                        .withRotation(simCar.orientation.toEuler().toDesiredRotation())
                                        .withAngularVelocity(simCar.angularVelocity.toDesiredVector())
                                ).withBoostAmount(simCar.boost)
                        ).buildPacket());
                        initiatePath = true;
                        path = new SegmentedPath(pathFound.get());

                    });
                    t.setName("PathFinder");
                    t.start();
                })
                .withRun((output, timer) -> {
                    final var gameData = GameData.current();
                    final var car = gameData.getCarData();
                    float dt = gameData.getDt();
                    var rend = gameData.getAdvancedRenderer();

                    if (path != null) {
                        path.draw(rend);
                        endTime = Math.min(endTime, timer + 8);

                        if (initiatePath) {
                            if (car.position.withZ(0).magnitude() < 30 && car.velocity.magnitude() > 1500)
                                initiatePath = false;
                        }
                        if (!initiatePath) {
                            if (!path.isDone()) {
                                var isDone = path.step(dt, output);
                                if (isDone)
                                    RLBotDll.setGameState(new GameState().withGameInfoState(new GameInfoState().withGameSpeed(1f)).buildPacket());
                            }
                        }
                    } else if (crude != null && crude.wipPath != null) {
                        var buffer = crude.wipPath;
                        var pat = new SegmentedPath(buffer);
                        pat.draw(rend);
                    }

                    return (timer > endTime) ? Scenario.RunState.COMPLETE : Scenario.RunState.CONTINUE;
                })
                .withOnComplete((f) -> {

                })
                .build();

        ScenarioLoader.loadScenario(s);
        assert ScenarioLoader.get().waitToCompletion(4000);
    }

    @Test
    public void performanceTest() {
        MainClass.loadLut();

        for (int i = 0; i < 30; i++) {
            var crude = new CrudeGraphMethod(new Vector3(2000, 3000, RLConstants.carElevation), new Vector3(0, -1, 0));

            CarData simCar = new CarData(
                    new Vector3(0, 0, RLConstants.carElevation),
                    new Vector3(0, -2000, 0),
                    new Vector3(),
                    Matrix3x3.lookAt(new Vector3(0, -1, 0))
            );
            simCar.boost = 100;

            long ms = System.currentTimeMillis();
            var pathFound = crude.findPath(simCar);

            System.out.println("Found path: " + pathFound.isPresent() + " in " + (System.currentTimeMillis() - ms) + "ms");
            assert pathFound.isPresent() : "No path found!!!";

            System.gc();
        }
    }
}
