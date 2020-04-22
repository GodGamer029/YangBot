package yangbot.path.builders;

import yangbot.input.CarData;
import yangbot.path.builders.segments.FlipSegment;
import yangbot.path.builders.segments.StraightLineSegment;
import yangbot.util.math.vector.Vector3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class PathBuilder {

    private final List<PathSegment> pathSegments;
    private final CarData start;
    private boolean optimize = false;

    public PathBuilder(CarData start) {
        this.start = start;
        this.pathSegments = new ArrayList<>();
    }

    public PathBuilder add(PathSegment pathSegment) {
        this.pathSegments.add(pathSegment);
        return this;
    }

    public PathBuilder optimize() {
        this.optimize = true;
        return this;
    }

    public Vector3 getCurrentPosition() {
        if (this.pathSegments.size() == 0)
            return this.start.position;
        return this.pathSegments.get(this.pathSegments.size() - 1).getEndPos();
    }

    public Vector3 getCurrentTangent() {
        if (this.pathSegments.size() == 0)
            return this.start.getPathStartTangent();
        return this.pathSegments.get(this.pathSegments.size() - 1).getEndTangent();
    }

    public float getCurrentSpeed() {
        if (this.pathSegments.size() == 0)
            return (float) this.start.forward().dot(start.velocity);
        return this.pathSegments.get(this.pathSegments.size() - 1).getEndSpeed();
    }

    public float getSpeedBeforeSegment(int segment) {
        if (segment == 0)
            return (float) this.start.forward().dot(start.velocity);
        assert segment < this.pathSegments.size();
        return this.pathSegments.get(segment - 1).getEndSpeed();
    }

    public float getSpeedAfterSegment(int segment) {
        assert segment < this.pathSegments.size();
        return this.pathSegments.get(segment).getEndSpeed();
    }

    public SegmentedPath build() {
        if (this.optimize) {
            final List<PathSegment> optimizedSegments = new ArrayList<>(this.pathSegments.size());
            final Deque<PathSegment> segmentQueue = new ArrayDeque<>(this.pathSegments.size() * 2);
            segmentQueue.addAll(this.pathSegments);

            float firstSpeed = this.getSpeedBeforeSegment(0);
            while (segmentQueue.size() > 0) {
                var startSpeed = firstSpeed;
                if (optimizedSegments.size() > 0)
                    startSpeed = optimizedSegments.get(optimizedSegments.size() - 1).getEndSpeed();

                var segment = segmentQueue.remove();

                if (segment instanceof StraightLineSegment) {
                    var straight = (StraightLineSegment) segment;

                    if (startSpeed > 500 && startSpeed < 2100) {
                        if (FlipSegment.canReplace(straight, startSpeed)) {
                            var flipSegment = new FlipSegment(straight.getStartPos(), straight.getStartTangent(), startSpeed);

                            optimizedSegments.add(flipSegment);
                            segmentQueue.addFirst(new StraightLineSegment(flipSegment.getEndPos(), straight.getEndPos()));
                            continue;
                        }
                    }
                }
                optimizedSegments.add(segment);
            }

            this.pathSegments.clear();
            this.pathSegments.addAll(optimizedSegments);
        }
        return new SegmentedPath(this.pathSegments);
    }

}
