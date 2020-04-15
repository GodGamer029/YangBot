package yangbot;

import rlbot.Bot;
import rlbot.ControllerState;
import rlbot.flat.GameTickPacket;
import yangbot.input.*;
import yangbot.input.fieldinfo.BoostManager;
import yangbot.strategy.AfterKickoffStrategy;
import yangbot.strategy.DefaultStrategy;
import yangbot.strategy.RecoverStrategy;
import yangbot.strategy.Strategy;
import yangbot.strategy.manuever.Maneuver;
import yangbot.strategy.manuever.kickoff.KickoffTester;
import yangbot.strategy.manuever.kickoff.SimpleKickoffManeuver;
import yangbot.util.AdvancedRenderer;

import java.awt.*;
import java.util.stream.Collectors;

public class YangBot implements Bot {

    private final int playerIndex;


    private State state = State.RESET;
    private float timer = -1.0f;
    private float lastTick = -1;
    private Maneuver kickoffManeuver = null;
    private Strategy currentPlan = null;
    private boolean hasSetPriority = false;

    public YangBot(int playerIndex) {
        this.playerIndex = playerIndex;
    }

    private ControlsOutput processInput(DataPacket input) {
        float dt = Math.max(input.gameInfo.secondsElapsed() - lastTick, RLConstants.tickFrequency);

        if (lastTick > 0)
            timer += Math.min(dt, 0.5f);

        AdvancedRenderer renderer = AdvancedRenderer.forBotLoop(this);
        final GameData gameData = GameData.current();
        CarData car = input.car;
        BallData ball = input.ball;
        {

            float det = RLConstants.tickFrequency;
            for (float time = 0; time < RLConstants.gameLatencyCompensation; time += det) {
                car.step(new ControlsOutput(), det);
                ball.step(det);
            }

            gameData.update(input.car, new ImmutableBallData(input.ball), input.allCars, input.gameInfo, dt, renderer);
        }

        drawDebugLines(input, gameData.getCarData());

        ControlsOutput output = new ControlsOutput();

        switch (state) {
            case RESET: {
                timer = 0.0f;
                if (KickoffTester.isKickoff() && KickoffTester.shouldGoForKickoff(car, gameData.getAllCars().stream().filter((c) -> c.team == car.team).collect(Collectors.toList()), ball)) {
                    kickoffManeuver = new SimpleKickoffManeuver();
                    state = State.KICKOFF;
                    output.withThrottle(1);
                    output.withBoost(true);
                } else {
                    currentPlan = new DefaultStrategy();
                    currentPlan.planStrategy();
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
                StringBuilder circularPlanExplainer = new StringBuilder();
                circularPlanExplainer.append(currentPlan.getClass().getSimpleName());
                while (currentPlan.isDone()) {
                    currentPlan = currentPlan.suggestStrategy().orElse(new DefaultStrategy());
                    circularPlanExplainer.append(" -> " + currentPlan.getClass().getSimpleName());
                    long ms = System.currentTimeMillis();
                    currentPlan.planStrategy();
                    long duration = System.currentTimeMillis() - ms;

                    i++;
                    if (i == 5) {
                        /*System.err.println("Circular Strategy: Defaulting to DefaultStrategy ("+circularPlanExplainer.toString()+")");
                        System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");*/
                        currentPlan = new DefaultStrategy();
                    }
                }

                currentPlan.step(dt, output);

                break;
            }
        }

        car.hitbox.draw(renderer, car.position, 1, Color.GREEN);

        if (car.hasWheelContact && output.holdBoost() && car.velocity.magnitude() >= CarData.MAX_VELOCITY - 20)
            output.withBoost(false);

        // Print Throttle info
        {
            GameData.current().getBallPrediction().draw(renderer, Color.BLUE, 2);
            renderer.drawString2d("Dt: " + dt, Color.WHITE, new Point(10, 240), 1, 1);

            renderer.drawString2d("State: " + state.name(), Color.WHITE, new Point(10, 270), 2, 2);
            if (state != State.KICKOFF)
                renderer.drawString2d("Strategy: " + (currentPlan == null ? "null" : currentPlan.getClass().getSimpleName()), (currentPlan != null && currentPlan.getClass() == RecoverStrategy.class) ? Color.YELLOW : Color.WHITE, new Point(10, 310), 2, 2);

            String text = this.playerIndex + ": " + (currentPlan == null ? "null" : currentPlan.getClass().getSimpleName());
            if (currentPlan != null)
                text += "\n" + currentPlan.getAdditionalInformation();
            renderer.drawString3d(text, (currentPlan != null && currentPlan.getClass() == RecoverStrategy.class) ? Color.YELLOW : Color.WHITE, car.position.add(0, 0, 70), 1, 1);


            if (false) {
                renderer.drawString2d(String.format("Yaw: %.1f", output.getYaw()), Color.WHITE, new Point(10, 350), 1, 1);
                renderer.drawString2d(String.format("Pitch: %.1f", output.getPitch()), Color.WHITE, new Point(10, 370), 1, 1);
                renderer.drawString2d(String.format("Roll: %.1f", output.getRoll()), Color.WHITE, new Point(10, 390), 1, 1);
                renderer.drawString2d(String.format("Steer: %.2f", output.getSteer()), Color.WHITE, new Point(10, 410), 1, 1);
                renderer.drawString2d(String.format("Throttle: %.2f", output.getThrottle()), Color.WHITE, new Point(10, 430), 1, 1);
            }

        }

        return output;
    }

    private void drawDebugLines(DataPacket input, CarData myCar) {
        AdvancedRenderer renderer = AdvancedRenderer.forBotLoop(this);

        renderer.drawString2d("BallP: " + input.ball.position, Color.WHITE, new Point(10, 150), 1, 1);
        if (false) {

            renderer.drawString2d("BallV: " + input.ball.velocity, Color.WHITE, new Point(10, 170), 1, 1);
        }
        renderer.drawString2d("Car: " + myCar.position, Color.WHITE, new Point(10, 190), 1, 1);
        renderer.drawString2d(String.format("CarSpeedXY: %.1f", myCar.velocity.flatten().magnitude()), Color.WHITE, new Point(10, 210), 1, 1);
        if (false) {
            renderer.drawString2d("Ang: " + myCar.angularVelocity, Color.WHITE, new Point(10, 230), 1, 1);
            renderer.drawString2d("Nose: " + myCar.forward(), Color.WHITE, new Point(10, 250), 1, 1);
        }
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
        } catch (Exception | AssertionError e) {
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
