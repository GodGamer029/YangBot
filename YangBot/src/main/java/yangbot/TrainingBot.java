package yangbot;

import rlbot.Bot;
import rlbot.ControllerState;
import rlbot.flat.GameTickPacket;
import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.DataPacket;
import yangbot.input.GameData;
import yangbot.input.fieldinfo.BoostManager;
import yangbot.strategy.DefaultStrategy;
import yangbot.strategy.Strategy;
import yangbot.util.AdvancedRenderer;
import yangbot.util.ControlsOutput;
import yangbot.vector.Vector3;

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
        float dt = Math.max(input.gameInfo.secondsElapsed() - lastTick, 0f);

        AdvancedRenderer renderer = AdvancedRenderer.forBotLoop(this);
        CarData car = input.car;
        BallData ball = input.ball;
        if (ball.position.distance(lastBallPos) > Math.max(700, ball.velocity.mul(0.5f).magnitude())) {
            System.out.println("Ball teleported, new exercise! (" + ball.position.distance(lastBallPos) + ")");
            state = State.RESET;
        }
        lastBallPos = ball.position;

        GameData.current().update(input.car, input.ball, input.allCars, input.gameInfo, dt, renderer);

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
                        System.err.println("Circular Strategy (" + currentPlan.getClass().getSimpleName() + ")! Defaulting to DefaultStrategy");
                        System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                        currentPlan = new DefaultStrategy();
                    }
                }

                currentPlan.step(dt, output);
                break;
        }

        // Print Throttle info
        {

            renderer.drawString2d(String.format("Yaw: %.1f", output.getYaw()), Color.WHITE, new Point(10, 400), 1, 1);
            renderer.drawString2d(String.format("Pitch: %.1f", output.getPitch()), Color.WHITE, new Point(10, 420), 1, 1);
            renderer.drawString2d(String.format("Roll: %.1f", output.getRoll()), Color.WHITE, new Point(10, 440), 1, 1);
            renderer.drawString2d(String.format("Steer: %.2f", output.getSteer()), Color.WHITE, new Point(10, 460), 1, 1);
            renderer.drawString2d(String.format("Throttle: %.2f", output.getThrottle()), Color.WHITE, new Point(10, 480), 1, 1);

            renderer.drawString2d(String.format("State: %s", state.name()), Color.WHITE, new Point(10, 510), 2, 2);
            if (state == State.RUN)
                renderer.drawString2d(String.format("Strategy: %s", currentPlan.getClass().getSimpleName()), Color.WHITE, new Point(10, 570), 2, 2);
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