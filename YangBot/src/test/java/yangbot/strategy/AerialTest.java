package yangbot.strategy;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rlbot.gamestate.GameInfoState;
import yangbot.cpp.YangBotCppInterop;
import yangbot.cpp.YangBotJNAInterop;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.input.RLConstants;
import yangbot.strategy.abstraction.AerialAbstraction;
import yangbot.strategy.lac.LACHelper;
import yangbot.strategy.lac.LACStrategy;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.vector.Vector3;
import yangbot.util.scenario.Scenario;
import yangbot.util.scenario.ScenarioLoader;
import yangbot.util.scenario.ScenarioUtil;

import java.awt.*;
import java.util.stream.Collectors;

public class AerialTest {

    @BeforeAll
    public static void init(){
        YangBotCppInterop.init((byte) 0, (byte) 0);
    }

    @Test
    public void test1(){
        GameData g = GameData.current();
        ScenarioUtil.decodeApplyToGameData(g, "eWFuZ3YxOmMoYj03Mi4wLHA9KDIwNjkuODUwLC00MjY4LjEwMCwxNy4wNDApLHY9KDQwMC40NjEsMTEyOS43NjEsMC41MjEpLGE9KC0wLjAwMCwtMC4wMDEsMi40NTcpLG89KC0wLjAxNywxLjI5OSwtMC4wMDApKSxiKHA9KDE5MTYuNTAwLC0zODk4LjMwMCw1MTUuMTYwKSx2PSgtODY3LjYwMSw5MzguMzIxLDEwNjguNzExKSxhPSgtMC41NjcsNS45MjAsLTAuNzkzKSk7");
        g.setBallPrediction(YangBotJNAInterop.getBallPrediction(g.getBallData().makeMutable(), RLConstants.tickRate, 5));
        final CarData car = g.getCarData();

        var aerialStrikeFrames = g.getBallPrediction().getFramesBetweenRelative(1.5f, 2.4f)
                .stream()
                .filter((frame) -> Math.signum(frame.ballData.position.y - (car.position.y + car.velocity.y * Math.max(0, frame.relativeTime * 0.6f - 0.1f))) == -car.getTeamSign()) // Ball is closer to enemy goal than to own
                .filter((frame) -> (frame.ballData.position.z >= LACStrategy.MIN_HEIGHT_AERIAL && frame.ballData.position.z < LACStrategy.MAX_HEIGHT_AERIAL)
                        && !frame.ballData.makeMutable().isInAnyGoal())
                .collect(Collectors.toList());
        var airStrike = LACHelper.planAerialIntercept(YangBallPrediction.from(aerialStrikeFrames, g.getBallPrediction().tickFrequency), true);
        System.out.println("Air strike: "+(airStrike.isPresent() ? "present" : "empty"));
        airStrike.ifPresent(LACHelper.StrikeInfo::execute);
    }

    private AerialAbstraction abs;

    @Test
    public void executionTest(){
        Scenario s = new Scenario.Builder()
                .withTransitionDelay(0.2f)
                .withGameState(ScenarioUtil.decodeToGameState("eWFuZ3YxOmMoYj0xMDAuMCxwPSgyMDY5Ljg1MCwtNDI2OC4xMDAsMTcuMDQwKSx2PSg0MDAuNDYxLDExMjkuNzYxLDAuNTIxKSxhPSgtMC4wMDAsLTAuMDAxLDIuNDU3KSxvPSgtMC4wMTcsMS4yOTksLTAuMDAwKSksYihwPSgxOTE2LjUwMCwtMzg5OC4zMDAsNTE1LjE2MCksdj0oLTg2Ny42MDEsOTM4LjMyMSwxMDY4LjcxMSksYT0oLTAuNTY3LDUuOTIwLC0wLjc5MykpOw==")
                        .withGameInfoState(new GameInfoState().withGameSpeed(0.1f)))
                .withInit((controlsOutput -> {
                    System.out.println("########## init");
                    var g = GameData.current();
                    var car = g.getCarData();
                    car.getPlayerInfo().resetInactive();
                    abs = new AerialAbstraction(new Vector3(-356.66, -1523, 875));
                    abs.arrivalTime = car.elapsedSeconds + 2.8f;
                    //System.out.println("is viable: "+AerialAbstraction.isViable(car, abs.targetPos, abs.arrivalTime));
                }))
                .withRun((output, timer) -> {
                    final GameData g = GameData.current();
                    var r = g.getAdvancedRenderer();

                    abs.draw(r);
                    var state = abs.step(g.getDt(), output);

                    r.drawControlsOutput(output, 440);
                    return state.isDone() ? Scenario.RunState.COMPLETE : Scenario.RunState.CONTINUE;
                })
                .withOnComplete((f) -> {

                })
                .build();
        ScenarioLoader.loadScenario(s);
        assert ScenarioLoader.get().waitToCompletion(2000);
    }
}
