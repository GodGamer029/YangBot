package yangbot;


import rlbot.Bot;
import rlbot.ControllerState;
import rlbot.cppinterop.RLBotDll;
import rlbot.flat.GameTickPacket;
import rlbot.gamestate.*;
import yangbot.input.*;
import yangbot.input.fieldinfo.BoostManager;
import yangbot.input.playerinfo.PlayerInfoManager;
import yangbot.path.EpicMeshPlanner;
import yangbot.path.builders.SegmentedPath;
import yangbot.util.AdvancedRenderer;
import yangbot.util.Tuple;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
import java.util.concurrent.ArrayBlockingQueue;

public class TestBot implements Bot {

    private final int playerIndex;

    private State state = State.RESET;
    private float timer = -1.0f;
    private float lastTick = -1;
    private boolean hasSetPriority = false;
    private final ArrayBlockingQueue<Tuple<CarData, Boolean>> trail = new ArrayBlockingQueue<>(300);

    private SegmentedPath path;

    public TestBot(int playerIndex) {
        this.playerIndex = playerIndex;
    }


    private ControlsOutput processInput(DataPacket input) {
        float dt = Math.max(input.gameInfo.secondsElapsed() - lastTick, RLConstants.tickFrequency * 0.9f);

        if (lastTick > 0)
            timer += Math.min(dt, 0.1f);

        lastTick = input.gameInfo.secondsElapsed();

        final CarData carBoi = input.car;
        final BallData realBall = input.ball;
        final BallData ball = realBall.makeImmutable().makeMutable();
        CarData controlCar = input.allCars.stream().filter((c) -> c.team != carBoi.team).findFirst().orElse(carBoi);

        final AdvancedRenderer renderer = AdvancedRenderer.forBotLoop(this);
        GameData.current().update(carBoi, new ImmutableBallData(input.ball), input.allCars, input.gameInfo, dt, renderer);
        final YangBallPrediction ballPrediction = GameData.current().getBallPrediction();
        drawDebugLines(input, controlCar);
        ControlsOutput output = new ControlsOutput();

        switch (state) {
            case YEET:
                if (timer > 0.25f)
                    this.state = State.RESET;
                break;
            case RESET: {
                this.trail.clear();
                this.timer = 0.0f;
                this.state = State.INIT;

                final Vector3 startPos = new Vector3(-20, RLConstants.goalDistance + 500, 20);
                final Vector3 startTangent = new Vector3(0, -1f, 0).normalized(); //
                final float startSpeed = 0f; //
                RLBotDll.setGameState(new GameState()
                        .withGameInfoState(new GameInfoState().withGameSpeed(1f))
                        .withCarState(controlCar.playerIndex, new CarState().withPhysics(new PhysicsState()
                                .withLocation(startPos.toDesiredVector())
                                .withVelocity(new Vector3(0, 0, -5).toDesiredVector())
                                .withRotation(Matrix3x3.lookAt(startTangent, new Vector3(0, 0, 1)).toEuler().toDesiredRotation())
                                .withAngularVelocity(new Vector3().toDesiredVector())
                        ).withBoostAmount(100f))
                        .withBallState(new BallState().withPhysics(new PhysicsState()
                                .withLocation(new DesiredVector3(0f, 0f, 500f))
                                .withVelocity(new DesiredVector3(0f, 0f, 0f))
                                .withAngularVelocity(new DesiredVector3(0f, 0f, 0f))
                        ))
                        .buildPacket());

                System.out.println("############");
                break;
            }
            case INIT: {
                if (timer > 0.7f) {
                    var plan = new EpicMeshPlanner()
                            .withCreationStrategy(EpicMeshPlanner.PathCreationStrategy.YANGPATH)
                            .withStart(controlCar)
                            .withEnd(new Vector3(1000, 1000, 17), controlCar.position.normalized().mul(-1))
                            .withArrivalSpeed(2000)
                            //.withEnd(new Vector3(Math.random() * 2000 - 1000, Math.random() * 2000 - 1000, 17), controlCar.position.normalized().mul(-1))
                            .plan();
                    this.path = plan.get();
                    this.state = State.RUN;
                }

                break;
            }
            case RUN: {
                this.path.draw(renderer);
                this.path.step(dt, output);
                if (this.path.isDone())
                    this.state = State.YEET;
                break;
            }
        }

        controlCar.hitbox.draw(renderer, controlCar.position, 1, Color.GREEN);

        // Print Throttle info
        {
            renderer.drawString2d("State: " + state.name(), Color.WHITE, new Point(10, 270), 2, 2);
            renderer.drawString2d(String.format("Yaw: %.1f", output.getYaw()), Color.WHITE, new Point(10, 350), 1, 1);
            renderer.drawString2d(String.format("Pitch: %.1f", output.getPitch()), Color.WHITE, new Point(10, 370), 1, 1);
            renderer.drawString2d(String.format("Roll: %.1f", output.getRoll()), Color.WHITE, new Point(10, 390), 1, 1);
            renderer.drawString2d(String.format("Steer: %.2f", output.getSteer()), Color.WHITE, new Point(10, 410), 1, 1);
            renderer.drawString2d(String.format("Throttle: %.2f", output.getThrottle()), output.getThrottle() < 0 ? Color.RED : Color.WHITE, new Point(10, 430), 1, 1);
        }

        return output;
    }

    /**
     * This is a nice example of using the rendering feature.
     */
    private void drawDebugLines(DataPacket input, CarData myCar) {
        AdvancedRenderer renderer = AdvancedRenderer.forBotLoop(this);

        renderer.drawString2d("BallP: " + input.ball.position, Color.WHITE, new Point(10, 150), 1, 1);
        renderer.drawString2d("BallV: " + input.ball.velocity + " m=" + input.ball.velocity.magnitude(), Color.WHITE, new Point(10, 170), 1, 1);
        //renderer.drawString2d("CarV: " + myCar.velocity, Color.WHITE, new Point(10, 190), 1, 1);
        renderer.drawString2d("CarX: " + myCar.position, Color.WHITE, new Point(10, 190), 1, 1);

        renderer.drawString2d(String.format("CarSpeedXY: %.1f", myCar.velocity.dot(myCar.forward())), Color.WHITE, new Point(10, 210), 1, 1);
        renderer.drawString2d("Ang: " + myCar.angularVelocity, Color.WHITE, new Point(10, 230), 1, 1);
        //renderer.drawString2d("Nose: " + myCar.forward(), Color.WHITE, new Point(10, 250), 1, 1);
        //renderer.drawString2d("CarF: " + myCar.forward(), Color.WHITE, new Point(10, 250), 1, 1);
        //float accel = (float) (myCar.velocity.dot(myCar.forward()) - lastSpeed);
        //lastSpeed = (float) myCar.velocity.dot(myCar.forward());
        //accel -= CarData.driveForceForward(new ControlsOutput().withThrottle(1), (float) myCar.velocity.dot(myCar.forward()), 0, 0);
        //renderer.drawString2d(String.format("Accel: % 4.1f", accel / GameData.current().getDt()), Color.WHITE, new Point(10, 250), 1, 1);

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
        PlayerInfoManager.reset();
        System.out.println("Retiring Test bot " + playerIndex);
    }

    enum State {
        YEET,
        RESET,
        INIT,
        RUN
    }
}
