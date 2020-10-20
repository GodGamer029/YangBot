package yangbot.path.navmesh;

import yangbot.util.IntArrayList;
import yangbot.util.Tuple;
import yangbot.util.math.vector.Vector3;

import java.util.Arrays;
import java.util.PriorityQueue;

public class Graph {

    private int numEdges;
    private int numVertices;

    public int[] offsets;
    public int[] destinations;
    public float[] weights;
    public float[] lastBestWeights;

    public Graph(Edge[] edges) {
        try {
            Edge[] sortedEdges = new Edge[edges.length];
            Thread.sleep(0);
            System.arraycopy(edges, 0, sortedEdges, 0, edges.length);
            Thread.sleep(0);

            Arrays.sort(sortedEdges, (i, j) -> {
                if (i.src == j.src && i.dst == j.dst && i.weight == j.weight)
                    return 0;
                return ((i.src < j.src) || ((i.src == j.src) && (i.dst < j.dst))) ? -1 : 1;
            });
            Thread.sleep(0);

            numEdges = sortedEdges.length;
            numVertices = sortedEdges[sortedEdges.length - 1].src + 1;
            if (sortedEdges[0].src > numVertices - 1) {
                throw new RuntimeException("WRONG SORT " + sortedEdges[0].src + " > " + (numVertices - 1));
            }

            offsets = new int[numVertices + 1];
            destinations = new int[numEdges];
            weights = new float[numEdges];

            for (int e = 0; e < numEdges; e++) {
                offsets[sortedEdges[e].src + 1]++;
                destinations[e] = sortedEdges[e].dst;
                weights[e] = sortedEdges[e].weight;
            }
            Thread.sleep(0);

            for (int i = 0; i < numVertices; i++) {
                offsets[i + 1] += offsets[i];
            }
            Thread.sleep(0);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    public int[] astar_sssp(int start, int end, float maximum_weight) {
        Vector3 destNode = Navigator.navigationNodes[end / Navigator.numDirectionDistinctions];

        final int nbits = 64;
        final long one = 1;

        long[] closeSet = new long[(numVertices + nbits - 1) / nbits];

        float[] predictedWeights = new float[numVertices];
        Arrays.fill(predictedWeights, -1);
        int[] bestParents = new int[numVertices];
        Arrays.fill(bestParents, -1);
        float[] bestWeights = new float[numVertices];
        Arrays.fill(bestWeights, maximum_weight);

        PriorityQueue<Tuple</*id*/Integer, /*total weight*/Float>> openSet = new PriorityQueue<>((x, y) -> Float.compare(x.getValue() + predictedWeights[x.getKey()], y.getValue() + predictedWeights[y.getKey()]));

        openSet.add(new Tuple<>(start, 0f));
        int i = 0;
        while (!openSet.isEmpty()) {
            i++;
            final var current = openSet.remove();
            final int currentId = current.getKey();
            // Has been visited?
            if ((closeSet[currentId / nbits] & (one << (currentId % nbits))) == 0) {

                if (currentId == end) {
                    System.out.println("Iterations: " + i);
                    return bestParents;
                }

                // Add current node to close set
                closeSet[currentId / nbits] |= (one << (currentId % nbits));

                // Loop through neighbours
                final int neighbourBegin = offsets[currentId];
                final int neighbourEnd = offsets[currentId + 1];

                for (int j = neighbourBegin; j < neighbourEnd; j++) {

                    final int neighbourId = destinations[j];
                    final int neighbourIdNode = neighbourId / Navigator.numDirectionDistinctions;
                    // Not visited yet?
                    if ((closeSet[neighbourId / nbits] & (one << (neighbourId % nbits))) == 0) {

                        // Heuristic: Seconds it takes to go from neighbour to end node at max speed + 100
                        float predictedWeight = predictedWeights[neighbourIdNode];
                        if (predictedWeight == -1) {
                            predictedWeight = ((float) Navigator.navigationNodes[neighbourIdNode].distance(destNode)) / (1400 + 100);
                            predictedWeights[neighbourIdNode] = predictedWeight;
                        }

                        // G Cost
                        float distToNeighbour = weights[j];
                        float totalWeight = current.getValue() + distToNeighbour;

                        if (totalWeight < bestWeights[neighbourId]) {
                            bestWeights[neighbourId] = totalWeight;
                            bestParents[neighbourId] = currentId;

                            openSet.add(new Tuple<>(neighbourId, totalWeight));
                        }
                    }
                }
            }/*else{
                if(current.getValue() < bestWeights[current.getKey()])
                    System.out.println("Neighbour in set twice: "+current.getValue()+" old "+bestWeights[current.getKey()]);
            }*/
        }
        System.out.println("Iterations: " + i);
        return bestParents;
    }

    public int[] bellman_ford_sssp(int start, float maximum_weight) {
        int[] bestParents = new int[numVertices];
        Arrays.fill(bestParents, -1);
        float[] bestWeights = new float[numVertices];
        Arrays.fill(bestWeights, maximum_weight);

        bestParents[start] = start;
        bestWeights[start] = 0f;

        IntArrayList frontier = new IntArrayList(numVertices);
        frontier.add(start);

        long ms = System.nanoTime();

        int hop = 0;
        for (int iter = 0; iter < 128 && frontier.getSize() > 0; iter++) {
            bellman_ford_iteration(frontier, bestParents, bestWeights);
            hop++;
        }
        //System.out.println("Hop: " + hop);

        //System.out.println("analyze: bellman_ford_iteration took "+((System.nanoTime() - ms) * 0.000001f)+"ms");
        lastBestWeights = bestWeights;
        return bestParents;
    }

    @SuppressWarnings("UnusedReturnValue")
    private boolean bellman_ford_iteration(IntArrayList frontier, int[] bestParents, float[] bestWeights) {
        final int nbits = 64;
        final long one = 1;

        long[] visited = new long[(numVertices + nbits - 1) / nbits];

        final int[] arr = frontier.getArray();
        final int siz = frontier.getSize();
        for (int i = 0; i < siz; i++) {
            int source = arr[i];
            int begin = offsets[source];
            int end = offsets[source + 1];

            for (int j = begin; j < end; j++) {
                final int destination = destinations[j];

                final float new_weight = bestWeights[source] + weights[j];
                final float old_weight = bestWeights[destination];

                if (new_weight < old_weight) {

                    bestParents[destination] = source;
                    bestWeights[destination] = new_weight;
                    visited[destination / nbits] |= (one << (destination % nbits));
                }
            }
        }

        frontier.clear();

        for (int i = 0; i < visited.length; i++) {
            if (visited[i] != 0) {
                for (int j = 0; j < nbits; j++) {
                    if ((visited[i] & (one << j)) != 0) {
                        frontier.add(i * nbits + j);
                    }
                }
            }
        }

        return frontier.getSize() == 0;
    }

    public static class Edge {
        public final int src, dst;
        public final float weight;

        public Edge(int src, int dst, float weight) {
            this.src = src;
            this.dst = dst;
            this.weight = weight;
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName() + "(src=" + src + "; dst=" + dst + "; weight:" + weight + ")";
        }
    }
}
