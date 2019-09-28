package yangbot.util;

import java.util.Arrays;

public class Graph {

    private int numEdges;
    private int numVertices;

    private int[] offsets;
    private int[] destinations;
    private float[] weights;

    public Graph(Edge[] edges){
        Edge[] sortedEdges = new Edge[edges.length];
        System.arraycopy(edges, 0, sortedEdges, 0, edges.length);

        Arrays.sort(sortedEdges, (i, j) -> {
            if(i.src == j.src && i.dst == j.dst && i.weight == j.weight)
                return 0;
            return ((i.src < j.src) || ((i.src == j.src) && (i.dst < j.dst))) ? -1 : 1;
        });

        numEdges = sortedEdges.length;
        numVertices = sortedEdges[sortedEdges.length - 1].src + 1;
        if(sortedEdges[0].src > numVertices - 1){
            throw new RuntimeException("WRONG SORT "+sortedEdges[0].src+" > " + (numVertices - 1));
        }

        offsets = new int[numVertices + 1];
        destinations = new int[numEdges];
        weights = new float[numEdges];

        for(int e = 0; e < numEdges; e++){
            offsets[sortedEdges[e].src + 1]++;
            destinations[e] = sortedEdges[e].dst;
            weights[e] = sortedEdges[e].weight;
        }

        for (int i = 0; i < numVertices; i++) {
            offsets[i+1] += offsets[i];
        }

    }

    public int[] bellman_ford_sssp(int start, float maximum_weight){
        int[] bestParents = new int[numVertices];
        Arrays.fill(bestParents, -1);
        float[] bestWeights = new float[numVertices];
        Arrays.fill(bestWeights, maximum_weight);

        bestParents[start] = start;
        bestWeights[start] = 0f;

        IntArrayList frontier = new IntArrayList(numVertices);
        frontier.add(start);

        long ms = System.nanoTime();

        for(int iter = 0; iter < 128 && frontier.getSize() > 0; iter++){
            bellman_ford_iteration(frontier, bestParents, bestWeights);
        }

        //System.out.println("analyze: bellman_ford_iteration took "+((System.nanoTime() - ms) * 0.000001f)+"ms");


        return bestParents;
    }

    @SuppressWarnings("UnusedReturnValue")
    private boolean bellman_ford_iteration(IntArrayList frontier, int[] bestParents, float[] bestWeights){
        final int nbits = 64;
        final long one = 1;

        long[] visited = new long[(numVertices + nbits - 1) / nbits];

        final int[] arr = frontier.getArray();
        final int siz = frontier.getSize();
        for(int i = 0; i < siz; i++){
            int source = arr[i];
            int begin = offsets[source];
            int end = offsets[source + 1];

            for(int j = begin; j < end; j++) {
                final int destination = destinations[j];

                final float new_weight = bestWeights[source] + weights[j];
                final float old_weight = bestWeights[destination];

                if(new_weight < old_weight){
                    bestParents[destination] = source;
                    bestWeights[destination] = new_weight;
                    visited[destination / nbits] |= (one << (destination % nbits));
                }
            }
        }

        frontier.clear();

        for(int i = 0; i < visited.length; i++){
            if(visited[i] != 0){
                for(int j = 0; j < nbits; j++){
                    if ((visited[i] & (one << j)) != 0){
                        frontier.add(i * nbits + j);
                    }
                }
            }
        }

        return frontier.getSize() == 0;
    }

    public static class Edge {
        int src, dst;
        float weight;

        public Edge(int src, int dst, float weight) {
            this.src = src;
            this.dst = dst;
            this.weight = weight;
        }

        @Override
        public String toString(){
            return this.getClass().getSimpleName()+"(src="+src+"; dst="+dst+"; weight:"+weight+")";
        }
    }
}
