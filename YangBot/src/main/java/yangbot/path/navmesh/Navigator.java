package yangbot.path.navmesh;

import yangbot.input.CarData;
import yangbot.path.Curve;
import yangbot.util.Tuple;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Navigator {

    public static Vector3[] navigationNodes;
    public static Vector3[] navigationTangents;
    public static Vector3[] navigationNormals;
    private static AtomicBoolean loadedStatics = new AtomicBoolean(false);
    public static int numDirectionDistinctions = 16;
    private static int scale = -1;
    private static int nx = -1;
    private static int nv = -1;
    private static Vector3[] directions = new Vector3[0];
    private static int[] strides = new int[3];
    private static float[] LUT_times;
    private static int[] LUT_paths;
    public static Graph navigationGraph;
    public final PathAlgorithm pathAlgorithm;
    public int source_node = -1;
    public int source_direction = -1;
    public int[] navigation_paths;
    public Vector3 source;

    public Navigator(PathAlgorithm pathAlgorithm) {
        this.pathAlgorithm = pathAlgorithm;
    }

    public static boolean isLoaded() {
        return loadedStatics.get();
    }

    public static void initStatics(int[] parameters, float[] times, int[] paths, Graph.Edge[] edges, Vector3[] nav_nodes, Vector3[] nav_normals) {
        navigationGraph = new Graph(edges);

        navigationNodes = nav_nodes;
        navigationNormals = nav_normals;

        scale = parameters[0];
        nx = parameters[1];
        numDirectionDistinctions = parameters[2];
        nv = parameters[3];

        if (numDirectionDistinctions >= 1024)
            throw new IllegalStateException("Read faulty parameters");

        directions = new Vector3[numDirectionDistinctions];

        final float k = numDirectionDistinctions / 6.28318530f;

        for (int i = 0; i < numDirectionDistinctions; i++) {
            directions[i] = new Vector3(Math.cos(((float) i) / k), Math.sin(((float) i) / k), 0f);
        }

        strides[0] = nv * numDirectionDistinctions * (2 * nx + 1);
        strides[1] = nv * numDirectionDistinctions;
        strides[2] = nv;

        LUT_times = times;
        LUT_paths = paths;

        navigationTangents = new Vector3[navigationNormals.length * numDirectionDistinctions];
        for (int i = 0; i < navigationNormals.length; i++) {
            Matrix3x3 basis = Matrix3x3.R3_basis(navigationNormals[i]);
            for (int j = 0; j < numDirectionDistinctions; j++) {
                navigationTangents[i * numDirectionDistinctions + j] = basis.dot(directions[j]);
            }
        }

        loadedStatics.set(true);
    }

    private Curve lutPathTo(CarData car, Vector3 destination, Vector3 tangent, float offset) {
        final float k = numDirectionDistinctions / 6.28318530f;
        final Vector3 n = new Vector3(0, 0, 1f);

        Vector3 unitTangent = tangent.normalized();
        Matrix3x3 orientation = Matrix3x3.lookAt(new Vector3(car.forward().flatten()), n);

        Vector3 destinationLocal = destination.sub(unitTangent.mul(offset)).sub(car.position).dot(orientation);
        Vector3 tangentLocal;

        int x = (int) MathUtils.clip(Math.round(destinationLocal.x / scale), -nx, nx);
        int y = (int) MathUtils.clip(Math.round(destinationLocal.y / scale), -nx, nx);

        tangentLocal = unitTangent.dot(orientation);
        float angle = (float) Math.atan2(tangentLocal.y, tangentLocal.x);
        int theta = Math.round(k * angle);
        if (theta < 0) theta += numDirectionDistinctions;

        int v = -1;
        int v_min = 0;
        int v_max = nv - 1;

        float speed = 1400.f;

        if (Float.isFinite(speed)) {
            v_min = (int) MathUtils.clip((int) Math.floor(speed / 100f), 0, nv - 1);
        }

        float best_time = 1.0e10f;

        for (int u = v_min; u <= v_max; u++) {
            if (LUT_times[to_id(x, y, theta, u)] < best_time) {
                best_time = LUT_times[to_id(x, y, theta, u)];
                v = u;
            }
        }

        List<Curve.ControlPoint> ctrl_pts = new ArrayList<>();

        for (int i = 0; i < 32; i++) {
            Vector3 p = orientation.dot(new Vector3(x * scale, y * scale, 0f)).add(car.position);
            Vector3 t = orientation.dot(directions[theta]);

            if (i == 0) {
                p = destination;
                t = tangent;
            }

            ctrl_pts.add(new Curve.ControlPoint(p, t, n));

            LutComp comp = from_id(LUT_paths[to_id(x, y, theta, v)]);
            x = comp.x;
            y = comp.y;
            theta = comp.theta;
            v = comp.v;

            if (x == 0 && y == 0 && theta == 0)
                break;
        }

        ctrl_pts.add(new Curve.ControlPoint(car.position, car.forward(), n));

        Collections.reverse(ctrl_pts);

        ctrl_pts.add(new Curve.ControlPoint(destination, unitTangent, n));

        return new Curve(ctrl_pts);
    }

    private int to_id(int x, int y, int theta, int v) {
        return (((x + nx) * strides[0] + (y + nx) * strides[1] + theta * strides[2] + v));
    }

    private LutComp from_id(long id) {
        int x = (int) ((id / strides[0]) - nx);
        id %= strides[0];
        int y = (int) ((id / strides[1]) - nx);
        id %= strides[1];
        int theta = (int) (id / strides[2]);
        id %= strides[2];
        int v = (int) (id);
        return new LutComp(x, y, theta, v);
    }

    private Curve navmeshPathTo(Vector3 startTangent, Vector3 destination, Vector3 tangent, float offset) {
        Vector3 unitTangent = tangent.normalized();

        int destinationNode = -1;
        float minimum = 1000000.0f;
        for (int i = 0; i < navigationNodes.length; i++) {
            float distance = (float) destination.sub(unitTangent.mul(offset)).sub(navigationNodes[i]).magnitude();
            if (distance < minimum) {
                destinationNode = i;
                minimum = distance;
            }
        }

        int destination_direction = -1;
        float maximum_alignment = -2.0f;
        for (int j = 0; j < numDirectionDistinctions; j++) {
            float alignment = (float) unitTangent.dot(navigationTangents[destinationNode * numDirectionDistinctions + j]);
            if (alignment > maximum_alignment) {
                destination_direction = j;
                maximum_alignment = alignment;
            }
        }

        int source_id = source_node * numDirectionDistinctions + source_direction;
        int dest_id = destinationNode * numDirectionDistinctions + destination_direction;

        if (this.pathAlgorithm == PathAlgorithm.ASTAR) {
            long ms = System.nanoTime();
            navigation_paths = navigationGraph.astar_sssp(source_id, dest_id, 8F);
            System.out.println("Navigator: astar_sssp took " + ((System.nanoTime() - ms) * 0.000001f) + "ms");
        }

        Vector3 p = navigationNodes[destinationNode];
        Vector3 t = navigationTangents[dest_id];
        Vector3 n = navigationNormals[dest_id / numDirectionDistinctions];

        destination = destination.sub(n.mul(destination.sub(p).dot(n)));
        List<Curve.ControlPoint> ctrl_pts = new ArrayList<>();

        ctrl_pts.add(new Curve.ControlPoint(p, t, n));

        for (int i = 0; i < 32; i++) {

            // find the navigation node and tangent that brings me here
            dest_id = navigation_paths[dest_id];

            // if it exists, add another control point to the path
            if (dest_id != -1) {

                p = navigationNodes[dest_id / numDirectionDistinctions];
                t = navigationTangents[dest_id];
                n = navigationNormals[dest_id / numDirectionDistinctions];

                ctrl_pts.add(new Curve.ControlPoint(p, t, n));

                // if we reach the navigation node for the car,
                // handle that case differently, and exit the loop
                if (dest_id == source_id) break;

                // otherwise, the path is unreachable
            } else {
                System.out.println("Could not reach! " + i);
                return null;
            }

        }

        Collections.reverse(ctrl_pts);

        Vector3 dx1 = source.add(startTangent.mul(offset)).sub(ctrl_pts.get(0).pos);
        Vector3 dt1 = startTangent.sub(ctrl_pts.get(0).tangent);

        Vector3 dx2 = destination.sub(unitTangent.mul(offset)).sub(ctrl_pts.get(ctrl_pts.size() - 1).pos);
        Vector3 dt2 = unitTangent.sub(ctrl_pts.get(ctrl_pts.size() - 1).tangent);

        return new Curve(ctrl_pts, dx1, dt1, dx2, dt2, source, destination);
    }

    public Curve pathTo(Vector3 startTangent, Vector3 destination, Vector3 tangent, float offset) {
        while (!loadedStatics.get()) {
            try {
                Thread.sleep(1);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        //if (Math.max(car.position.z, destination.z) < 50) {
        //return lutPathTo(car, destination, tangent, offset);
        //} else
        return navmeshPathTo(startTangent, destination, tangent, offset);
    }

    public Tuple<Integer, Vector3> findClosestNode(Vector3 pos, Vector3 direction) {
        if (!loadedStatics.get())
            throw new IllegalStateException("Navigator didn't load yet");

        Tuple<Integer, Vector3> closestNode = this.findClosestNode(pos);
        if (closestNode == null)
            return null;

        float maximum_alignment = -2.0f;
        int dir = 0;
        for (int j = 0; j < numDirectionDistinctions; j++) {
            float alignment = (float) direction.dot(navigationTangents[closestNode.getKey() * numDirectionDistinctions + j]);
            if (alignment > maximum_alignment) {
                dir = j;
                maximum_alignment = alignment;
            }
        }

        return new Tuple<>(closestNode.getKey() * numDirectionDistinctions + dir, closestNode.getValue());
    }

    public Tuple<Integer, Vector3> findClosestNode(Vector3 pos) {
        if (!loadedStatics.get())
            throw new IllegalStateException("Navigator didn't load yet");
        int closest = -1;
        float minimum = 1000000.0f;
        for (int i = 0; i < navigationNodes.length; i++) {
            float distance = (float) pos.sub(navigationNodes[i]).magnitude();
            if (distance < minimum) {
                closest = i;
                minimum = distance;
            }
        }
        if (closest == -1)
            return null;
        else
            return new Tuple<>(closest, navigationNodes[closest]);
    }

    public List<Tuple<Integer, Vector3>> findClosestNodes(Vector3 pos, int num) {
        if (!loadedStatics.get()) {
            System.err.println("Navigator didn't load yet");
            return new ArrayList<>();
        }

        return IntStream.range(0, navigationNodes.length)
                .boxed()
                .map(node -> new Tuple<>(node, pos.sub(navigationNodes[node]).magnitude()))
                .sorted(Comparator.comparingDouble(Tuple::getValue))
                .limit(num)
                .map(p -> new Tuple<>(p.getKey(), navigationNodes[p.getKey()]))
                .collect(Collectors.toList());
    }

    public void analyzeSurroundings(Vector3 pos, Vector3 dir) {
        while (!loadedStatics.get()) {
            try {
                Thread.sleep(1);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        this.source = pos;

        this.source_node = -1;
        float minimum = 1000000.0f;
        for (int i = 0; i < navigationNodes.length; i++) {
            float distance = (float) this.source.sub(navigationNodes[i]).magnitude();
            if (distance < minimum) {
                source_node = i;
                minimum = distance;
            }
        }

        Vector3 p = navigationNodes[this.source_node];
        Vector3 n = navigationNormals[this.source_node];
        this.source = this.source.sub(n.mul(this.source.sub(p).dot(n)));

        Vector3 f = dir;
        this.source_direction = -1;
        float maximum_alignment = -2.0f;
        for (int j = 0; j < numDirectionDistinctions; j++) {
            float alignment = (float) f.dot(navigationTangents[this.source_node * numDirectionDistinctions + j]);
            if (alignment > maximum_alignment) {
                this.source_direction = j;
                maximum_alignment = alignment;
            }
        }

        int source_id = this.source_node * numDirectionDistinctions + this.source_direction;

        switch (this.pathAlgorithm) {
            case BELLMANN_FORD: {
                long ms = System.nanoTime();

                navigation_paths = navigationGraph.bellman_ford_sssp(source_id, 8F);
                System.out.println("Navigator: bellman_ford_sssp took " + ((System.nanoTime() - ms) * 0.000001f) + "ms");
            }
            case ASTAR: {
                /*long ms = System.nanoTime();
                navigation_paths = navigationGraph.astar_sssp(source_id, -1, 8F);
                System.out.println("Navigator: astar_sssp took " + ((System.nanoTime() - ms) * 0.000001f) + "ms");*/
            }
        }
    }

    public void analyzeSurroundings(CarData car) {
        this.analyzeSurroundings(car.position, car.forward());
    }

    public enum PathAlgorithm {
        BELLMANN_FORD,
        ASTAR
    }

    private static class NavData {

    }

    private static class LutComp {
        public int x;
        public int y;
        public int theta;
        public int v;

        public LutComp(int x, int y, int theta, int v) {
            this.x = x;
            this.y = y;
            this.theta = theta;
            this.v = v;
        }
    }
}
