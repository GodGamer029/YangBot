package yangbot.strategy;

import org.junit.jupiter.api.Test;
import rlbot.gamestate.GameInfoState;
import yangbot.input.GameData;
import yangbot.strategy.abstraction.DriveDodgeStrikeAbstraction;
import yangbot.strategy.lac.LACStrategy;
import yangbot.util.YangBallPrediction;
import yangbot.util.scenario.Scenario;
import yangbot.util.scenario.ScenarioLoader;
import yangbot.util.scenario.ScenarioUtil;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class IdleTest {

    private Strategy strategy;

    @Test
    public void sample1() {

        List<YangBallPrediction.YangPredictionFrame> strikeableFrames = new ArrayList<>();
        Scenario s = new Scenario.Builder()
                .withTransitionDelay(0.2f)
                .withGameState(ScenarioUtil.decodeToGameState("eWFuZ3YxOmMoYj0xNS4wLHA9KDMwNS45OTAsODIuMzgwLDE3LjAzMCksdj0oLTI4Mi4zMjEsLTE4LjY1MSwxLjA4MSksYT0oMC4wMDAsLTAuMDA1LDAuMzcxKSxvPSgtMC4wMTYsLTMuMTAyLDAuMDAwKSk7")
                        .withGameInfoState(new GameInfoState().withGameSpeed(0.1f)))
                .withInit((controlsOutput -> {
                    System.out.println("########## init");
                    var g = GameData.current();
                    var car = g.getCarData();
                    car.getPlayerInfo().resetInactive();
                    strategy = new LACStrategy();
                    ((LACStrategy)strategy).spoofNoBoost = true;
                    ((LACStrategy)strategy).spoofIdle = true;
                    strategy.planStrategy();

                    strikeableFrames.clear();

                    strikeableFrames.addAll(g.getBallPrediction().getFramesBetweenRelative(0.25f, 3.5f)
                            .stream()
                            .filter((frame) -> (frame.ballData.position.z <= DriveDodgeStrikeAbstraction.MAX_STRIKE_HEIGHT))
                            .filter((frame) -> Math.signum(frame.ballData.position.y - (car.position.y + car.velocity.y * Math.max(0, frame.relativeTime * 0.6f - 0.1f))) == -car.getTeamSign()) // Ball is closer to enemy goal than to own
                            .collect(Collectors.toList()));
                }))
                .withRun((output, timer) -> {
                    var r = GameData.current().getAdvancedRenderer();

                    //GameData.current().getBallPrediction().draw(r, Color.MAGENTA, 3);
                    r.drawString2d(strategy.getAdditionalInformation(), Color.WHITE, new Point(100, 300), 1, 1);

                    if(!strategy.isDone())
                        strategy.step(GameData.current().getDt(), output);
                    r.drawControlsOutput(output, 440);

                    return timer > 6f || strategy.isDone() ? Scenario.RunState.COMPLETE : Scenario.RunState.CONTINUE;
                })
                .withOnComplete((f) -> {

                })
                .build();

        ScenarioLoader.loadScenario(s);
        assert ScenarioLoader.get().waitToCompletion(4000);
    }

}
