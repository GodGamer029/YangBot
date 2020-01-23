package yangbot;

import rlbot.Bot;
import rlbot.ControllerState;
import rlbot.flat.GameTickPacket;
import yangbot.input.*;
import yangbot.input.fieldinfo.BoostManager;
import yangbot.manuever.DodgeManeuver;
import yangbot.util.AdvancedRenderer;

import java.awt.*;

public class TestBot implements Bot {

    private final int playerIndex;

    private State state = State.RESET;
    private float timer = -1.0f;
    private float lastTick = -1;
    private boolean hasSetPriority = false;

    private DodgeManeuver simDodge;
    private float lastSpeed = 0;

    public TestBot(int playerIndex) {
        this.playerIndex = playerIndex;
    }

    private ControlsOutput processInput(DataPacket input) {
        float dt = Math.max(input.gameInfo.secondsElapsed() - lastTick, RLConstants.tickFrequency);

        if (lastTick > 0)
            timer += Math.min(dt, 0.1f);

        AdvancedRenderer renderer = AdvancedRenderer.forBotLoop(this);
        CarData car = input.car;
        BallData ball = input.ball;
        GameData.current().update(input.car, new ImmutableBallData(input.ball), input.allCars, input.gameInfo, dt, renderer);

        GameData.current().getBallPrediction().draw(renderer, Color.RED, 3);
        CarData controlCar = input.allCars.stream().filter((c) -> c.team != car.team).findFirst().orElse(car);

        drawDebugLines(input, controlCar);

        ControlsOutput output = new ControlsOutput();

        switch (state) {
            case RESET: {
                timer = 0.0f;

                state = State.INIT;

               /* RLBotDll.setGameState(new GameState()
                        .withGameInfoState(new GameInfoState().withWorldGravityZ(0.00001f).withGameSpeed(1f))
                        .withBallState(new BallState().withPhysics(new PhysicsState()
                                        .withLocation(new DesiredVector3(133.2f, 10f, 95f)) // 320
                                        .withVelocity(new DesiredVector3(20f, 0f, -1f))
                                        .withAngularVelocity(new DesiredVector3(0f, 0f, 0f))
                                )
                        )
                        .withCarState(this.playerIndex, new CarState().withPhysics(new PhysicsState()
                                .withLocation(new DesiredVector3(0f, 0f, 370f))
                                .withRotation(new DesiredRotation(0f, (float) Math.PI / 2f, (float) Math.PI / -2f))
                                .withVelocity(new DesiredVector3(0f, 0f, -1f))
                                .withAngularVelocity(new DesiredVector3(0f, 0f, 0f))
                        )).buildPacket());
                RLConstants.gravity = new Vector3(0, 0, 0.00001f);*/
                break;
            }
            case INIT: {

                simDodge = new DodgeManeuver();
                simDodge.direction = car.velocity.flatten().add(1, 1).normalized().mul(-1);
                simDodge.duration = 0.15f;
                simDodge.delay = 0.5f;

                state = State.RUN;
                break;
            }
            case RUN: {
                simDodge.step(dt, output);

                if (timer > 1.5f && car.hasWheelContact)
                    state = State.RESET;
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
        //renderer.drawString2d("Nose: " + myCar.forward(), Color.WHITE, new Point(10, 250), 1, 1);
        //renderer.drawString2d("CarF: " + myCar.forward(), Color.WHITE, new Point(10, 250), 1, 1);
        float accel = (float) (myCar.velocity.dot(myCar.forward()) - lastSpeed);
        lastSpeed = (float) myCar.velocity.dot(myCar.forward());
        accel -= CarData.driveForceForward(new ControlsOutput().withThrottle(1), (float) myCar.velocity.dot(myCar.forward()), 0, 0);
        renderer.drawString2d(String.format("Accel: %.1f", accel), Color.WHITE, new Point(10, 250), 1, 1);

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
        RUN
    }
}
