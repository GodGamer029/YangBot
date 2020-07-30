package yangbot;

import rlbot.Bot;
import rlbot.ControllerState;
import rlbot.cppinterop.RLBotDll;
import rlbot.flat.GameTickPacket;
import rlbot.flat.QuickChat;
import rlbot.flat.QuickChatMessages;
import rlbot.flat.QuickChatSelection;
import yangbot.input.*;
import yangbot.input.fieldinfo.BoostManager;
import yangbot.input.playerinfo.PlayerInfoManager;
import yangbot.strategy.*;
import yangbot.strategy.manuever.Maneuver;
import yangbot.strategy.manuever.kickoff.KickoffTester;
import yangbot.strategy.manuever.kickoff.SimpleKickoffManeuver;
import yangbot.util.AdvancedRenderer;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class YangBot implements Bot {

    private final int playerIndex;
    private int lastMessageId = -1;

    private State state = State.RESET;
    private float lastTick = -1;
    private Maneuver kickoffManeuver = null;
    private Strategy currentPlan = null;
    private boolean hasSetPriority = false;
    private String oldStrat = "";

    private boolean renderingActive = true;

    public YangBot(int playerIndex) {
        this.playerIndex = playerIndex;
    }

    private ControlsOutput processInput(DataPacket input) {
        float dt = Math.max(input.gameInfo.secondsElapsed() - lastTick, RLConstants.tickFrequency * 0.9f);

        AdvancedRenderer renderer = AdvancedRenderer.forBotLoop(this);
        if (!renderingActive)
            renderer = new DummyRenderer(this.playerIndex);

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
                if (KickoffTester.isKickoff() && KickoffTester.shouldGoForKickoff(car, gameData.getAllCars().stream().filter((c) -> c.team == car.team).collect(Collectors.toList()), ball)) {
                    System.out.println("##################### Kickoff");
                    this.kickoffManeuver = new SimpleKickoffManeuver();
                    this.state = State.KICKOFF;
                    output.withThrottle(1);
                    output.withBoost(true);
                } else {
                    this.currentPlan = new DefaultStrategy();
                    this.currentPlan.planStrategy();
                    this.state = State.RUN;
                }
                break;
            }
            case KICKOFF: {
                this.kickoffManeuver.step(dt, output);
                if (this.kickoffManeuver.isDone()) {
                    this.state = State.RUN;
                    this.currentPlan = new AfterKickoffStrategy();
                    this.currentPlan.planStrategy();
                }
                break;
            }
            case RUN: {
                int i = 0;
                StringBuilder circularPlanExplainer = new StringBuilder();
                circularPlanExplainer.append(this.currentPlan.getClass().getSimpleName());
                while (this.currentPlan.isDone()) {
                    this.currentPlan = currentPlan.suggestStrategy().orElse(new DefaultStrategy());
                    circularPlanExplainer.append(" -> " + this.currentPlan.getClass().getSimpleName());
                    this.currentPlan.planStrategy();

                    i++;
                    if (i == 5) {
                        System.err.println("Circular Strategy: Defaulting to DefaultStrategy (" + circularPlanExplainer.toString() + ")");
                        System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                        this.currentPlan = new DefaultStrategy();
                    }
                }

                this.currentPlan.step(dt, output);
                break;
            }
        }

        car.hitbox.draw(renderer, car.position, 1, Color.GREEN);

        if (car.hasWheelContact && output.holdBoost() && car.forward().dot(car.velocity) >= CarData.MAX_VELOCITY - 20)
            output.withBoost(false);

        // Print Throttle info
        {
            GameData.current().getBallPrediction().draw(renderer, Color.BLUE, 2);
            renderer.drawString2d("Dt: " + dt, Color.WHITE, new Point(10, 250), 1, 1);

            renderer.drawString2d("State: " + this.state.name(), Color.WHITE, new Point(10, 270), 2, 2);
            if (this.state != State.KICKOFF) {

                if (this.currentPlan != null && this.currentPlan.getClass() != RecoverStrategy.class) {
                    this.oldStrat = "Strategy: " + this.currentPlan.getClass().getSimpleName() + "\n" + this.currentPlan.getAdditionalInformation();
                } else if (this.currentPlan != null)
                    renderer.drawString2d(this.oldStrat, Color.GREEN, new Point(10, 400), 2, 2);


                renderer.drawString2d("Strategy: " + (this.currentPlan == null ? "null" : this.currentPlan.getClass().getSimpleName()), (currentPlan != null && currentPlan.getClass() == RecoverStrategy.class) ? Color.YELLOW : Color.WHITE, new Point(10, 310), 2, 2);
                if (this.currentPlan != null)
                    renderer.drawString2d(this.currentPlan.getAdditionalInformation(), Color.WHITE, new Point(10, 350), 2, 2);

                String text = this.playerIndex + ": " + (this.currentPlan == null ? "null" : this.currentPlan.getClass().getSimpleName());
                if (this.currentPlan != null)
                    text += "\n" + this.currentPlan.getAdditionalInformation();
                renderer.drawString3d(text, (this.currentPlan != null && this.currentPlan.getClass() == RecoverStrategy.class) ? Color.YELLOW : Color.WHITE, car.position.add(0, 0, 70), 1, 1);
            }
            if (true) {
                renderer.drawString2d(String.format("Yaw: %.1f", output.getYaw()), Color.WHITE, new Point(10, 400), 1, 1);
                renderer.drawString2d(String.format("Pitch: %.1f", output.getPitch()), Color.WHITE, new Point(10, 420), 1, 1);
                renderer.drawString2d(String.format("Roll: %.1f", output.getRoll()), Color.WHITE, new Point(10, 440), 1, 1);
                renderer.drawString2d(String.format("Steer: %.2f", output.getSteer()), Color.WHITE, new Point(10, 460), 1, 1);
                renderer.drawString2d(String.format("Throttle: %.2f", output.getThrottle()), output.getThrottle() < 0 ? Color.RED : Color.WHITE, new Point(10, 480), 1, 1);
            }

        }

        return output;
    }

    private void drawDebugLines(DataPacket input, CarData myCar) {
        AdvancedRenderer renderer = GameData.current().getAdvancedRenderer();

        renderer.drawString2d("BallP: " + input.ball.position, Color.WHITE, new Point(10, 150), 1, 1);
        if (false) {
            renderer.drawString2d("BallV: " + input.ball.velocity, Color.WHITE, new Point(10, 170), 1, 1);
        }
        renderer.drawString2d("Car: " + myCar.position, Color.WHITE, new Point(10, 190), 1, 1);
        renderer.drawString2d(String.format("CarSpeedXY: %.1f", myCar.velocity.flatten().magnitude()), Color.WHITE, new Point(10, 210), 1, 1);
        renderer.drawString2d(String.format("Time: %.2f", myCar.elapsedSeconds), Color.WHITE, new Point(10, 230), 1, 1);
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

        if (GameData.timeOfMatchStart < 0)
            GameData.timeOfMatchStart = packet.gameInfo().secondsElapsed();

        AdvancedRenderer r;
        if (playerIndex == 0 || playerIndex == 3)
            r = AdvancedRenderer.forBotLoop(this);
        else {
            r = new DummyRenderer(this.playerIndex);
            this.renderingActive = false;
        }

        r.startPacket();

        BoostManager.loadGameTickPacket(packet);

        DataPacket dataPacket = new DataPacket(packet, playerIndex);

        {
            var quickchats = this.receiveQuickChat(dataPacket.car);
            var needBoostChatters = quickchats.stream()
                    .filter((q) -> q.quickChatSelection() == QuickChatSelection.Information_NeedBoost)
                    // Convert to CarData
                    .map((c) -> dataPacket.allCars.stream().filter(b -> b.playerIndex == c.playerIndex()).findFirst())
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    //
                    .filter((q) -> q.team == dataPacket.car.team)
                    .collect(Collectors.toList());

            for (var needBoost : needBoostChatters) {
                needBoost.getPlayerInfo().setInactiveRotatorUntil(needBoost.elapsedSeconds + 0.7f);
            }

            var inactiveShooterChatters = quickchats.stream()
                    .filter((q) -> q.quickChatSelection() == QuickChatSelection.Information_AllYours || q.quickChatSelection() == QuickChatSelection.Information_Defending || q.quickChatSelection() == QuickChatSelection.Information_GoForIt)
                    // Convert to CarData
                    .map((c) -> dataPacket.allCars.stream().filter(b -> b.playerIndex == c.playerIndex()).findFirst())
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    //
                    .filter((q) -> q.team == dataPacket.car.team)
                    .collect(Collectors.toList());

            for (var inactiveShoot : inactiveShooterChatters) {
                inactiveShoot.getPlayerInfo().setInactiveShooterUntil(inactiveShoot.elapsedSeconds + 0.7f);
            }
        }

        ControlsOutput controlsOutput = new ControlsOutput();

        //long t = System.nanoTime();
        try {
            controlsOutput = processInput(dataPacket);
        } catch (Exception | AssertionError e) {
            e.printStackTrace();
        }
        //if(dataPacket.car.playerIndex == 0 || dataPacket.car.playerIndex == 3)
        //System.out.println("ms: "+((System.nanoTime() - t)*0.000001f));


        lastTick = dataPacket.gameInfo.secondsElapsed();

        r.finishAndSendIfDifferent();
        return controlsOutput;
    }

    @Override
    public void retire() {
        PlayerInfoManager.reset();
        System.out.println("Retiring Yang bot " + playerIndex);
    }

    protected List<QuickChat> receiveQuickChat(CarData car) {
        try {
            QuickChatMessages messages = RLBotDll.receiveQuickChat(this.playerIndex, car.team, lastMessageId);

            if (messages.messagesLength() > 0) {
                lastMessageId = messages.messages(messages.messagesLength() - 1).messageIndex();
            }

            var list = new ArrayList<QuickChat>();
            for (int i = 0; i < messages.messagesLength(); i++)
                list.add(messages.messages(i));

            return list;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    enum State {
        RESET,
        RUN,
        KICKOFF
    }
}
