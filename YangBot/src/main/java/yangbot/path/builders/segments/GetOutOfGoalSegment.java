package yangbot.path.builders.segments;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.GameData;
import yangbot.input.RLConstants;
import yangbot.path.builders.PathSegment;
import yangbot.strategy.manuever.DodgeManeuver;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.strategy.manuever.TurnManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.math.Car2D;
import yangbot.util.math.Line2;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

import java.awt.*;

// TODO: use navmesh
public class GetOutOfGoalSegment extends PathSegment {

    private Vector3 startPos;
    private Vector3 endPos, endTangent;
    private float endSpeed;
    private float timeEstimate;
    private boolean needsJump = false;
    private State state = State.STOP;
    private DodgeManeuver dodgeManeuver;
    private TurnManeuver turnManeuver;

    public GetOutOfGoalSegment(Vector3 startPos, Vector3 tangent, float startSpeed) {
        super(startSpeed);
        this.startPos = startPos;

        if (Math.abs(startPos.y) <= RLConstants.goalDistance - 100)
            return;

        var tangent2d = tangent.flatten().normalized();

        if (startPos.z < RLConstants.carElevation + 30 && startSpeed > -300 && tangent.dot(new Vector3(0, -Math.signum(startPos.y), 0)) > 0.9) {
            // Basically aligned to go out of the goal
            var car2d = new Car2D(startPos.flatten(), tangent2d.mul(startSpeed), tangent2d, 0, 0);
            this.endPos = this.makeEndPos(startPos);
            car2d.simulateDriveDistanceForward((float) startPos.flatten().distance(this.endPos.flatten()), false);

            this.endTangent = this.endPos.sub(startPos).normalized();
            this.endSpeed = (float) car2d.velocity.magnitude();
            this.timeEstimate = car2d.time;

            System.out.println("Tangent shortcut");
            return;
        }

        this.timeEstimate = 0;
        // Slow down

        var car2d = new Car2D(startPos.flatten(), tangent2d.mul(startSpeed), tangent2d, 0, 0);
        car2d.simulateFullStop(); // TODO: figure out if we even need to fullstop or we can just continue driving out of the goal
        this.timeEstimate += car2d.time;
        Vector3 stopPos = car2d.position.withZ(startPos.z);

        if (Math.abs(stopPos.y) <= RLConstants.goalDistance - 60 && Math.abs(stopPos.x) < RLConstants.goalCenterToPost) {
            this.endPos = stopPos;
            this.endTangent = car2d.tangent.withZ(0);
            this.endSpeed = 900;
            this.timeEstimate = (float) startPos.distance(this.endPos) / ((900 + Math.abs(startSpeed)) / 2);

            System.out.println("Stopping shortcut");
            return;
        }

        // Clip pos to within goal
        stopPos = stopPos.withX(MathUtils.clip(stopPos.x, -RLConstants.goalCenterToPost, RLConstants.goalCenterToPost));
        stopPos = stopPos.withY(Math.signum(stopPos.y) * Math.max(RLConstants.goalDistance, Math.abs(stopPos.y)));

        this.needsJump = true;

        this.endPos = this.makeEndPos(stopPos);

        this.turnManeuver = new TurnManeuver();
        this.turnManeuver.target = Matrix3x3.lookAt(this.endPos.sub(stopPos).withZ(0), new Vector3(0, 0, 1));
        var simCar = new CarData(new Vector3(), new Vector3(), new Vector3(), Matrix3x3.lookAt(tangent, new Vector3(0, 0, 1)));
        simCar.hasWheelContact = false;
        float timeNeededForTurn = this.turnManeuver.simulate(simCar).elapsedSeconds;
        final float jumpTolerance = 0.05f;
        this.timeEstimate += MathUtils.clip(timeNeededForTurn + jumpTolerance, 1, 2.5f); // Jump

        car2d.time = 0;
        car2d.velocity = new Vector2();
        car2d.simulateDriveDistanceForward((float) stopPos.flatten().distance(this.endPos.flatten()), false);
        this.timeEstimate += car2d.time; // time to drive out of goal
        this.endSpeed = (float) car2d.velocity.magnitude();
        this.endTangent = new Vector3(0, -Math.signum(this.endPos.y), 0);

        this.dodgeManeuver = new DodgeManeuver();
        this.dodgeManeuver.duration = CarData.getJumpHoldDurationForTotalAirTime(timeNeededForTurn + jumpTolerance, -RLConstants.gravity.z);
        this.dodgeManeuver.delay = 999;

        this.state = State.STOP;
        System.out.println("no shortcut taken");
    }

    private Vector3 makeEndPos(Vector3 startPos) {
        final var goal = new Vector2(0, (RLConstants.goalDistance - 100) * Math.signum(startPos.y));
        final var goalLine = new Line2(goal.sub(RLConstants.goalCenterToPost * 0.7f, 0), goal.add(RLConstants.goalCenterToPost * 0.7f, 0));
        var endPos = goalLine.closestPointOnLine(startPos.flatten());
        return endPos.withZ(RLConstants.carElevation);
    }

    @Override
    public boolean step(float dt, ControlsOutput output) {
        super.step(dt, output);
        final GameData gameData = GameData.current();
        final CarData carData = gameData.getCarData();

        if (Math.abs(carData.position.y) < RLConstants.goalDistance - 60)
            return true;

        if (!this.needsJump) { // just drive out of goal
            DriveManeuver.steerController(output, carData, this.endPos);
            DriveManeuver.speedController(dt, output, carData.forwardSpeed(), this.endSpeed - 2, this.endSpeed + 2, 0.03f, false);
            return false;
        }

        switch (this.state) {
            case STOP: {
                DriveManeuver.speedController(dt, output, carData.forwardSpeed(), 0, 0, 0.03f, false);
                if (Math.abs(carData.forwardSpeed()) < 10) {
                    this.state = State.JUMP;

                    this.turnManeuver.target = Matrix3x3.lookAt(this.endPos.sub(carData.position).withZ(0), new Vector3(0, 0, 1));
                }
            }
            break;
            case JUMP: {
                this.dodgeManeuver.step(dt, output);
                if (carData.hasWheelContact) {
                    if (this.dodgeManeuver.timer > 0.1 || this.dodgeManeuver.isDone()) {
                        this.state = State.DRIVE;
                        break; // done with jumping
                    }
                } else {
                    this.turnManeuver.step(dt, output);
                }
            }
            break;
            case DRIVE: {
                DriveManeuver.steerController(output, carData, this.endPos);
                DriveManeuver.speedController(dt, output, carData.forwardSpeed(), 1400, 1400, 0.03f, false);
            }
            break;
        }

        return false;
    }

    @Override
    public void draw(AdvancedRenderer renderer, Color color) {
        renderer.drawCentered3dCube(Color.RED, this.startPos, 100);
        renderer.drawCentered3dCube(Color.GREEN, this.endPos, 100);

        if (!this.needsJump) {
            renderer.drawLine3d(color, this.startPos, this.endPos);
            return;
        }

    }

    @Override
    public boolean shouldBeInAir() {
        return this.state == State.JUMP;
    }

    @Override
    public Vector3 getEndPos() {
        return this.endPos;
    }

    @Override
    public Vector3 getEndTangent() { // Pointing from endpos to middle, bad approximation
        return this.endTangent;
    }

    @Override
    public float getEndSpeed() {
        return this.endSpeed;
    }

    @Override
    public float getTimeEstimate() {
        return this.timeEstimate;
    }

    enum State {
        STOP,
        JUMP,
        DRIVE
    }
}
