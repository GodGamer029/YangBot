package yangbot.path.builders;

import org.jetbrains.annotations.NotNull;
import yangbot.input.CarData;
import yangbot.input.Physics2D;
import yangbot.input.Physics3D;
import yangbot.path.builders.segments.CompositeBakeSegment;
import yangbot.path.builders.segments.FlipSegment;
import yangbot.path.builders.segments.StraightLineSegment;
import yangbot.util.math.vector.Matrix2x2;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class PathBuilder {

    private final List<PathSegment> pathSegments;
    private final Physics3D start;
    private final float startBoost;
    private boolean allowDodge = false;
    private float arrivalTime = -1, arrivalSpeed = -1;

    protected PathBuilder() {
        // not recommended
        this.pathSegments = new ArrayList<>();
        this.start = null;
        this.startBoost = 0;
        assert false;
    }

    protected PathBuilder(CarData start) {
        this.start = start.toPhysics3d();
        this.startBoost = (float) start.boost;
        this.pathSegments = new ArrayList<>();
    }

    public PathBuilder(Physics3D start, float startBoost) {
        this.startBoost = startBoost;
        this.start = start;
        this.pathSegments = new ArrayList<>();
    }

    public PathBuilder withArrivalTime(float arrivalTime) {
        this.arrivalTime = arrivalTime;
        return this;
    }

    public PathBuilder withArrivalSpeed(float arrivalSpeed) {
        this.arrivalSpeed = arrivalSpeed;
        return this;
    }

    public PathBuilder add(PathSegment pathSegment) {
        this.pathSegments.add(pathSegment);
        return this;
    }

    public PathBuilder optimize() {
        this.allowDodge = true;
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

    public float getCurrentBoost() {
        if (this.pathSegments.size() == 0)
            return this.startBoost;
        return this.pathSegments.get(this.pathSegments.size() - 1).getEndBoost();
    }

    public Physics2D getCurrentPhysics() {
        return new Physics2D(this.getCurrentPosition().flatten(), this.getCurrentTangent().flatten().mul(this.getCurrentSpeed()), Matrix2x2.fromRotation((float) this.getCurrentTangent().flatten().angle()), 0);
    }

    public Physics3D getCurrentPhysics3d() {
        return new Physics3D(this.getCurrentPosition(), this.getCurrentTangent().mul(this.getCurrentSpeed()), Matrix3x3.lookAt(this.getCurrentTangent()), new Vector3());
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

    public int getNumSegments(){
        return this.pathSegments.size();
    }

    @NotNull
    public SegmentedPath build() {
        if (this.allowDodge) {
            final List<PathSegment> optimizedSegments = new ArrayList<>(this.pathSegments.size());
            final Deque<PathSegment> segmentQueue = new ArrayDeque<>(this.pathSegments.size() * 2);
            segmentQueue.addAll(this.pathSegments);

            float firstSpeed = this.getSpeedBeforeSegment(0);
            float firstBoost = this.startBoost;
            while (segmentQueue.size() > 0) {
                var startSpeed = firstSpeed;
                if (optimizedSegments.size() > 0)
                    startSpeed = optimizedSegments.get(optimizedSegments.size() - 1).getEndSpeed();
                var startBoost = firstBoost;
                if (optimizedSegments.size() > 0)
                    startBoost = optimizedSegments.get(optimizedSegments.size() - 1).getEndBoost();

                var segment = segmentQueue.remove();

                if (segment instanceof StraightLineSegment straight) {

                    if (startSpeed > 400 && startSpeed < 2100) {
                        if (FlipSegment.canReplace(straight, startSpeed)) {
                            /*System.out.println("Replaced straight segment at "+optimizedSegments.size()+" speed "+startSpeed);
                            if(optimizedSegments.size() > 0)
                                System.out.println("Segment before was: "+optimizedSegments.get(optimizedSegments.size() - 1).getClass().getSimpleName());*/
                            var flipSegment = new FlipSegment(straight.getStartPos(), straight.getStartTangent(), startSpeed, startBoost);
                            var newStraight = new StraightLineSegment(flipSegment.getEndPos(), flipSegment.getEndSpeed(), straight.getEndPos(), straight.arrivalSpeed, straight.arrivalTime, straight.allowBoost, flipSegment.getEndBoost());
                            if(flipSegment.getTimeEstimate() + newStraight.getTimeEstimate() < straight.getTimeEstimate()){
                                optimizedSegments.add(flipSegment);
                                segmentQueue.addFirst(newStraight);
                                continue;
                            }
                        }
                    }
                }
                optimizedSegments.add(segment);
            }

            this.pathSegments.clear();
            this.pathSegments.addAll(optimizedSegments);
        }
        // stitch together curve segments
        {
            final List<PathSegment> optimizedSegments = new ArrayList<>(this.pathSegments.size());
            List<BakeablePathSegment> bakeSegments = new ArrayList<>(3);
            final Deque<PathSegment> segmentQueue = new ArrayDeque<>(this.pathSegments.size() * 2);
            segmentQueue.addAll(this.pathSegments);
            while (segmentQueue.size() > 0) {
                var segment = segmentQueue.remove();

                if(!(segment instanceof BakeablePathSegment)){
                    if(bakeSegments.size() > 1)
                        optimizedSegments.add(new CompositeBakeSegment(bakeSegments, this.arrivalSpeed, this.arrivalTime));
                    else
                        optimizedSegments.addAll(bakeSegments);
                    bakeSegments = new ArrayList<>();
                    optimizedSegments.add(segment);
                    continue;
                }

                bakeSegments.add((BakeablePathSegment) segment);
            }
            if(bakeSegments.size() > 1){
                optimizedSegments.add(new CompositeBakeSegment(bakeSegments, this.arrivalSpeed, this.arrivalTime));
            }else
                optimizedSegments.addAll(bakeSegments);
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
