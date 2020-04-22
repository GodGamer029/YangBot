package yangbot;

import rlbot.Bot;
import rlbot.ControllerState;
import rlbot.cppinterop.RLBotDll;
import rlbot.flat.GameTickPacket;
import rlbot.gamestate.*;
import yangbot.input.*;
import yangbot.input.fieldinfo.BoostManager;
import yangbot.path.builders.PathBuilder;
import yangbot.path.builders.SegmentedPath;
import yangbot.path.builders.segments.StraightLineSegment;
import yangbot.path.builders.segments.TurnCircleSegment;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.Tuple;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
import java.util.concurrent.ArrayBlockingQueue;

public class TestBot implements Bot {

    private final int playerIndex;

    private State state = State.YEET;
    private float timer = -1.0f;
    private float lastTick = -1;
    private boolean hasSetPriority = false;
    private final ArrayBlockingQueue<Tuple<CarData, Boolean>> trail = new ArrayBlockingQueue<>(300);

    private SegmentedPath path;
    private float lastSpeed = 0;

    public TestBot(int playerIndex) {
        this.playerIndex = playerIndex;
    }

    private ControlsOutput processInput(DataPacket input) {
        float dt = Math.max(input.gameInfo.secondsElapsed() - lastTick, RLConstants.tickFrequency * 0.9f);

        if (lastTick > 0)
            timer += Math.min(dt, 0.1f);

        lastTick = input.gameInfo.secondsElapsed();

        final CarData carBoi = input.car;
        final BallData ball = input.ball;
        CarData controlCar = input.allCars.stream().filter((c) -> c.team != carBoi.team).findFirst().orElse(carBoi);
        controlCar = carBoi;

        final AdvancedRenderer renderer = AdvancedRenderer.forBotLoop(this);
        GameData.current().update(controlCar, new ImmutableBallData(input.ball), input.allCars, input.gameInfo, dt, renderer);

        GameData.current().update(controlCar, new ImmutableBallData(input.ball), input.allCars, input.gameInfo, dt, renderer);
        GameData.current().getBallPrediction().draw(renderer, Color.RED, 3);

        drawDebugLines(input, controlCar);
        ControlsOutput output = new ControlsOutput();

        switch (state) {
            case YEET:
                this.state = State.RESET;
                break;
            case RESET: {
                this.trail.clear();
                this.timer = 0.0f;
                this.state = State.INIT;

                RLBotDll.setGameState(new GameState()
                        .withGameInfoState(new GameInfoState().withGameSpeed(1f))
                        .withCarState(controlCar.playerIndex, new CarState().withPhysics(new PhysicsState()
                                .withLocation(new Vector3(0, -2000f, 18f).toDesiredVector())
                                .withVelocity(new Vector3(0, -700, 0).toDesiredVector())
                                .withRotation(new DesiredRotation(0f, (float) Math.PI / 2f, 0f))
                        ))
                        .withBallState(new BallState().withPhysics(new PhysicsState()
                                .withLocation(new DesiredVector3(0f, 0f, 100f))
                                .withVelocity(new DesiredVector3(0f, 0f, 0f))
                        ))
                        .buildPacket());
                break;
            }
            case INIT: {
                if (this.timer > 0.5f) {
                    this.state = State.RUN;
                    var builder = new PathBuilder(controlCar)
                            .optimize()
                            .add(new StraightLineSegment(controlCar.position, new Vector3(0, 2000, controlCar.position.z)));

                    System.out.println("Speed: " + builder.getCurrentSpeed());
                    var turn = new TurnCircleSegment(new Physics2D(builder.getCurrentPosition().flatten(), builder.getCurrentTangent().mul(builder.getCurrentSpeed()).flatten(), Matrix3x3.lookAt(builder.getCurrentTangent(), new Vector3(0, 0, 1)).flatten()),
                            1 / DriveManeuver.maxTurningCurvature(builder.getCurrentSpeed() + 50), new Vector2(1000, 0));
                    builder.add(turn);

                    var str = new StraightLineSegment(builder.getCurrentPosition(), builder.getCurrentPosition().add(builder.getCurrentTangent().mul(1000)));
                    builder.add(str);
                    this.path = builder.build();

                    this.timer = 0;
                }
                output.withThrottle(0.02f)
                        .withSteer(0);
                break;
            }
            case RUN: {
                this.path.draw(renderer);
                if (this.path.step(dt, output)) {
                    this.state = State.RESET;
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
        renderer.drawString2d("CarV: " + myCar.velocity, Color.WHITE, new Point(10, 190), 1, 1);
        renderer.drawString2d(String.format("CarSpeedXY: %.1f", myCar.velocity.dot(myCar.forward())), Color.WHITE, new Point(10, 210), 1, 1);
        renderer.drawString2d("Ang: " + myCar.angularVelocity, Color.WHITE, new Point(10, 230), 1, 1);
        //renderer.drawString2d("Nose: " + myCar.forward(), Color.WHITE, new Point(10, 250), 1, 1);
        //renderer.drawString2d("CarF: " + myCar.forward(), Color.WHITE, new Point(10, 250), 1, 1);
        float accel = (float) (myCar.velocity.dot(myCar.forward()) - lastSpeed);
        lastSpeed = (float) myCar.velocity.dot(myCar.forward());
        //accel -= CarData.driveForceForward(new ControlsOutput().withThrottle(1), (float) myCar.velocity.dot(myCar.forward()), 0, 0);
        renderer.drawString2d(String.format("Accel: % 4.1f", accel / GameData.current().getDt()), Color.WHITE, new Point(10, 250), 1, 1);

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

        ControlsOutput controlsOutput = new ControlsOutput();
        try {
            controlsOutput = processInput(dataPacket);
        } catch (Exception | AssertionError e) {
            e.printStackTrace();
        }


        //lastGameTime = dataPacket.gameInfo.gameTimeRemaining();

        r.finishAndSendIfDifferent();
        return controlsOutput;
    }

    @Override
    public void retire() {
        System.out.println("Retiring Test bot " + playerIndex);
    }

    enum State {
        YEET,
        RESET,
        INIT,
        RUN
    }
}
