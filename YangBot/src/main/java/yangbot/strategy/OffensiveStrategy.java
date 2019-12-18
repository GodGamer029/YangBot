package yangbot.strategy;

import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.manuever.DodgeManeuver;
import yangbot.prediction.YangBallPrediction;
import yangbot.util.AdvancedRenderer;
import yangbot.util.ControlsOutput;

import java.util.Optional;

public class OffensiveStrategy extends Strategy {

    private DodgeManeuver dodgeManeuver = new DodgeManeuver();
    private boolean dodging = false;

    @Override
    protected void planStrategyInternal() {
        if (!dodging && this.checkReset(0.5f))
            return;
    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {
        if (this.reevaluateStrategy(0.5f))
            return;
        GameData gameData = GameData.current();
        CarData car = gameData.getCarData();
        BallData ball = gameData.getBallData();
        AdvancedRenderer renderer = gameData.getAdvancedRenderer();
        YangBallPrediction ballPrediction = gameData.getBallPrediction();

        DefaultStrategy.smartBallChaser(dt, controlsOutput);

        if (dodging) {
            dodgeManeuver.target = ballPrediction.getFrameAtRelativeTime(dodgeManeuver.delay - dodgeManeuver.timer).get().ballData.position;
            dodgeManeuver.step(dt, controlsOutput);
            if (dodgeManeuver.isDone()) {
                dodgeManeuver = new DodgeManeuver();
                dodging = false;
            }
        } else if (car.position.flatten().add(car.velocity.flatten().mul(0.4f)).distance(ballPrediction.getFrameAtRelativeTime(0.3f).get().ballData.position.flatten()) < 200) {
            dodgeManeuver = new DodgeManeuver();
            dodgeManeuver.delay = 0.3f;
            dodgeManeuver.duration = 0.1f;
        }
    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.empty();
    }
}
