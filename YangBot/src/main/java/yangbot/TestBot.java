package yangbot;

import rlbot.Bot;
import rlbot.ControllerState;
import rlbot.cppinterop.RLBotDll;
import rlbot.flat.GameTickPacket;
import rlbot.gamestate.*;
import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.DataPacket;
import yangbot.input.GameData;
import yangbot.input.fieldinfo.BoostManager;
import yangbot.util.AdvancedRenderer;
import yangbot.util.ControlsOutput;
import yangbot.vector.Vector3;

import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class TestBot implements Bot {

    private final int playerIndex;
    /**
     * This is the most important function. It will automatically get called by the framework with fresh data
     * every frame. Respond with appropriate controls!
     */
    public float all = 0;
    public int count = 0;
    public int realCount = 0;
    private State state = State.RESET;
    private float timer = -1.0f;
    private float lastTick = -1;
    private Vector3 startPos = new Vector3();
    private Map<Float, Float> speedMap = new LinkedHashMap<>();
    private boolean hasSetPriority = false;

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

        drawDebugLines(input, car);
        AdvancedRenderer renderer = AdvancedRenderer.forBotLoop(this);
        ControlsOutput output = new ControlsOutput();

        final float testDuration = 4;

        switch (state) {
            case RESET: {
                timer = 0.0f;
                GameState st = new GameState()
                        .withCarState(this.playerIndex, new CarState()
                                .withBoostAmount(100f)
                                .withPhysics(new PhysicsState()
                                        .withLocation(new DesiredVector3(0f, -4900f, 0f))
                                        .withAngularVelocity(new DesiredVector3(0f, 0f, 0f))
                                        .withVelocity(new DesiredVector3(0f, 0f, 0f))
                                        .withRotation(new DesiredRotation(0f, (float) Math.PI / 2f, 0f))
                                )
                        )
                        .withBallState(new BallState().withPhysics(new PhysicsState()
                                .withLocation(new DesiredVector3(0f, 0f, 10000f))
                                .withAngularVelocity(new DesiredVector3(0f, 0f, 0f))
                                .withVelocity(new DesiredVector3(0f, 0f, 0f))
                                .withRotation(new DesiredRotation(0f, 0f, 0f))
                        ));
                RLBotDll.setGameState(st.buildPacket());
                state = State.INIT;
                break;
            }
            case INIT: {
                if (timer >= 2) {
                    //float dist = DriveManuver.maxDistance((float) car.velocity.magnitude(), testDuration);
                    //System.out.println("Predicted dist: "+dist);
                    startPos = car.position;
                    state = State.RUN;
                    timer = 0;
                }
                break;
            }
            case RUN: {
                // Capture data
                speedMap.put(timer, (float) car.position.distance(startPos));
                if (timer < testDuration) {
                    output.withThrottle(-1);
                    output.withBoost(true);
                } else {
                    Vector3 endPos = car.position;

                    state = State.STOP;
                    {

                    }

                    System.out.println("Start Pos: " + startPos);
                    System.out.println("End Pos: " + endPos);
                    System.out.println("Dist: " + startPos.distance(endPos));
                }


                break;
            }
        }

        // Print Throttle info
        {
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
