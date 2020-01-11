package yangbot.strategy;

import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.GameData;
import yangbot.input.fieldinfo.BoostManager;
import yangbot.input.fieldinfo.BoostPad;
import yangbot.manuever.FollowPathManeuver;
import yangbot.prediction.Curve;
import yangbot.vector.Vector3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class NeutralStrategy extends Strategy {

    private State state = State.BALLCHASE;
    private FollowPathManeuver followPathManeuver = new FollowPathManeuver();

    @Override
    protected void planStrategyInternal() {
        if (this.checkReset(0.5f))
            return;

        GameData gameData = GameData.current();
        CarData car = gameData.getCarData();
        BallData ball = gameData.getBallData();

        if (!car.hasWheelContact) {
            this.setDone();
            return;
        }

        state = State.BALLCHASE;

        if (car.boost < 90 && car.position.distance(ball.position) > 1500) {
            List<BoostPad> fullPads = BoostManager.getAllBoosts();
            List<BoostPad> closestPadList = fullPads.stream()
                    .filter((pad) -> pad.isActive() || pad.boostAvailableIn() < 1)
                    .filter((pad) -> Math.abs(car.forward().flatten().correctionAngle(pad.getLocation().flatten().sub(car.position.add(car.velocity.mul(0.2f)).flatten()).normalized())) < 0.5f)
                    .sorted((a, b) -> (int) (a.getLocation().distance(car.position) - b.getLocation().distance(car.position)))
                    .limit(5)
                    .collect(Collectors.toList());

            if (closestPadList.size() > 0) {
                Curve shortestPath = null;
                float shortestPathLength = 2000;
                for (BoostPad pad : closestPadList) {

                    final List<Curve.ControlPoint> controlPoints = new ArrayList<>();
                    controlPoints.add(new Curve.ControlPoint(car.position, car.forward()));
                    Vector3 padLocation = pad.getLocation().withZ(car.position.z);
                    Vector3 offToBallLocation = pad.getLocation().withZ(car.position.z)
                            .add(
                                    ball.position
                                            .add(ball.velocity.mul(0.6f))
                                            .sub(pad.getLocation())
                                            .withZ(0)
                                            .normalized().mul(100)
                            );
                    controlPoints.add(new Curve.ControlPoint(padLocation, offToBallLocation.sub(padLocation).normalized()));
                    controlPoints.add(new Curve.ControlPoint(offToBallLocation, offToBallLocation.add(ball.position.add(ball.velocity.mul(0.8f)).sub(offToBallLocation).withZ(0).normalized().mul(150))));

                    Curve path = new Curve(controlPoints);
                    float pathLength = path.length;
                    if (pad.isFullBoost())
                        pathLength -= 800;
                    if (path.length < shortestPathLength && path.length > 0) {
                        shortestPathLength = path.length;
                        shortestPath = path;
                    }
                }

                if (shortestPath != null) {
                    followPathManeuver.path = shortestPath;
                    state = State.GET_BOOST;
                }
            }
        }

    }

    @Override
    protected void stepInternal(float dt, ControlsOutput controlsOutput) {
        if (this.reevaluateStrategy(this.state == State.GET_BOOST ? 0.8f : 0.5f))
            return;

        GameData gameData = GameData.current();

        switch (state) {
            case BALLCHASE: {
                DefaultStrategy.smartBallChaser(dt, controlsOutput);
                break;
            }
            case GET_BOOST: {
                followPathManeuver.path.draw(gameData.getAdvancedRenderer());
                followPathManeuver.step(dt, controlsOutput);
                controlsOutput.withBoost(false);
                if (followPathManeuver.isDone())
                    this.reevaluateStrategy(0);
            }
        }

    }

    @Override
    public Optional<Strategy> suggestStrategy() {
        return Optional.empty();
    }

    enum State {
        BALLCHASE,
        GET_BOOST
    }
}
