package yangbot.util.lut;

import rlbot.cppinterop.RLBotDll;
import rlbot.gamestate.*;
import yangbot.input.*;
import yangbot.strategy.manuever.DriftControllerManeuver;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.Range;
import yangbot.util.Tuple;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Matrix2x2;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

public class LutMaker {

    private final ValueHelper speedHelper = new ValueHelper(new Range(100, 2300), 22); // 22
    private final ValueHelper rotationHelper = new ValueHelper(new Range(0, 180), 10); // 10
    private State state = State.YEET;
    private float timer = -1.0f;
    private float lastSpeed = 0;
    private DriftControllerManeuver maneuver;
    private ArrayBlockingQueue<Tuple<CarData, Boolean>> trail = new ArrayBlockingQueue<>(300);
    private int speedInd = 0;
    private int rotationInd = 0;
    private int lutTry = -1;
    private boolean lutSet = false;
    private float currentValue = 0;
    private float endSpeed;
    private Vector3 currentEndPosition = new Vector3();
    private ArrayLutTable<Tuple<Float, Float>, PowerslideEntry, Value2ToIndexFunction> lutTable = new ArrayLutTable<>(new Value2ToIndexFunction(speedHelper, rotationHelper));

    private float probableStartSpeed = 0;
    private float probableStartAngle = 0;
    private Vector2 startPosition;

    private float stateSetStartSpeed = 0;

