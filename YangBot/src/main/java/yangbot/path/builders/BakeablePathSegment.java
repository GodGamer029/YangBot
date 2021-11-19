package yangbot.path.builders;

import org.jetbrains.annotations.NotNull;
import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.GameData;
import yangbot.input.RLConstants;
import yangbot.path.Curve;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.strategy.manuever.FollowPathManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Vector3;

import java.awt.*;

public abstract class BakeablePathSegment extends PathSegment {

    protected Curve bakedPath = null;
    public FollowPathManeuver followPathManeuver;
    private float timeEstimate = -1;
    protected float arrivalTime = -1;
    protected float bakedArrivalTime = -1, bakedEndSpeed = -1;
    protected float arrivalSpeed = -1;
    protected boolean allowBoost = false;

    protected BakeablePathSegment(float startSpeed, float startBoost, float endSpeed, float arrivalTime) {
        super(startSpeed, startBoost);
        this.followPathManeuver = new FollowPathManeuver();
        this.followPathManeuver.arrivalTime = -1;
        this.arrivalSpeed = endSpeed;
        this.arrivalTime = arrivalTime;
        this.allowBoost |= this.arrivalSpeed > DriveManeuver.max_throttle_speed;
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

    public boolean isAllowBoost() {
        return allowBoost;
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
        if(this.arrivalTime <= 0 || this.startTime <= 0 || p.maxSpeeds.length == 0 || p.minimumSpeed < 0 ||
                this.getTimeEstimate() - this.arrivalTime - this.startTime > -0.2f)
            return p.maxSpeedAt(0);
        if(this.bakedEndSpeed >= 0)
            return this.bakedEndSpeed;

        float T = this.arrivalTime - this.startTime;
        assert T < 20;
        float reqSpeed = p.length / T;
        if(reqSpeed < p.minimumSpeed){
            this.bakedEndSpeed = reqSpeed;
            return this.bakedEndSpeed;
        }
        float dt = RLConstants.tickFrequency;
        float s = p.length;
        float v = p.maxSpeedAt(s);

        float boost = this.startBoost;
        float t = 0;
        for (; t < T; t += dt){
            //var targetSpeed = FollowPathManeuver.determineSpeedPlan(s, Math.max(dt * 3, T - t), dt, v, this.arrivalSpeed, p);
            var targetSpeed = Math.min(p.maxSpeedAt(s), s / Math.max(RLConstants.tickFrequency * 2, T - t));
            assert Float.isFinite(targetSpeed) : p.maxSpeedAt(s) + " s="+s + " T="+T+ " t="+t;
            var c = new ControlsOutput();
            DriveManeuver.speedController(dt, c, v, targetSpeed, targetSpeed + 5, 0.04f, true, 0);
            if(c.holdBoost()){
                if(boost <= 0)
                    c.withBoost(false);
                else
                    boost -= CarData.BOOST_CONSUMPTION * dt;
            }
            var accel = CarData.driveForceForward(c, v, 0, 0) * dt;
            assert Float.isFinite(accel);
            assert Float.isFinite(v);
            s -= MathUtils.clip(v + 0.5f * accel, -CarData.MAX_VELOCITY, CarData.MAX_VELOCITY) * dt;
            v += accel;
            v = MathUtils.clip(v, -CarData.MAX_VELOCITY, CarData.MAX_VELOCITY);

            if(s <= 0){
                break;
            }
        }
        this.bakedEndSpeed = v;

        return this.bakedEndSpeed;
    }

    @Override
    public void setStartTime(float startTime) {
        super.setStartTime(startTime);
        if (this.arrivalTime > 0)
            this.bakedArrivalTime = Math.max(this.arrivalTime, this.startTime + this.getTimeEstimate());
    }

    @Override
    public float getTimeEstimate() {
        if (this.timeEstimate == -1)
            this.bake(SegmentedPath.MAX_SAMPLES);

        assert this.timeEstimate != -1;

        return this.timeEstimate;
    }

    public float getArrivalTime() {
        return this.arrivalTime;
    }

    protected abstract @NotNull Curve bakeInternal(int maxSamples);

    public final @NotNull Curve bake(int maxSamples) {
        if (this.bakedPath == null) {
            this.bakedPath = this.bakeInternal(maxSamples);
            assert this.bakedPath != null : this.getClass().getSimpleName();
            this.timeEstimate = this.bakedPath.calculateMaxSpeeds(
                    MathUtils.clip(this.getStartSpeed(), 0, CarData.MAX_VELOCITY),
                    this.arrivalSpeed < 0 ? -1 : this.arrivalSpeed,
                    this.allowBoost ? this.startBoost : 0);
            this.bakedEndSpeed = -1;
        }else if(this.bakedPath.maxSpeeds.length == 0){
            this.timeEstimate = this.bakedPath.calculateMaxSpeeds(
                    MathUtils.clip(this.getStartSpeed(), 0, CarData.MAX_VELOCITY),
                    this.arrivalSpeed < 0 ? -1 : this.arrivalSpeed,
                    this.allowBoost ? this.startBoost : 0);
            this.bakedEndSpeed = -1;
        }
        assert Float.isFinite(this.bakedPath.length);

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
