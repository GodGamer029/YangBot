package yangbot.path.builders.segments;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.GameData;
import yangbot.path.builders.PathSegment;
import yangbot.strategy.manuever.DodgeManeuver;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.strategy.manuever.TurnManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector3;

import java.awt.*;

public class FlipSegment extends PathSegment {

    private final Vector3 startPos, startTangent;
    private final Vector3 jumpStartPos, jumpEndPos;
    private final Vector3 endPos;
    private final float endSpeed;

    private DodgeManeuver dodgeManeuver;
    private TurnManeuver realignManeuver;

    public FlipSegment(Vector3 startPos, Vector3 startTangent, float startSpeed) {
        super(startSpeed);
        assert startPos.z < 100;
        this.startPos = startPos;
        this.startTangent = startTangent;

        Vector3 currentPos = startPos;
        Vector3 currentVel = startTangent.mul(startSpeed).withZ(0);

        // Jump phase
        currentPos = currentPos.add(currentVel.mul(0.15f));
        this.jumpStartPos = currentPos;

        // Dodge
        CarData simCar = new CarData(currentPos, currentVel, new Vector3(), Matrix3x3.lookAt(currentVel, new Vector3(0, 0, 1)));
        simCar.hasWheelContact = false;
        simCar.jumped = true;
        simCar.doubleJumped = false;
        simCar.jumpTimer = 0.15f;
        simCar.lastControllerInputs.withJump(false);
        simCar.step(new ControlsOutput().withJump(true).withPitch(-1), 0);
        assert simCar.doubleJumped;

        currentVel = simCar.velocity.withZ(0);

        this.jumpEndPos = currentPos.add(currentVel.mul(1.25f - 0.15f));

        currentPos = currentPos.add(currentVel.mul(1.25f - 0.15f + 0.1f));

        this.endPos = currentPos;
        this.endSpeed = (float) currentVel.magnitude();

        this.dodgeManeuver = new DodgeManeuver();
        this.realignManeuver = new TurnManeuver();
    }

    public static boolean canReplace(StraightLineSegment straightSegment, float startSpeed) {
        if (straightSegment.getStartPos().z > 50 || straightSegment.getEndPos().z > 50)
            return false;

        final float tolerance = 0.1f; // seconds added before and after the dodge

        // Wait 0.1s
        var startTangent = straightSegment.getStartTangent();
        var startPos = straightSegment.getStartPos().add(straightSegment.getStartTangent().mul(startSpeed).mul(0.15f));
        if (startPos.z > 50)
            return false;
        var flipSegment = new FlipSegment(startPos, straightSegment.getStartTangent(), startSpeed + 20);
        // Did we overshoot?
        var flipEndPos = flipSegment.endPos.add(startTangent.mul(flipSegment.endSpeed * tolerance));
        var tangent = straightSegment.getEndPos().sub(flipEndPos).normalized();

        if (tangent.dot(startTangent) < 0)
            return false;

        return true;
    }

    @Override
    public boolean step(float dt, ControlsOutput output) {
        super.step(dt, output);

        final GameData gameData = GameData.current();
        final CarData car = gameData.getCarData();

        output.withThrottle(0.03f);
        if (car.hasWheelContact && this.dodgeManeuver.timer > 0.1f) {
            DriveManeuver.steerController(output, car, this.endPos);
            return this.timer >= this.getTimeEstimate();
        }

        if (!car.hasWheelContact && car.doubleJumped && Math.abs(car.angularVelocity.dot(car.right())) < 5) {
            this.realignManeuver.target = Matrix3x3.lookAt(this.getEndPos().add(this.getEndTangent().mul(car.velocity.magnitude() * 0.3)).sub(car.position).withZ(0).normalized().add(car.velocity.normalized()).normalized(), new Vector3(0, 0, 1));
            this.realignManeuver.step(dt, output);
        } else if (this.timer > 0.2f || this.dodgeManeuver.timer > 0 || !car.hasWheelContact || (car.forward().angle(this.getEndTangent()) < Math.PI * 0.2f && this.getEndTangent().angle(car.velocity.normalized()) < Math.PI * 0.05f && Math.abs(car.velocity.z) < 50)) {
            this.dodgeManeuver.duration = 0.08f;
            this.dodgeManeuver.delay = this.dodgeManeuver.duration + 0.07f;
            this.dodgeManeuver.target = this.endPos;

            this.dodgeManeuver.step(dt, output);
        } else {
            // Align with tangent
            DriveManeuver.steerController(output, car, car.position.add(this.getEndTangent()), 1.5f);
        }

        // Timeout
        return this.dodgeManeuver.timer > 0.1f + this.getTimeEstimate() || (this.dodgeManeuver.timer > 0.3 && car.hasWheelContact);
    }

    @Override
    public void draw(AdvancedRenderer renderer, Color color) {
        renderer.drawCentered3dCube(Color.RED, this.startPos, 20);

        renderer.drawLine3d(color, this.startPos, this.jumpStartPos);
        renderer.drawLine3d(color, this.jumpStartPos, this.jumpStartPos.add(0, 0, 50));
        renderer.drawLine3d(color, this.jumpStartPos.add(0, 0, 50), this.jumpEndPos.add(0, 0, 50));
        renderer.drawLine3d(color, this.jumpEndPos.add(0, 0, 50), this.jumpEndPos);
        renderer.drawLine3d(color, this.jumpEndPos, endPos);

        renderer.drawCentered3dCube(Color.GREEN, this.endPos, 20);
    }

    @Override
    public boolean canInterrupt() {
        return this.dodgeManeuver.timer <= 0 || (this.dodgeManeuver.timer > 0.1f && GameData.current().getCarData().hasWheelContact);
    }

    @Override
    public Vector3 getEndPos() {
        return this.endPos;
    }

    @Override
    public Vector3 getEndTangent() {
        return this.startTangent;
    }

    @Override
    public float getEndSpeed() {
        return endSpeed;
    }

    @Override
    public float getTimeEstimate() {
        return 1.35f;
    }

    @Override
    public boolean shouldBeInAir() {
        return true;
    }
}
