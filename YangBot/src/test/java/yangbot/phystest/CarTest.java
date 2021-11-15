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
import yangbot.util.CsvLogger;
import yangbot.util.Range;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;
import yangbot.util.scenario.Scenario;
import yangbot.util.scenario.ScenarioLoader;

import java.awt.*;
import java.util.Optional;

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

    @Test
    public void turnTimeBySpeed() {
        var speedIter = Range.of(500, 2300).stepBy(500);
        var angleIter = Range.of(0, 170).stepBy(15);
        angleIter.next();
        var logger = new CsvLogger(new String[]{"t", "accel", "pred"});

        Scenario s = new Scenario.Builder()
                .withTransitionDelay(0.05f)
                .withDynamicGameState(invoc -> {
                    if (!speedIter.hasNext()) {
                        speedIter.reset();
                        if (!angleIter.hasNext())
                            return Optional.empty();
                        angleIter.next();
                    }
                    var speedVal = speedIter.next();
                    System.out.println("angle: " + angleIter.current() + " speed: " + speedVal);

                    return Optional.of(new GameState()
                            .withCarState(0, new CarState()
                                    .withBoostAmount(100f)
                                    .withPhysics(new PhysicsState()
                                            .withVelocity(new Vector3(0, speedVal, 0).toDesiredVector())
                                            .withLocation(new Vector3(0, -2000, RLConstants.carElevation + 1).toDesiredVector())
                                            .withRotation(Matrix3x3.lookAt(new Vector3(0, 1, 0)).toEuler().toDesiredRotation())
                                            .withAngularVelocity(new Vector3().toDesiredVector())
                                    )));
                })
                .withRun((output, timer) -> {
                    final var gameData = GameData.current();
                    final var car = gameData.getCarData();
                    float dt = gameData.getDt();

                    var targetTangent = new Vector3(new Vector2(0, 1).rotateBy(-angleIter.current() * (Math.PI / 180)), 0);
                    var align = car.forward().dot(targetTangent);
                    if (align > 0.5f)
                        DriveManeuver.steerController(output, car, car.position.add(targetTangent));
                    else
                        output.withSteer(-1);
                    DriveManeuver.speedController(dt, output, car.forwardSpeed(), 900, 1100, 0.04f, false);

                    return (align > 0.99f || timer > 8) ? Scenario.RunState.RESET : Scenario.RunState.CONTINUE;
                })
                .withOnRunComplete((timer, numInvoc) -> {
                    System.out.println("Run complete at " + timer);
                    logger.log(new float[]{angleIter.current(), timer, speedIter.current()});
                })
                .build();

        ScenarioLoader.loadScenario(s);
        ScenarioLoader.get().waitToCompletion(0);
        logger.save("turntime.txt");
    }

    private float lastVel = 0;

    @Test
    public void turnTests() {
        var speedIter = Range.of(300, 400).stepWith(2);
        var steerIter = Range.of(0.7f, 1).stepWith(5);
        steerIter.next();
        var logger = new CsvLogger(new String[]{"ang", "speed", "accel","time"});

        Scenario s = new Scenario.Builder()
                .withTransitionDelay(0.05f)
                .withDynamicGameState(invoc -> {
                    if (!speedIter.hasNext()) {
                        speedIter.reset();
                        if (!steerIter.hasNext())
                            return Optional.empty();
                        steerIter.next();
                    }
                    var speedVal = speedIter.next();
                    System.out.println("steerIter: " + steerIter.current() + " speed: " + speedVal);
                    lastVel = 0;
                    return Optional.of(new GameState()
                                    .withGameInfoState(new GameInfoState().withGameSpeed(0.1f))
                            .withCarState(0, new CarState()
                                    .withBoostAmount(100f)
                                    .withPhysics(new PhysicsState()
                                            .withVelocity(new Vector3(0, speedVal, 0).toDesiredVector())
                                            .withLocation(new Vector3(2000, -3000, RLConstants.carElevation + 1).toDesiredVector())
                                            .withRotation(Matrix3x3.lookAt(new Vector3(0, 1, 0)).toEuler().toDesiredRotation())
                                            .withAngularVelocity(new Vector3().toDesiredVector())
                                    )));
                })
                .withRun((output, timer) -> {
                    final var gameData = GameData.current();
                    final var car = gameData.getCarData();
                    float dt = gameData.getDt();
                    var r = gameData.getAdvancedRenderer();

                    output.withThrottle(0.015f);

                    if(timer > 0.1f && lastVel != 0){
                        if(timer > 0.3f && timer < 1)
                            output.withSteer(1);
                        float throttleAccel = DriveManeuver.throttleAcceleration(car.forwardSpeed()) * output.getThrottle();
                        float accel = ((float)car.velocity.magnitude() - lastVel) / dt;
                        accel -= throttleAccel;

                        logger.log(new float[]{car.angularVelocity.dot(car.up()), (float)car.velocity.magnitude(), accel, timer });
                        r.drawString2d(String.format("V: %03.1f\nAccel: %02.2f\nSteer: %.1f", car.velocity.magnitude(), accel, output.getSteer()), Color.WHITE, new Point(220, 300), 2, 2);
                    }

                    lastVel = (float)car.velocity.magnitude();

                    return (timer > 1.4) ? Scenario.RunState.RESET : Scenario.RunState.CONTINUE;
                })
                .withOnRunComplete((timer, numInvoc) -> {
                    System.out.println("Run complete at " + timer);
                })
                .build();

        ScenarioLoader.loadScenario(s);
        ScenarioLoader.get().waitToCompletion(0);
        System.out.println("Data saved");
        logger.save("..\\data\\turntime.csv");
    }

    private float nextThrottleAccel = -1;

    @Test
    public void straightSteerTransitionTest() {
        var logger = new CsvLogger(new String[]{"ang", "speed", "accel","time","riserate", "maxcurve", "steer"});
        //var speedIter = Range.of(500, 2000).stepWith(2);
        //var throttleIter = Range.of(0, 1).stepWith(2);
        var steerIter = Range.of(0.8f, 1).stepBy(0.2f);

        Scenario s = new Scenario.Builder()
                .withTransitionDelay(0.05f)
                .withDynamicGameState(invoc -> {
                    if (!steerIter.hasNext()) {
                        steerIter.reset();
                        //if (!throttleIter.hasNext())
                            return Optional.empty();
                        //throttleIter.next();
                    }
                    steerIter.next();
                    steerIter.next();
                    //var speedVal = speedIter.next();
                    var speedVal = 1000;
                    this.lastVel = 0;
                    this.nextThrottleAccel = -1;
                    return Optional.of(new GameState()
                            .withGameInfoState(new GameInfoState().withGameSpeed(1f))
                            .withCarState(0, new CarState()
                                    .withBoostAmount(100f)
                                    .withPhysics(new PhysicsState()
                                            .withVelocity(new Vector3(0, speedVal, 0).toDesiredVector())
                                            .withLocation(new Vector3(3200, -2500, RLConstants.carElevation + 1).toDesiredVector())
                                            .withRotation(Matrix3x3.lookAt(new Vector3(0, 1, 0)).toEuler().toDesiredRotation())
                                            .withAngularVelocity(new Vector3().toDesiredVector())
                                    )));
                })
                .withRun((output, timer) -> {
                    final var gameData = GameData.current();
                    final var car = gameData.getCarData();
                    float dt = gameData.getDt();
                    var r = gameData.getAdvancedRenderer();

                    //if(car.forwardSpeed() > 600)
                    /*var tr = throttleIter.current();
                    if(tr == 1)
                        output.withThrottle(0.015f);
                    else if(tr == 2)
                        output.withThrottle(1);

                     */
                    output.withSteer(steerIter.current());
                    if(timer > 1.5f && lastVel != 0){
                        float vF = car.forwardSpeed();
                        float vT = car.velocity.magnitudeF();
                        float throttleAccel = DriveManeuver.throttleAcceleration(vF) * output.getThrottle();
                        if(output.getThrottle() == 0)
                            throttleAccel = DriveManeuver.coasting_acceleration;
                        else if(Math.signum(output.getThrottle()) != Math.signum(car.forwardSpeed()))
                            throttleAccel = DriveManeuver.brake_acceleration;
                        float accel = (vF - lastVel) / dt;
                        accel -= nextThrottleAccel;
                        accel -= car.slowdownForceFromSteering(output.getSteer());
                        if(timer > 0f && vF > 150 && accel < 200 && nextThrottleAccel != -1 /*&& (vT < 1400 || vT > 1410)*/)
                            logger.log(new float[]{car.angularVelocity.dot(car.up()), vF, accel, timer, throttleAccel, DriveManeuver.maxTurningCurvature(vF), output.getSteer()});

                        nextThrottleAccel = throttleAccel;

                        r.drawString2d(String.format("V: %03.1f\nAccel: %02.2f\nSteer: %.1f", vF, accel, output.getSteer()), Color.WHITE, new Point(220, 300), 2, 2);
                    }else if(timer < 1.3f)
                        output.withBoost();

                    lastVel = car.forwardSpeed();
                    if(RLConstants.isPosNearWall(car.position.flatten(), 200))
                        return Scenario.RunState.RESET;
                    return (timer > 5 || lastVel < 150) ? Scenario.RunState.RESET : Scenario.RunState.CONTINUE;
                })
                .withOnRunComplete((timer, numInvoc) -> {
                    System.out.println("Run complete at " + timer);
                    System.out.println("End speed: "+GameData.current().getCarData().velocity.magnitude());
                    System.out.println("End Angle: "+Math.abs((180 / Math.PI) * GameData.current().getCarData().forward().flatten().angleBetween(new Vector2(0, 1))));
                    System.out.println("######");
                })
                .build();

        ScenarioLoader.loadScenario(s);
        ScenarioLoader.get().waitToCompletion(0);
        System.out.println("Data saved");
        logger.save("..\\data\\turntime.csv");
    }

}
