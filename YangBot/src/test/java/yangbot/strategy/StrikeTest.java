package yangbot.strategy;

import org.junit.jupiter.api.Test;
import rlbot.gamestate.GameInfoState;
import yangbot.input.GameData;
import yangbot.strategy.abstraction.DriveDodgeStrikeAbstraction;
import yangbot.util.YangBallPrediction;
import yangbot.util.scenario.Scenario;
import yangbot.util.scenario.ScenarioLoader;
import yangbot.util.scenario.ScenarioUtil;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class StrikeTest {

    private VibeStrategy strategy;
    private int numResets = 3;
    private YangBallPrediction ballPredBeginn;

    @Test
    public void sample1() {

        List<YangBallPrediction.YangPredictionFrame> strikeableFrames = new ArrayList<>();


        Scenario s = new Scenario.Builder()
                .withTransitionDelay(0.15f)
                .withGameState(ScenarioUtil.encodedGameStateToGameState("eWFuZ3YxOmMoYj04Ni4wLHA9KDIwMzAuODkwLC0yMDAuNzEwLDE3LjA2MCksdj0oMTE0OC44NTEsLTE3NS4wNjEsLTAuMTExKSxhPSgtMC4wMDEsMC4wMDAsLTEuNjA1KSxvPSgtMC4wMTcsLTAuMjI2LDAuMDAwKSksYihwPSgzODg3Ljc4MCwyNTQuNTEwLDg4Ny4wNjApLHY9KC0zOS40NTEsLTUzOS4xMTEsLTY4Ny4xMTEpLGE9KDEuMDQwLDUuMzc3LDIuNDUxKSk7").withGameInfoState(new GameInfoState().withGameSpeed(0.5f)))
                .withInit((controlsOutput -> {
                    System.out.println("########## init");
                    var g = GameData.current();
                    var car = g.getCarData();
                    car.getPlayerInfo().resetInactiveShooter();
                    strategy = new VibeStrategy();
                    strategy.planStrategy();
                    numResets--;

                    strikeableFrames.clear();


                    ballPredBeginn = g.getBallPrediction();

                    strikeableFrames.addAll(g.getBallPrediction().getFramesBetweenRelative(0.25f, 3.5f)
                            .stream()
                            .filter((frame) -> (frame.ballData.position.z <= DriveDodgeStrikeAbstraction.MAX_STRIKE_HEIGHT))
                            .filter((frame) -> Math.signum(frame.ballData.position.y - (car.position.y + car.velocity.y * Math.max(0, frame.relativeTime * 0.6f - 0.1f))) == -car.getTeamSign()) // Ball is closer to enemy goal than to own
                            .collect(Collectors.toList()));
                }))
                .withRun((output, timer) -> {
                    var r = GameData.current().getAdvancedRenderer();
                    for (var fra : strikeableFrames) {
                        r.drawLine3d(Color.MAGENTA, fra.ballData.position, ballPredBeginn.getFrameAfterRelativeTime(fra.relativeTime).get().ballData.position);
                    }

                    r.drawString2d(strategy.getAdditionalInformation(), Color.WHITE, new Point(100, 300), 1, 1);

                    strategy.step(GameData.current().getDt(), output);
                    return strategy.isDone() || timer > 5f ? (numResets <= 0 ? Scenario.RunState.COMPLETE : Scenario.RunState.DELAYED_RESET) : Scenario.RunState.CONTINUE;
                })
                .withOnComplete((f) -> {

                })
                .build();

        ScenarioLoader.loadScenario(s);
        assert ScenarioLoader.get().waitToCompletion(4000);
    }

}
