package yangbot;


import rlbot.Bot;
import rlbot.ControllerState;
import rlbot.cppinterop.RLBotDll;
import rlbot.flat.GameTickPacket;
import rlbot.gamestate.GameInfoState;
import yangbot.input.*;
import yangbot.input.fieldinfo.BoostManager;
import yangbot.input.playerinfo.PlayerInfoManager;
import yangbot.path.EpicMeshPlanner;
import yangbot.path.builders.SegmentedPath;
import yangbot.strategy.manuever.DodgeManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.Tuple;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector3;
import yangbot.util.scenario.ScenarioUtil;

import java.awt.*;
import java.util.concurrent.ArrayBlockingQueue;

public class TestBot implements Bot {

    private final int playerIndex;

    private State state = State.YEET;
    private final ArrayBlockingQueue<Tuple<CarData, Float>> trail = new ArrayBlockingQueue<>(300);
    private float lastTick = -1;
    private boolean hasSetPriority = false;
    private float timer = -0.15f;

    private SegmentedPath path;
    private DodgeManeuver dodgeManeuver;

    public TestBot(int playerIndex) {
        this.playerIndex = playerIndex;
    }

    private ControlsOutput processInput(DataPacket input) throws InterruptedException {
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
                if (timer > 0.2f)
                    this.state = State.RESET;
                break;
            case RESET: {
                this.trail.clear();
                this.timer = 0.0f;
                this.state = State.INIT;

                final Vector3 startPos = new Vector3(400, RLConstants.goalDistance + 500, 20);
                //final Vector3 startTangent = new Vector3(Math.random() * 2 - 1, Math.random() * 2 - 1, 0).normalized(); //
                final Vector3 startTangent = new Vector3(-0.5f, -1f, 0).normalized();

                var simCar = new CarData(new Vector3(), new Vector3(), new Vector3(), Matrix3x3.lookAt(new Vector3(0, 1, 1).normalized(), new Vector3(0, 0, 1)));
                System.out.println(simCar.hitbox.permutatePoint(simCar.position, 1, 0, 0));

                float startSpeed = (float) 0; //
                /*RLBotDll.setGameState(new GameState()
                        .withGameInfoState(new GameInfoState().withGameSpeed(1f))
                        .withCarState(controlCar.playerIndex, new CarState()/*.withPhysics(new PhysicsState()
                                .withLocation(startPos.toDesiredVector())
                                .withVelocity(startTangent.mul(startSpeed).toDesiredVector())
                                .withRotation(Matrix3x3.lookAt(startTangent, new Vector3(0, 0, 1)).toEuler().toDesiredRotation())
                                .withAngularVelocity(new Vector3().toDesiredVector())
                        )*.withBoostAmount(100f))
                        .withBallState(new BallState().withPhysics(new PhysicsState()
                                .withLocation(new DesiredVector3(0f, 0f, DriveDodgeStrikeAbstraction.MAX_STRIKE_HEIGHT))
                                .withVelocity(new DesiredVector3(0f, 0f, -5f))
                                .withAngularVelocity(new DesiredVector3(0f, 0f, 0f))
                        ))
                        .buildPacket());*/

                var gstate = ScenarioUtil.decodeToGameState("eWFuZ3YxOmMocD0oMzcwMC40NSwtMjI2My4zMSwyMi4yOSksdj0oLTE4NC42NCwxMDgyLjM2LC0zMTcuMzApLGE9KDAuNjUsLTAuMDAsLTAuNzIpLG89KC0wLjAxLDEuNTcsMC4wMCkpLGIocD0oMzg3NC45NiwtMTQzNS4wMyw5OC45Myksdj0oLTM1Mi45NSwxMzk1LjgwLC04NS4wOCksYT0oLTMuMDIsLTAuODQsLTQuNzApKTs")
                        .withGameInfoState(new GameInfoState().withGameSpeed(0.1f));
                gstate.getCarState(0).withBoostAmount(0f);
                RLBotDll.setGameState(gstate.buildPacket());

                dodgeManeuver = new DodgeManeuver();
                System.out.println("############");
                break;
            }
            case INIT: {
                if (timer > 0.05f) {
                    this.timer = 0;
                    this.state = State.RUN;
                    this.path = new EpicMeshPlanner()
                            .withStart(controlCar)
                            .withEnd(new Vector3(3268.63f, 1030, 17f), new Vector3(-0.20f, 0.98f, 0))
                            .withArrivalTime(controlCar.elapsedSeconds + 2.18f - 0.05f)
                            .withArrivalSpeed(2300)
                            .allowOptimize(false)
                            .withCreationStrategy(EpicMeshPlanner.PathCreationStrategy.YANGPATH)
                            .plan().get();
                    System.out.println("Est: " + this.path.getTotalTimeEstimate());
                }

                break;
            }
            case RUN: {

                this.path.draw(renderer);
                if (!this.path.isDone())
                    this.path.step(dt, output);
                if (this.path.isDone()) {
                    this.state = State.YEET;
                    this.timer = -0.5f;
                }

                if (this.trail.size() >= 298)
                    this.trail.remove();
                this.trail.put(new Tuple<>(controlCar, output.getSteer()));

                Vector3 last = this.trail.peek().getKey().position.add(0, 0, -1);
                for (var e : this.trail) {
                    var cur = e.getKey().position.add(0, 0, -1);
                    if (last.distance(cur) > 40) {
                        renderer.drawLine3d(Math.abs(e.getValue()) > 0.9f ? Color.RED : Color.LIGHT_GRAY, last, cur);
                        last = cur;
                    }
                }
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

        renderer.drawString2d(String.format("CarSpeedXY: %.1f", myCar.velocity.flatten().magnitude()), Color.WHITE, new Point(10, 210), 1, 1);
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
            this.timer = 0;
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
