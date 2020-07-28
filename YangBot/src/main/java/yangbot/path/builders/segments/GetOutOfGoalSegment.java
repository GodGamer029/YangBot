package yangbot.path.builders.segments;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.GameData;
import yangbot.input.RLConstants;
import yangbot.path.builders.PathSegment;
import yangbot.strategy.manuever.DodgeManeuver;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.strategy.manuever.TurnManeuver;
import yangbot.util.math.Car2D;
import yangbot.util.math.Line2;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

// TODO: use navmesh
public class GetOutOfGoalSegment extends PathSegment {

    private Vector3 endPos, endTangent;
    private float endSpeed;
    private float timeEstimate;
    private boolean needsJump = false;
    private State state = State.STOP;
    private DodgeManeuver dodgeManeuver;
    private TurnManeuver turnManeuver;

    public GetOutOfGoalSegment(Vector3 startPos, Vector3 tangent, float startSpeed) {
        super(startSpeed);

        if (Math.abs(startPos.y) <= RLConstants.goalDistance)
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
            return;
        }

        this.timeEstimate = 0;
        // Slow down

        var car2d = new Car2D(startPos.flatten(), tangent2d.mul(startSpeed), tangent2d, 0, 0);
        car2d.simulateFullStop(); // TODO: figure out if we even need to fullstop or we can just continue driving out of the goal
        this.timeEstimate += car2d.time;
        this.timeEstimate += 1; // Jump
        Vector3 stopPos = car2d.position.withZ(startPos.z);

        if (Math.abs(stopPos.y) <= RLConstants.goalDistance && Math.abs(stopPos.x) < RLConstants.goalCenterToPost) {
            this.endPos = stopPos;
            this.endTangent = car2d.tangent.withZ(0);
            this.endSpeed = 900;
            this.timeEstimate = (float) startPos.distance(this.endPos) / ((900 + Math.abs(startSpeed)) / 2);
            return;
        }

        // Clip pos to within goal
        stopPos = stopPos.withX(MathUtils.clip(stopPos.x, -RLConstants.goalCenterToPost, RLConstants.goalCenterToPost));
        stopPos = stopPos.withY(Math.signum(stopPos.y) * Math.max(RLConstants.goalDistance, Math.abs(stopPos.y)));

        this.needsJump = true;

        this.endPos = this.makeEndPos(stopPos);
        car2d.time = 0;
        car2d.velocity = new Vector2();
        car2d.simulateDriveDistanceForward((float) startPos.flatten().distance(this.endPos.flatten()), false);
        this.timeEstimate += car2d.time; // time to drive out of goal
        this.endSpeed = (float) car2d.velocity.magnitude();
        this.endTangent = new Vector3(0, -Math.signum(this.endPos.y), 0);

        this.dodgeManeuver = new DodgeManeuver();
        this.dodgeManeuver.duration = 0.025f; // minimum
        this.dodgeManeuver.delay = 999;

        this.turnManeuver = new TurnManeuver();
        this.turnManeuver.target = Matrix3x3.lookAt(this.endPos.sub(stopPos), new Vector3(0, 0, 1));

        this.state = State.STOP;
    }

    private Vector3 makeEndPos(Vector3 startPos) {
        final var goal = new Vector2(0, (RLConstants.goalDistance - 50) * Math.signum(startPos.y));
        final var goalLine = new Line2(goal.sub(RLConstants.goalCenterToPost * 0.7f, 0), goal.add(RLConstants.goalCenterToPost * 0.7f, 0));
        var endPos = goalLine.closestPointOnLine(startPos.flatten());
        return endPos.withZ(RLConstants.carElevation);
    }

    @Override
    public boolean step(float dt, ControlsOutput output) {
        super.step(dt, output);
        final GameData gameData = GameData.current();
        final CarData carData = gameData.getCarData();

        if (Math.abs(carData.position.y) < RLConstants.goalDistance - 25)
            return true;

        if (!this.needsJump) { // just drive out of goal
            DriveManeuver.steerController(output, carData, this.endPos);
            DriveManeuver.speedController(dt, output, carData.forwardSpeed(), this.endSpeed - 2, this.endSpeed + 2, 0.03f, false);
            return false;
        }

        switch (this.state) {
            case STOP: {
                DriveManeuver.speedController(dt, output, carData.forwardSpeed(), 0, 0, 0.03f, false);
                if (Math.abs(carData.forwardSpeed()) < 10)
                    this.state = State.JUMP;
            }
            break;
            case JUMP: {
                this.dodgeManeuver.step(dt, output);
                if (carData.hasWheelContact || this.dodgeManeuver.isDone()) {
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
