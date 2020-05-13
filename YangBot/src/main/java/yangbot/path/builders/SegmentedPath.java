package yangbot.path.builders;

import yangbot.input.ControlsOutput;
import yangbot.util.AdvancedRenderer;

import java.awt.*;
import java.util.Collections;
import java.util.List;

public class SegmentedPath {

    private final List<PathSegment> segmentList;
    private int currentSegment = 0;

    public SegmentedPath(List<PathSegment> segments) {
        this.segmentList = Collections.unmodifiableList(segments);
        this.segmentList.forEach(seg -> {
            if (seg instanceof BakeablePathSegment)
                ((BakeablePathSegment) seg).bake(16);
        });
    }

    public void draw(AdvancedRenderer renderer) {
        for (int i = 0; i < segmentList.size(); i++) {
            var seg = segmentList.get(i);
            seg.draw(renderer, i == currentSegment ? Color.BLUE : Color.YELLOW);
            renderer.drawString3d(seg.getClass().getSimpleName(), Color.WHITE, seg.getEndPos().add(0, 0, 40 + (i % 2) * 30), 1, 1);
        }

    }

    public boolean canInterrupt() {
        if (this.currentSegment >= this.segmentList.size())
            return true; // Already done

        var current = this.segmentList.get(this.currentSegment);
        return current.canInterrupt();
    }

    public boolean shouldBeInAir() {
        if (this.currentSegment >= this.segmentList.size())
            return false; // Already done

        var current = this.segmentList.get(this.currentSegment);
        return current.shouldBeInAir();
    }

    public boolean step(float dt, ControlsOutput output) {
        assert this.currentSegment < this.segmentList.size() : "Segmented path already done (" + this.currentSegment + " >= " + this.segmentList.size() + ")";
        var current = this.segmentList.get(this.currentSegment);
        var isDone = current.step(dt, output);
        if (isDone) {
            this.currentSegment++;
            return this.currentSegment >= this.segmentList.size();
        }

        return false;
    }
}
