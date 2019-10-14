package yangbot;

import rlbot.Bot;
import rlbot.ControllerState;
import rlbot.cppinterop.RLBotDll;
import rlbot.flat.GameTickPacket;
import rlbot.gamestate.*;
import yangbot.cpp.YangBotCppInterop;
import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.DataPacket;
import yangbot.input.GameData;
import yangbot.input.fieldinfo.BoostManager;
import yangbot.util.AdvancedRenderer;
import yangbot.util.ControlsOutput;
import yangbot.vector.Matrix3x3;
import yangbot.vector.Vector3;

import java.awt.*;
import java.util.Random;

public class TestBot implements Bot {

    private final int playerIndex;
    /**
     * This is the most important function. It will automatically get called by the framework with fresh data
     * every frame. Respond with appropriate controls!
     */
    private State state = State.RESET;
    private float timer = -1.0f;
    private float lastTick = -1;
    private final float desiredGravity = -0.1f;
    private boolean hasSetPriority = false;
    private float lastGameTime = -1;
    private Vector3 lastPos = new Vector3();
    private Vector3 lastAng = new Vector3();
    private int timesTested = 0;
    private Matrix3x3 actionTarget = null;
    private float timeNeeded = 0;
    private int counter = 0;

    public TestBot(int playerIndex) {
        this.playerIndex = playerIndex;
    }

    private ControlsOutput processInput(DataPacket input) {
        float dt = Math.max(input.gameInfo.secondsElapsed() - lastTick, 0f);

        if (lastTick > 0)
            timer += Math.min(dt, 0.5f);

        CarData car = input.car;
        BallData ball = input.ball;
        GameData.current().update(input.car, input.ball, input.allCars, input.gameInfo, dt);

        CarData controlCar = input.allCars.stream().filter((c) -> c.team != car.team).findFirst().orElse(car);

        drawDebugLines(input, controlCar);
        AdvancedRenderer renderer = AdvancedRenderer.forBotLoop(this);
        ControlsOutput output = new ControlsOutput();

        switch (state) {
            case RESET: {
                timer = 0.0f;

                state = State.RUN;

                Random rng = new Random(timesTested);
                actionTarget = Matrix3x3.axisToRotation(new Vector3(rng.nextFloat() * 4 - 2, rng.nextFloat() * 4 - 2, rng.nextFloat() * 4 - 2));
                Vector3 f = actionTarget.forward();

                GameState st = new GameState()
                        .withGameInfoState(new GameInfoState().withWorldGravityZ(desiredGravity))
                        .withCarState(this.playerIndex, new CarState()
                                .withBoostAmount(100f)
                                .withPhysics(new PhysicsState()
                                                .withLocation(new DesiredVector3(0f, 0f, 1000f))
                                                .withAngularVelocity(new DesiredVector3(0f, 0f, 0f))
                                                .withVelocity(new DesiredVector3(0f, 0f, 0f))
                                        //.withRotation(new DesiredRotation(0f, (float)0, 0f))
                                )
                        )
                        .withBallState(new BallState().withPhysics(new PhysicsState()
                                .withLocation(new DesiredVector3(500f * f.x, 500f * f.y, 500f * f.z + 1000))
                                .withAngularVelocity(new DesiredVector3(0f, 0f, 0f))
                                .withVelocity(new DesiredVector3(0f, -0f, 0f))
                                .withRotation(new DesiredRotation(0f, 0f, 0f))
                        ));

                RLBotDll.setGameState(st.buildPacket());
                break;
            }
            case INIT: {

                if (timer >= 2.5f) {

                    state = State.RUN;
                    timer = 0;
                }
                break;
            }
            case RUN: {
                if (timer > 2f || (car.orientationMatrix.angle(actionTarget) < 0.01f && car.angularVelocity.magnitude() < 0.15f)) {
                    timesTested++;
                    timeNeeded += timer;
                    counter++;
                    state = State.RESET;
                } else {
                    float[] outputF = YangBotCppInterop.aerialML(car.orientationMatrix.toEuler(), car.angularVelocity, actionTarget.toEuler(), dt);
                    if (outputF != null && outputF.length > 0) {
                        output.withRoll(outputF[0]);
                        output.withPitch(outputF[1]);
                        output.withYaw(outputF[2]);
                    } else
                        System.out.println("big error");
                }

                renderer.drawCentered3dCube(Color.GREEN, controlCar.position, 30);

                break;
            }
        }

        lastPos = controlCar.position;
        lastAng = controlCar.angularVelocity;

        // Print Throttle info
        {
            renderer.drawString2d(String.format("OrientIndex: %d", timesTested), Color.WHITE, new Point(500, 500), 2, 2);
            renderer.drawString2d(String.format("Avg: %.2f", timeNeeded / counter), Color.WHITE, new Point(500, 600), 2, 2);
            renderer.drawString2d("State: " + state.name(), Color.WHITE, new Point(10, 270), 2, 2);
            renderer.drawString2d(String.format("Yaw: %.1f", output.getYaw()), Color.WHITE, new Point(10, 350), 1, 1);
            renderer.drawString2d(String.format("Pitch: %.1f", output.getPitch()), Color.WHITE, new Point(10, 370), 1, 1);
            renderer.drawString2d(String.format("Roll: %.1f", output.getRoll()), Color.WHITE, new Point(10, 390), 1, 1);
            renderer.drawString2d(String.format("Steer: %.2f", output.getSteer()), Color.WHITE, new Point(10, 410), 1, 1);
            renderer.drawString2d(String.format("Throttle: %.2f", output.getThrottle()), Color.WHITE, new Point(10, 430), 1, 1);
        }

        return output;
    }

