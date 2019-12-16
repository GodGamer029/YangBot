package yangbot.prediction;

import yangbot.input.CarData;
import yangbot.util.Graph;
import yangbot.util.MathUtils;
import yangbot.vector.Matrix3x3;
import yangbot.vector.Vector3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class Navigator {

    public static Vector3[] navigationNodes;
    public static Vector3[] navigationTangents;
    public static Vector3[] navigationNormals;
    private static AtomicBoolean loadedStatics = new AtomicBoolean(false);
    private static int nTheta = 16;
    private static int scale = -1;
    private static int nx = -1;
    private static int nv = -1;
    private static Vector3[] directions = new Vector3[0];
    private static int[] strides = new int[3];
    private static float[] LUT_times;
    private static int[] LUT_paths;
    private static int source_node = -1;
    private static int source_direction = -1;
    private static int[] navigation_paths;
    private static Vector3 source;
    private static Graph navigationGraph;

    public static void initStatics(int[] parameters, float[] times, int[] paths, Graph.Edge[] edges, Vector3[] nav_nodes, Vector3[] nav_normals) {
        navigationGraph = new Graph(edges);


        navigationNodes = nav_nodes;
        navigationNormals = nav_normals;

        scale = parameters[0];
        nx = parameters[1];
        nTheta = parameters[2];
        nv = parameters[3];

        if (nTheta >= 1024)
            throw new IllegalStateException("Read faulty parameters");

        directions = new Vector3[nTheta];

        final float k = nTheta / 6.28318530f;
        System.out.println("Direction-Init with " + nTheta + " elements");
        for (int i = 0; i < nTheta; i++) {
            directions[i] = new Vector3(Math.cos(((float) i) / k), Math.sin(((float) i) / k), 0f);
        }

        strides[0] = nv * nTheta * (2 * nx + 1);
        strides[1] = nv * nTheta;
        strides[2] = nv;

        LUT_times = times;
        LUT_paths = paths;

        navigationTangents = new Vector3[navigationNormals.length * nTheta];
        for (int i = 0; i < navigationNormals.length; i++) {
            Matrix3x3 basis = Matrix3x3.R3_basis(navigationNormals[i]);
            for (int j = 0; j < nTheta; j++) {
                navigationTangents[i * nTheta + j] = basis.dot(directions[j]);
            }
        }

        loadedStatics.set(true);
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

    private Curve lutPathTo(CarData car, Vector3 destination, Vector3 tangent, float offset) {
        final float k = nTheta / 6.28318530f;
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
        if (theta < 0) theta += nTheta;

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

    private Curve navmeshPathTo(CarData car, Vector3 destination, Vector3 tangent, float offset) {
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
        for (int j = 0; j < nTheta; j++) {
            float alignment = (float) unitTangent.dot(navigationTangents[destinationNode * nTheta + j]);
            if (alignment > maximum_alignment) {
                destination_direction = j;
                maximum_alignment = alignment;
            }
        }

        int source_id = source_node * nTheta + source_direction;
        int dest_id = destinationNode * nTheta + destination_direction;

        Vector3 p = navigationNodes[destinationNode];
        Vector3 t = navigationTangents[dest_id];
        Vector3 n = navigationNormals[dest_id / nTheta];

        destination = destination.sub(n.mul(destination.sub(p).dot(n)));
        List<Curve.ControlPoint> ctrl_pts = new ArrayList<>();

        ctrl_pts.add(new Curve.ControlPoint(p, t, n));

        for (int i = 0; i < 32; i++) {

            // find the navigation node and tangent that brings me here
            dest_id = navigation_paths[dest_id];

            // if it exists, add another control point to the path
            if (dest_id != -1) {

                p = navigationNodes[dest_id / nTheta];
                t = navigationTangents[dest_id];
                n = navigationNormals[dest_id / nTheta];

                ctrl_pts.add(new Curve.ControlPoint(p, t, n));

                // if we reach the navigation node for the car,
                // handle that case differently, and exit the loop
                if (dest_id == source_id) break;

                // otherwise, the path is unreachable
            } else {
                return new Curve();
            }

        }

        Collections.reverse(ctrl_pts);

        Vector3 dx1 = source.add(car.forward().mul(offset)).sub(ctrl_pts.get(0).point);
        Vector3 dt1 = car.forward().sub(ctrl_pts.get(0).tangent);

        Vector3 dx2 = destination.sub(unitTangent.mul(offset)).sub(ctrl_pts.get(ctrl_pts.size() - 1).point);
        Vector3 dt2 = unitTangent.sub(ctrl_pts.get(ctrl_pts.size() - 1).tangent);

        return new Curve(ctrl_pts, dx1, dt1, dx2, dt2, source, destination);
    }

    public Curve pathTo(CarData car, Vector3 destination, Vector3 tangent, float offset) {
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
        return navmeshPathTo(car, destination, tangent, offset);
    }

    public void analyzeSurroundings(CarData car) {
        while (!loadedStatics.get()) {
            try {
                Thread.sleep(1);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        source = car.position;
        int nnodes = navigationNodes.length;

        long ms = System.nanoTime();

        source_node = -1;
        float minimum = 1000000.0f;
        for (int i = 0; i < nnodes; i++) {
            float distance = (float) source.sub(navigationNodes[i]).magnitude();
            if (distance < minimum) {
                source_node = i;
                minimum = distance;
            }
        }
        System.out.println("analyze: sourceNode took " + ((System.nanoTime() - ms) * 0.000001f) + "ms");
        ms = System.nanoTime();

        Vector3 p = navigationNodes[source_node];
        Vector3 n = navigationNormals[source_node];
        source = source.sub(n.mul(source.sub(p).dot(n)));

        Vector3 f = car.forward();
        source_direction = -1;
        float maximum_alignment = -2.0f;
        for (int j = 0; j < nTheta; j++) {
            float alignment = (float) f.dot(navigationTangents[source_node * nTheta + j]);
            if (alignment > maximum_alignment) {
                source_direction = j;
                maximum_alignment = alignment;
            }
        }
        System.out.println("analyze: direction took " + ((System.nanoTime() - ms) * 0.000001f) + "ms");
        ms = System.nanoTime();

        int source_id = source_node * nTheta + source_direction;
        navigation_paths = navigationGraph.bellman_ford_sssp(source_id, 15f);
        System.out.println("analyze: bellman_ford_sssp took " + ((System.nanoTime() - ms) * 0.000001f) + "ms");

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
