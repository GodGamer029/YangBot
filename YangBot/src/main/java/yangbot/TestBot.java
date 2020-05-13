package yangbot;

import rlbot.Bot;
import rlbot.ControllerState;
import rlbot.cppinterop.RLBotDll;
import rlbot.flat.GameTickPacket;
import rlbot.gamestate.*;
import yangbot.input.*;
import yangbot.input.fieldinfo.BoostManager;
import yangbot.input.playerinfo.PlayerInfoManager;
import yangbot.path.Curve;
import yangbot.path.builders.SegmentedPath;
import yangbot.path.builders.segments.DriftSegment;
import yangbot.path.builders.segments.FlipSegment;
import yangbot.strategy.abstraction.AerialAbstraction;
import yangbot.strategy.manuever.AerialManeuver;
import yangbot.strategy.manuever.DodgeManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.Tuple;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;

public class TestBot implements Bot {

    private final int playerIndex;

    private State state = State.YEET;
    private float timer = -1.0f;
    private float lastTick = -1;
    private boolean hasSetPriority = false;
    private final ArrayBlockingQueue<Tuple<CarData, Boolean>> trail = new ArrayBlockingQueue<>(300);

    private SegmentedPath path;
    private Vector3 lastVel = new Vector3();
    private float lastSpeed = 0;
    private AerialAbstraction aerialAbstraction;
    private Curve drawCurve;
    private Curve.PathCheckStatus pathCheckStatus;
    private DriftSegment driftSegment;
    private FlipSegment flipSegment;
    private AerialManeuver aerialManeuver;

    public TestBot(int playerIndex) {
        this.playerIndex = playerIndex;
    }

    private boolean planAerialIntercept(YangBallPrediction ballPrediction, boolean debug) {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();

        float t = DodgeManeuver.max_duration + 0.15f;

        // Find intercept
        do {
            final Optional<YangBallPrediction.YangPredictionFrame> interceptFrameOptional = ballPrediction.getFrameAfterRelativeTime(t);
            if (interceptFrameOptional.isEmpty())
                break;

            final YangBallPrediction.YangPredictionFrame interceptFrame = interceptFrameOptional.get();
            final Vector3 targetPos = interceptFrame.ballData.position;

            // We should arrive at the ball a bit early to catch it
            boolean isPossible = AerialAbstraction.isViable(car, targetPos, interceptFrame.absoluteTime);
            if (isPossible) {
                this.aerialAbstraction = new AerialAbstraction();
                this.aerialAbstraction.targetPos = targetPos;
                this.aerialAbstraction.arrivalTime = interceptFrame.absoluteTime;
                return true;
            }

            t = interceptFrame.relativeTime;
            t += RLConstants.simulationTickFrequency * 2; // 30 ticks / s
            //if(t > 1.75f)
            //    t += RLConstants.simulationTickFrequency * 2; // speed up even more after 2s
        } while (t < ballPrediction.relativeTimeOfLastFrame());

        return false;
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
        GameData.current().update(controlCar, new ImmutableBallData(input.ball), input.allCars, input.gameInfo, dt, renderer);
        //GameData.current().getBallPrediction().draw(renderer, Color.RED, 3);
        final YangBallPrediction ballPrediction = GameData.current().getBallPrediction();
        drawDebugLines(input, controlCar);
        ControlsOutput output = new ControlsOutput();

        switch (state) {
            case YEET:
                if (timer > 0.5f)
                    this.state = State.RESET;
                break;
            case RESET: {
                this.trail.clear();
                this.timer = 0.0f;
                this.state = State.INIT;

                final Vector3 startPos = new Vector3(0, 100, 17);
                final Vector3 startTangent = new Vector3(0f, -1f, 0f).normalized(); //
                final Vector3 endTangent = new Vector3(0.02, 1, 0).normalized();
                final Vector3 targetPos = new Vector3(600, -RLConstants.goalDistance * 0.9f, 120);
                final float startSpeed = 0f; // 1260

                float z = 1000;
                RLBotDll.setGameState(new GameState()
                        .withGameInfoState(new GameInfoState().withGameSpeed(1f))
                        .withCarState(controlCar.playerIndex, new CarState().withPhysics(new PhysicsState()
                                .withLocation(startPos.toDesiredVector())
                                .withVelocity(startTangent.mul(startSpeed).toDesiredVector())
                                .withRotation(Matrix3x3.lookAt(startTangent, new Vector3(0, 0, 1)).toEuler().toDesiredRotation())
                                .withAngularVelocity(new Vector3().toDesiredVector())
                        ).withBoostAmount(100f))
                        .withBallState(new BallState().withPhysics(new PhysicsState()
                                .withLocation(new DesiredVector3(0f, 0f, z))
                                .withVelocity(new DesiredVector3((float) Math.random() * 400 - 200f, (float) Math.random() * 50f, 200f))
                        ))
                        .buildPacket());

                this.aerialManeuver = new AerialManeuver();
                this.aerialManeuver.arrivalTime = controlCar.elapsedSeconds + 2;
                this.aerialManeuver.target = new Vector3(0, 0, z);

                System.out.println("############");
                break;
            }
            case INIT: {
                if (timer > 0.05f) {
                    this.state = State.RUN;
                    if (!this.planAerialIntercept(ballPrediction, false))
                        this.state = State.YEET;
                }

                break;
            }
            case RUN: {


                //if(timer < 0.1f){
                //    System.out.println("Is viable: "+AerialAbstraction.isViable(controlCar, this.aerialManeuver.target, this.aerialManeuver.arrivalTime));
                //}

                this.aerialAbstraction.draw(renderer);
                this.aerialAbstraction.step(dt, output);

                //renderer.drawString2d("IsValid: " + this.pathCheckStatus.pathStatus, Color.WHITE, new Point(400, 400), 2, 2);
                if (timer > 5f || this.aerialAbstraction.isDone())
                    this.state = State.RESET;
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
        renderer.drawString2d("BallV: " + input.ball.velocity + " m=" + input.ball.velocity.magnitude(), Color.WHITE, new Point(10, 170), 1, 1);
        //renderer.drawString2d("CarV: " + myCar.velocity, Color.WHITE, new Point(10, 190), 1, 1);
        renderer.drawString2d("CarX: " + myCar.position, Color.WHITE, new Point(10, 190), 1, 1);

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
