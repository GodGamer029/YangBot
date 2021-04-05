package yangbot.phystest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rlbot.gamestate.BallState;
import rlbot.gamestate.GameInfoState;
import rlbot.gamestate.GameState;
import rlbot.gamestate.PhysicsState;
import yangbot.cpp.YangBotCppInterop;
import yangbot.cpp.YangBotJNAInterop;
import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.input.RLConstants;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector3;
import yangbot.util.scenario.Scenario;
import yangbot.util.scenario.ScenarioLoader;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BallDataTest {

    @BeforeAll
    public static void setup() {
        YangBotCppInterop.init((byte) 0, (byte) 0);
    }

    @Test
    public void predictCollisionTest1() {
        BallData ballData = new BallData(new Vector3(0, -4594.94, 142.56999), new Vector3(0, 28.821f, 10.931f), new Vector3(0.27231002f, 0.0f, 0.0f));
        CarData carData = new CarData(new Vector3(-0.0, -4607.73, 16.97), new Vector3(-0.0, 0.311, -0.951), new Vector3(), Matrix3x3.lookAt(new Vector3(4.370481E-8, 0.99984944, -0.017352287), new Vector3(0, 0, 1)));

        ballData.stepWithCollide(0.008331299f, carData);

        assert ballData.collidesWith(carData) : "Ball should collide with car";
        assertEquals(ballData.position.x, 0, 0.1f);
        assertEquals(ballData.position.y, -4594.6997, 0.2f);
        assertEquals(ballData.position.z, 142.62f, 0.2f);

        assertEquals(ballData.velocity.x, 0, 0.1f);
        assertEquals(ballData.velocity.y, 32.650997, 0.2f);
        assertEquals(ballData.velocity.z, 25.210999, 1.2f);

        assertEquals(ballData.angularVelocity.x, 0.27231002, 0.01f);
        assertEquals(ballData.angularVelocity.y, 0, 0.01f);
        assertEquals(ballData.angularVelocity.z, 0, 0.01f);

        /*System.out.println("Pos: "+ballData.position);
        System.out.println("Vel: "+ballData.velocity);
        System.out.println("Ang: "+ballData.angularVelocity);*/

    }

    @Test
    public void predictCollisionTest2() {
        BallData ballData = new BallData(new Vector3(-0.0, -4595.39, 142.5), new Vector3(-0.0, 25.860998, -12.501), new Vector3(0.25031, 0.0, 0.0));
        CarData carData = new CarData(new Vector3(-0.0, -4607.73, 16.99), new Vector3(-0.0, -0.071, 0.86099994), new Vector3(-0.00531, 0.0, 0.0), Matrix3x3.lookAt(new Vector3(4.370481E-8, 0.99984944, -0.017352287), new Vector3(7.584926E-10, 0.017352287, 0.99984944)));

        ballData.stepWithCollide(0.008331299f, carData);

        assert ballData.collidesWith(carData) : "Ball should collide with car";
        /*assertEquals(ballData.position.x, 0, 0.1f);
        assertEquals(ballData.position.y, -4594.6997, 0.2f);
        assertEquals(ballData.position.z, 142.62f, 0.2f);

        assertEquals(ballData.velocity.x, 0, 0.1f);
        assertEquals(ballData.velocity.y, 32.650997, 0.2f);
        assertEquals(ballData.velocity.z, 25.210999, 0.2f);

        assertEquals(ballData.angularVelocity.x, 0.27231002, 0.01f);
        assertEquals(ballData.angularVelocity.y, 0, 0.01f);
        assertEquals(ballData.angularVelocity.z, 0, 0.01f);*/

        /*System.out.println("Pos: "+ballData.position);
        System.out.println("Vel: "+ballData.velocity);
        System.out.println("Ang: "+ballData.angularVelocity);
*/
    }

    @Test
    public void predictCollisionTest3() {
        BallData ballData = new BallData(new Vector3(-0.0, -4607.4297, 148), new Vector3(-0.0, 1.451, -102.631), new Vector3(0.02321, 0.0, 0.0));
        CarData carData = new CarData(new Vector3(-0.0, -4607.84, 17.01), new Vector3(-0.0, 0.0, 0.211), new Vector3(5.1E-4, 0.0, 0.0),
                Matrix3x3.lookAt(new Vector3(4.370938E-8, 0.99995404, -0.009587233), new Vector3(4.1907128E-10, 0.009587233, 0.99995404)));

        float dt = 0.008333206f;

        //assert ballData.collidesWith(carData) : "Ball should collide with car";
        ballData.stepWithCollide(dt, carData);

        System.out.println("Java collide:");
        System.out.println("Pos: " + ballData.position);
        System.out.println("Vel: " + ballData.velocity);
        System.out.println("Ang: " + ballData.angularVelocity);
    }

    @Test
    public void predictCollisionTest4() {
        BallData ballData = new BallData(new Vector3(-0.0, -4607.4297, 148), new Vector3(-0.0, 1.451, -102.631), new Vector3(0.02321, 0.0, 0.0));
        CarData carData = new CarData(new Vector3(-0.0, -4607.84, 17.01), new Vector3(-0.0, 0.0, 0.211), new Vector3(5.1E-4, 0.0, 0.0),
                Matrix3x3.lookAt(new Vector3(4.370938E-8, 0.99995404, -0.009587233), new Vector3(4.1907128E-10, 0.009587233, 0.99995404)));

        float dt = 0.008333206f;

        //assert ballData.collidesWith(carData) : "Ball should collide with car";
        ballData.stepWithCollideChip(dt, carData);

        System.out.println("Chip collide:");
        System.out.println("Pos: " + ballData.position);
        System.out.println("Vel: " + ballData.velocity);
        System.out.println("Ang: " + ballData.angularVelocity);
    }

    private boolean terminate = false;
    private float myTimer = 0;
    private float lastPredVel = 0;
    private float lastPredAng = 0;
    private float lastvel = 0;
    private float lastPos = 0;
    private boolean printNext = false;
    private float startTime = 0;
    private float predNextVel = -1;
    private float lastVel = -1;

    @Test
    public void predictBallRolling() {
        BallData ballData = new BallData(
                new Vector3(2500, -3000, BallData.COLLISION_RADIUS * 1.01f + 1000),
                new Vector3(6000, 0, 0),
                new Vector3(0, 0, 0));
        Scenario s = new Scenario.Builder()
                .withTransitionDelay(0.05f)
                .withGameState(new GameState()
                        .withGameInfoState(new GameInfoState().withGameSpeed(0.1f).withWorldGravityZ(RLConstants.gravity.z))
                        .withBallState(new BallState().withPhysics(new PhysicsState()
                                .withLocation(ballData.position.toDesiredVector())
                                .withVelocity(ballData.velocity.toDesiredVector())
                                .withAngularVelocity(ballData.angularVelocity.toDesiredVector())
                        )))
                .withInit((c) -> {
                    startTime = GameData.current().getCarData().elapsedSeconds;
                })
                .withRun((output, timer) -> {
                    final var gameData = GameData.current();
                    final var car = gameData.getCarData();
                    float dt = gameData.getDt();
                    myTimer += dt;
                    var ballPred = YangBotJNAInterop.getBallPrediction(gameData.getBallData().makeMutable(), 120);
                    //var gucciPred = gameData.getBallPrediction();
                    //var ballPred = gameData.getBallData().makeMutable().makeBallPrediction(RLConstants.tickFrequency, 4f);

                    var renderer = gameData.getAdvancedRenderer();
                    renderer.drawCentered3dCube(Color.WHITE, gameData.getBallData().position, BallData.COLLISION_RADIUS * 2);

                    //gucciPred.draw(renderer, Color.GREEN, 3);
                    ballPred.draw(renderer, Color.YELLOW, 3);
                    var ball = gameData.getBallData();

                    if (printNext || ball.position.z < BallData.COLLISION_RADIUS) {
                        //if(printNext)
                        //System.out.println((ball.position.z - ball.velocity.z * RLConstants.tickFrequency) + " " + printNext);
                        //else
                        //System.out.println(ball.position.z + " ####");
                        printNext = false;
                    }
                    if (ball.position.z < BallData.COLLISION_RADIUS) {
                        printNext = true;
                    }
                    //System.out.println(String.format("%9.4f %9.4f", ball.velocity.z, ball.position.z));

                    renderer.drawString2d(String.format("bpy=%7.1f bpz=%.6f bpx=%.5f", ball.position.y, ball.position.z, ball.position.x), Color.WHITE, new Point(50, 200), 1, 1);
                    renderer.drawString2d(String.format("bv=%.2f pred=%.2f diff=%7.4f", ball.velocity.y, lastPredVel, lastPredVel - ball.velocity.y), Color.WHITE, new Point(50, 220), 1, 1);
                    renderer.drawString2d(String.format("ba=%.2f pred=%.2f diff=%7.4f", ball.angularVelocity.dot(new Vector3(1, 0, 0)), lastPredAng, lastPredAng - ball.angularVelocity.dot(new Vector3(1, 0, 0))), Color.WHITE, new Point(50, 240), 1, 1);

                    //float targetTime = 3.1f - myTimer;

                    for (float f = 0.5f; f < 4.5f; f += 0.5f) {
                        float t = startTime + f;
                        float r = t - car.elapsedSeconds;
                        if (r < 0)
                            continue; // done

                        var frame = ballPred.getFrameAtAbsoluteTime(t);
                        if (frame.isPresent()) {
                            var framesBefore = ballPred.getFramesBeforeRelative(frame.get().relativeTime);
                            if (framesBefore.size() == 0)
                                continue;
                            var frameBefore = framesBefore.get(framesBefore.size() - 1);
                            var interpPos = MathUtils.lerp(frameBefore.ballData.position, frame.get().ballData.position, MathUtils.remapClip(r, frameBefore.relativeTime, frame.get().relativeTime, 0, 1));
                            renderer.drawCentered3dCube(Color.YELLOW, interpPos, BallData.COLLISION_RADIUS * 2);
                        }
                        /*var gucciFrame = gucciPred.getFrameAtAbsoluteTime(t);
                        if(gucciFrame.isPresent()){
                            var framesBefore = gucciPred.getFramesBeforeRelative(gucciFrame.get().relativeTime);
                            if(framesBefore.size() != 0){
                                var frameBefore = framesBefore.get(framesBefore.size() - 1);
                                var interpPos = MathUtils.lerp(frameBefore.ballData.position, gucciFrame.get().ballData.position, MathUtils.remapClip(r, frameBefore.relativeTime, gucciFrame.get().relativeTime, 0, 1));
                                renderer.drawCentered3dCube(Color.GREEN, gucciFrame.get().ballData.position, BallData.COLLISION_RADIUS * 2);
                            }
                        }*/
                    }

                    BallData simBall = gameData.getBallData().makeMutable();

                    float zPre = simBall.velocity.y;
                    float xangPre = simBall.angularVelocity.dot(new Vector3(1, 0, 0));
                    simBall.stepCollideGround(RLConstants.tickFrequency * 1);
                    if (ball.position.x > 3900)
                        System.out.printf("x=" + ball.position.x + " v=" + ball.velocity.x + " pred=" + (lastPos + ball.velocity.x * RLConstants.tickFrequency) + " diff=%.3f \n", ((lastPos + ball.velocity.x * RLConstants.tickFrequency) - ball.position.x));

                    //System.out.println(String.format("Diff %7.2f %7.2f", zPre - simBall.velocity.y, xangPre - simBall.angularVelocity.dot(new Vector3(1, 0, 0))));
                    lastPredVel = simBall.velocity.y;
                    lastvel = gameData.getBallData().velocity.x;
                    lastPos = gameData.getBallData().position.x;

                    lastPredAng = simBall.angularVelocity.dot(new Vector3(1, 0, 0));
                    if (terminate)
                        return Scenario.RunState.COMPLETE;

                    return timer > 0.7f ? Scenario.RunState.RESET : Scenario.RunState.CONTINUE;
                })
                .withOnComplete((f) -> {

                })
                .withOnRunComplete((timer, invoc) -> {
                    System.out.println(invoc);
                    myTimer = 0;
                    if (invoc >= 0)
                        terminate = true;
                })
                .build();

        ScenarioLoader.loadScenario(s);
        assert ScenarioLoader.get().waitToCompletion(4000);
    }

    @Test
    public void ballRecorder() {
        BallData ballData = new BallData(
                new Vector3(-RLConstants.arenaHalfWidth * 0.75f, -RLConstants.arenaHalfLength * 0.8f, BallData.COLLISION_RADIUS * 1.01f),
                new Vector3(350, 450, 0),
                new Vector3(0, 0, 0));

        Scenario s = new Scenario.Builder()
                .withTransitionDelay(0.7f)
                .withGameState(new GameState()
                        .withGameInfoState(new GameInfoState().withGameSpeed(0.5f).withWorldGravityZ(-650f))
                        .withBallState(new BallState().withPhysics(new PhysicsState()
                                .withLocation(ballData.position.toDesiredVector())
                                .withVelocity(ballData.velocity.toDesiredVector())
                                .withAngularVelocity(ballData.angularVelocity.toDesiredVector())
                        )))
                .withRun((output, timer) -> {
                    final var gameData = GameData.current();
                    var ball = gameData.getBallData();
                    var rend = gameData.getAdvancedRenderer();

                    rend.drawString2d(String.format("t=%.2f v=%.2f dt=%.4f", timer, ball.velocity.magnitude(), gameData.getDt()), Color.WHITE, new Point(50, 200), 2, 2);

                    System.out.println(timer + "\t" + lastVel + "\t" + (ball.velocity.magnitude() - predNextVel) + "\t" + ball.angularVelocity.magnitude());

                    //if(ball.position.magnitude() > RLConstants.arenaHalfWidth * 0.8f)
                    //    RLBotDll.setGameState(new GameState().withBallState(new BallState().withPhysics(new PhysicsState().withLocation(new Vector3(0, 0, ballData.position.z).toDesiredVector()))).buildPacket());

                    var sBa = ball.makeMutable();
                    sBa.stepCollideGround(RLConstants.tickFrequency);
                    predNextVel = (float) sBa.velocity.magnitude();
                    lastVel = (float) ball.velocity.magnitude();

                    if (terminate)
                        return Scenario.RunState.COMPLETE;

                    return (timer > 40f || ball.velocity.magnitude() < 30) ? Scenario.RunState.COMPLETE : Scenario.RunState.CONTINUE;
                })
                .withOnComplete((f) -> {

                })
                .withOnRunComplete((timer, invoc) -> {
                    System.out.println(invoc);
                    myTimer = 0;
                    if (invoc >= 0)
                        terminate = true;
                })
                .build();

        ScenarioLoader.loadScenario(s);
        assert ScenarioLoader.get().waitToCompletion(4000);
    }

}