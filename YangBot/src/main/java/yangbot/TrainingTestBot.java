package yangbot;

import rlbot.Bot;
import rlbot.ControllerState;
import rlbot.flat.GameTickPacket;
import yangbot.input.*;
import yangbot.input.fieldinfo.BoostManager;
import yangbot.prediction.YangBallPrediction;
import yangbot.util.AdvancedRenderer;
import yangbot.util.hitbox.YangCarHitbox;
import yangbot.vector.Vector3;

import java.awt.*;
import java.util.Optional;

public class TrainingTestBot implements Bot {

    private final int playerIndex;
    private float lastTick = -1;
    private State state = State.RESET;
    private Vector3 lastBallPos = new Vector3();

    private YangBallPrediction hitPrediction = null;
    private CarData predictedHitCar = null;
    private Vector3 predictedContactPoint = null;
    private Vector3 predictedContactNormal = null;
    private Vector3 predictedHitBall = null;
    private YangCarHitbox hitboxAtActualBallHit;
    private Vector3 positionAtActualBallHit;
    private float lastActualBallHit = 0;

    private Vector3 lastBallPos2 = null;
    private Vector3 lastCarPos = null;
    private Vector3 lastCarAng = null;
    private Vector3 lastCarNose = null;

    public TrainingTestBot(int playerIndex) {
        this.playerIndex = playerIndex;
    }

    @Override
    public int getIndex() {
        return playerIndex;
    }

