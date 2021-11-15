package yangbot.strategy.forcedStrats;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.GameData;
import yangbot.path.EpicMeshPlanner;
import yangbot.path.builders.SegmentedPath;
import yangbot.strategy.Strategy;
import yangbot.util.math.MathUtils;

import java.util.Optional;

public class ForceBumpStrategy extends Strategy {

    private final int targetPlayerIndex;
    private float timeToIntercept = 0;
    private float replanTimeout = 0;
    private SegmentedPath path = null;

    public ForceBumpStrategy(int targetPlayerIndex) {
        this.targetPlayerIndex = targetPlayerIndex;
    }

    @Override
    protected void planStrategyInternal() {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();

        if (!car.hasWheelContact) {
            this.setDone();
            return;
        }

        var targetCarOpt = gameData.getAllCars().stream()
                .filter(c -> c.playerIndex == targetPlayerIndex && !c.isDemolished && c.hasWheelContact && c.position.z < 50)
                .findFirst();

        if (targetCarOpt.isEmpty()) {
            this.setDone();
            return;
        }
        var targetCar = targetCarOpt.get();

        this.timeToIntercept = MathUtils.clip((float) (car.position.distance(targetCar.position) / Math.max(car.forwardSpeed(), 300)), 0.05f, 2.5f);
    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {
        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();

        this.replanTimeout -= dt;

        if (this.checkReset(4f))
            return;

        var targetCarOpt = gameData.getAllCars().stream()
                .filter(c -> c.playerIndex == targetPlayerIndex && !c.isDemolished && c.hasWheelContact && c.position.z < 50)
                .findFirst();

        if (targetCarOpt.isEmpty()) {
            this.setDone();
            return;
        }
        var targetCar = targetCarOpt.get();

        if (this.replanTimeout <= 0 && (this.path == null || this.path.canInterrupt())) {
            var simCar = new CarData(targetCar);
            simCar.smartPrediction(this.timeToIntercept);

            var pathOpt = new EpicMeshPlanner()
                    .withStart(car)
                    .withEnd(simCar.position, simCar.position.sub(car.position))
                    .withCreationStrategy(EpicMeshPlanner.PathCreationStrategy.YANGPATH)
                    .withArrivalSpeed(2300)
                    .allowDodge(false)
                    .plan();

            if (pathOpt.isEmpty()) {
                this.setDone();
                return;
            }

            this.path = pathOpt.get();
            this.replanTimeout = 0.05f;

            this.timeToIntercept = MathUtils.clip(path.getTotalTimeEstimate() * 0.95f, 0.05f, 2.5f);
        }

        if (this.path.shouldReset(car)) {
            this.setDone();
            return;
        }

        this.path.draw(gameData.getAdvancedRenderer());
        if (this.path.step(dt, controlsOutput)) {
            // Done
            this.setDone();
        }
    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.empty();
    }
}
