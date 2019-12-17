package yangbot;

import rlbot.Bot;
import rlbot.ControllerState;
import rlbot.flat.GameTickPacket;
import yangbot.input.*;
import yangbot.input.fieldinfo.BoostManager;
import yangbot.manuever.RegularKickoffManeuver;
import yangbot.strategy.AfterKickoffStrategy;
import yangbot.strategy.DefaultStrategy;
import yangbot.strategy.Strategy;
import yangbot.util.AdvancedRenderer;
import yangbot.util.ControlsOutput;

import java.awt.*;

public class YangBot implements Bot {

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
    private RegularKickoffManeuver kickoffManeuver = null;
    private Strategy currentPlan = null;
    private boolean hasSetPriority = false;

    public YangBot(int playerIndex) {
        this.playerIndex = playerIndex;
    }

    private ControlsOutput processInput(DataPacket input) {
        float dt = Math.max(input.gameInfo.secondsElapsed() - lastTick, 1 / 60f);

        if (lastTick > 0)
            timer += Math.min(dt, 0.5f);

        AdvancedRenderer renderer = AdvancedRenderer.forBotLoop(this);
        CarData car = input.car;
        BallData ball = input.ball;

        float det = RLConstants.tickFrequency;
        for (float time = 0; time < RLConstants.gameLatencyCompensation; time += det) {
            car.step(new ControlsOutput(), det);
            ball.step(det);
        }

        GameData.current().update(input.car, input.ball, input.allCars, input.gameInfo, dt, renderer);

        drawDebugLines(input, car);

        ControlsOutput output = new ControlsOutput();

        switch (state) {
            case RESET: {
                timer = 0.0f;
                if (RegularKickoffManeuver.isKickoff()) {
                    kickoffManeuver = new RegularKickoffManeuver();
                    state = State.KICKOFF;
                    output.withThrottle(1);
                    output.withBoost(true);
                } else {
                    currentPlan = new DefaultStrategy();
                    state = State.RUN;
                }
                break;
            }
            case KICKOFF: {
                kickoffManeuver.step(dt, output);
                if (kickoffManeuver.isDone()) {
                    state = State.RUN;
                    currentPlan = new AfterKickoffStrategy();
                    currentPlan.planStrategy();
                }
                break;
            }
            case RUN: {
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
        }

        if (car.hasWheelContact && output.holdBoost() && car.velocity.magnitude() >= CarData.MAX_VELOCITY - 20)
            output.withBoost(false);

        // Print Throttle info
        {
            renderer.drawString2d("State: " + state.name(), Color.WHITE, new Point(10, 270), 2, 2);
            if (state != State.KICKOFF)
                renderer.drawString2d("Strategy: " + (currentPlan == null ? "null" : currentPlan.getClass().getSimpleName()), Color.WHITE, new Point(10, 310), 2, 2);
            renderer.drawString2d(String.format("Yaw: %.1f", output.getYaw()), Color.WHITE, new Point(10, 350), 1, 1);
            renderer.drawString2d(String.format("Pitch: %.1f", output.getPitch()), Color.WHITE, new Point(10, 370), 1, 1);
            renderer.drawString2d(String.format("Roll: %.1f", output.getRoll()), Color.WHITE, new Point(10, 390), 1, 1);
            renderer.drawString2d(String.format("Steer: %.2f", output.getSteer()), Color.WHITE, new Point(10, 410), 1, 1);
            renderer.drawString2d(String.format("Throttle: %.2f", output.getThrottle()), Color.WHITE, new Point(10, 430), 1, 1);
        }

        return output;
    }

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

        if (packet.playersLength() <= playerIndex || packet.ball() == null) {
            GameData.timeOfMatchStart = packet.gameInfo().secondsElapsed();
            state = State.RESET;
            return new ControlsOutput().withBoost(true);
        }

        if (!packet.gameInfo().isRoundActive()) {
            GameData.timeOfMatchStart = packet.gameInfo().secondsElapsed();
            state = State.RESET;
            return new ControlsOutput().withThrottle(1).withBoost(true);
        }

        /*if(state != State.RESET && state != State.KICKOFF && packet.gameInfo().isKickoffPause() && new Vector3(packet.players(this.playerIndex).physics().velocity()).magnitude() < 10){
            GameData.timeOfMatchStart = packet.gameInfo().secondsElapsed();
            state = State.RESET;
        }*/

        if (GameData.timeOfMatchStart < 0)
            GameData.timeOfMatchStart = packet.gameInfo().secondsElapsed();

        AdvancedRenderer r = AdvancedRenderer.forBotLoop(this);
        r.startPacket();

        BoostManager.loadGameTickPacket(packet);

        DataPacket dataPacket = new DataPacket(packet, playerIndex);

        ControlsOutput controlsOutput = new ControlsOutput();
        //long ms = System.nanoTime();
        try {
            controlsOutput = processInput(dataPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }
        /*realCount++;
        if(realCount >= 100){
            all += ((System.nanoTime() - ms) / 1000000f);
            count++;
            System.out.println("It took "+(all / count));
        }*/

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
        RUN,
        KICKOFF
    }
}
