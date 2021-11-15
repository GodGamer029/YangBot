package yangbot.strategy.manuever.kickoff;

import yangbot.input.*;
import yangbot.path.EpicMeshPlanner;
import yangbot.path.builders.SegmentedPath;
import yangbot.strategy.abstraction.DriveDodgeStrikeAbstraction;
import yangbot.strategy.manuever.DodgeManeuver;
import yangbot.strategy.manuever.Maneuver;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector3;

public class SimpleKickoffManeuver extends Maneuver {

    private float timer = 0;
    private float timeoutTimer = 0;
    private KickOffState kickOffState = KickOffState.INIT;
    private KickoffTester.KickOffLocation kickOffLocation = null;
    private SegmentedPath path = null;
    private DodgeManeuver dodgeManeuver = null;
    private static final boolean SLOWKICKOFF = false;
    private Vector3 targetPos;
    private DriveDodgeStrikeAbstraction driveStrikeAbstraction;
    private boolean dontJump = false;

    @Override
    public void step(float dt, ControlsOutput controlsOutput) {
        final GameData gameData = this.getGameData();
        final CarData car = gameData.getCarData();
        final ImmutableBallData ball = gameData.getBallData();

        timer += dt;

        if (ball.position.flatten().magnitude() > 5) {
            if (this.dodgeManeuver == null || this.dodgeManeuver.isDone() || timeoutTimer > 0.2) {
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

                this.kickOffLocation = KickoffTester.getKickoffLocation(car);

                var planner = new EpicMeshPlanner()
                        .withArrivalSpeed(2300)
                        .withStart(car)
                        .allowFullSend(true)
                        .withCreationStrategy(EpicMeshPlanner.PathCreationStrategy.SIMPLE)
                        .withEnd(ball.position.withZ(17), ball.position.sub(car.position).withZ(0).normalized().add(new Vector3(0, -1 * car.getTeamSign(), 0).mul(0.5f)).normalized());

                this.targetPos = ball.position;

                if (SLOWKICKOFF && this.kickOffLocation == KickoffTester.KickOffLocation.CORNER) {
                    var enemy = gameData.getAllCars().stream()
                            .filter((c) -> c.team != car.team && Math.abs(c.position.x) >= 2040 && Math.abs(c.position.x) <= 2056 && Math.signum(c.position.x) == -Math.signum(car.position.x))
                            .findFirst();
                    if (enemy.isPresent()) {
                        this.dodgeManeuver.duration = 0.1f;
                        var midPos = new Vector3(Math.signum(car.position.x) * 50, Math.signum(car.position.y) * 700, 17);
                        var endPos = new Vector3(Math.signum(car.position.x) * 0, Math.signum(car.position.y) * 300, 17);
                        planner = planner
                                .addPoint(midPos, new Vector3(0, -Math.signum(car.position.y), 17))
                                .withEnd(endPos, ball.position.sub(endPos).normalized().add(new Vector3(0, -1 * car.getTeamSign(), 0).mul(0.1f)).normalized())
                                .withArrivalTime(car.elapsedSeconds + 1.9f)
                                .withArrivalSpeed(1900);
                        this.targetPos = endPos;

                        this.dontJump = true;

                        this.path = planner.plan().get();

                        /*this.driveStrikeAbstraction = new DriveStrikeAbstraction(path);
                        this.driveStrikeAbstraction.jumpBeforeStrikeDelay = 0.15f; // depend on path being done
                        this.driveStrikeAbstraction.strikeCalcDelay = 0.3f;
                        this.driveStrikeAbstraction.maxJumpDelay = 0.45f;
                        this.driveStrikeAbstraction.jumpDelayStep = 0.05f;
                        this.driveStrikeAbstraction.strikeDodge.duration = 0.08f;
                        this.driveStrikeAbstraction.arrivalTime = car.elapsedSeconds + path.getTotalTimeEstimate();
                        this.driveStrikeAbstraction.forceJump = true;

                        //this.kickOffState = KickOffState.DRIVESTRIKE;
                        //return;*/
                    }
                }

                if (this.kickOffLocation == KickoffTester.KickOffLocation.OFF_CENTER) {
                    Vector3 padLoc = new Vector3(Math.signum(car.position.x) * 120f, 2816.0f * car.getTeamSign(), RLConstants.carElevation);
                    planner.addPoint(padLoc, padLoc.sub(car.position).withZ(17).normalized());
                }

                if (kickOffLocation == KickoffTester.KickOffLocation.UNKNOWN) {
                    this.setDone();
                    return;
                }

                this.path = planner.plan().get();

                this.kickOffState = KickOffState.DRIVE;
                break;
            }
            case DRIVESTRIKE: {
                this.driveStrikeAbstraction.step(dt, controlsOutput);
                if (this.driveStrikeAbstraction.isDone())
                    this.setDone();

                break;
            }
            case DRIVE: {
                if (!path.isDone())
                    this.path.step(dt, controlsOutput);
                if (this.path.isDone() || car.position.flatten().add(car.velocity.flatten().mul(0.3f)).distance(this.targetPos.flatten()) < BallData.COLLISION_RADIUS + car.hitbox.getForwardExtent()) {
                    if (!this.dontJump)
                        this.kickOffState = KickOffState.FLIP;
                }

                if (this.dontJump && this.path.isDone())
                    this.setDone();

                this.path.draw(gameData.getAdvancedRenderer());
                break;
            }
            case FLIP: {
                if (!car.doubleJumped && dodgeManeuver.delay > 0 && dodgeManeuver.timer > dodgeManeuver.duration + RLConstants.tickFrequency) {
                    BallData simBall = ball.makeMutable();
                    CarData simCar = new CarData(car);
                    simCar.step(new ControlsOutput(), 0.05f);
                    if (simBall.collidesWith(simCar)) {
                        dodgeManeuver.delay = dodgeManeuver.timer;
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
        FLIP,
        DRIVESTRIKE
    }
}
