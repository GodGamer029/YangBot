package yangbot.path;

import yangbot.cpp.YangBotJNAInterop;
import yangbot.input.CarData;
import yangbot.optimizers.path.AvoidObstacleInPathUtil;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.vector.Vector3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EpicPathPlanner {
    private Vector3 startPos = null, startTangent;
    private Vector3 endPos = null, endTangent;
    private boolean avoidBall = false;
    private boolean ballAvoidanceNecessary = false;
    private CarData carSim = null;
    private float arrivalTime;
    private PathCreationStrategy pathCreationStrategy = PathCreationStrategy.SIMPLE;

    public EpicPathPlanner withStart(Vector3 pos, Vector3 tangent) {
        this.startPos = pos;
        this.startTangent = tangent;
        return this;
    }

    public EpicPathPlanner withEnd(Vector3 pos, Vector3 tangent) {
        this.endPos = pos;
        this.endTangent = tangent;
        return this;
    }

    public EpicPathPlanner withBallAvoidance(boolean enable, CarData car, float arrivalTime, boolean isRequired) {
        this.avoidBall = enable;
        this.carSim = car;
        this.arrivalTime = arrivalTime;
        this.ballAvoidanceNecessary = isRequired;
        return this;
    }

    public EpicPathPlanner withCreationStrategy(PathCreationStrategy creationStrategy) {
        this.pathCreationStrategy = creationStrategy;
        return this;
    }

    public Optional<Curve> plan() {
        assert this.startPos != null && this.startTangent != null;
        assert this.endPos != null && this.endTangent != null;

        Optional<Curve> currentPath;
        switch (this.pathCreationStrategy) {
            case SIMPLE: {
                List<Curve.ControlPoint> controlPoints = new ArrayList<>();
                // Construct Path
                {
                    controlPoints.add(new Curve.ControlPoint(this.startPos, this.startTangent));
                    controlPoints.add(new Curve.ControlPoint(this.endPos, this.endTangent));

                    currentPath = Optional.of(new Curve(controlPoints));
                }
                break;
            }
            case NAVMESH: {
                currentPath = YangBotJNAInterop.findPath(this.startPos, this.startTangent, this.endPos, this.endTangent, 20);
                if (currentPath.isPresent() && currentPath.get().length <= 0)
                    currentPath = Optional.empty();
                break;
            }
            default:
                throw new IllegalStateException("Unknown path creation strategy: " + this.pathCreationStrategy);
        }


        if (this.avoidBall) {
            assert this.pathCreationStrategy == PathCreationStrategy.SIMPLE : "Ball avoidance only works in SIMPLE path creation.";
            assert this.carSim != null;
            assert currentPath.isPresent();
            Optional<Curve> avoidancePath = AvoidObstacleInPathUtil.mutatePath(currentPath.get(), this.carSim, this.arrivalTime, YangBallPrediction.get(), 7);
            if (avoidancePath.isPresent())
                currentPath = avoidancePath; // Replace if it worked
            else if (this.ballAvoidanceNecessary) // Return nothing if it didn't work, but was required
                currentPath = Optional.empty();
        }
        return currentPath;
    }

    public enum PathCreationStrategy {
        SIMPLE,
        NAVMESH
    }
}