    private ControlsOutput processInput(DataPacket input) {
        float dt = Math.max(input.gameInfo.secondsElapsed() - lastTick, 0f);

        AdvancedRenderer renderer = AdvancedRenderer.forBotLoop(this);
        CarData car = input.car;
        BallData ball = input.ball;
        if (ball.position.distance(lastBallPos) > Math.max(700, ball.velocity.mul(0.5f).magnitude())) {
            System.out.println("#################");
            state = State.RESET;
        }
        lastBallPos = ball.position;

        GameData.current().update(input.car, new ImmutableBallData(input.ball), input.allCars, input.gameInfo, dt, renderer);
        RLConstants.gravity = new Vector3(0, 0, -0.000001f);
        YangBallPrediction ballPrediction = ball.makeBallPrediction(RLConstants.tickFrequency, 3);
        ballPrediction.draw(renderer, Color.RED, 2f);

        drawDebugLines(input, car);

        ControlsOutput output = new ControlsOutput();

        switch (state) {
            case RESET: {
                state = State.RUN;


                CarData simCar = new CarData(car);
                simCar.elapsedSeconds = 0;

                BallData simBall = new BallData(ball);
                simBall.hasBeenTouched = false;

                FoolGameData foolGameData = GameData.current().fool();
                Vector3 simContact = null;

                for (float time = 0; time < 3; time += dt) {
                    ControlsOutput simControls = new ControlsOutput();

                    foolGameData.foolCar(simCar);

                    simCar.step(simControls, dt);

                    simContact = simBall.collide(simCar);

                    if (simBall.hasBeenTouched) {


                        break;
                    }

                    Optional<YangBallPrediction.YangPredictionFrame> frameOptional = ballPrediction.getFrameAtRelativeTime(time - 10f * RLConstants.tickFrequency);
                    if (!frameOptional.isPresent())
                        break;
                    simBall = frameOptional.get().ballData.makeMutable();
                    simBall.hasBeenTouched = false;
                }

                if (simBall.hasBeenTouched) {
                    YangBallPrediction simBallPred = simBall.makeBallPrediction(RLConstants.tickFrequency, 2);

                    this.hitPrediction = simBallPred;
                    this.predictedHitCar = simCar;
                    this.predictedHitBall = simBall.position;
                    this.predictedContactPoint = simContact;
                    this.predictedContactNormal = simBall.position.sub(simContact).normalized();
                }
                break;
            }
            case RUN:
                if (this.hitPrediction != null) {
                    this.hitPrediction.draw(renderer, Color.MAGENTA, 2f);
                    this.predictedHitCar.hitbox.draw(renderer, this.predictedHitCar.position, 1, Color.GREEN);
                    //renderer.drawCentered3dCube(Color.GREEN, this.predictedContactPoint, 5);
                    //renderer.drawLine3d(Color.MAGENTA, this.predictedContactPoint, this.predictedContactPoint.add(this.predictedContactNormal.mul(100)));
                    //renderer.drawCentered3dCube(Color.BLUE, this.predictedHitBall, 200);

                    if (ball.hasBeenTouched && this.lastBallPos2 != null) {
                        if (ball.latestTouch.gameSeconds > this.lastActualBallHit) {
                            this.lastActualBallHit = ball.latestTouch.gameSeconds;
                            this.hitboxAtActualBallHit = car.hitbox;
                            this.positionAtActualBallHit = car.position;
                            /*System.out.println("Ball Velocity after touch: "+ball.velocity);
                            System.out.println("Contact Position: "+ball.latestTouch.position.toString());
                            System.out.println("Contact Normal: "+ball.latestTouch.normal.toString(30));
                            System.out.println("Ball position before touch: "+this.lastBallPos2.toString(30));
                            System.out.println("Car position before touch: "+this.lastCarPos.toString(30));
                            System.out.println("Car angular before touch: "+this.lastCarAng.toString(30));
                            System.out.println("Car nose before touch: "+this.lastCarNose.toString(30));
                            System.out.println("Car position after: "+car.position);
                            System.out.println("Car velocity after: "+car.velocity);
                            System.out.println("Car angular after: "+car.angularVelocity);
                            System.out.println("Ball position after: "+ball.position);
                            System.out.println("Ball velocity after: "+ball.velocity);
                            System.out.println("Ball angular after: "+ball.angularVelocity);*/
                        }
                        if (this.lastActualBallHit > 0) {
                            this.hitboxAtActualBallHit.draw(renderer, this.positionAtActualBallHit, 1, Color.BLUE);
                        }
                        //renderer.drawCentered3dCube(Color.BLUE, ball.latestTouch.position, 5);
                        //renderer.drawLine3d(Color.RED, ball.latestTouch.position, ball.latestTouch.position.add(ball.latestTouch.normal.mul(100)));
                    }

                    lastBallPos2 = ball.position;
                    lastCarPos = car.position;
                    lastCarNose = car.forward();
                    lastCarAng = car.angularVelocity;
                    //renderer.drawCentered3dCube(Color.CYAN, ball.position, 200);
                    //car.hitbox.draw(renderer, car.position, 1, Color.BLUE);
                }
                break;
        }

        // Print Throttle info
        {

            renderer.drawString2d(String.format("Yaw: %.1f", output.getYaw()), Color.WHITE, new Point(10, 390), 1, 1);
            renderer.drawString2d(String.format("Pitch: %.1f", output.getPitch()), Color.WHITE, new Point(10, 410), 1, 1);
            renderer.drawString2d(String.format("Roll: %.1f", output.getRoll()), Color.WHITE, new Point(10, 430), 1, 1);
            renderer.drawString2d(String.format("Steer: %.2f", output.getSteer()), Color.WHITE, new Point(10, 450), 1, 1);
            renderer.drawString2d(String.format("Throttle: %.2f", output.getThrottle()), Color.WHITE, new Point(10, 470), 1, 1);
            renderer.drawString2d(String.format("Slide: %s", output.holdHandbrake() ? "Enabled" : "Disabled"), output.holdHandbrake() ? Color.GREEN : Color.WHITE, new Point(10, 490), 1, 1);

            renderer.drawString2d(String.format("State: %s", state.name()), Color.WHITE, new Point(10, 510), 2, 2);
        }

        return output;
    }

    private void drawDebugLines(DataPacket input, CarData myCar) {
        AdvancedRenderer renderer = AdvancedRenderer.forBotLoop(this);

        renderer.drawString2d("Team: " + input.car.team, Color.WHITE, new Point(10, 230), 1, 1);
        renderer.drawString2d("BallP: " + input.ball.position, Color.WHITE, new Point(10, 250), 1, 1);
        renderer.drawString2d("BallV: " + input.ball.velocity, Color.WHITE, new Point(10, 270), 1, 1);
        renderer.drawString2d("Car: " + myCar.position, Color.WHITE, new Point(10, 290), 1, 1);
        renderer.drawString2d(String.format("CarSpeedXY: %.1f", myCar.velocity.flatten().magnitude()), Color.WHITE, new Point(10, 310), 1, 1);
        renderer.drawString2d("Ang: " + myCar.angularVelocity, Color.WHITE, new Point(10, 330), 1, 1);
        renderer.drawString2d("Nose: " + myCar.forward(), Color.WHITE, new Point(10, 350), 1, 1);
    }

    @Override
    public ControllerState processInput(GameTickPacket packet) {
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
        System.out.println("Retiring Training bot " + playerIndex);
    }

    enum State {
        RESET,
        RUN
    }
}
