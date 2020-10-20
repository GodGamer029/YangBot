package yangbot.path;

import yangbot.cpp.YangBotJNAInterop;
import yangbot.input.CarData;
import yangbot.input.Physics3D;
import yangbot.input.RLConstants;
import yangbot.input.fieldinfo.BoostManager;
import yangbot.path.builders.PathBuilder;
import yangbot.path.builders.SegmentedPath;
import yangbot.path.builders.segments.*;
import yangbot.path.navmesh.Navigator;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.util.Tuple;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class EpicMeshPlanner {
    private Vector3 startPos = null, startTangent;
    private float startSpeed = DriveManeuver.max_throttle_speed;
    private Vector3 endPos = null, endTangent;
    private boolean avoidBall = false;
    private float boostAvailable = 100;
    private float snapToBoostDist = 0;
    private boolean ballAvoidanceNecessary = false;
    private boolean allowOptimize = true;
    private boolean allowFullSend = false; // Enable boost, even when arrival speed < 1400
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

    public EpicMeshPlanner snapToBoost(float dist) {
        assert dist >= 0;
        this.snapToBoostDist = dist;
        return this;
    }

    public EpicMeshPlanner snapToBoost() {
        this.snapToBoostDist = 50;
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
        this.boostAvailable = (float) car.boost;
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

        if (this.snapToBoostDist > 0 && this.endPos.z < 100 && !RLConstants.isPosNearWall(this.endPos.flatten(), 30)) {
            // Snap to nearest boost pad at destination
            var closestBoost = BoostManager.getAllBoosts().stream()
                    .filter(p -> p.boostAvailableIn() < 0.5f)
                    .map(p -> new Tuple<>(p, (float) p.getLocation().flatten().distance(this.endPos.flatten())))
                    .filter(p -> p.getValue() < this.snapToBoostDist)
                    .min(Comparator.comparingDouble(Tuple::getValue))
                    .map(Tuple::getKey);
            closestBoost.ifPresent(boostPad -> this.endPos = boostPad.getLocation().withZ(RLConstants.carElevation));
        }

        Optional<SegmentedPath> currentPath;
        switch (this.pathCreationStrategy) {
            case SIMPLE: {
                assert !this.allowFullSend;
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
                assert !this.allowFullSend;
                var curveOptional = YangBotJNAInterop.findPath(this.startPos, this.startTangent, this.endPos, this.endTangent, 20);
                if (curveOptional.isEmpty() || curveOptional.get().length <= 0)
                    currentPath = Optional.empty();
                else
                    currentPath = Optional.of(SegmentedPath.from(curveOptional.get(), this.startSpeed, this.arrivalTime, this.arrivalSpeed));
                break;
            }
            case JAVA_NAVMESH: {
                assert this.additionalPoints.size() == 0;
                assert !this.allowFullSend;
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

                var builder = new PathBuilder(new Physics3D(this.startPos, this.startTangent.mul(this.startSpeed), Matrix3x3.lookAt(this.startTangent, new Vector3(0, 0, 1))), this.boostAvailable);
                if (this.allowOptimize)
                    builder.optimize();

                if (Math.abs(this.startPos.y) > RLConstants.goalDistance - 50 && Math.abs(this.startPos.x) < RLConstants.goalCenterToPost) {
                    var getOutOfGoal = new GetOutOfGoalSegment(builder, this.endPos);
                    builder.add(getOutOfGoal);
                }

                if (this.startPos.z > 50) { // Get to ground if needed
                    var getToGround = new GetToGroundSegment(builder);
                    builder.add(getToGround);
                }
                var neededTangent = this.endPos.sub(builder.getCurrentPosition()).flatten().normalized();
                float turnAngle = (float) Math.abs(builder.getCurrentTangent().flatten().angleBetween(neededTangent));
                if (builder.getCurrentSpeed() > 300 && builder.getCurrentSpeed() < 1300 && turnAngle < 90 * (Math.PI / 180) && turnAngle > 30 * (Math.PI / 180) && builder.getCurrentPosition().flatten().distance(this.endPos.flatten()) > 1400) {
                    var drift = new DriftSegment(builder, this.endPos.sub(builder.getCurrentPosition()).normalized());
                    builder.add(drift);
                    neededTangent = this.endPos.sub(builder.getCurrentPosition()).flatten().normalized();
                    turnAngle = (float) Math.abs(builder.getCurrentTangent().flatten().angleBetween(neededTangent));
                }

                boolean turnImpossible = false;
                if (turnAngle > 1 * (Math.PI / 180)) {
                    float wantedSpeed = MathUtils.clip(builder.getCurrentSpeed(), 1400, 2200);
                    float illegalSpeed = wantedSpeed - 1100;
                    if (illegalSpeed < 0)
                        illegalSpeed = 0;

                    // 1 if we can keep all speed, 0 if we should throttle down to 1100
                    float allowance = MathUtils.remapClip(turnAngle, 0, (float) (70.f * (Math.PI / 180)), 1f, this.arrivalTime != -1 ? 0 : 0.3f);

                    float targetSpeed = 1100 + illegalSpeed * allowance;
                    //System.out.println("Allowance: "+allowance+" target: "+targetSpeed);

                    var turn = new TurnCircleSegment(builder.getCurrentPhysics(), 1 / DriveManeuver.maxTurningCurvature(MathUtils.clip(targetSpeed, 1100, 2000)), this.endPos.flatten(), builder.getCurrentBoost());
                    if (turn.tangentPoint != null)
                        builder.add(turn);
                    else
                        turnImpossible = true;
                }
                if (turnImpossible && builder.getCurrentTangent().dot(this.endPos.sub(builder.getCurrentPosition())) < 0.7f)
                    return Optional.empty(); // Path doesn't quite work

                builder.add(new StraightLineSegment(builder, this.endPos, this.arrivalSpeed, this.arrivalTime, this.allowFullSend));
                currentPath = Optional.of(builder.build());

                break;
            }
            case ATBA_YANGPATH: {
                assert this.additionalPoints.size() == 0;
                assert !this.avoidBall;

                var builder = new PathBuilder(new Physics3D(this.startPos, this.startTangent.mul(this.startSpeed), Matrix3x3.lookAt(this.startTangent, new Vector3(0, 0, 1))), this.boostAvailable);
                if (this.allowOptimize)
                    builder.optimize();

                builder.add(new AtbaSegment(builder, this.endPos));

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
        YANGPATH,
        ATBA_YANGPATH
    }
}
