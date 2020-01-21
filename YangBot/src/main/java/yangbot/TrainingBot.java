package yangbot;

import rlbot.Bot;
import rlbot.ControllerState;
import rlbot.flat.GameTickPacket;
import yangbot.input.*;
import yangbot.input.fieldinfo.BoostManager;
import yangbot.strategy.DefaultStrategy;
import yangbot.strategy.Strategy;
import yangbot.util.AdvancedRenderer;
import yangbot.util.math.vector.Vector3;

import java.awt.*;

public class TrainingBot implements Bot {

    private final int playerIndex;
    private float lastTick = -1;
    private State state = State.RESET;
    private Strategy currentPlan = null;
    private Vector3 lastBallPos = new Vector3();

    public TrainingBot(int playerIndex) {
        this.playerIndex = playerIndex;
    }

    @Override
    public int getIndex() {
        return playerIndex;
    }

    private ControlsOutput processInput(DataPacket input) {
        float dt = Math.max(input.gameInfo.secondsElapsed() - lastTick, RLConstants.tickFrequency);

        AdvancedRenderer renderer = AdvancedRenderer.forBotLoop(this);
        CarData car = input.car;
        BallData ball = input.ball;
        if (ball.position.distance(lastBallPos) > Math.max(700, ball.velocity.mul(0.5f).magnitude())) {
            System.out.println("#################");
            state = State.RESET;
        }
        lastBallPos = ball.position;

        GameData.current().update(input.car, new ImmutableBallData(input.ball), input.allCars, input.gameInfo, dt, renderer);
        GameData.current().getBallPrediction().draw(renderer, Color.RED, 2);
        drawDebugLines(input, car);

        ControlsOutput output = new ControlsOutput();

        switch (state) {
            case RESET:
                currentPlan = new DefaultStrategy();
                state = State.RUN;
                break;
            case RUN:
                int i = 0;
                while (currentPlan.isDone()) {
                    currentPlan = currentPlan.suggestStrategy().orElse(new DefaultStrategy());
                    currentPlan.planStrategy();
                    i++;
                    if (i == 5) {
                        //System.err.println("Circular Strategy (" + currentPlan.getClass().getSimpleName() + ")! Defaulting to DefaultStrategy");
                        //System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                        currentPlan = new DefaultStrategy();
                    }
                }

                currentPlan.step(dt, output);
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
            if (state == State.RUN)
                renderer.drawString2d(String.format("Strategy: %s", currentPlan.getClass().getSimpleName()), Color.WHITE, new Point(10, 630), 2, 2);
        }

        return output;
    }

    private void drawDebugLines(DataPacket input, CarData myCar) {
        AdvancedRenderer renderer = AdvancedRenderer.forBotLoop(this);
        renderer.drawString2d("hasWheelContact: " + input.car.hasWheelContact, input.car.hasWheelContact ? Color.GREEN : Color.RED, new Point(450, 230), 2, 2);

        renderer.drawString2d("Team: " + input.car.team, Color.WHITE, new Point(10, 230), 1, 1);
        renderer.drawString2d("BallP: " + input.ball.position, Color.WHITE, new Point(10, 250), 1, 1);
        renderer.drawString2d("BallV: " + input.ball.velocity + " mag=" + input.ball.velocity.magnitude(), Color.WHITE, new Point(10, 270), 1, 1);
        renderer.drawString2d("Car: " + myCar.position, Color.WHITE, new Point(10, 290), 1, 1);
        renderer.drawString2d(String.format("CarSpeedXY: %.1f", myCar.velocity.flatten().magnitude()), Color.WHITE, new Point(10, 310), 1, 1);
        renderer.drawString2d("Ang: " + myCar.angularVelocity, Color.WHITE, new Point(10, 330), 1, 1);
        renderer.drawString2d("dt: " + GameData.current().getDt(), Color.WHITE, new Point(10, 350), 1, 1);
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
