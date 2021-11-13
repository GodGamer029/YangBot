package yangbot.path.builders;

import org.jetbrains.annotations.NotNull;
import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.GameData;
import yangbot.path.Curve;
import yangbot.strategy.manuever.FollowPathManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Vector3;

import java.awt.*;

public abstract class BakeablePathSegment extends PathSegment {

    protected Curve bakedPath = null;
    protected FollowPathManeuver followPathManeuver;
    private float timeEstimate = -1;
    protected float arrivalTime = -1;
    protected float bakedArrivalTime = -1;
    protected float arrivalSpeed = -1;
    protected boolean allowBoost = false;

    protected BakeablePathSegment(float startSpeed, float startBoost, float endSpeed, float arrivalTime) {
        super(startSpeed, startBoost);
        this.followPathManeuver = new FollowPathManeuver();
        this.followPathManeuver.arrivalTime = -1;
        this.arrivalSpeed = endSpeed;
        this.arrivalTime = arrivalTime;
    }

    protected BakeablePathSegment(PathBuilder builder, float endSpeed, float arrivalTime) {
        this(builder.getCurrentSpeed(), builder.getCurrentBoost(), endSpeed, arrivalTime);
    }

    @Override
    public boolean step(float dt, ControlsOutput output) {
        super.step(dt, output);

        this.followPathManeuver.path = this.getBakedPath();
        this.followPathManeuver.arrivalTime = this.bakedArrivalTime >= 0 ? this.bakedArrivalTime : this.arrivalTime;

        this.followPathManeuver.draw(GameData.current().getAdvancedRenderer(), GameData.current().getCarData());
        try {
            this.followPathManeuver.step(dt, output);
        } catch (Exception e) {
            System.err.println("Exception in Segment: " + this.getClass().getSimpleName());
            throw e;
        }

        return this.followPathManeuver.isDone();
    }

    @Override
    public Vector3 getEndPos() {
        return this.bake(SegmentedPath.MAX_SAMPLES).pointAt(0);
    }

    @Override
    public Vector3 getEndTangent() {
        return this.bake(SegmentedPath.MAX_SAMPLES).tangentAt(0);
    }

    @Override
    public float getEndSpeed() {
        var p = this.bake(SegmentedPath.MAX_SAMPLES);
        return p.maxSpeedAt(0);
    }

    @Override
    public void setStartTime(float startTime) {
        super.setStartTime(startTime);
        if (this.arrivalTime > 0)
            this.bakedArrivalTime = Math.max(this.arrivalTime, startTime + this.getTimeEstimate());
    }

    @Override
    public float getTimeEstimate() {
        if (this.timeEstimate == -1)
            this.bake(SegmentedPath.MAX_SAMPLES);

        assert this.timeEstimate != -1;
        //assert !Float.isNaN(this.timeEstimate) && !Float.isInfinite(this.timeEstimate) : this.timeEstimate;

        return this.timeEstimate;
    }

    protected abstract @NotNull Curve bakeInternal(int maxSamples);

    public final @NotNull Curve bake(int maxSamples) {
        if (this.bakedPath == null) {
            this.bakedPath = this.bakeInternal(maxSamples);
            assert this.bakedPath != null : this.getClass().getSimpleName();
            this.timeEstimate = this.bakedPath.calculateMaxSpeeds(
                    MathUtils.clip(this.getStartSpeed(), 0, CarData.MAX_VELOCITY),
                    this.arrivalSpeed < 0 ? -1 : this.arrivalSpeed,
                    this.allowBoost/*automatically turns true if arrival speed needs boost*/);
        }

        return this.bakedPath;
    }

    public @NotNull Curve getBakedPath() {
        assert this.bakedPath != null;
        return this.bakedPath;
    }

    @Override
    public void draw(AdvancedRenderer renderer, Color color) {
        if(this.bakedPath == null)
            return;

        this.bakedPath.draw(renderer, color);
    }

    public void setArrivalSpeed(float newSpeed){
        this.arrivalSpeed = newSpeed;
        this.bakedPath = null;
    }

    public void setArrivalTime(float t){
        this.arrivalTime = t;
        this.bakedPath = null;
    }
}