    /**
     * This is a nice example of using the rendering feature.
     */
    private void drawDebugLines(DataPacket input, CarData myCar) {
        AdvancedRenderer renderer = AdvancedRenderer.forBotLoop(this);

        renderer.drawString2d("BallP: " + input.ball.position, Color.WHITE, new Point(10, 150), 1, 1);
        renderer.drawString2d("BallV: " + input.ball.velocity, Color.WHITE, new Point(10, 170), 1, 1);
        renderer.drawString2d("Car: " + myCar.position, Color.WHITE, new Point(10, 190), 1, 1);
        renderer.drawString2d(String.format("CarSpeedXY: %.1f", myCar.velocity.flatten().magnitude()), Color.WHITE, new Point(10, 210), 1, 1);
        renderer.drawString2d("Ang: " + myCar.angularVelocity, Color.WHITE, new Point(10, 230), 1, 1);
        renderer.drawString2d("Nose: " + myCar.forward(), Color.WHITE, new Point(10, 250), 1, 1);
    }

    @Override
    public int getIndex() {
        return this.playerIndex;
    }

    @Override
    public ControllerState processInput(GameTickPacket packet) {


        if (!hasSetPriority) {
            hasSetPriority = true;
            Thread.currentThread().setPriority(10);
        }

        if (packet.playersLength() <= playerIndex || packet.ball() == null)
            return new ControlsOutput();

        if (!packet.gameInfo().isRoundActive()) {
            GameData.timeOfMatchStart = packet.gameInfo().secondsElapsed();
            return new ControlsOutput();
        }

        if (GameData.timeOfMatchStart < 0)
            GameData.timeOfMatchStart = packet.gameInfo().secondsElapsed();

        AdvancedRenderer r = AdvancedRenderer.forBotLoop(this);
        r.startPacket();

        BoostManager.loadGameTickPacket(packet);

        DataPacket dataPacket = new DataPacket(packet, playerIndex);

        ControlsOutput controlsOutput = processInput(dataPacket);

        lastTick = dataPacket.gameInfo.secondsElapsed();
        //lastGameTime = dataPacket.gameInfo.gameTimeRemaining();

        r.finishAndSendIfDifferent();
        return controlsOutput;
    }

    @Override
    public void retire() {
        System.out.println("Retiring sample bot " + playerIndex);
    }

    enum State {
        RESET,
        INIT,
        RUN,
        STOP
    }
}
