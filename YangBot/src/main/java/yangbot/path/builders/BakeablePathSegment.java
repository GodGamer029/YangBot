package yangbot.path.builders;

import org.jetbrains.annotations.NotNull;
import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.path.Curve;
import yangbot.strategy.manuever.FollowPathManeuver;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Vector3;

public abstract class BakeablePathSegment extends PathSegment {

    protected Curve bakedPath = null;
    protected FollowPathManeuver followPathManeuver;
    private float timeEstimate = -1;
    protected float arrivalTime = -1;
    protected float arrivalSpeed = -1;

    protected BakeablePathSegment(float startSpeed, float endSpeed, float arrivalTime) {
        super(startSpeed);
        this.followPathManeuver = new FollowPathManeuver();
        this.followPathManeuver.arrivalTime = -1;
        this.arrivalSpeed = endSpeed;
        this.arrivalTime = arrivalTime;
    }

    @Override
    public boolean step(float dt, ControlsOutput output) {
        super.step(dt, output);

        this.followPathManeuver.path = this.getBakedPath();
        this.followPathManeuver.arrivalTime = this.arrivalTime;

        //this.followPathManeuver.arrivalSpeed = this.arrivalSpeed; // not needed because arrival speed is capped by curve.calculatemaxspeeds
        //this.followPathManeuver.arrivalTime = (this.getTimeEstimate() - this.timer) + GameData.current().getCarData().elapsedSeconds;
        //this.followPathManeuver.draw(GameData.current().getAdvancedRenderer(), GameData.current().getCarData());
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
    public float getTimeEstimate() {
        if (this.timeEstimate == -1)
            this.bake(SegmentedPath.MAX_SAMPLES);

        assert this.timeEstimate != -1;

        return this.timeEstimate;
    }

    protected abstract @NotNull Curve bakeInternal(int maxSamples);

    public final @NotNull Curve bake(int maxSamples) {
        if (this.bakedPath == null) {
            this.bakedPath = this.bakeInternal(maxSamples);
            this.timeEstimate = this.bakedPath.calculateMaxSpeeds(MathUtils.clip(this.getStartSpeed(), 0, CarData.MAX_VELOCITY), this.arrivalSpeed < 0 ? CarData.MAX_VELOCITY : this.arrivalSpeed, false/*automatically turns true if arrival speed needs boost*/);
        }

        return this.bakedPath;
    }


    public @NotNull Curve getBakedPath() {
        assert this.bakedPath != null;
        return this.bakedPath;
    }
}
