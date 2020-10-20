package yangbot.path.builders.segments;

import org.jetbrains.annotations.NotNull;
import yangbot.path.Curve;
import yangbot.path.builders.BakeablePathSegment;
import yangbot.path.builders.PathBuilder;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
import java.util.List;

public class StraightLineSegment extends BakeablePathSegment {

    private final Vector3 startPos, endPos, normal;

    public StraightLineSegment(Vector3 startPos, float startSpeed, Vector3 endPos, float startBoost) {
        this(startPos, startSpeed, endPos, new Vector3(0, 0, 1), -1, -1, false, startBoost);
    }

    public StraightLineSegment(PathBuilder b, Vector3 endPos, float endSpeed, float arrivalTime, boolean allowBoost) {
        this(b.getCurrentPosition(), b.getCurrentSpeed(), endPos, new Vector3(0, 0, 1), endSpeed, arrivalTime, allowBoost, b.getCurrentBoost());
    }

    public StraightLineSegment(Vector3 startPos, float startSpeed, Vector3 endPos, float endSpeed, float arrivalTime, boolean allowBoost, float startBoost) {
        this(startPos, startSpeed, endPos, new Vector3(0, 0, 1), endSpeed, arrivalTime, allowBoost, startBoost);
    }

    public StraightLineSegment(Vector3 startPos, float startSpeed, Vector3 endPos, Vector3 normal, float endSpeed, float arrivalTime, boolean allowBoost, float startBoost) {
        super(startSpeed, startBoost, endSpeed, arrivalTime);
        this.allowBoost = allowBoost;
        this.startPos = startPos;
        this.endPos = endPos;
        this.normal = normal;
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
        if (this.hasMontoneSpeed()) {
            var estimate = DriveManeuver.driveArriveAt(this.getStartSpeed(), (float) this.startPos.distance(this.endPos), this.arrivalTime - this.startTime - 0.1f, allowBoost);
            //System.out.println("Time estimate: t="+estimate.getKey()+" vf="+estimate.getValue()+" prev="+(this.arrivalTime - this.startTime)+ " s="+((float) this.startPos.distance(this.endPos))+" v0="+this.getStartSpeed());
            var oldEstimate = super.getTimeEstimate();
            //System.out.println("old="+oldEstimate+" new="+estimate.getKey());
            return estimate.getKey();
        }

        return super.getTimeEstimate();
    }

    @Override
    public float getEndSpeed() {
        if (this.hasMontoneSpeed())
            return DriveManeuver.driveArriveAt(this.getStartSpeed(), (float) this.startPos.distance(this.endPos), this.arrivalTime - this.startTime - 0.1f, allowBoost).getValue();
        return super.getEndSpeed();
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
