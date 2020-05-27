package yangbot.path.builders;

import yangbot.input.CarData;
import yangbot.input.Physics2D;
import yangbot.input.Physics3D;
import yangbot.path.builders.segments.FlipSegment;
import yangbot.path.builders.segments.StraightLineSegment;
import yangbot.util.math.vector.Matrix2x2;
import yangbot.util.math.vector.Vector3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class PathBuilder {

    private final List<PathSegment> pathSegments;
    private final Physics3D start;
    private boolean optimize = false;

    public PathBuilder(CarData start) {
        this.start = start.toPhysics3d();
        this.pathSegments = new ArrayList<>();
    }

    public PathBuilder(Physics3D start) {
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
            return this.start.forward();
        return this.pathSegments.get(this.pathSegments.size() - 1).getEndTangent();
    }

    public float getCurrentSpeed() {
        if (this.pathSegments.size() == 0)
            return this.start.forwardSpeed();
        return this.pathSegments.get(this.pathSegments.size() - 1).getEndSpeed();
    }

    public Physics2D getCurrentPhysics() {
        return new Physics2D(this.getCurrentPosition().flatten(), this.getCurrentTangent().flatten().mul(this.getCurrentSpeed()), Matrix2x2.fromRotation((float) this.getCurrentTangent().flatten().angle()));
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

                    if (startSpeed > 700 && startSpeed < 2100) {
                        if (FlipSegment.canReplace(straight, startSpeed)) {
                            /*System.out.println("Replaced straight segment at "+optimizedSegments.size()+" speed "+startSpeed);
                            if(optimizedSegments.size() > 0)
                                System.out.println("Segment before was: "+optimizedSegments.get(optimizedSegments.size() - 1).getClass().getSimpleName());*/
                            var flipSegment = new FlipSegment(straight.getStartPos(), straight.getStartTangent(), startSpeed);

                            optimizedSegments.add(flipSegment);
                            segmentQueue.addFirst(new StraightLineSegment(flipSegment.getEndPos(), flipSegment.getEndSpeed(), straight.getEndPos()));
                            continue;
                        }
                    }
                }
                optimizedSegments.add(segment);
            }

            this.pathSegments.clear();
            this.pathSegments.addAll(optimizedSegments);
        }
        if (false) {
            System.out.println("Path: ");
            StringBuilder builder = new StringBuilder();
            builder.append(" -> Start " + this.start.forward() + " (" + this.start.forward().dot(this.start.velocity) + ")");
            for (var path : this.pathSegments)
                builder.append(" -> " + path.getClass().getSimpleName() + " " + path.getEndTangent() + " (" + path.getEndSpeed() + ")");

            System.out.println(builder.toString());
        }

        return new SegmentedPath(this.pathSegments);
    }

}
