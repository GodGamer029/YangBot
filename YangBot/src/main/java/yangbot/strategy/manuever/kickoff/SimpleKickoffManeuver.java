package yangbot.strategy.manuever.kickoff;

import yangbot.input.*;
import yangbot.path.EpicMeshPlanner;
import yangbot.strategy.manuever.DodgeManeuver;
import yangbot.strategy.manuever.FollowPathManeuver;
import yangbot.strategy.manuever.Maneuver;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector3;

import java.awt.*;

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

                this.dodgeManeuver = new DodgeManeuver();
                this.dodgeManeuver.target = ball.position;
                this.dodgeManeuver.duration = 0.08f;
                this.dodgeManeuver.delay = 0.3f;
                this.dodgeManeuver.enablePreorient = true;
                this.dodgeManeuver.preorientOrientation = Matrix3x3.lookAt(new Vector3(0, -1 * (car.team * 2 - 1), 0).normalized(), new Vector3(0, 0, 1));

                this.followPathManeuver = new FollowPathManeuver();

                this.kickOffLocation = KickoffTester.getKickoffLocation(car);

                var planner = new EpicMeshPlanner()
                        .withStart(car)
                        .withEnd(ball.position, ball.position.sub(car.position).normalized().add(new Vector3(0, -1 * (car.team * 2 - 1), 0).mul(0.5f)).normalized());

                if (this.kickOffLocation == KickoffTester.KickOffLocation.OFF_CENTER) {
                    Vector3 padLoc = new Vector3(Math.signum(car.position.x) * 120f, 2816.0f * car.getTeamSign(), RLConstants.carElevation);
                    planner.addPoint(padLoc, padLoc.sub(car.position).withZ(0).normalized());
                }

                this.followPathManeuver.path = planner.plan().get();
                this.followPathManeuver.arrivalSpeed = CarData.MAX_VELOCITY;

                this.kickOffState = KickOffState.DRIVE;

                if (kickOffLocation == KickoffTester.KickOffLocation.UNKNOWN)
                    this.setDone();
                break;
            }
            case DRIVE: {
                followPathManeuver.step(dt, controlsOutput);
                if (followPathManeuver.isDone() || car.position.flatten().add(car.velocity.flatten().mul(0.3f)).distance(ball.position.flatten()) < 150) {
                    kickOffState = KickOffState.FLIP;
                }

                followPathManeuver.path.draw(gameData.getAdvancedRenderer(), Color.BLUE.darker());
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
