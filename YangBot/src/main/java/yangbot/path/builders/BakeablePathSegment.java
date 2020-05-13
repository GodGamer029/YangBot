package yangbot.path.builders;

import org.jetbrains.annotations.NotNull;
import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.path.Curve;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.strategy.manuever.FollowPathManeuver;
import yangbot.util.math.vector.Vector3;

public abstract class BakeablePathSegment extends PathSegment {

    protected Curve bakedPath = null;
    protected FollowPathManeuver followPathManeuver;
    private float timeEstimate = -1;

    protected BakeablePathSegment() {
        this.followPathManeuver = new FollowPathManeuver();
        this.followPathManeuver.arrivalTime = -1;
    }

    @Override
    public boolean step(float dt, ControlsOutput output) {
        super.step(dt, output);

        this.followPathManeuver.path = this.getBakedPath();
        //this.followPathManeuver.arrivalTime = (this.getTimeEstimate() - this.timer) + GameData.current().getCarData().elapsedSeconds;
        //this.followPathManeuver.draw(GameData.current().getAdvancedRenderer(), GameData.current().getCarData());
        this.followPathManeuver.step(dt, output);

        return this.followPathManeuver.isDone();
    }

    @Override
    public Vector3 getEndPos() {
        return this.bake(16).pointAt(0);
    }

    @Override
    public Vector3 getEndTangent() {
        return this.bake(16).tangentAt(0);
    }

    @Override
    public float getEndSpeed() {
        return this.bake(16).maxSpeedAt(0);
    }

    @Override
    public float getTimeEstimate() {
        if (this.timeEstimate == -1)
            this.timeEstimate = this.bake(16).distances[0] / DriveManeuver.max_throttle_speed + 0.05f;
        return this.timeEstimate;
    }

    protected abstract @NotNull Curve bakeInternal(int maxSamples);

    public final @NotNull Curve bake(int maxSamples) {
        if (this.bakedPath == null) {
            this.bakedPath = this.bakeInternal(maxSamples);
            this.bakedPath.calculateMaxSpeeds(CarData.MAX_VELOCITY, CarData.MAX_VELOCITY);
        }

        return this.bakedPath;
    }


    public @NotNull Curve getBakedPath() {
        assert this.bakedPath != null;
        return this.bakedPath;
    }
}
