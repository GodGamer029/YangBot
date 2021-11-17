package yangbot.path.builders.segments;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.GameData;
import yangbot.input.RLConstants;
import yangbot.path.builders.PathBuilder;
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

    private static final boolean printTimes = false;
    private Vector3 startPos, stopPos;
    private float endSpeed;
    private float timeEstimate;
    private boolean needsJump = false;
    private State state = State.STOP;
    private DodgeManeuver dodgeManeuver;
    private TurnManeuver turnManeuver;
    private Vector3 endPos = null, endTangent;
    private float timeForJump = 1;
    private float t = 0;

    public GetOutOfGoalSegment(PathBuilder builder, Vector3 targetPos) {
        super(builder);
        this.startPos = builder.getCurrentPosition();
        var startTangent = builder.getCurrentTangent();

        if (Math.abs(startPos.y) <= RLConstants.goalDistance - 100 || Math.abs(startPos.x) > RLConstants.goalCenterToPost + 50)
            return;

        var tangent2d = startTangent.flatten().normalized();

        boolean canJustDriveOut = false;
        final var goal = new Vector2(0, (RLConstants.goalDistance - 100) * Math.signum(startPos.y));

        if (tangent2d.dot(new Vector2(0, -Math.signum(startPos.y))) > 0 && startPos.z < RLConstants.carElevation + 50/* not upside down */ && Math.abs(startTangent.z) < 0.3f) { // roughly driving the right direction (out)
            // Determine if the car will drive out of the goal on its own

            float xBoundary = RLConstants.goalCenterToPost - 120;
            final var leftBoundary = new Line2(goal.add(xBoundary * 1, 0), goal.add(xBoundary * 1, 2000 * Math.signum(startPos.y)));
            final var rightBoundary = new Line2(goal.add(xBoundary * -1, 0), goal.add(xBoundary * -1, 2000 * Math.signum(startPos.y)));
            final var goalLine = new Line2(goal.sub(xBoundary, 0), goal.add(xBoundary, 0));

            var carPath = new Line2(startPos.flatten(), startPos.flatten().add(startTangent.flatten().mul(3000)));
            if (carPath.getIntersectionPointWithOtherLine(leftBoundary).isEmpty() && carPath.getIntersectionPointWithOtherLine(rightBoundary).isEmpty()) {
                // We drive out of the goal without colliding with the goal walls
                canJustDriveOut = true;

                var intersect = goalLine.getIntersectionPointWithInfOtherLine(carPath);
                if (intersect.isPresent()) {
                    this.endPos = intersect.get().withZ(RLConstants.carElevation);
                    this.endTangent = startTangent;
                    this.endSpeed = MathUtils.clip(startSpeed, 100, 2300);
                    this.timeEstimate = (float) startPos.distance(endPos) / endSpeed;
                }
            }
        }

        if (startPos.z < RLConstants.carElevation + 50 && startSpeed > -500 && canJustDriveOut) {
            // Basically aligned to go out of the goal
            if (this.endPos == null || this.endPos.isZero()) {
                this.endPos = this.makeEndPos(startPos, targetPos);
                this.endTangent = this.endPos.sub(startPos).normalized();
            }
            var car2d = new Car2D(startPos.flatten(), tangent2d.mul(startSpeed), tangent2d, 0, 0, 0);
            car2d.simulateDriveDistanceForward((float) startPos.flatten().distance(this.endPos.flatten()), false);
            this.endSpeed = (float) car2d.velocity.magnitude();
            this.timeEstimate = car2d.time;

            return;
        }

        this.timeEstimate = 0;
        // Slow down

        var car2d = new Car2D(startPos.flatten(), tangent2d.mul(startSpeed), tangent2d, 0, 0, 0);
        car2d.simulateGentleFullStop();
        this.timeEstimate += car2d.time;
        if (printTimes)
            System.out.println("Full stop " + this.timeEstimate);
        this.stopPos = car2d.position.withZ(startPos.z);

        if (Math.abs(this.stopPos.y) <= RLConstants.goalDistance - 60 && Math.abs(this.stopPos.x) < RLConstants.goalCenterToPost) {
            this.endPos = this.stopPos;
            this.endTangent = car2d.tangent.withZ(0);
            this.endSpeed = 900;
            this.timeEstimate = (float) startPos.distance(this.endPos) / ((900 + Math.abs(startSpeed)) / 2);

            //System.out.println("Stopping shortcut");
            return;
        }

        // Clip pos to within goal
        this.stopPos = this.stopPos.withX(MathUtils.clip(this.stopPos.x, -RLConstants.goalCenterToPost, RLConstants.goalCenterToPost));
        this.stopPos = this.stopPos.withY(Math.signum(this.stopPos.y) * Math.max(RLConstants.goalDistance - 60, Math.abs(this.stopPos.y)));

        this.needsJump = true;

        this.endPos = this.makeEndPos(this.stopPos, targetPos);

        this.turnManeuver = new TurnManeuver();
        this.turnManeuver.target = Matrix3x3.lookAt(this.endPos.sub(this.stopPos).withZ(0), new Vector3(0, 0, 1));
        var simCar = new CarData(new Vector3(), new Vector3(), new Vector3(), Matrix3x3.lookAt(startTangent, new Vector3(0, 0, 1)));
        simCar.hasWheelContact = false;
        float timeNeededForTurn = this.turnManeuver.simulate(simCar).elapsedSeconds;
        final float jumpTolerance = 0.05f;
        this.timeForJump = MathUtils.clip(timeNeededForTurn + jumpTolerance, 0.95f, 2.5f) + 0.05f /*tolerance, jumps are hella wierd*/;
        this.timeEstimate += timeForJump; // Jump

        if (printTimes)
            System.out.println("JumpTurn " + timeForJump);

        if (this.stopPos.flatten().distance(this.endPos.flatten()) > 30) {
            car2d.time = 0;
            car2d.velocity = new Vector2();
            //System.out.println("s="+stopPos.flatten()+" end="+this.endPos.flatten());
            car2d.simulateDriveDistanceForward((float) this.stopPos.flatten().distance(this.endPos.flatten()), false);
            this.timeEstimate += car2d.time + RLConstants.tickFrequency; // time to drive out of goal

            if (printTimes)
                System.out.println("Drive " + car2d.time);

            this.endSpeed = (float) car2d.velocity.magnitude();
        } else {
            this.endSpeed = 100;
        }

        this.endTangent = this.endPos.sub(this.stopPos).withZ(0).normalized();

        this.dodgeManeuver = new DodgeManeuver();
        this.dodgeManeuver.duration = CarData.getJumpHoldDurationForTotalAirTime(timeNeededForTurn + jumpTolerance, -RLConstants.gravity.z);
        this.dodgeManeuver.delay = 999;

        this.state = State.STOP;
        //System.out.println(GameData.current().getCarData().playerIndex+": no shortcut taken "+startPos+" "+startTangent+" "+startSpeed+" "+targetPos);
    }

    private Vector3 makeEndPos(Vector3 startPos, Vector3 targetPos) {
        final var goal = new Vector2(0, (RLConstants.goalDistance - 60) * Math.signum(startPos.y));
        final var goalLine = new Line2(goal.sub(RLConstants.goalCenterToPost * 0.9f - 30, 0), goal.add(RLConstants.goalCenterToPost * 0.9f - 30, 0));
        Vector2 endPos;

        if (!targetPos.isZero() && Math.abs(targetPos.y) < RLConstants.goalDistance - 100) {
            // Align with target pos
            Line2 targetPath = new Line2(startPos.flatten(), targetPos.flatten());
            var intersect = goalLine.getIntersectionPointWithOtherLine(targetPath);
            endPos = intersect.orElseGet(() -> goalLine.closestPointOnLine(targetPos.flatten()));
        } else
            endPos = goalLine.closestPointOnLine(startPos.flatten());

        return endPos.withZ(RLConstants.carElevation);
    }

    @Override
    public boolean step(float dt, ControlsOutput output) {
        t += dt;
        super.step(dt, output);
        final GameData gameData = GameData.current();
        final CarData carData = gameData.getCarData();

        if ((Math.abs(carData.position.y) < Math.abs(this.endPos.y) || carData.position.flatten().distance(this.endPos.flatten()) < 15f) && carData.hasWheelContact && (!this.needsJump || carData.forward().dot(this.getEndTangent()) > 0.9f)) {
            if (printTimes)
                System.out.println("EndDrive = " + t);
            t = 0;
            return true;
        }

        if (!this.needsJump) { // just drive out of goal
            DriveManeuver.steerController(output, carData, this.endPos);
            DriveManeuver.speedController(dt, output, carData.forwardSpeed(), this.endSpeed - 2, this.endSpeed + 2, 0.03f, false);
            return false;
        }

        switch (this.state) {
            case STOP: {
                DriveManeuver.speedController(dt, output, carData.forwardSpeed(), 0, 0, 0.03f, false);
                if (Math.abs(carData.forwardSpeed()) < 10) {

                    if (printTimes)
                        System.out.println("Fullstop = " + t);
                    t = 0;

                    this.turnManeuver.target = Matrix3x3.lookAt(this.endPos.withY((RLConstants.goalDistance - 100) * Math.signum(this.endPos.y)).sub(carData.position).withZ(0), new Vector3(0, 0, 1));

                    if (carData.forward().dot(this.getEndTangent()) > 0.97f)
                        this.state = State.DRIVE; // Theres no point in jumping if we're already aligned
                    else
                        this.state = State.JUMP;
                }
            }
            break;
            case JUMP: {
                this.dodgeManeuver.step(dt, output);
                if (carData.hasWheelContact) {
                    if (this.dodgeManeuver.timer >= timeForJump - RLConstants.tickFrequency * 0.5f) {
                        this.state = State.DRIVE;
                        if (printTimes)
                            System.out.println("Jump = " + t);
                        t = 0;
                        break; // done with jumping
                    }
                } else {
                    this.turnManeuver.step(dt, output);
                    if (this.turnManeuver.isDone())
                        output.withThrottle(1);
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
        renderer.drawCentered3dCube(Color.RED, this.startPos, 20);
        renderer.drawCentered3dCube(Color.GREEN, this.endPos, 20);

        if (!this.needsJump) {
            renderer.drawLine3d(color, this.startPos, this.endPos);
            return;
        }

        renderer.drawLine3d(color, this.startPos, this.stopPos);
        renderer.drawLine3d(color.darker(), this.stopPos, this.stopPos.add(0, 0, 50));
        renderer.drawLine3d(color.brighter(), this.stopPos, this.endPos);
    }

    @Override
    public boolean shouldBeInAir() {
        return this.state == State.JUMP;
    }

    @Override
    public boolean canInterrupt() {
        return this.state == State.DRIVE || !this.needsJump;
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
