package yangbot;

import rlbot.Bot;
import rlbot.ControllerState;
import rlbot.cppinterop.RLBotDll;
import rlbot.flat.BoxShape;
import rlbot.flat.GameTickPacket;
import rlbot.gamestate.*;
import yangbot.cpp.CarCollisionInfo;
import yangbot.cpp.YangBotCppInterop;
import yangbot.cpp.YangBotJNAInterop;
import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.DataPacket;
import yangbot.input.GameData;
import yangbot.input.fieldinfo.BoostManager;
import yangbot.manuever.TurnManuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.ControlsOutput;
import yangbot.vector.Matrix3x3;
import yangbot.vector.Vector3;

import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

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
    private TurnManuver turnManuver = new TurnManuver();

    public TestBot(int playerIndex) {
        this.playerIndex = playerIndex;
    }

    private long runner = 0;

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

        final float testDuration = 4;

        switch (state) {
            case RESET: {
                timer = 0.0f;

                state = State.INIT;
                break;
            }
            case INIT: {

                if (timer >= 0.5f) {
                    turnManuver = new TurnManuver();
                    GameState st = new GameState()
                            .withCarState(this.playerIndex, new CarState()
                                    .withBoostAmount(100f)
                                    .withPhysics(new PhysicsState()
                                            .withLocation(new DesiredVector3(0f, 0f, 1000f))
                                            .withAngularVelocity(new DesiredVector3((float) (Math.random() * 5 * 2 - 5), (float) (Math.random() * 5 * 2 - 5), (float) (Math.random() * 5 * 2 - 5)))
                                            .withVelocity(new DesiredVector3((float) (Math.random() * 3000 - 1500), 4000f, 1900f + (float) (Math.random() * 1500)))
                                            .withRotation(new DesiredRotation((float) (Math.random() * Math.PI * 4 - 2 * Math.PI), (float) (Math.random() * Math.PI * 4 - 2 * Math.PI), (float) (Math.random() * Math.PI * 4 - 2 * Math.PI)))
                                    )
                            )
                        /*.withBallState(new BallState().withPhysics(new PhysicsState()
                                .withLocation(new DesiredVector3(0f, 0f, 1000f))
                                .withAngularVelocity(new DesiredVector3(10000f, 0f, 0f))
                                .withVelocity(new DesiredVector3(0f, 0f, -1f))
                                .withRotation(new DesiredRotation(0f, 0f, 0f))
                        )
                        */;

                    RLBotDll.setGameState(st.buildPacket());
                    //float dist = DriveManuver.maxDistance((float) car.velocity.magnitude(), testDuration);
                    //System.out.println("Predicted dist: "+dist);
                    startPos = car.position;
                    state = State.RUN;
                    timer = 0;
                }
                break;
            }
            case RUN: {
                if (timer > 4)
                    state = State.RESET;
                // Capture data
                /*speedMap.put(timer, (float) car.position.distance(startPos));
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
                }*/

                float[] data = YangBotCppInterop.ballstep(ball.position, ball.velocity, ball.spin);
                if (data.length > 0) {
                    Vector3[] positions = new Vector3[data.length / 3];

                    for (int i = 0; i < positions.length; i++) {
                        positions[i] = new Vector3(data[i * 3], data[i * 3 + 1], data[i * 3 + 2]);
                    }

                    Vector3 lastPos = positions[0];
                    for (int i = 0; i < positions.length; i++) {
                        if (lastPos.distance(positions[i]) >= 50) {
                            renderer.drawLine3d(Color.RED, lastPos, positions[i]);
                            lastPos = positions[i];
                        }
                    }
                }

                /*data = YangBotCppInterop.getSurfaceCollision(controlCar.position, 60);
                if (data.length > 0) {
                    Vector3 start = new Vector3(data[0], data[1], data[2]);
                    Vector3 direction = new Vector3(data[3], data[4], data[5]);

                    renderer.drawCentered3dCube(Color.RED, controlCar.position, 50);
                    if (direction.magnitude() > 0) {
                        renderer.drawLine3d(Color.YELLOW, start, start.add(direction.mul(150)));
                        renderer.drawCentered3dCube(Color.GREEN, start, 100);
                    }
                }*/

                Optional<CarCollisionInfo> simulateCar = YangBotJNAInterop.simulateCarWallCollision(controlCar);
                if (simulateCar.isPresent()) {
                    CarCollisionInfo carCollisionInfo = simulateCar.get();
                    Vector3 start = new Vector3(carCollisionInfo.impact().start());
                    Vector3 direction = new Vector3(carCollisionInfo.impact().direction());
                    float simulationTime = carCollisionInfo.carData().elapsedSeconds();
                    Matrix3x3 orientation = Matrix3x3.eulerToRotation(new Vector3(carCollisionInfo.carData().eulerRotation()));

                    turnManuver.target = Matrix3x3.roofTo(direction);
                    turnManuver.step(dt, output);

                    renderer.drawCentered3dCube(Color.RED, controlCar.position, 50);

                    renderer.drawLine3d(Color.YELLOW, start, start.add(direction.mul(150)));
                    if (simulationTime >= 2f / 60f)
                        renderer.drawString2d(String.format("Arriving in: %.1f", simulationTime), Color.WHITE, new Point(400, 400), 2, 2);

                    {
                        BoxShape realhitbox = controlCar.hitbox;
                        Vector3 hitbox = new Vector3(realhitbox.length(), realhitbox.width(), realhitbox.height()).mul(1.5f);

                        Color c = Color.RED;
                        Vector3 p = start;
                        Vector3 hitboxOffset = new Vector3(13.88f, 0f, 20.75f);

                        Vector3 f = orientation.forward();
                        Vector3 u = orientation.up();
                        Vector3 r = orientation.left();

                        p = p.add(f.mul(hitboxOffset.x)).add(r.mul(hitboxOffset.y)).add(u.mul(hitboxOffset.z));

                        //Vector3 fL = f.mul(hitbox.length() / 2);
                        //Vector3 rW = r.mul(hitbox.width() / 2);
                        //Vector3 uH = u.mul(hitbox.height() / 2);

                        Vector3 fL = f.mul(hitbox.x / 2);
                        Vector3 rW = r.mul(hitbox.y / 2);
                        Vector3 uH = u.mul(hitbox.z / 2);

                        renderer.drawLine3d(c, p.add(fL).add(uH).add(rW), p.add(fL).add(uH).sub(rW));
                        renderer.drawLine3d(c, p.add(fL).sub(uH).add(rW), p.add(fL).sub(uH).sub(rW));
                        renderer.drawLine3d(c, p.sub(fL).add(uH).add(rW), p.sub(fL).add(uH).sub(rW));
                        renderer.drawLine3d(c, p.sub(fL).sub(uH).add(rW), p.sub(fL).sub(uH).sub(rW));

                        renderer.drawLine3d(c, p.add(fL).add(uH).add(rW), p.sub(fL).add(uH).add(rW));
                        renderer.drawLine3d(c, p.add(fL).sub(uH).add(rW), p.sub(fL).sub(uH).add(rW));
                        renderer.drawLine3d(c, p.add(fL).add(uH).sub(rW), p.sub(fL).add(uH).sub(rW));
                        renderer.drawLine3d(c, p.add(fL).sub(uH).sub(rW), p.sub(fL).sub(uH).sub(rW));

                        renderer.drawLine3d(c, p.add(fL).add(uH).add(rW), p.add(fL).sub(uH).add(rW));
                        renderer.drawLine3d(c, p.sub(fL).add(uH).add(rW), p.sub(fL).sub(uH).add(rW));
                        renderer.drawLine3d(c, p.add(fL).add(uH).sub(rW), p.add(fL).sub(uH).sub(rW));
                        renderer.drawLine3d(c, p.sub(fL).add(uH).sub(rW), p.sub(fL).sub(uH).sub(rW));


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
