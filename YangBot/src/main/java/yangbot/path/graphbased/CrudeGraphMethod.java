package yangbot.path.graphbased;

import org.jetbrains.annotations.NotNull;
import yangbot.input.CarData;
import yangbot.input.Physics3D;
import yangbot.input.RLConstants;
import yangbot.path.builders.PathSegment;
import yangbot.path.builders.segments.DriftSegment;
import yangbot.path.builders.segments.StraightLineSegment;
import yangbot.path.builders.segments.TurnCircleSegment;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.util.Range;
import yangbot.util.Tuple;
import yangbot.util.math.Car2D;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector3;

import java.util.*;

public class CrudeGraphMethod {

    public List<PathSegment> wipPath = new ArrayList<>();
    int iter = 0;
    private Vector3 endPos, endTangent;
    private List<PathNode> allNodes;

    public CrudeGraphMethod(Vector3 end, Vector3 tang) {
        this.endPos = end;
        this.endTangent = tang;
    }

    private float estimateCost(Physics3D state, float boost) {
        float t = (float) state.position.distance(endPos) / CarData.MAX_VELOCITY;
        //float t = 0.97f * Car1D.simulateDriveDistanceForwardAccel((float) state.position.distance(endPos), (float) state.velocity.magnitude(), Math.min(100, boost * 1.5f + 10)).time;

        return t;
    }

    private PathNode getOrMakeNode(Physics3D state, float boost) {
        //System.out.println("new node at "+state.position);
        // Just make a new one, overlaps are very unlikely
        var next = new PathNode(
                allNodes.size(),
                boost,
                state,
                state.forward().dot(endTangent) > 0.97f && state.position.distance(endPos) < 10,
                -2,
                null,
                Float.MAX_VALUE,
                Float.MAX_VALUE,
                -1
        );

        allNodes.add(next);
        return next;
    }

    public Optional<List<PathSegment>> findPath(CarData state) {

        var openSet = new PriorityQueue<PathNode>(10000);
        allNodes = new ArrayList<>(10000);

        var start = new PathNode(
                0,
                state.boost,
                state.toPhysics3d(),
                false,
                -1,
                null,
                0,
                estimateCost(state.toPhysics3d(), state.boost),
                0
        );
        openSet.add(start);
        allNodes.add(start);
        long startMs = System.currentTimeMillis();

        HashMap<String, Tuple<Long, Integer>> times = new HashMap<>();

        long ns = 0;
        int ttt = 0;
        while (!openSet.isEmpty()) {

            var prepNode = openSet.poll();
            if (prepNode.isGoalNode) {
                List<PathSegment> segmentList = new ArrayList<>();
                var cur = prepNode;
                do {
                    if (cur.prevNode < 0)
                        break; // this is the first node!
                    assert cur.connectingEdge != null;
                    segmentList.add(0, cur.connectingEdge);
                    cur = allNodes.get(cur.prevNode);
                } while (cur != null);
                System.out.printf(Locale.US, "Done! NodesVisited: " + iter + " allNodes: " + allNodes.size() + " gScore: " + prepNode.routeScore + " heuristic:" + prepNode.heuristicScore + " msPerNode: %.5f avgEdge: %.5f\n", ((System.currentTimeMillis() - startMs) / (float) iter), (ns / (float) ttt * 0.000001f));
                times.forEach((seg, lol) -> {
                    System.out.printf(Locale.US, "Seg: %s t=%.6fms\n", seg, (lol.getKey() / lol.getValue() * 0.000001f));
                });
                return Optional.of(segmentList);
            }
            /*if(iter % 5000 == 0){
                // find best one so far, reconstruct path
                //var smallest = allNodes.stream().min(Comparator.comparingDouble(node -> node.heuristicScore - node.routeScore)).get();
                var smallest = prepNode;
                List<PathSegment> segmentList = new ArrayList<>();
                var cur = smallest;
                do {
                    if(cur.prevNode < 0)
                        break; // this is the first node!
                    assert cur.connectingEdge != null;
                    segmentList.add(0, cur.connectingEdge);
                    cur = allNodes.get(cur.prevNode);
                } while(cur != null);
                this.wipPath = segmentList;
            }*/

            if (prepNode.depth >= 6)
                continue; // too deep! .-.
            iter++;
            // Find edges from the current node
            long startNs = System.nanoTime();
            var edges = getEdges(prepNode.state, prepNode.boostAvailable, prepNode.connectingEdge == null ? PathSegment.class : prepNode.connectingEdge.getClass());
            ns += System.nanoTime() - startNs;

            /*edges.forEach(edge -> {
                //long startns = System.nanoTime();
                edge.getTimeEstimate();
                /*startns = System.nanoTime() - startns;
                var old = times.getOrDefault(edge.getClass().getSimpleName(), new Tuple<>(0L, 0));
                times.put(edge.getClass().getSimpleName(), new Tuple<>(old.getKey() + startns, old.getValue() + 1));*
            });*/

            ttt += edges.size();
            edges.forEach(edge -> {

                float edgeTime = edge.getTimeEstimate();
                Physics3D endState = new Physics3D(edge.getEndPos(), edge.getEndTangent().mul(edge.getEndSpeed()), Matrix3x3.lookAt(edge.getEndTangent()), Vector3.ZERO);
                var next = getOrMakeNode(endState, edge.getEndBoost());
                float newScore = prepNode.routeScore + edgeTime;
                if (newScore >= next.routeScore)
                    return;

                next.updatePrevNode(prepNode, edge);
                next.routeScore = newScore;
                next.heuristicScore = newScore + estimateCost(endState, edge.getEndBoost());
                //System.out.println("Added node to open set edgeType="+edge.getClass().getSimpleName());
                openSet.add(next);
            });

            //System.out.println("++");
        }

        System.out.println("Iterations: " + iter);

        return Optional.empty();
    }

