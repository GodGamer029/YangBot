package yangbot.manuever.kickoff;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.GameData;
import yangbot.input.ImmutableBallData;
import yangbot.manuever.DodgeManeuver;
import yangbot.manuever.FollowPathManeuver;
import yangbot.manuever.Maneuver;
import yangbot.prediction.Curve;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector3;

import java.util.ArrayList;
import java.util.List;

public class SimpleKickoffManeuver extends Maneuver {

    private float timer = 0;
    private KickOffState kickOffState = KickOffState.INIT;
    private KickoffTester.KickOffLocation kickOffLocation = null;
    private FollowPathManeuver followPathManeuver = null;
    private DodgeManeuver dodgeManeuver = null;

    @Override
    public boolean isViable() {
        return false;
    }

    @Override
    public void step(float dt, ControlsOutput controlsOutput) {
        final GameData gameData = this.getGameData();
        final CarData car = gameData.getCarData();
        final ImmutableBallData ball = gameData.getBallData();

        timer += dt;

        if (ball.position.flatten().magnitude() > 5) {
            this.setIsDone(true);
            return;
        }

        switch (kickOffState) {
            case INIT: {
                controlsOutput.withThrottle(1);
                controlsOutput.withBoost(true);

                dodgeManeuver = new DodgeManeuver();
                dodgeManeuver.target = ball.position;
                dodgeManeuver.duration = 0.15f;
                dodgeManeuver.delay = 0.3f;
                dodgeManeuver.enablePreorient = true;
                dodgeManeuver.preorientOrientation = Matrix3x3.lookAt(new Vector3(0, -1 * (car.team * 2 - 1), 1f).normalized(), new Vector3(0, 0, 1));

                followPathManeuver = new FollowPathManeuver();

                {
                    final List<Curve.ControlPoint> controlPoints = new ArrayList<>();
                    controlPoints.add(new Curve.ControlPoint(car.position, car.forward()));
                    controlPoints.add(new Curve.ControlPoint(ball.position, ball.position.sub(car.position).normalized().add(new Vector3(0, -1 * (car.team * 2 - 1), 0)).normalized()));

                    followPathManeuver.path = new Curve(controlPoints);
                }
                followPathManeuver.arrivalSpeed = CarData.MAX_VELOCITY;

                kickOffLocation = KickoffTester.getKickoffLocation(car);

                kickOffState = KickOffState.DRIVE;

                if (kickOffLocation == KickoffTester.KickOffLocation.UNKNOWN)
                    this.setDone();
                break;
            }
            case DRIVE: {
                followPathManeuver.step(dt, controlsOutput);
                if (followPathManeuver.isDone() || car.position.add(car.velocity.mul(0.2f)).distance(ball.position) < 250)
                    kickOffState = KickOffState.FLIP;
                followPathManeuver.path.draw(gameData.getAdvancedRenderer());
                break;
            }
            case FLIP: {
                dodgeManeuver.step(dt, controlsOutput);
                if (dodgeManeuver.isDone())
                    this.setDone();
            }
        }
    }

    @Override
    public CarData simulate(CarData car) {
        throw new IllegalStateException("Not implemented");
    }

    enum KickOffState {
        INIT,
        DRIVE,
        FLIP
    }
}
