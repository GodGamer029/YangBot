package yangbot.path.builders.segments;

import org.jetbrains.annotations.NotNull;
import yangbot.input.Physics3D;
import yangbot.path.Curve;
import yangbot.path.builders.BakeablePathSegment;
import yangbot.path.builders.PathBuilder;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.math.Car1D;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
import java.util.List;

public class StraightLineSegment extends BakeablePathSegment {

    private final Vector3 startPos, endPos, normal;
    private float timeEstimate = -1, endSpeed = -1;

    public StraightLineSegment(PathBuilder b, Vector3 endPos, float endSpeed, float arrivalTime, boolean allowBoost) {
        this(b.getCurrentPosition(), b.getCurrentSpeed(), endPos, endSpeed, arrivalTime, allowBoost, b.getCurrentBoost());
    }

    public StraightLineSegment(Physics3D state, float startBoost, Vector3 endPos, float endSpeed, float arrivalTime, boolean allowBoost) {
        this(state.position, state.forwardSpeed(), endPos, endSpeed, arrivalTime, allowBoost, startBoost);
    }

    public StraightLineSegment(Vector3 startPos, float startSpeed, Vector3 endPos, float endSpeed, float arrivalTime, boolean allowBoost, float startBoost) {
        super(startSpeed, allowBoost ? startBoost : 0, endSpeed, arrivalTime);
        this.allowBoost = allowBoost;
        this.startPos = startPos;
        this.endPos = endPos;
        this.normal = new Vector3(0, 0, 1);
        this.endSpeed = startSpeed;
    }

    public Vector3 getStartPos() {
        return this.startPos;
    }

    public Vector3 getStartTangent() {
        return this.endPos.sub(this.startPos).normalized();
    }

    @Override
    public Vector3 getEndPos() {
        return this.endPos;
    }

    @Override
    public Vector3 getEndTangent() {
        return this.getStartTangent();
    }

    private boolean hasMontoneSpeed() {
        return this.arrivalTime > 0 && this.startTime > 0 && this.arrivalTime > this.startTime;
    }

    @Override
    public float getTimeEstimate() {
        if(this.timeEstimate >= 0)
            return this.timeEstimate;
        if (this.hasMontoneSpeed()) {
            var estimate = DriveManeuver.driveArriveAt(Math.max(0, this.getStartSpeed()), (float) this.startPos.distance(this.endPos), this.arrivalTime - this.startTime - 0.1f, allowBoost);
            this.timeEstimate = estimate.getKey();
        } else {
            var sim = Car1D.simulateDriveDistanceForwardAccel((float) this.endPos.distance(this.startPos), this.startSpeed, this.startBoost);
            this.timeEstimate = sim.time;
            this.endSpeed = sim.speed;
        }
        return this.timeEstimate;
    }

    @Override
    public float getEndSpeed() {
        if (this.hasMontoneSpeed())
            return DriveManeuver.driveArriveAt(Math.max(0, this.getStartSpeed()), (float) this.startPos.distance(this.endPos), this.arrivalTime - this.startTime - 0.1f, allowBoost).getValue();
        if (this.endSpeed < 0)
            getTimeEstimate();
        return this.endSpeed;
    }

    @Override
    protected @NotNull Curve bakeInternal(int maxSamples) {
        return new Curve(List.of(
                new Curve.ControlPoint(startPos, getStartTangent(), normal),
                new Curve.ControlPoint(endPos, getEndTangent(), normal)),
                maxSamples);
    }

    @Override
    public void draw(AdvancedRenderer renderer, Color color) {
        renderer.drawCentered3dCube(Color.GREEN, this.startPos, 20);
        renderer.drawCentered3dCube(Color.RED, this.endPos, 20);
        renderer.drawLine3d(color, this.startPos, this.endPos);
    }
}
