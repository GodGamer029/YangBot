package yangbot.strategy.abstraction;

import yangbot.input.*;
import yangbot.input.fieldinfo.BoostManager;
import yangbot.input.fieldinfo.BoostPad;
import yangbot.path.EpicMeshPlanner;
import yangbot.path.builders.SegmentedPath;
import yangbot.util.Tuple;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class GetBoostAbstraction extends Abstraction {

    public boolean allowSmallBoost = false;
    public float maxPathTime = 3.5f;
    public boolean ignoreTeammates = false;
    private boolean hasFoundTarget = false;
    private SegmentedPath pathToBoost;
    private Vector2 chosenBoostLocation;

    private boolean didTryFindBoost = false;

    private static boolean isViableSimple(CarData car) {
        if (car.boost > 70)
            return false;

        if (car.position.z > 50 || !car.hasWheelContact)
            return false;

        return true;
    }

    @Override
    public boolean isViable() {
        return this.hasFoundTarget || this.findBoost();
    }

    private boolean findBoost() {
        assert !this.didTryFindBoost;
        this.didTryFindBoost = true;

        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();
        final int teamSign = car.getTeamSign();
        final ImmutableBallData ball = gameData.getBallData();

        if (!isViableSimple(car))
            return false;

        List<BoostPad> allPads = BoostManager.getAllBoosts();
        List<CarData> teammates = gameData.getAllCars().stream().filter(c -> c.team == car.team && c.playerIndex != car.playerIndex && c.position.z < 200).collect(Collectors.toList());

        /*
         * Inactive filters:
         *
         *      // We don't go out of position too much (go closer to enemy goal)
         *        .filter((pad) -> /*close on y axis* Math.abs(pad.getLocation().y - car.position.y) < 350 || Math.signum(car.position.y - pad.getLocation().y) == -teamSign)
         *
         *   // We don't have to change our angle that much
         *    .filter((pad) -> Math.abs(car.forward().flatten().correctionAngle(pad.getLocation().flatten().sub(car.position.add(car.velocity.mul(0.2f)).flatten()).normalized())) < 1f)
         */

        List<BoostPad> closestPadList = allPads.stream()
                .filter(pad -> allowSmallBoost || pad.isFullBoost())
                // Pad is active
                .filter((pad) -> pad.isActive() /*|| pad.boostAvailableIn() < 1*/)
                // Pad is closer to our goal than ball
                .filter((pad) -> Math.signum(MathUtils.minAbs(ball.position.y, RLConstants.goalDistance * 0.8f /*always allow own corner boosts*/) - pad.getLocation().y) == -teamSign)

                // Of our teammates, we are the fastest to the boost
                .filter((pad) -> {
                    if (teammates.size() == 0)
                        return true;
                    if (ignoreTeammates)
                        return true;

                    Vector3 carToPad = pad.getLocation().sub(car.position).withZ(0).normalized();
                    float ourSpeed = Math.abs(car.forwardSpeed()) * MathUtils.remap(car.forward().dot(carToPad), -1, 1, 0.5f, 1);
                    ourSpeed = MathUtils.clip(ourSpeed + 50, 400, 2000);

                    float ourTime = (float) pad.getLocation().distance(car.position) / ourSpeed;
                    if (ourTime < 0.5f && car.forward().dot(carToPad) > 0.5f) // If we're that close, might as well get it
                        return true;

                    // Loop through teammates
                    for (CarData mate : teammates) {
                        assert mate.playerIndex != car.playerIndex;

                        Vector3 mateToPad = pad.getLocation().sub(mate.position).withZ(0).normalized();
                        var mateForward = mate.hasWheelContact ? mate.forward() : mate.velocity.normalized();
                        float mateSpeed = Math.abs(mate.velocity.dot(mateForward)) * MathUtils.remap(mateForward.dot(mateToPad), -1, 1, 0.5f, 1);
                        mateSpeed = MathUtils.clip(mateSpeed + 50, 400, 2000);
                        float mateTime = (float) pad.getLocation().distance(mate.position) / mateSpeed;

                        if (mateTime < ourTime) // If they beat us to it, don't go
                            return false;
                    }
                    return true;
                })
                // Sort by distance
                .sorted((a, b) -> (int) (a.getLocation().distance(car.position) - b.getLocation().distance(car.position)))
                .limit(allowSmallBoost ? 8 : 4)
                .collect(Collectors.toList());

        if (closestPadList.size() <= 0)
            return false;

        var chosenPadPath = closestPadList.stream()
                .map(pad -> {
                    Vector3 padLocation = pad.getLocation().withZ(car.position.z);

                    var pathOpt = new EpicMeshPlanner()
                            .withStart(car)
                            .withEnd(padLocation, car.position.sub(padLocation).normalized())
                            .allowFullSend(pad.isFullBoost())
                            .allowDodge()
                            .withArrivalSpeed(900) // We want to get outta there afterwards, don't faceplant into the next wall
                            .withCreationStrategy(EpicMeshPlanner.PathCreationStrategy.YANGPATH)
                            .plan();

                    if (pathOpt.isEmpty())
                        return null;

                    var path = pathOpt.get();

                    float pathTimeEstimate = path.getTotalTimeEstimate();
                    if (pad.isFullBoost() && allowSmallBoost)
                        pathTimeEstimate *= 0.8f; // Full boosts > small boosts

                    return new Tuple<>(path, pathTimeEstimate);
                })
                .filter(Objects::nonNull)
                .min(Comparator.comparingDouble(Tuple::getValue))
                .filter(t -> t.getValue() <= this.maxPathTime)
                .map(Tuple::getKey);

        if (chosenPadPath.isEmpty())
            return false;

        this.pathToBoost = chosenPadPath.get();
        this.chosenBoostLocation = this.pathToBoost.getEndPos().flatten();
        this.hasFoundTarget = true;
        return true;
    }

    @Override
    protected RunState stepInternal(float dt, ControlsOutput controlsOutput) {

        if (!this.hasFoundTarget) {
            boolean successful = this.findBoost();
            if (!successful)
                return RunState.FAILED;
        }

        final GameData gameData = this.getGameData();
        final CarData carData = gameData.getCarData();

        assert !this.pathToBoost.isDone();

        if (this.pathToBoost.shouldReset(carData))
            return RunState.FAILED;

        this.pathToBoost.step(dt, controlsOutput);
        this.pathToBoost.draw(gameData.getAdvancedRenderer());

        if (this.pathToBoost.canInterrupt()) {
            var myBoost = BoostManager.getAllBoosts().stream()
                    .filter(pad -> pad.getLocation().flatten().distance(chosenBoostLocation) < 10)
                    .findAny();
            if (myBoost.isEmpty() || (!myBoost.get().isActive() && myBoost.get().boostAvailableIn() > 1))
                return RunState.FAILED; // our boost got stolen :(
        }

        return this.pathToBoost.isDone() ? RunState.DONE : RunState.CONTINUE;
    }

    @Override
    public boolean canInterrupt() {
        return !this.hasFoundTarget || this.pathToBoost.canInterrupt();
    }
}
