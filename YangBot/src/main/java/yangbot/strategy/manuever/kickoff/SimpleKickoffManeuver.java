package yangbot.strategy.manuever.kickoff;

import yangbot.input.*;
import yangbot.path.EpicPathPlanner;
import yangbot.strategy.manuever.DodgeManeuver;
import yangbot.strategy.manuever.FollowPathManeuver;
import yangbot.strategy.manuever.Maneuver;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector3;

public class SimpleKickoffManeuver extends Maneuver {

    private float timer = 0;
    private float timeoutTimer = 0;
    private KickOffState kickOffState = KickOffState.INIT;
    private KickoffTester.KickOffLocation kickOffLocation = null;
    private FollowPathManeuver followPathManeuver = null;
    private DodgeManeuver dodgeManeuver = null;

    @Override
    public void step(float dt, ControlsOutput controlsOutput) {
        final GameData gameData = this.getGameData();
        final CarData car = gameData.getCarData();
        final ImmutableBallData ball = gameData.getBallData();

        timer += dt;

        if (ball.position.flatten().magnitude() > 5) {
            if (this.dodgeManeuver.isDone() || timeoutTimer > 0.2) {
                this.setIsDone(true);
                return;
            } else
                timeoutTimer += dt;
        }

        switch (kickOffState) {
            case INIT: {
                controlsOutput.withThrottle(1);
                controlsOutput.withBoost(true);

                dodgeManeuver = new DodgeManeuver();
                dodgeManeuver.target = ball.position;
                dodgeManeuver.duration = 0.08f;
                dodgeManeuver.delay = 0.3f;
                dodgeManeuver.enablePreorient = true;
                dodgeManeuver.preorientOrientation = Matrix3x3.lookAt(new Vector3(0, -1 * (car.team * 2 - 1), 0).normalized(), new Vector3(0, 0, 1));

                followPathManeuver = new FollowPathManeuver();

                followPathManeuver.path = new EpicPathPlanner()
                        .withStart(car.position, car.forward())
                        .withEnd(ball.position, ball.position.sub(car.position).normalized().add(new Vector3(0, -1 * (car.team * 2 - 1), 0).mul(0.5f)).normalized())
                        .plan().get();

                followPathManeuver.arrivalSpeed = CarData.MAX_VELOCITY;

                kickOffLocation = KickoffTester.getKickoffLocation(car);

                kickOffState = KickOffState.DRIVE;

                if (kickOffLocation == KickoffTester.KickOffLocation.UNKNOWN)
                    this.setDone();
                break;
            }
            case DRIVE: {
                followPathManeuver.step(dt, controlsOutput);
                if (followPathManeuver.isDone() || car.position.flatten().add(car.velocity.flatten().mul(0.3f)).distance(ball.position.flatten()) < 150) {
                    kickOffState = KickOffState.FLIP;
                }

                followPathManeuver.path.draw(gameData.getAdvancedRenderer());
                break;
            }
            case FLIP: {
                if (!car.doubleJumped && dodgeManeuver.delay > 0 && dodgeManeuver.timer > dodgeManeuver.duration + RLConstants.tickFrequency) {
                    BallData simBall = ball.makeMutable();
                    CarData simCar = new CarData(car);
                    simCar.step(new ControlsOutput(), 0.01f);
                    if (simBall.collidesWith(simCar)) {
                        dodgeManeuver.delay = 0;
                    }
                }
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