    private List<PathSegment> getEdges(Physics3D state, final float boostAvailable, Class<? extends PathSegment> prevSegment) {
        List<PathSegment> segs = new LinkedList<>();
        var pos = state.position;
        if (Math.abs(pos.x) > RLConstants.arenaHalfWidth ||
                Math.abs(pos.y) > RLConstants.goalDistance ||
                RLConstants.isPosNearWall(state.position.flatten(), 0))
            return segs; // Outside of arena

        float vF = state.forwardSpeed();
        var neededTangent = this.endPos.sub(state.position).flatten().normalized();
        float turnAngle = (float) Math.abs(state.forward().flatten().angleBetween(neededTangent));

        // Drift!
        if (vF > 300 && turnAngle > 30 * (Math.PI / 180) && state.position.flatten().distance(this.endPos.flatten()) > 1400) {
            var drift = new DriftSegment(state, this.endPos.sub(state.position).normalized(), boostAvailable);
            segs.add(drift);
        }

        boolean computeShotIntoNowhere = false;
        if (turnAngle > 1 * (Math.PI / 180)) {
            computeShotIntoNowhere = true;

            // Don't do another TurnCircle right after the last one, unnecessary
            if (prevSegment != TurnCircleSegment.class) {
                Range.of(800, 2300).stepBy(150).forEachRemaining(speed -> {
                    var turn = new TurnCircleSegment(state.flatten(), 1 / DriveManeuver.maxTurningCurvature(speed), this.endPos.flatten(), boostAvailable, true);
                    if (turn.tangentPoint != null)
                        segs.add(turn);
                });
                Range.of(-1, 1).stepWith(2).forEachRemaining(direction -> {
                    Range.of(900, 1100).stepWith(2).forEachRemaining(speed -> {
                        var turn = new TurnCircleSegment(state.flatten(), 1 / DriveManeuver.maxTurningCurvature(speed),
                                direction * 30f * ((float) Math.PI / 180f), boostAvailable, true);
                        if (turn.tangentPoint != null)
                            segs.add(turn);
                    });
                });
            }

        } else {
            if (state.forward().dot(endTangent) > 0.97f) {
                var straight = new StraightLineSegment(state, boostAvailable, endPos, CarData.MAX_VELOCITY, -1, true);
                segs.add(straight);
            } else {
                computeShotIntoNowhere = true;
            }
        }

        if (computeShotIntoNowhere) {
            Car2D simCar = new Car2D(state.flatten(), 0, boostAvailable);
            for (int i = 0; i < 5; i++) {
                simCar.simulateDriveTimeForward(0.2f, true);
                if (simCar.position.distance(state.position.flatten()) > 100)
                    break;
            }

            var arrivePos = simCar.position;

            var straight = new StraightLineSegment(state, boostAvailable, arrivePos.withZ(state.position.z), CarData.MAX_VELOCITY, -1, true);
            segs.add(straight); // straight into nowhere!

            simCar.simulateDriveTimeForward(1, true);
            arrivePos = simCar.position;
            straight = new StraightLineSegment(state, boostAvailable, arrivePos.withZ(state.position.z), CarData.MAX_VELOCITY, -1, true);
            segs.add(straight); // straight into nowhere!
        }

        return segs;
    }

    private static class PathNode implements Comparable<PathNode> {
        public final Physics3D state;
        public final float boostAvailable;
        public final int myId;
        public final boolean isGoalNode;
        public int prevNode;
        public PathSegment connectingEdge;
        public float routeScore, heuristicScore;
        public int depth;

        public PathNode(int myId, float boostAvailable, Physics3D state, boolean isGoalNode, int prevNode, PathSegment connectingEdge, float routeScore, float heuristicScore, int depth) {
            this.myId = myId;
            this.state = state;
            this.boostAvailable = boostAvailable;
            this.isGoalNode = isGoalNode;
            this.prevNode = prevNode;
            this.connectingEdge = connectingEdge;
            this.routeScore = routeScore;
            this.heuristicScore = heuristicScore;
            this.depth = depth;
        }

        public void updatePrevNode(PathNode pre, PathSegment connect) {
            this.prevNode = pre.myId;
            this.connectingEdge = connect;
            this.depth = pre.depth + 1;
        }

        @Override
        public int compareTo(@NotNull CrudeGraphMethod.PathNode other) {
            if (this.heuristicScore > other.heuristicScore) {
                return 1;
            } else if (this.heuristicScore < other.heuristicScore) {
                return -1;
            } else {
                return 0;
            }
        }
    }

}
