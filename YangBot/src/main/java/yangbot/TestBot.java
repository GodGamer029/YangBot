package yangbot;

import rlbot.Bot;
import rlbot.ControllerState;
import rlbot.cppinterop.RLBotDll;
import rlbot.flat.GameTickPacket;
import rlbot.gamestate.*;
import yangbot.input.*;
import yangbot.input.fieldinfo.BoostManager;
import yangbot.strategy.manuever.DriftControllerManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.Range;
import yangbot.util.Tuple;
import yangbot.util.lut.ArrayLutTable;
import yangbot.util.lut.Value2ToIndexFunction;
import yangbot.util.lut.ValueHelper;
import yangbot.util.lut.ValueToIndexFunction;
import yangbot.util.math.vector.Matrix2x2;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
import java.io.*;
import java.util.concurrent.ArrayBlockingQueue;

public class TestBot implements Bot {

    private final int playerIndex;

    private State state = State.YEET;
    private float timer = -1.0f;
    private float lastTick = -1;
    private boolean hasSetPriority = false;

    private float lastSpeed = 0;
    private DriftControllerManeuver maneuver;
    private ArrayBlockingQueue<Tuple<CarData, Boolean>> trail = new ArrayBlockingQueue<>(300);
    private ValueHelper speedHelper = new ValueHelper(new Range(100, 2300), 22); // 22
    private ValueHelper rotationHelper = new ValueHelper(new Range(0, 180), 10); // 10
    private int speedInd = 0;
    private int rotationInd = 0;
    private int lutTry = -1;
    private boolean lutSet = false;
    private float currentValue = 0;
    private float endSpeed;
    private Vector3 currentEndPosition = new Vector3();
    private ArrayLutTable<Tuple<Float, Float>, Tuple<Float, Tuple<Float, Vector2>>> lutTable = new ArrayLutTable<>(new Value2ToIndexFunction(speedHelper, rotationHelper));

    public TestBot(int playerIndex) {
        this.playerIndex = playerIndex;
    }