    public ControlsOutput processInput() {
        final GameData gameData = GameData.current();
        final CarData controlCar = gameData.getCarData();
        final AdvancedRenderer renderer = gameData.getAdvancedRenderer();
        float dt = gameData.getDt();

        timer += Math.min(dt, 0.1f);

        drawDebugLines(gameData, controlCar, renderer);
        ControlsOutput output = new ControlsOutput();

        switch (state) {
            case YEET:
                if (this.timer > 2f) {
                    this.state = State.RESET;
                }

                break;
            case RESET: {
                this.timer = 0.0f;
                this.state = State.INIT;
                this.lutSet = false;

                this.lutTry++;
                if (lutTry >= 3) {
                    float avgTime = this.currentValue / this.lutTry;
                    float avgSpeed = this.endSpeed / this.lutTry;
                    Matrix2x2 startOrient = Matrix2x2.fromRotation((float) new Vector2(-1, 0).angle());
                    Vector2 avgPos = this.currentEndPosition.div(this.lutTry).flatten().sub(this.startPosition).dot(startOrient);
                    System.out.println((this.lutTable.getKeyToIndexFunction().i2ToIndex(this.speedInd, this.rotationInd)) + "(" + this.rotationInd + "/" + this.rotationHelper.getMaxIndex() + ") (" + this.speedInd + "/" + this.speedHelper.getMaxIndex() + "): " + avgPos + " " + avgTime);

                    if (avgTime <= RLConstants.tickFrequency * 2.1f) {
                        avgTime = 0;
                        avgPos = new Vector2();
                    }

                    this.lutTable.setWithIndex((this.lutTable.getKeyToIndexFunction().i2ToIndex(this.speedInd, this.rotationInd)), new PowerslideEntry(avgTime, avgSpeed, avgPos));
                    this.lutTry = 0;
                    this.currentValue = 0;
                    this.endSpeed = 0;
                    this.currentEndPosition = new Vector3();
                    this.speedInd++;
                    if (this.speedInd > this.speedHelper.getMaxIndex()) {
                        this.speedInd = 0;
                        this.rotationInd++;

                        if (this.rotationInd > this.rotationHelper.getMaxIndex()) {
                            this.rotationInd = 0;
                            System.out.println("############");

                            try {
                                var file = new File("C:\\Users\\gcaker\\Desktop\\lut\\boi.lut");
                                if (file.exists())
                                    file.delete();
                                file.createNewFile();

                                ObjectOutputStream s = new ObjectOutputStream(new FileOutputStream(file.getAbsoluteFile()));
                                s.writeObject(this.lutTable);
                                s.flush();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            System.exit(0);
                        }
                    }
                }

                this.maneuver = new DriftControllerManeuver();
                this.maneuver.targetDirection = new Vector3(new Vector2(-1, 0).rotateBy((Math.PI / 180) * Math.min(179.9f /*always rotate around the right for consistency reasons*/, Math.max(0, this.rotationHelper.getValueForIndex(this.rotationInd)/* + Math.random() * 17*/))), 0);

                this.trail.clear();
                this.stateSetStartSpeed = this.speedHelper.getValueForIndex(speedInd);
                RLBotDll.setGameState(new GameState()
                        .withGameInfoState(new GameInfoState().withGameSpeed(1f))
                        .withBallState(new BallState().withPhysics(new PhysicsState()
                                .withLocation(new DesiredVector3(3500f, 0f, BallData.COLLISION_RADIUS))
                                .withVelocity(new DesiredVector3(0f, 0f, 0f))
                        ))
                        .withCarState(controlCar.playerIndex, new CarState().withPhysics(new PhysicsState()
                                .withLocation(new DesiredVector3(1000f, 2000f, RLConstants.carElevation + 5f))
                                .withRotation(new DesiredRotation(0f, (float) Math.PI, 0f))
                                .withVelocity(new DesiredVector3(-(stateSetStartSpeed), 0f, 0f))
                                .withAngularVelocity(new DesiredVector3(0f, 0f, 0f))
                        )).buildPacket());

                output.withThrottle(0.03f);
                break;
            }
            case INIT: {

                if (timer > RLConstants.tickFrequency * 3 && MathUtils.floatsAreEqual((float) controlCar.forward().dot(controlCar.velocity), this.stateSetStartSpeed, 40f)) {
                    this.state = State.RUN;
                    this.timer = 0;

                    this.probableStartSpeed = (float) controlCar.forward().dot(controlCar.velocity);
                    this.probableStartAngle = (float) controlCar.forward().flatten().correctionAngle(maneuver.targetDirection.flatten());
                    this.probableStartAngle = (float) (this.probableStartAngle * (180f / Math.PI));
                    this.startPosition = controlCar.position.flatten();

                    //System.out.println(this.probableStartSpeed+" : "+this.probableStartAngle);
                    //System.out.println("Actual: "+this.speedHelper.getValueForIndex(speedInd)+" : "+this.rotationHelper.getValueForIndex(this.rotationInd));
                    output.withThrottle(0.03f);
                } else {
                    output.withThrottle(0.03f);
                    if (timer > 0.1f) {
                        System.out.println(controlCar.forward().dot(controlCar.velocity));
                        DriveManeuver.speedController(dt, output, (float) controlCar.forward().dot(controlCar.velocity), this.stateSetStartSpeed, this.stateSetStartSpeed, 0.5f, true);
                    }
                }
                break;
            }
            case RUN: {

                if (this.timer > 2f)
                    this.state = State.RESET;

                this.maneuver.step(dt, output);
                if (this.maneuver.isDone() && !this.lutSet) {
                    this.lutSet = true;
                    this.currentValue += this.timer;
                    this.endSpeed += controlCar.forward().dot(controlCar.velocity);
                    this.currentEndPosition = this.currentEndPosition.add(controlCar.position);
                    System.out.println("> (" + this.speedHelper.getValueForIndex(this.speedInd) + ", " + this.rotationHelper.getValueForIndex(this.rotationInd) + "): " + this.timer + " " + controlCar.forward().dot(controlCar.velocity));
                    this.timer = Math.max(this.timer, 1.9f);
                }

                // predict end result
                if (false) {
                    // this.lutTable.set(new Tuple<>(this.speedHelper.getValueForIndex(this.speedInd), this.rotationHelper.getValueForIndex(this.rotationInd)),
                    // new Tuple<>(avgTime, new Tuple<>(avgSpeed, avgPos)));

                    var coolLut = LutManager.get().getDriftLut();

                    float interpX = InterpolationUtil.bilinear((i1, i2) -> {
                        float v1 = this.speedHelper.getValueForIndexClip(i1);
                        float v2 = this.rotationHelper.getValueForIndexClip(i2);
                        float val = coolLut.get(new Tuple<>(v1, v2)).finalPos.x;
                        return val;
                    }, this.speedHelper.getFloatIndexForValue(this.probableStartSpeed), this.rotationHelper.getFloatIndexForValue(Math.abs(this.probableStartAngle)));

                    float interpY = InterpolationUtil.bilinear((i1, i2) -> {
                        float v1 = this.speedHelper.getValueForIndexClip(i1);
                        float v2 = this.rotationHelper.getValueForIndexClip(i2);
                        return coolLut.get(new Tuple<>(v1, v2)).finalPos.y;
                    }, this.speedHelper.getFloatIndexForValue(this.probableStartSpeed), this.rotationHelper.getFloatIndexForValue(Math.abs(this.probableStartAngle)));
                    interpY *= Math.signum(this.probableStartAngle); // Flip right component if necessary

                    var startOrient = Matrix2x2.fromRotation((float) new Vector2(-1, 0).angle());

                    {
                        float x = this.speedHelper.getFloatIndexForValue(this.probableStartSpeed);
                        float y = this.rotationHelper.getFloatIndexForValue(Math.abs(this.probableStartAngle));

                        for (Integer xT : List.of((int) x, (int) Math.ceil(x))) {
                            for (Integer yT : List.of((int) y, (int) Math.ceil(y))) {
                                var endPos = coolLut.get(new Tuple<>(this.speedHelper.getValueForIndex(xT), this.rotationHelper.getValueForIndex(yT))).finalPos;
                                endPos = endPos.mul(1, Math.signum(this.probableStartAngle));
                                endPos = endPos.dot(startOrient).add(this.startPosition);

                                renderer.drawCentered3dCube(Color.BLACK, endPos.withZ(20), 50);
                                renderer.drawCentered3dCube(Color.BLACK, endPos.withZ(20), 10);
                                renderer.drawLine3d(Color.YELLOW.darker().darker(), endPos.withZ(20), endPos.withZ(20).add(this.maneuver.targetDirection.mul(20)));
                            }
                        }
                    }
                    {
                        var endPos = new Vector2(interpX, interpY).dot(startOrient).add(this.startPosition);

                        renderer.drawCentered3dCube(Color.RED, endPos.withZ(20), 50);
                        renderer.drawCentered3dCube(Color.RED, endPos.withZ(20), 10);
                        renderer.drawLine3d(Color.YELLOW, endPos.withZ(20), endPos.withZ(20).add(this.maneuver.targetDirection.mul(50)));
                    }
                }


                // Remove old trail parts
                {
                    if (this.trail.remainingCapacity() <= 1)
                        this.trail.remove();

                    this.trail.add(new Tuple<>(controlCar, output.holdHandbrake()));
                }

                // Draw trail
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

    private void drawDebugLines(GameData input, CarData myCar, AdvancedRenderer renderer) {

        renderer.drawString2d("BallP: " + input.getBallData().position, Color.WHITE, new Point(10, 150), 1, 1);
        renderer.drawString2d("BallV: " + input.getBallData().velocity, Color.WHITE, new Point(10, 170), 1, 1);
        renderer.drawString2d("Car: " + myCar.position, Color.WHITE, new Point(10, 190), 1, 1);
        renderer.drawString2d(String.format("CarSpeedXY: %.1f", myCar.velocity.dot(myCar.forward())), Color.WHITE, new Point(10, 210), 1, 1);
        renderer.drawString2d("Ang: " + myCar.angularVelocity, Color.WHITE, new Point(10, 230), 1, 1);
        //renderer.drawString2d("Nose: " + myCar.forward(), Color.WHITE, new Point(10, 250), 1, 1);
        //renderer.drawString2d("CarF: " + myCar.forward(), Color.WHITE, new Point(10, 250), 1, 1);
        float accel = (float) (myCar.velocity.dot(myCar.forward()) - lastSpeed);
        lastSpeed = (float) myCar.velocity.dot(myCar.forward());
        //accel -= CarData.driveForceForward(new ControlsOutput().withThrottle(1), (float) myCar.velocity.dot(myCar.forward()), 0, 0);
        renderer.drawString2d(String.format("Accel: % 4.1f", accel / GameData.current().getDt()), Color.WHITE, new Point(10, 250), 1, 1);

    }

    enum State {
        YEET,
        RESET,
        INIT,
        RUN
    }

}
