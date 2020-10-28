package yangbot.phystest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import rlbot.gamestate.CarState;
import rlbot.gamestate.GameInfoState;
import rlbot.gamestate.GameState;
import rlbot.gamestate.PhysicsState;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.input.RLConstants;
import yangbot.strategy.manuever.AerialManeuver;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.util.math.vector.Vector3;
import yangbot.util.scenario.Scenario;
import yangbot.util.scenario.ScenarioLoader;

public class CarTest {
    @Test
    public void jumpAirTimeTest() {
        Assertions.assertEquals(0.025f, CarData.getJumpHoldDurationForTotalAirTime(1, 650));
        Assertions.assertEquals(0.2f, CarData.getJumpHoldDurationForTotalAirTime(2, 650));
    }

    private double oldCarV = 0;
    private boolean didBoost = false;
    private float totalCarVel = 0;
    private int carVelNum = 0;

    @RepeatedTest(3)
    public void boostTimeTest() {
        this.oldCarV = 0;
        this.didBoost = false;
        Scenario s = new Scenario.Builder()
                .withTransitionDelay(0.05f)
                .withGameState(new GameState()
                        .withGameInfoState(new GameInfoState().withGameSpeed(0.5f).withWorldGravityZ(-1f))
                        .withCarState(0, new CarState()
                                .withBoostAmount(100f)
                                .withPhysics(new PhysicsState()
                                        .withVelocity(new Vector3().toDesiredVector())
                                        .withLocation(new Vector3(0, 0, 100).toDesiredVector())
                                        .withRotation(new Vector3().toDesiredRotation())
                                        .withAngularVelocity(new Vector3().toDesiredVector())
                                )))
                .withRun((output, timer) -> {
                    final var gameData = GameData.current();
                    final var car = gameData.getCarData();
                    float dt = gameData.getDt();

                    if (timer >= 0.1f && timer < 0.25f) {
                        didBoost = true;
                        output.withBoost(true);
                        //output.withThrottle(1);
                    }

                    double carV = car.velocity.magnitude();
                    boolean isDecreasing = timer > 0.5f;

                    float throttleAccel;//= DriveManeuver.throttleAcceleration((carV - oldCarV) / 2);
                    throttleAccel = AerialManeuver.throttle_acceleration;
                    float totalAccel = 0;

                    System.out.printf("t=%.3f accel=%7.2f " +/*expected=%6.1f throttle=%6.1f*/"p=%4.1f v=%5.1f tickrate=%5.1f dt=%.5f car.boost=%5.1f isBoosting=%s %n",
                            timer,
                            (carV - oldCarV) / dt,
                            //totalAccel,
                            // throttleAccel,
                            car.position.x,
                            car.velocity.x,
                            1f / dt, dt, car.boost, output.holdBoost() + "");
                    if (isDecreasing) {
                        System.out.println("We're done here t=" + timer + " oldV=" + oldCarV + " now=" + carV);
                    }
                    oldCarV = carV;

                    return isDecreasing ? Scenario.RunState.COMPLETE : Scenario.RunState.CONTINUE;
                })
                .build();

        ScenarioLoader.loadScenario(s);
        assert ScenarioLoader.get().waitToCompletion(3000);
    }

    @Test
    public void boostVelocityTest() {
        this.oldCarV = 0;
        Scenario s = new Scenario.Builder()
                .withTransitionDelay(0.3f)
                .withGameState(new GameState()
                        .withGameInfoState(new GameInfoState().withGameSpeed(1f))
                        .withCarState(0, new CarState()
                                .withBoostAmount(100f)
                                .withPhysics(new PhysicsState()
                                        .withVelocity(new Vector3().toDesiredVector())
                                        .withLocation(new Vector3(0, 0, RLConstants.carElevation + 1).toDesiredVector())
                                        .withRotation(new Vector3().toDesiredRotation())
                                        .withAngularVelocity(new Vector3().toDesiredVector())
                                )))
                .withRun((output, timer) -> {
                    final var gameData = GameData.current();
                    final var car = gameData.getCarData();
                    float dt = gameData.getDt();

                    if (timer >= 0.1f && timer < 2f) {
                        output.withBoost(true);
                        output.withThrottle(1);
                    }

                    double carV = car.velocity.magnitude();

                    float throttleAccel = DriveManeuver.throttleAcceleration((float) oldCarV);

                    if ((carV - oldCarV) / dt - throttleAccel > 900 && timer > 0.1f) {
                        totalCarVel += (carV - oldCarV) / dt - throttleAccel;
                        carVelNum++;
                    }

                    System.out.printf("t=%.3f accel=%7.2f throttle=%6.1f " +/*expected=%6.1f */"p=%4.1f v=%5.1f car.boost=%5.1f isBoosting=%s %n",
                            timer,
                            (carV - oldCarV) / dt - throttleAccel,
                            throttleAccel,
                            car.position.x,
                            car.velocity.x,
                            car.boost, output.holdBoost() + "");

                    oldCarV = carV;

                    return timer > 2f ? Scenario.RunState.COMPLETE : Scenario.RunState.CONTINUE;
                })
                .withOnComplete((f) -> {
                    System.out.println("Average accel: " + (totalCarVel / carVelNum) + " ");
                })
                .build();

        ScenarioLoader.loadScenario(s);
        assert ScenarioLoader.get().waitToCompletion(4000);
    }
}
