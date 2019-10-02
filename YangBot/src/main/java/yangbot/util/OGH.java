package yangbot.util;

import yangbot.vector.Vector3;

public class OGH { // oink

    float a0, a1;

    Vector3 P0, P1, V0, V1;

    public OGH(Vector3 _P0, Vector3 _V0, Vector3 _P1, Vector3 _V1) {
        P0 = _P0;
        V0 = _V0.normalized();

        P1 = _P1;
        V1 = _V1.normalized();

        Vector3 dP = P1.sub(P0);
        float V0dotV1 = (float) V0.dot(V1);
        float denom = 4f - V0dotV1 * V0dotV1;

        a0 = (float) (6.0f * dP.dot(V0) - 3.0f * dP.dot(V1) * V0dotV1) / denom;
        a1 = (float) (6.0f * dP.dot(V1) - 3.0f * dP.dot(V0) * V0dotV1) / denom;
    }

    public Vector3 evaluate(float t) {
        return P0
                .mul((2.0f * t + 1.0f) * (t - 1.0f) * (t - 1.0f))
                .add(V0.mul(((t - 1.0f) * (t - 1.0f) * t) * a0))
                .add(P1.mul((3.0f - 2.0f * t) * t * t))
                .add(V1.mul(((t - 1.0f) * t * t) * a1));
    }

    public Vector3 tangent(float t) {
        return P0.mul(6.0f * t * t - 6.0f * t)
                .add(V0.mul((1.0f - 4.0f * t + 3.0f * t * t) * a0))
                .add(P1.mul(6.0f * t - 6.0f * t * t))
                .add(V1.mul((3.0f * t * t - 2.0f * t) * a1));
    }

    public Vector3 acceleration(float t) {
        return P0.mul(12.0f * t - 6.0f)
                .add(V0.mul((6.0f * t - 4.0f) * a0))
                .sub(P1.mul(12.0f * t - 6.0f))
                .add(V1.mul((6.0f * t - 2.0f) * a1));
    }
}
