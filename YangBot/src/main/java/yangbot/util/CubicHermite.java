package yangbot.util;

import yangbot.vector.Vector3;

public class CubicHermite {

    Vector3 x0, t0;

    Vector3 x1, t1;

    float L;

    public CubicHermite(Vector3 x0, Vector3 t0, Vector3 x1, Vector3 t1, float l) {
        this.x0 = x0;
        this.t0 = t0;
        this.x1 = x1;
        this.t1 = t1;
        L = l;
    }


    public Vector3 e(float s) {
        float u = s / L;
        return x0.mul((1.0f - u) * (1.0f - u) * (1.0f + 2.0f * u))
                .add(t0.mul(L * (1.0f - u) * (1.0f - u) * u))
                .add(x1.mul((3.0f - 2.0f * u) * u * u))
                .add(t1.mul(L * (u - 1.0f) * u * u));
    }
}
