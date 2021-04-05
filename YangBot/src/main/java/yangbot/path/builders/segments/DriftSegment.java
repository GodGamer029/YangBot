package yangbot.path.builders.segments;

import yangbot.input.ControlsOutput;
import yangbot.input.Physics3D;
import yangbot.path.builders.PathSegment;
import yangbot.strategy.manuever.DriftControllerManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.lut.PowerslideUtil;
import yangbot.util.math.vector.Vector3;

import java.awt.*;

public class DriftSegment extends PathSegment {

    private final DriftControllerManeuver driftControllerManeuver;

    private final Vector3 startPos;
    private final Vector3 startTangent;
    private final Vector3 endTangent;
    private final Vector3 endPos;
    private final float totalTraversalTime;
    private final float endSpeed;
    private final boolean isEasySlide;

    public DriftSegment(Physics3D state, Vector3 endTangent, float startBoost) {
        super(state, startBoost);
        this.endTangent = endTangent;
        this.startPos = state.position;
        this.startTangent = state.forward();

        this.driftControllerManeuver = new DriftControllerManeuver();
        this.driftControllerManeuver.targetDirection = endTangent.normalized();

        assert startPos.z < 100;

        final float angle = (float) Math.abs(startTangent.flatten().normalized().angleBetween(endTangent.flatten().normalized()) * (180f / Math.PI));
        isEasySlide = angle < 80;

        var powerslide = PowerslideUtil.getPowerslide(startSpeed, startPos.flatten(), startTangent.flatten().normalized(), endTangent.flatten().normalized());
        this.endPos = powerslide.finalPos.withZ(startPos.z).add(endTangent.withZ(0).mul(15));
        this.totalTraversalTime = powerslide.time;
        this.endSpeed = powerslide.finalSpeed;
    }

    @Override
    public boolean step(float dt, ControlsOutput output) {
        super.step(dt, output);
        assert !this.driftControllerManeuver.isDone() : "Path segment cannot be traversed twice";

        this.driftControllerManeuver.step(dt, output);

        return this.driftControllerManeuver.isDone();
    }

    @Override
    public boolean canInterrupt() {
        return isEasySlide;
    }

    @Override
    public Vector3 getEndPos() {
        return this.endPos;
    }

    @Override
    public Vector3 getEndTangent() {
        return this.endTangent;
    }

    @Override
    public float getEndSpeed() {
        return this.endSpeed;
    }

    @Override
    public float getTimeEstimate() {
        return this.totalTraversalTime * 1.05f;
    }

    @Override
    public void draw(AdvancedRenderer renderer, Color color) {
        renderer.drawCentered3dCube(Color.RED, this.startPos, 30);
        renderer.drawLine3d(color, this.startPos, this.startPos.add(this.startTangent.mul(20)));

        //renderer.drawString3d("Drift", Color.WHITE, this.startPos.add(0, 0, 20), 1, 1);
        //renderer.drawLine3d(color.darker().darker(), this.startPos, this.endPos);

        renderer.drawCentered3dCube(Color.GREEN, this.endPos, 30);
        renderer.drawLine3d(color, this.endPos, this.endPos.add(this.endTangent.mul(20)));
    }
}
