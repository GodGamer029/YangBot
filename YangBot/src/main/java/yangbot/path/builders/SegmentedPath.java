package yangbot.path.builders;

import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.GameData;
import yangbot.path.Curve;
import yangbot.path.builders.segments.CurveSegment;
import yangbot.util.AdvancedRenderer;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class SegmentedPath {

    public static final int MAX_SAMPLES = 64;
    private final List<PathSegment> segmentList;
    private int currentSegment = 0;
    private float arrivalTime = 0;
    private float startTime = 0;
    private Vector3 endTangent = new Vector3(), endPos = new Vector3();
    private float t = 0;

    public SegmentedPath(List<PathSegment> segments) {
        this.segmentList = Collections.unmodifiableList(segments);
        this.segmentList.forEach(seg -> {
            if (seg instanceof BakeablePathSegment)
                ((BakeablePathSegment) seg).bake(MAX_SAMPLES);

            seg.setStartTime(arrivalTime + GameData.current().getCarData().elapsedSeconds);
            arrivalTime += seg.getTimeEstimate();
        });
        if (this.segmentList.size() > 0) {
            var lastSegment = this.segmentList.get(this.segmentList.size() - 1);
            this.endTangent = lastSegment.getEndTangent();
            this.endPos = lastSegment.getEndPos();
        }
    }

    public static SegmentedPath from(Curve c, float startSpeed, float arrivalTime, float arrivalSpeed) {
        return new SegmentedPath(List.of(new CurveSegment(c, startSpeed, arrivalTime, arrivalSpeed, 0)));
    }

    public static SegmentedPath from(Curve c, float startSpeed) {
        return new SegmentedPath(List.of(new CurveSegment(c, startSpeed, 0)));
    }

    public List<PathSegment> getSegmentList() {
        return segmentList;
    }

    public boolean canInterrupt() {
        if (this.currentSegment >= this.segmentList.size())
            return true; // Already done

        var current = this.segmentList.get(this.currentSegment);
        return current.canInterrupt();
    }

    public PathSegment getLastPathSegment() {
        assert this.segmentList.size() > 0;
        return this.segmentList.get(this.segmentList.size() - 1);
    }

    public Optional<PathSegment> getCurrentPathSegment() {
        if (this.currentSegment >= this.segmentList.size())
            return Optional.empty(); // Already done

        return Optional.of(this.segmentList.get(this.currentSegment));
    }

    public boolean shouldBeOnGround() {
        if (this.currentSegment >= this.segmentList.size())
            return true; // Already done

        var current = this.segmentList.get(this.currentSegment);
        return !current.shouldBeInAir();
    }

    public float getTotalTimeEstimate() {
        float t = 0;
        for (var seg : this.segmentList) {
            t += seg.getTimeEstimate();
        }
        return t;
    }

    public void draw(AdvancedRenderer renderer) {
        for (int i = 0; i < segmentList.size(); i++) {
            var seg = segmentList.get(i);
            seg.draw(renderer, i == currentSegment ? Color.BLUE : Color.YELLOW);
            renderer.drawString3d(seg.getClass().getSimpleName(), Color.WHITE, seg.getEndPos().add(0, 0, 40 + (i % 2) * 30), 1, 1);
        }

        var curSeg = this.getCurrentPathSegment();
        if (curSeg.isPresent()) {
            renderer.drawString2d("Seg: " + curSeg.get().getClass().getSimpleName() + " (" + (this.currentSegment + 1) + "/" + this.segmentList.size() + ", canInterrupt=" + curSeg.get().canInterrupt() + ")", Color.WHITE, new Point(400, 400), 2, 2);
            renderer.drawString2d("Arrival: " + ((this.arrivalTime + this.startTime) - GameData.current().getCarData().elapsedSeconds), Color.WHITE, new Point(400, 450), 2, 2);
            renderer.drawString2d("Speed: " + GameData.current().getCarData().forwardSpeed(), Color.WHITE, new Point(400, 490), 1, 1);
            renderer.drawString2d("StartSpeed: " + curSeg.get().getStartSpeed(), Color.WHITE, new Point(400, 510), 1, 1);
            renderer.drawString2d("EndSpeed: " + curSeg.get().getEndSpeed(), Color.WHITE, new Point(400, 530), 1, 1);
            renderer.drawString2d("T estimate: " + curSeg.get().getTimeEstimate(), Color.WHITE, new Point(400, 550), 1, 1);
        }
    }

    public Vector3 getEndTangent() {
        return this.endTangent;
    }

    public float getEndSpeed() {
        return this.getLastPathSegment().getEndSpeed();
    }

    public boolean isDone() {
        return this.currentSegment >= this.segmentList.size();
    }

    public boolean shouldReset(CarData carData) {
        return (!carData.hasWheelContact && this.shouldBeOnGround());
    }

    public Vector3 getEndPos() {
        return this.endPos;
    }

    public boolean step(float dt, ControlsOutput output) {
        assert this.currentSegment < this.segmentList.size() : "Segmented path already done (" + this.currentSegment + " >= " + this.segmentList.size() + ")";

        t += dt;
        if (this.startTime == 0)
            this.startTime = GameData.current().getCarData().elapsedSeconds;
        var current = this.segmentList.get(this.currentSegment);
        var isDone = current.step(dt, output);
        if (isDone) {
            //System.out.println("Segment completed t="+t+ " pred="+current.getTimeEstimate()+" Sstart="+current.getStartSpeed()+" Send="+current.getEndSpeed()+" now="+GameData.current().getCarData().velocity.flatten().magnitude());
            t = 0;
            this.currentSegment++;

            return this.currentSegment >= this.segmentList.size();
        }

        return false;
    }
}
