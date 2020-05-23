package yangbot.path;

import yangbot.cpp.YangBotJNAInterop;
import yangbot.input.CarData;
import yangbot.input.Physics3D;
import yangbot.path.builders.PathBuilder;
import yangbot.path.builders.SegmentedPath;
import yangbot.path.builders.segments.DriftSegment;
import yangbot.path.builders.segments.GetToGroundSegment;
import yangbot.path.builders.segments.StraightLineSegment;
import yangbot.path.builders.segments.TurnCircleSegment;
import yangbot.path.navmesh.Navigator;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.util.Tuple;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EpicMeshPlanner {
    private Vector3 startPos = null, startTangent;
    private float startSpeed = DriveManeuver.max_throttle_speed * 0.9f;
    private Vector3 endPos = null, endTangent;
    private boolean avoidBall = false;
    private boolean ballAvoidanceNecessary = false;
    private CarData carSim = null;
    private float arrivalTime = -1, arrivalSpeed = -1;
    private PathCreationStrategy pathCreationStrategy;
    private List<Tuple<Vector3, Vector3>> additionalPoints;

    public EpicMeshPlanner() {
        this.additionalPoints = new ArrayList<>();
        this.pathCreationStrategy = PathCreationStrategy.SIMPLE;
    }

    public EpicMeshPlanner withArrivalTime(float arrivalTime) {
        this.arrivalTime = arrivalTime;
        if (arrivalTime < 0)
            this.arrivalTime = -1;

        return this;
    }

    public EpicMeshPlanner withStart(Vector3 pos, Vector3 tangent) {
        return this.withStart(pos, tangent, 0);
    }

    public EpicMeshPlanner withStart(Vector3 pos, Vector3 tangent, float offset) {
        assert offset < 50 && offset >= 0 : offset; // Don't be stupid

        this.startTangent = tangent;
        this.startPos = pos.add(this.startTangent.mul(offset));
        return this;
    }

    public EpicMeshPlanner withStart(CarData car, float offset) {
        this.startSpeed = car.forwardSpeed();
        return this.withStart(car.position, car.getPathStartTangent(), offset);
    }

    public EpicMeshPlanner withStart(CarData car) {
        return this.withStart(car, 0);
    }

    public EpicMeshPlanner withEnd(Vector3 pos, Vector3 tangent) {
        return this.withEnd(pos, tangent, 0);
    }

    public EpicMeshPlanner withEnd(Vector3 pos, Vector3 tangent, float offset) {
        assert offset < 50 && offset >= 0 : offset; // Don't be stupid

        this.endTangent = tangent;
        this.endPos = pos.sub(this.endTangent.mul(offset));
        return this;
    }

    public EpicMeshPlanner addPoint(Vector3 pos, Vector3 tangent) {
        this.additionalPoints.add(new Tuple<>(pos, tangent));
        return this;
    }

    public EpicMeshPlanner withArrivalSpeed(float speed) {
        assert speed > 0 && speed <= CarData.MAX_VELOCITY;
        this.arrivalSpeed = speed;
        return this;
    }

    public EpicMeshPlanner withBallAvoidance(boolean enable, CarData car, float arrivalTime, boolean isRequired) {
        this.avoidBall = enable;
        this.carSim = car;
        this.arrivalTime = arrivalTime;
        this.ballAvoidanceNecessary = isRequired;
        return this;
    }

    public boolean isUsingBallAvoidance() {
        return this.avoidBall;
    }

    public EpicMeshPlanner withCreationStrategy(PathCreationStrategy creationStrategy) {
        this.pathCreationStrategy = creationStrategy;
        return this;
    }

    public Optional<SegmentedPath> plan() {
        assert this.startPos != null && this.startTangent != null;
        assert this.endPos != null && this.endTangent != null;

        Optional<SegmentedPath> currentPath;
        switch (this.pathCreationStrategy) {
            case SIMPLE: {
                List<Curve.ControlPoint> controlPoints = new ArrayList<>();
                // Construct Path
                {
                    controlPoints.add(new Curve.ControlPoint(this.startPos, this.startTangent));
                    for (var p : this.additionalPoints)
                        controlPoints.add(new Curve.ControlPoint(p.getKey(), p.getValue()));
                    controlPoints.add(new Curve.ControlPoint(this.endPos, this.endTangent));

                    currentPath = Optional.of(SegmentedPath.from(new Curve(controlPoints, 32), this.startSpeed, this.arrivalTime, this.arrivalSpeed));
                }
                break;
            }
            case NAVMESH: {
                assert this.additionalPoints.size() == 0;
                var curveOptional = YangBotJNAInterop.findPath(this.startPos, this.startTangent, this.endPos, this.endTangent, 20);
                if (curveOptional.isEmpty() || curveOptional.get().length <= 0)
                    currentPath = Optional.empty();
                else
                    currentPath = Optional.of(SegmentedPath.from(curveOptional.get(), this.startSpeed, this.arrivalTime, this.arrivalSpeed));
                break;
            }
            case JAVA_NAVMESH: {
                assert this.additionalPoints.size() == 0;
                var nav = new Navigator(Navigator.PathAlgorithm.BELLMANN_FORD);
                nav.analyzeSurroundings(this.startPos, this.startTangent);
                var curve = nav.pathTo(this.startTangent, this.endPos, this.endTangent, 20);
                if (curve == null || curve.length == 0)
                    currentPath = Optional.empty();
                else
                    currentPath = Optional.of(SegmentedPath.from(curve, this.startSpeed, this.arrivalTime, this.arrivalSpeed));
                break;
            }
            case YANGPATH: {
                assert this.additionalPoints.size() == 0;
                assert !this.avoidBall;

                var builder = new PathBuilder(new Physics3D(this.startPos, this.startTangent.mul(this.startSpeed), Matrix3x3.lookAt(this.startTangent, new Vector3(0, 0, 1))))
                        .optimize();

                if (this.startPos.z > 50) { // Get to ground if needed
                    var getToGround = new GetToGroundSegment(builder.getCurrentPosition(), builder.getCurrentTangent(), builder.getCurrentSpeed());
                    builder.add(getToGround);
                }
                var neededTangent = this.endPos.sub(builder.getCurrentPosition()).flatten().normalized();
                if (builder.getCurrentSpeed() > 300 && Math.abs(builder.getCurrentTangent().flatten().angleBetween(neededTangent)) > 30 * (Math.PI / 180)) {
                    var drift = new DriftSegment(builder.getCurrentPosition(), builder.getCurrentTangent(), this.endPos.sub(builder.getCurrentPosition()).normalized(), builder.getCurrentSpeed());
                    builder.add(drift);
                    neededTangent = this.endPos.sub(builder.getCurrentPosition()).flatten().normalized();
                }

                if (Math.abs(builder.getCurrentTangent().flatten().angleBetween(neededTangent)) > 1 * (Math.PI / 180)) {
                    var turn = new TurnCircleSegment(builder.getCurrentPhysics(), 1 / DriveManeuver.maxTurningCurvature(Math.max(1100, builder.getCurrentSpeed())), this.endPos.flatten());
                    if (turn.tangentPoint != null)
                        builder.add(turn);
                }

                builder.add(new StraightLineSegment(builder.getCurrentPosition(), builder.getCurrentSpeed(), this.endPos, this.arrivalSpeed));

                currentPath = Optional.of(builder.build());
                break;
            }
            default:
                throw new IllegalStateException("Unknown path creation strategy: " + this.pathCreationStrategy);
        }

        /*if (this.avoidBall) {
            assert this.pathCreationStrategy == PathCreationStrategy.SIMPLE : "Ball avoidance only works in SIMPLE path creation.";
            assert this.carSim != null;
            assert currentPath.isPresent();
            Optional<Curve> avoidancePath = AvoidObstacleInPathUtil.mutatePath(currentPath.get(), this.carSim, this.arrivalTime, YangBallPrediction.get(), 7);
            if (avoidancePath.isPresent())
                currentPath = avoidancePath; // Replace if it worked
            else if (this.ballAvoidanceNecessary) // Return nothing if it didn't work, but was required
                currentPath = Optional.empty();
        }*/
        return currentPath;
    }

    public enum PathCreationStrategy {
        SIMPLE,
        NAVMESH,
        JAVA_NAVMESH,
        YANGPATH
    }
}
