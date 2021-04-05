package yangbot;

import rlbot.Bot;
import rlbot.ControllerState;
import rlbot.cppinterop.RLBotDll;
import rlbot.flat.GameTickPacket;
import rlbot.gamestate.GameInfoState;
import yangbot.input.*;
import yangbot.input.fieldinfo.BoostManager;
import yangbot.input.playerinfo.PlayerInfoManager;
import yangbot.strategy.DefaultStrategy;
import yangbot.strategy.RecoverStrategy;
import yangbot.strategy.Strategy;
import yangbot.util.AdvancedRenderer;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;
import yangbot.util.scenario.ScenarioUtil;

import java.awt.*;

public class TrainingTestBot implements Bot {

    private final int playerIndex;
    private float lastTick = -1;
    private State state = State.RESET;
    private Strategy currentPlan = null;
    private Vector3 lastBallPos = new Vector3();
    private float timer = 0;

    public TrainingTestBot(int playerIndex) {
        this.playerIndex = playerIndex;
    }

    @Override
    public int getIndex() {
        return playerIndex;
    }

    private ControlsOutput processInput(DataPacket input) {
        float dt = Math.max(input.gameInfo.secondsElapsed() - lastTick, RLConstants.tickFrequency * 0.5f);
        timer += dt;
        AdvancedRenderer renderer = AdvancedRenderer.forBotLoop(this);
        CarData car = input.car;

        BallData ball = input.ball;

        lastBallPos = ball.position;
        final Vector2 ownGoal = new Vector2(0, car.getTeamSign() * RLConstants.goalDistance);
        GameData.current().update(input.car, new ImmutableBallData(input.ball), input.allCars, input.gameInfo, dt, renderer);

        final YangBallPrediction ballPrediction = GameData.current().getBallPrediction();
        drawDebugLines(input, car);

        ControlsOutput output = new ControlsOutput();

        switch (state) {
            case RESET:
                if (this.timer > 0.5f) {
                    this.state = State.INIT;
                    RLBotDll.setGameState(ScenarioUtil.encodedGameStateToGameState("eWFuZ3YxOmMocD0oMzcwMC40NSwtMjI2My4zMSwyMi4yOSksdj0oLTE4NC42NCwxMDgyLjM2LC0zMTcuMzApLGE9KDAuNjUsLTAuMDAsLTAuNzIpLG89KC0wLjAxLDEuNTcsMC4wMCkpLGIocD0oMzg3NC45NiwtMTQzNS4wMyw5OC45Myksdj0oLTM1Mi45NSwxMzk1LjgwLC04NS4wOCksYT0oLTMuMDIsLTAuODQsLTQuNzApKTs")
                            .withGameInfoState(new GameInfoState().withGameSpeed(1f))

                            .buildPacket());
                    this.timer = 0;
                }
                break;
            case INIT:
                if (timer >= 0.05f) {
                    currentPlan = new DefaultStrategy();
                    currentPlan.planStrategy();
                    state = State.RUN;
                    timer = 0;


                }
                break;
            case RUN:
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

                if (this.timer > 2.5f)
                    this.state = State.RESET;

                break;
        }

        // Print Throttle info
        {
            car.hitbox.draw(renderer, car.position, 1, Color.GREEN);
            GameData.current().getBallPrediction().draw(renderer, Color.BLUE, 2);

            renderer.drawString2d(String.format("Yaw: %.1f", output.getYaw()), Color.WHITE, new Point(10, 390), 1, 1);
            renderer.drawString2d(String.format("Pitch: %.1f", output.getPitch()), Color.WHITE, new Point(10, 410), 1, 1);
            renderer.drawString2d(String.format("Roll: %.1f", output.getRoll()), Color.WHITE, new Point(10, 430), 1, 1);
            renderer.drawString2d(String.format("Steer: %.2f", output.getSteer()), Color.WHITE, new Point(10, 450), 1, 1);
            renderer.drawString2d(String.format("Throttle: %.2f", output.getThrottle()), output.getThrottle() < 0 ? Color.RED : Color.WHITE, new Point(10, 470), 1, 1);
            renderer.drawString2d(String.format("Slide: %s", output.holdHandbrake() ? "Enabled" : "Disabled"), output.holdHandbrake() ? Color.GREEN : Color.WHITE, new Point(10, 490), 1, 1);

            renderer.drawString2d(String.format("State: %s", state.name()), Color.WHITE, new Point(10, 510), 2, 2);
            if (state == State.RUN)
                renderer.drawString2d(String.format("Strategy: %s", currentPlan.getClass().getSimpleName()), Color.WHITE, new Point(10, 630), 2, 2);

            renderer.drawString3d(this.playerIndex + ": " + (currentPlan == null ? "null" : currentPlan.getClass().getSimpleName()), (currentPlan != null && currentPlan.getClass() == RecoverStrategy.class) ? Color.YELLOW : Color.WHITE, car.position.add(0, 0, 50), 1, 1);
            if (currentPlan != null)
                renderer.drawString3d(currentPlan.getAdditionalInformation(), Color.WHITE, car.position.add(0, 0, 100), 1, 1);

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
        ControlsOutput controlsOutput;
        try {
            controlsOutput = processInput(dataPacket);
        } catch (Exception | AssertionError e) {
            controlsOutput = new ControlsOutput();
            e.printStackTrace();
        }

        lastTick = dataPacket.gameInfo.secondsElapsed();

        r.finishAndSendIfDifferent();
        return controlsOutput;
    }

    @Override
    public void retire() {
        PlayerInfoManager.reset();
        System.out.println("Retiring Training bot " + playerIndex);
    }

    enum State {
        RESET,
        RUN,
        INIT
    }
}