    private ControlsOutput processInput(DataPacket input) {
        float dt = Math.max(input.gameInfo.secondsElapsed() - lastTick, RLConstants.tickFrequency);

        if (lastTick > 0)
            timer += Math.min(dt, 0.1f);

        final AdvancedRenderer renderer = AdvancedRenderer.forBotLoop(this);
        final CarData carBoi = input.car;
        final BallData ball = input.ball;
        CarData controlCar = input.allCars.stream().filter((c) -> c.team != carBoi.team).findFirst().orElse(carBoi);
        controlCar = carBoi;

        GameData.current().update(controlCar, new ImmutableBallData(input.ball), input.allCars, input.gameInfo, dt, renderer);
        GameData.current().getBallPrediction().draw(renderer, Color.RED, 3);

        drawDebugLines(input, controlCar);
        ControlsOutput output = new ControlsOutput();

        switch (state) {
            case YEET:
                if (this.timer > 0.5f)
                    this.state = State.RESET;

                break;
            case RESET: {
                this.timer = 0.0f;
                this.state = State.INIT;
                this.lutSet = false;
                // Load luttable
                if (false) {
                    try {
                        var file = new File("C:\\Users\\gcaker\\Desktop\\lut\\boi.data");

                        ObjectInputStream s = new ObjectInputStream(new FileInputStream(file.getAbsoluteFile()));
                        var lutTable = (ArrayLutTable<Float, Float>) s.readObject();
                        lutTable.setKeyToIndexFunction(new ValueToIndexFunction(speedHelper));
                        System.out.println("############");
                        for (int i = 0; i <= this.speedHelper.getMaxIndex(); i++) {
                            System.out.println(this.speedHelper.getValueForIndex(i) + ": " + lutTable.get(this.speedHelper.getValueForIndex(i)));
                        }
                        System.out.println("###########");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                this.lutTry++;
                if (lutTry >= 3) {
                    float avgTime = this.currentValue / this.lutTry;
                    Vector2 startPos = new Vector2(1000f, 2000f);
                    float avgSpeed = this.endSpeed / this.lutTry;
                    Matrix2x2 startOrient = Matrix2x2.fromRotation((float) new Vector2(-1, 0).angle());
                    Vector2 avgPos = this.currentEndPosition.div(this.lutTry).flatten().sub(startPos).dot(startOrient);
                    System.out.println("(" + this.rotationInd + "/" + this.rotationHelper.getMaxIndex() + ") (" + this.speedInd + "/" + this.speedHelper.getMaxIndex() + "): " + avgPos + " " + avgTime);

                    if (avgTime <= RLConstants.tickFrequency * 2.1f) {
                        avgTime = 0;
                        avgPos = new Vector2();
                    }

                    this.lutTable.set(new Tuple<>(this.speedHelper.getValueForIndex(this.speedInd), this.rotationHelper.getValueForIndex(this.rotationInd)), new Tuple<>(avgTime, new Tuple<>(avgSpeed, avgPos)));
                    this.lutTry = 0;
                    this.currentValue = 0;
                    this.endSpeed = 0;
                    this.currentEndPosition = new Vector3();
                    this.rotationInd++;
                    if (this.rotationInd > this.rotationHelper.getMaxIndex()) {
                        this.rotationInd = 0;
                        this.speedInd++;
                        if (this.speedInd > this.speedHelper.getMaxIndex()) {
                            this.speedInd = 0;
                            System.out.println("############");
                            /*for(int i = 0; i <= this.speedHelper.getMaxIndex(); i++){
                                System.out.println(this.speedHelper.getValueForIndex(i)+": "+this.lutTable.get(this.speedHelper.getValueForIndex(i)));
                            }*/
                            System.out.println("###########");

                            try {
                                var file = new File("C:\\Users\\gcaker\\Desktop\\lut\\boi.data");
                                if (file.exists())
                                    file.delete();
                                file.createNewFile();

                                ObjectOutputStream s = new ObjectOutputStream(new FileOutputStream(file.getAbsoluteFile()));
                                s.writeObject(this.lutTable);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }

                }

                this.maneuver = new DriftControllerManeuver();
                this.maneuver.targetDirection = new Vector3(new Vector2(-1, 0).rotateBy((Math.PI / 180) * Math.min(179.9f /*always rotate around the right for consistency reasons*/, this.rotationHelper.getValueForIndex(this.rotationInd))), 0);

                this.trail.clear();
                RLBotDll.setGameState(new GameState()
                        .withGameInfoState(new GameInfoState().withGameSpeed(1f))
                        .withBallState(new BallState().withPhysics(new PhysicsState()
                                .withLocation(new DesiredVector3(3500f, 0f, BallData.COLLISION_RADIUS))
                                .withVelocity(new DesiredVector3(0f, 0f, 0f))
                        ))
                        .withCarState(this.playerIndex, new CarState().withPhysics(new PhysicsState()
                                .withLocation(new DesiredVector3(1000f, 2000f, RLConstants.carElevation))
                                .withRotation(new DesiredRotation(0f, (float) Math.PI, 0f))
                                .withVelocity(new DesiredVector3(-this.speedHelper.getValueForIndex(speedInd), 0f, 0f))
                                .withAngularVelocity(new DesiredVector3(0f, 0f, 0f))
                        )).buildPacket());

                break;
            }
            case INIT: {
                this.timer = 0;

                this.state = State.RUN;
                break;
            }
            case RUN: {

                if (this.timer > 3)
                    this.state = State.RESET;

                this.maneuver.step(dt, output);
                if (this.maneuver.isDone() && !this.lutSet) {
                    this.lutSet = true;
                    this.currentValue += this.timer;
                    this.endSpeed += controlCar.forward().dot(controlCar.velocity);
                    this.currentEndPosition = this.currentEndPosition.add(controlCar.position);
                    System.out.println("> (" + this.speedHelper.getValueForIndex(this.speedInd) + ", " + this.rotationHelper.getValueForIndex(this.rotationInd) + "): " + this.timer + " " + controlCar.forward().dot(controlCar.velocity));
                    this.timer = Math.max(this.timer, 2.7f);
                }

                // Remove old trail parts
                {
                    if (this.trail.remainingCapacity() <= 1)
                        this.trail.remove();

                    this.trail.add(new Tuple<>(controlCar, output.holdHandbrake()));
                }

                // Draw
                if (this.trail.size() > 0) {
                    var it = this.trail.iterator();
                    var lastPoint = it.next().getKey();
                    while (it.hasNext()) {
                        var nex = it.next();
                        var next = nex.getKey();
                        if (lastPoint.position.distance(next.position) > 40 || lastPoint.velocity.normalized().dot(next.velocity.normalized()) < 0.98f) {
                            var col = Color.WHITE;
                            if (nex.getValue())
                                col = Color.GREEN;

                            renderer.drawLine3d(col, lastPoint.position, next.position);
                            renderer.drawLine3d(Color.ORANGE, next.position, next.position.add(next.forward().mul(40)));
                            lastPoint = next;
                        }
                    }
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

        ControlsOutput controlsOutput = new ControlsOutput();
        try {
            controlsOutput = processInput(dataPacket);
        } catch (Exception | AssertionError e) {
            e.printStackTrace();
        }

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
        YEET,
        RESET,
        INIT,
        RUN
    }
}
