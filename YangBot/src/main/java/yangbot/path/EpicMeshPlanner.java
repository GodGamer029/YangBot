package yangbot.path;

import yangbot.cpp.YangBotJNAInterop;
import yangbot.input.CarData;
import yangbot.input.Physics3D;
import yangbot.input.RLConstants;
import yangbot.path.builders.PathBuilder;
import yangbot.path.builders.SegmentedPath;
import yangbot.path.builders.segments.GetOutOfGoalSegment;
import yangbot.path.builders.segments.GetToGroundSegment;
import yangbot.path.builders.segments.StraightLineSegment;
import yangbot.path.builders.segments.TurnCircleSegment;
import yangbot.path.navmesh.Navigator;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.util.Tuple;
import yangbot.util.math.MathUtils;
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
    private boolean allowOptimize = true;
    private boolean allowFullSend = true; // Enable boost, even when arrival speed < 1400
    private float arrivalTime = -1, arrivalSpeed = DriveManeuver.max_throttle_speed;
    private PathCreationStrategy pathCreationStrategy;
    private List<Tuple<Vector3, Vector3>> additionalPoints;

    public EpicMeshPlanner() {
        this.additionalPoints = new ArrayList<>();
        this.pathCreationStrategy = PathCreationStrategy.SIMPLE;
    }

    public EpicMeshPlanner allowOptimize(boolean allow) {
        this.allowOptimize = allow;
        return this;
    }

    public EpicMeshPlanner allowFullSend(boolean allow) {
        this.allowFullSend = allow;
        // the fact that you're reading this means that my bot is performing well :O
        // how neat!
        return this;
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
        this.startSpeed = MathUtils.clip(car.forwardSpeed(), 0, CarData.MAX_VELOCITY);
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

                var builder = new PathBuilder(new Physics3D(this.startPos, this.startTangent.mul(this.startSpeed), Matrix3x3.lookAt(this.startTangent, new Vector3(0, 0, 1))));
                if (this.allowOptimize)
                    builder.optimize();

                if (Math.abs(this.startPos.y) > RLConstants.goalDistance - 50 && Math.abs(this.startPos.x) < RLConstants.goalCenterToPost) {
                    var getOutOfGoal = new GetOutOfGoalSegment(builder.getCurrentPosition(), builder.getCurrentTangent(), builder.getCurrentSpeed(), this.endPos);
                    builder.add(getOutOfGoal);
                }

                if (this.startPos.z > 50) { // Get to ground if needed
                    var getToGround = new GetToGroundSegment(builder.getCurrentPosition(), builder.getCurrentSpeed());
                    builder.add(getToGround);
                }
                var neededTangent = this.endPos.sub(builder.getCurrentPosition()).flatten().normalized();
                /*if (builder.getCurrentSpeed() > 300 && (builder.getCurrentPosition().flatten().distance(this.endPos.flatten()) > 700 || builder.getCurrentSpeed() > 1700) && Math.abs(builder.getCurrentTangent().flatten().angleBetween(neededTangent)) > 30 * (Math.PI / 180)) {
                    var drift = new DriftSegment(builder.getCurrentPosition(), builder.getCurrentTangent(), this.endPos.sub(builder.getCurrentPosition()).normalized(), builder.getCurrentSpeed());
                    builder.add(drift);
                    neededTangent = this.endPos.sub(builder.getCurrentPosition()).flatten().normalized();
                }*/

                float turnAngle = (float) Math.abs(builder.getCurrentTangent().flatten().angleBetween(neededTangent));
                if (turnAngle > 1 * (Math.PI / 180)) {
                    float wantedSpeed = MathUtils.clip(builder.getCurrentSpeed(), 1400, 2200);
                    float illegalSpeed = wantedSpeed - 1100;
                    if (illegalSpeed < 0)
                        illegalSpeed = 0;

                    // 1 if we can keep all speed, 0 if we should throttle down to 1100
                    float allowance = MathUtils.remapClip(turnAngle, 0, (float) (70.f * (Math.PI / 180)), 1f, 0);

                    float targetSpeed = 1100 + illegalSpeed * allowance;
                    //System.out.println("Allowance: "+allowance+" target: "+targetSpeed);

                    var turn = new TurnCircleSegment(builder.getCurrentPhysics(), 1 / DriveManeuver.maxTurningCurvature(MathUtils.clip(targetSpeed, 1100, 2000)), this.endPos.flatten());
                    if (turn.tangentPoint != null)
                        builder.add(turn);
                }
                builder.add(new StraightLineSegment(builder.getCurrentPosition(), builder.getCurrentSpeed(), this.endPos, this.arrivalSpeed, this.arrivalTime));

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
