package yangbot;

import rlbot.Bot;
import rlbot.ControllerState;
import rlbot.cppinterop.RLBotDll;
import rlbot.flat.GameTickPacket;
import rlbot.gamestate.BallState;
import rlbot.gamestate.GameState;
import rlbot.gamestate.PhysicsState;
import yangbot.input.*;
import yangbot.input.fieldinfo.BoostManager;
import yangbot.util.AdvancedRenderer;
import yangbot.util.ControlsOutput;
import yangbot.util.hitbox.YangCarHitbox;
import yangbot.vector.Vector3;

import java.awt.*;

public class TestBot implements Bot {

    private final int playerIndex;

    private State state = State.RESET;
    private float timer = -1.0f;
    private float lastTick = -1;
    private boolean hasSetPriority = false;

    private Vector3 lastContact = null;
    private YangCarHitbox lastHitbox = null;
    private Vector3 lastPos = null;

    public TestBot(int playerIndex) {
        this.playerIndex = playerIndex;
    }

    private ControlsOutput processInput(DataPacket input) {
        float dt = Math.max(input.gameInfo.secondsElapsed() - lastTick, 0f);

        if (lastTick > 0)
            timer += Math.min(dt, 0.1f);

        AdvancedRenderer renderer = AdvancedRenderer.forBotLoop(this);
        CarData car = input.car;
        BallData ball = input.ball;
        GameData.current().update(input.car, input.ball, input.allCars, input.gameInfo, dt, renderer);

        CarData controlCar = input.allCars.stream().filter((c) -> c.team != car.team).findFirst().orElse(car);

        drawDebugLines(input, controlCar);

        ControlsOutput output = new ControlsOutput();

        switch (state) {
            case RESET: {
                timer = 0.0f;

                state = State.RUN;

                break;
            }
            case INIT: {
                state = State.RUN;
                break;
            }
            case RUN: {
                BallData customBall = new BallData(new Vector3(0, 0, BallData.COLLISION_RADIUS), new Vector3(), new Vector3());
                customBall = ball;
                renderer.drawCentered3dCube(Color.GREEN, customBall.position, 50);


                if (lastContact != null) {
                    renderer.drawCentered3dCube(Color.RED, lastContact, 20);
                    renderer.drawCentered3dCube(Color.RED, lastContact, 5);
                    lastHitbox.draw(renderer, lastPos, 1, Color.BLUE);
                }

                controlCar.hitbox.draw(renderer, controlCar.position, 1, Color.GREEN);

                Vector3 c = customBall.collide(controlCar);
                if (c != null && timer > RLConstants.tickFrequency * 4) {
                    customBall.step(dt);
                    lastContact = c;
                    lastPos = controlCar.position;
                    lastHitbox = controlCar.hitbox;
                    GameState st = new GameState()
                            .withBallState(new BallState().withPhysics(new PhysicsState()
                                    .withLocation(customBall.position.toDesiredVector())
                                    .withAngularVelocity(customBall.angularVelocity.toDesiredVector())
                                    .withVelocity(customBall.velocity.toDesiredVector())
                            ));

                    RLBotDll.setGameState(st.buildPacket());
                    timer = 0;
                }

                renderer.drawCentered3dCube(Color.GREEN, controlCar.position, 30);
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
        //lastGameTime = dataPacket.gameInfo.gameTimeRemaining();

        r.finishAndSendIfDifferent();
        return controlsOutput;
    }

    @Override
    public void retire() {
        System.out.println("Retiring Test bot " + playerIndex);
    }

    enum State {
        RESET,
        INIT,
        RUN,
        STOP
    }
}
