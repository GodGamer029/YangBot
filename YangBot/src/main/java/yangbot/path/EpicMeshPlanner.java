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
    private float endOffset = 0;
    private boolean avoidBall = false;
    private float boostAvailable = 100;
    private float snapToBoostDist = 0;
    private boolean ballAvoidanceNecessary = false;
    private boolean allowDodge = true;
    private boolean allowFullSend = false; // Enable boost, even when arrival speed < 1400
    private float arrivalTime = -1, arrivalSpeed = DriveManeuver.max_throttle_speed;
    private PathCreationStrategy pathCreationStrategy;
    private List<Tuple<Vector3, Vector3>> additionalPoints;

    public EpicMeshPlanner() {
        this.additionalPoints = new ArrayList<>();
        this.pathCreationStrategy = PathCreationStrategy.YANGPATH;
    }

    public EpicMeshPlanner allowDodge(){
        return this.allowDodge(true);
    }

    public EpicMeshPlanner allowDodge(boolean allow) {
        this.allowDodge = allow;
        return this;
    }

    public EpicMeshPlanner allowFullSend(){
        return this.allowFullSend(true);
    }

    public EpicMeshPlanner allowFullSend(boolean allow) {
        this.allowFullSend = allow;
        return this;
    }

    public EpicMeshPlanner snapToBoost(float dist) {
        assert dist >= 0;
        this.snapToBoostDist = dist;
        return this;
    }

    public EpicMeshPlanner snapToBoost() {
        this.snapToBoostDist = 150;
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
        assert offset < 1 && offset >= 0 : offset; // Don't be stupid

        this.startTangent = tangent;
        this.startPos = pos.add(this.startTangent.mul(offset * this.startSpeed));
        return this;
    }

    public EpicMeshPlanner withStart(CarData car, float offset) {
        this.startSpeed = car.forwardSpeed();
        this.boostAvailable = car.boost;
        return this.withStart(car.position, car.getPathStartTangent(), offset);
    }

    public EpicMeshPlanner withStart(CarData car) {
        return this.withStart(car, 0);
    }

    public EpicMeshPlanner withEnd(Vector3 pos, Vector3 tangent) {
        return this.withEnd(pos, tangent, 0);
    }

    public EpicMeshPlanner withEnd(Vector3 pos, Vector3 tangent, float offset) {
        assert offset < 5 && offset >= 0 : offset; // Don't be stupid
        this.endOffset = offset;
        this.endTangent = tangent;
        this.endPos = pos;
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

        if(this.pathCreationStrategy != PathCreationStrategy.YANG_ARC){
            this.endPos = this.endPos.sub(this.endTangent.mul(this.endOffset * this.arrivalSpeed));
        }

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
        if(this.allowFullSend)
            this.boostAvailable = 999;

        Optional<SegmentedPath> currentPath;
        switch (this.pathCreationStrategy) {
            case SIMPLE -> {
                List<Curve.ControlPoint> controlPoints = new ArrayList<>();
                // Construct Path
                {
                    controlPoints.add(new Curve.ControlPoint(this.startPos, this.startTangent));
                    for (var p : this.additionalPoints)
                        controlPoints.add(new Curve.ControlPoint(p.getKey(), p.getValue()));
                    controlPoints.add(new Curve.ControlPoint(this.endPos, this.endTangent));

                    currentPath = Optional.of(SegmentedPath.from(new Curve(controlPoints, 32), MathUtils.clip(this.startSpeed, 0, CarData.MAX_VELOCITY), this.arrivalTime, this.arrivalSpeed, this.boostAvailable));
                }
            }
            case NAVMESH -> {
                assert this.additionalPoints.size() == 0;
                assert !this.allowFullSend;
                var curveOptional = YangBotJNAInterop.findPath(this.startPos, this.startTangent, this.endPos, this.endTangent, 20);
                if (curveOptional.isEmpty() || curveOptional.get().length <= 0)
                    currentPath = Optional.empty();
                else
                    currentPath = Optional.of(SegmentedPath.from(curveOptional.get(), this.startSpeed, this.arrivalTime, this.arrivalSpeed, this.boostAvailable));
            }
            case JAVA_NAVMESH -> {
                assert this.additionalPoints.size() == 0;
                assert !this.allowFullSend;
                var nav = new Navigator(Navigator.PathAlgorithm.BELLMANN_FORD);

                nav.analyzeSurroundings(this.startPos, this.startTangent);

                var curve = nav.pathTo(this.startTangent, this.endPos, this.endTangent, 20);
                if (curve == null || curve.length == 0)
                    currentPath = Optional.empty();
                else
                    currentPath = Optional.of(SegmentedPath.from(curve, this.startSpeed, this.arrivalTime, this.arrivalSpeed, this.boostAvailable));
            }
            case YANG_ARC -> {
                assert this.additionalPoints.size() == 0;
                assert !this.avoidBall;

                var builder = new PathBuilder(new Physics3D(this.startPos, this.startTangent.mul(this.startSpeed), Matrix3x3.lookAt(this.startTangent, new Vector3(0, 0, 1)), new Vector3()), this.boostAvailable);
                builder.withArrivalSpeed(this.arrivalSpeed).withArrivalTime(this.arrivalTime);
                if (this.allowDodge)
                    builder.optimize();

                if (Math.abs(this.startPos.y) > RLConstants.goalDistance - 50 && Math.abs(this.startPos.x) < RLConstants.goalCenterToPost && this.startPos.z < RLConstants.goalHeight) {
                    var getOutOfGoal = new GetOutOfGoalSegment(builder, this.endPos);
                    builder.add(getOutOfGoal);
                }

                if (this.startPos.z > 50) { // Get to ground if needed
                    var getToGround = new GetToGroundSegment(builder);
                    builder.add(getToGround);
                }
                /*var neededTangent = this.endPos.sub(builder.getCurrentPosition()).flatten().normalized();
                float turnAngle = (float) Math.abs(builder.getCurrentTangent().flatten().angleBetween(neededTangent));
                if (builder.getCurrentSpeed() > 300 && builder.getCurrentSpeed() < 1300 && turnAngle < 90 * (Math.PI / 180) && turnAngle > 30 * (Math.PI / 180) && builder.getCurrentPosition().flatten().distance(this.endPos.flatten()) > 1400) {
                    var drift = new DriftSegment(builder.getCurrentPhysics3d(), this.endPos.sub(builder.getCurrentPosition()).normalized(), builder.getCurrentBoost());
                    builder.add(drift);
                    neededTangent = this.endPos.sub(builder.getCurrentPosition()).flatten().normalized();
                    turnAngle = (float) Math.abs(builder.getCurrentTangent().flatten().angleBetween(neededTangent));
                }*/
                var neededTangent = this.endPos.sub(builder.getCurrentPosition()).flatten().normalized();
                var turnAngle = (float) Math.abs(builder.getCurrentTangent().flatten().angleBetween(neededTangent));
                if(builder.getCurrentPosition().distance(this.endPos) > 5 || turnAngle > 0.1f){

                    float wantedSpeed = MathUtils.clip(builder.getCurrentSpeed(), 1100, 2200);
                    float illegalSpeed = wantedSpeed - 1200;
                    if (illegalSpeed < 0)
                        illegalSpeed = 0;

                    // 1 if we can keep all speed, 0 if we should throttle down to 1100
                    float allowance = MathUtils.remapClip(turnAngle, (float) (20 * (Math.PI / 180)), (float) (90 * (Math.PI / 180)), 1f, this.arrivalTime != -1 ? 0.1f : 0.3f);

                    float targetSpeed = 1200 + illegalSpeed * allowance;

                    var arcOpt = ArcLineArc.findOptimalALA(builder.getCurrentPhysics(),
                            builder.getCurrentBoost(), this.endPos.flatten(), this.endTangent.flatten(), 5, 1 / DriveManeuver.maxTurningCurvature(targetSpeed), 30 + this.endOffset * this.arrivalSpeed, 1 / DriveManeuver.maxTurningCurvature(this.arrivalSpeed));
                    if(arcOpt.isEmpty())
                        return Optional.empty();
                    var arc = arcOpt.get();
                    arc.setArrivalSpeed(this.arrivalSpeed);
                    arc.setArrivalTime(this.arrivalTime);
                    builder.add(arc);
                }

                if(builder.getNumSegments() == 0)
                    builder.add(new StraightLineSegment(builder, this.endPos, this.arrivalSpeed, this.arrivalTime, this.allowFullSend || this.arrivalTime > 0));

                currentPath = Optional.of(builder.build());
            }
            case YANGPATH -> {
                assert this.additionalPoints.size() == 0;
                assert !this.avoidBall;

                var builder = new PathBuilder(new Physics3D(this.startPos, this.startTangent.mul(this.startSpeed), Matrix3x3.lookAt(this.startTangent, new Vector3(0, 0, 1)), new Vector3()), this.boostAvailable);
                builder.withArrivalSpeed(this.arrivalSpeed).withArrivalTime(this.arrivalTime);
                if (this.allowDodge)
                    builder.optimize();

                if (Math.abs(this.startPos.y) > RLConstants.goalDistance - 50 && Math.abs(this.startPos.x) < RLConstants.goalCenterToPost && this.startPos.z < RLConstants.goalHeight) {
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
                    var drift = new DriftSegment(builder.getCurrentPhysics3d(), this.endPos.sub(builder.getCurrentPosition()).normalized(), builder.getCurrentBoost());
                    builder.add(drift);
                    neededTangent = this.endPos.sub(builder.getCurrentPosition()).flatten().normalized();
                    turnAngle = (float) Math.abs(builder.getCurrentTangent().flatten().angleBetween(neededTangent));
                }

                boolean turnImpossible = false;
                if (turnAngle > 1 * (Math.PI / 180)) {
                    float wantedSpeed = MathUtils.clip(builder.getCurrentSpeed(), 1100, 2200);
                    float illegalSpeed = wantedSpeed - 1200;
                    if (illegalSpeed < 0)
                        illegalSpeed = 0;

                    // 1 if we can keep all speed, 0 if we should throttle down to 1100
                    float allowance = MathUtils.remapClip(turnAngle, (float) (20.f * (Math.PI / 180)), (float) (90.f * (Math.PI / 180)), 1f, this.arrivalTime != -1 ? 0.1f : 0.3f);

                    float targetSpeed = 1100 + illegalSpeed * allowance;
                    //System.out.println("Allowance: "+allowance+" target: "+targetSpeed);

                    var turn = new TurnCircleSegment(builder.getCurrentPhysics(), 1 / DriveManeuver.maxTurningCurvature(targetSpeed), this.endPos.flatten(), builder.getCurrentBoost(), allowFullSend);
                    if (turn.tangentPoint != null)
                        builder.add(turn);
                    else
                        turnImpossible = true;
                }
                if (turnImpossible && builder.getCurrentTangent().dot(this.endPos.sub(builder.getCurrentPosition())) < 0.7f)
                    return Optional.empty(); // Path doesn't quite work

                builder.add(new StraightLineSegment(builder, this.endPos, this.arrivalSpeed, this.arrivalTime, this.allowFullSend || this.arrivalTime > 0));

                currentPath = Optional.of(builder.build());

            }
            case ATBA_YANGPATH -> {
                assert this.additionalPoints.size() == 0;
                assert !this.avoidBall;

                var builder = new PathBuilder(new Physics3D(this.startPos, this.startTangent.mul(this.startSpeed), Matrix3x3.lookAt(this.startTangent, new Vector3(0, 0, 1)), new Vector3()), this.boostAvailable);
                builder.withArrivalSpeed(this.arrivalSpeed).withArrivalTime(this.arrivalTime);
                if (this.allowDodge)
                    builder.optimize();

                builder.add(new AtbaSegment(builder, this.endPos));

                currentPath = Optional.of(builder.build());
            }
            default -> throw new IllegalStateException("Unknown path creation strategy: " + this.pathCreationStrategy);
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
        YANG_ARC,
        ATBA_YANGPATH
    }
}
