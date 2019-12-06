package yangbot.manuever;

import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.util.ControlsOutput;
import yangbot.vector.Matrix3x3;
import yangbot.vector.Vector3;

public class TurnManeuver extends Maneuver {

    private final Vector3[] e = new Vector3[]{
            new Vector3(1, 0, 0),
            new Vector3(0, 1, 0),
            new Vector3(0, 0, 1)
    };
    public ControlsOutput controls = new ControlsOutput();
    public Matrix3x3 target = null;
    public float eps_phi = 0.10f;
    public float eps_omega = 0.15f;
    private Vector3 omega = new Vector3();
    private Vector3 omega_local = new Vector3();
    private Vector3 dphi_dt = new Vector3();
    private Vector3 phi = new Vector3();
    private Matrix3x3 Z0 = new Matrix3x3();
    private Matrix3x3 theta = new Matrix3x3();
    private float horizon_time = 0.05f;
    private Vector3 alpha = new Vector3();
    private Matrix3x3 target_prev = new Matrix3x3();

    private Vector3 G(Vector3 q, Vector3 dq_dt) {
        final Vector3 T = new Vector3(-400.f, -130.f, 95.f).div(10.5f);
        final Vector3 D = new Vector3(-50.0, -30.0f, -20.0f).div(10.5f);

        float G_roll = -Math.signum(dq_dt.x) * (
                (Math.abs(dq_dt.x) / D.x) +
                        (T.x / (D.x * D.x)) * (float) Math.log(T.x / (T.x + D.x * Math.abs(dq_dt.x)))
        );

        float G_pitch = -Math.signum(dq_dt.y) * dq_dt.y * dq_dt.y / (2.0f * T.y);
        float G_yaw = Math.signum(dq_dt.z) * dq_dt.z * dq_dt.z / (2.0f * T.z);

        return new Vector3(G_roll, G_pitch, G_yaw);
    }

    private Vector3 f(Vector3 alpha_local, float dt) {
        Vector3 alpha_world = theta.dot(alpha_local);
        Vector3 omega_prediction = omega.add(alpha_world.mul(dt));
        Vector3 phi_prediction = phi
                .add(Z0
                        .dot(omega
                                .add(alpha_world
                                        .mul(0.5f * dt)
                                )
                        )
                        .mul(dt)
                );
        Vector3 dphi_dt_prediction = Z0.dot(omega_prediction);
        return phi_prediction.mul(-1).sub(G(phi_prediction, dphi_dt_prediction));
    }

    private Matrix3x3 Z(Vector3 q) {
        float norm_q = (float) q.magnitude();

        final float v1 = q.x * q.x + q.z * q.z;
        final float v2 = q.y * q.y + q.z * q.z;
        final float v3 = q.x * q.x + q.y * q.y;

        if (norm_q < 0.2f) {
            Matrix3x3 mat = new Matrix3x3();

            mat.assign(0, 0, 1.0f - v2 / 12.0f);
            mat.assign(0, 1, (q.x * q.y / 12.0f) + q.z / 2.0f);
            mat.assign(0, 2, (q.x * q.z / 12.0f) - q.y / 2.0f);

            mat.assign(1, 0, (q.y * q.x / 12.0f) - q.z / 2.0f);
            mat.assign(1, 1, 1.0f - v1 / 12.0f);
            mat.assign(1, 2, (q.y * q.z / 12.0f) + q.x / 2.0f);

            mat.assign(2, 0, (q.z * q.x / 12.0f) + q.y / 2.0f);
            mat.assign(2, 1, (q.z * q.y / 12.0f) - q.x / 2.0f);
            mat.assign(2, 2, 1.0f - v3 / 12.0f);

            return mat;
        } else {
            float qq = norm_q * norm_q;
            float c = 0.5f * norm_q * (float) Math.cos(0.5f * norm_q) / (float) Math.sin(0.5f * norm_q);

            Matrix3x3 mat = new Matrix3x3();

            mat.assign(0, 0, (q.x * q.x + c * v2) / qq);
            mat.assign(0, 1, ((1.0f - c) * q.x * q.y / qq) + q.z / 2.0f);
            mat.assign(0, 2, ((1.0f - c) * q.x * q.z / qq) - q.y / 2.0f);

            mat.assign(1, 0, ((1.0f - c) * q.y * q.x / qq) - q.z / 2.0f);
            mat.assign(1, 1, (q.y * q.y + c * v1) / qq);
            mat.assign(1, 2, ((1.0f - c) * q.y * q.z / qq) + q.x / 2.0f);

            mat.assign(2, 0, ((1.0f - c) * q.z * q.x / qq) + q.y / 2.0f);
            mat.assign(2, 1, ((1.0f - c) * q.z * q.y / qq) - q.x / 2.0f);
            mat.assign(2, 2, (q.z * q.z + c * v3) / qq);

            return mat;
        }
    }

    private float solvePwl(float y, Vector3 values) {
        float minValue = Math.min(Math.min(values.x, values.y), values.z);
        float maxValue = Math.max(Math.max(values.x, values.y), values.z);
        float clippedY = Math.min(Math.max(minValue, y), maxValue);

        if ((Math.min(values.x, values.y) <= clippedY) &&
                (clippedY <= Math.max(values.x, values.y))) {
            if (Math.abs(values.y - values.x) > 0.0001f)
                return (clippedY - values.y) / (values.y - values.x);
            else
                return -0.5f;
        } else {
            if (Math.abs(values.z - values.y) > 0.0001f)
                return (clippedY - values.y) / (values.z - values.y);
            else
                return 0.5f;
        }
    }

    private Vector3 findControlsFor(Vector3 idealAlpha) {
        final Vector3 w = omega_local;
        final Vector3 T = new Vector3(-400.f, -130.f, 95.f).div(10.5f);
        final Vector3 D = new Vector3(-50.0, -30.0f, -20.0f).div(10.5f);

        Vector3 alpha_values;

        alpha_values = new Vector3(-T.x + D.x * w.x, D.x * w.x, T.x + D.x * w.x);
        float x = solvePwl(idealAlpha.x, alpha_values);

        alpha_values = new Vector3(-T.y, D.y * w.y, T.y);
        float y = solvePwl(idealAlpha.y, alpha_values);

        alpha_values = new Vector3(-T.z, D.z * w.z, T.z);
        float z = solvePwl(idealAlpha.z, alpha_values);

        return new Vector3(x, y, z);
    }

    @Override
    public boolean isViable() {
        return false;
    }

    @Override
    public void step(float dt, ControlsOutput controlsOutput) {
        final GameData gameData = this.getGameData();
        final Vector3 gravity = gameData.getGravity();
        final CarData car = gameData.getCarData();
        final BallData ball = gameData.getBallData();

        omega = target.transpose().dot(car.angularVelocity);
        theta = target.transpose().dot(car.orientationMatrix);
        omega_local = omega.dot(theta);
        phi = Vector3.rotationToAxis(theta);

        this.setIsDone((phi.magnitude() < eps_phi) && (omega.magnitude() < eps_omega));

        if (this.isDone()) {
            controls.withRoll(0);
            controls.withYaw(0);
            controls.withPitch(0);

            if (controlsOutput != null) {
                controlsOutput.withPitch(0);
                controlsOutput.withYaw(0);
                controlsOutput.withRoll(0);
            }
        } else {
            this.Z0 = Z(phi);
            dphi_dt = this.Z0.dot(omega);
            this.horizon_time = Math.max(0.03f, 4.0f * dt);

            final int n_iterations = 5;
            float eps = 0.001f;
            for (int i = 0; i < n_iterations; i++) {
                Vector3 f0 = f(alpha, horizon_time);

                Matrix3x3 J = new Matrix3x3();
                for (int j = 0; j < 3; j++) {
                    Vector3 df_j = (f0.sub(
                            f(alpha.add(e[j].mul(eps)), horizon_time)
                    )).div(eps);
                    J.assign(0, j, df_j.x);
                    J.assign(1, j, df_j.y);
                    J.assign(2, j, df_j.z);

                    J.assign(j, j, J.get(j, j) + 0.00001f);
                }

                Vector3 delta_alpha = J.invert().dot(f0);
                alpha = alpha.add(delta_alpha);
                if (delta_alpha.magnitude() < 1.0f)
                    break;
            }
            Vector3 rpy = findControlsFor(alpha);

            controls.withRoll(rpy.x);
            controls.withPitch(rpy.y);
            controls.withYaw(rpy.z);

            if (controlsOutput != null) {
                controlsOutput.withRoll(rpy.x);
                controlsOutput.withPitch(rpy.y);
                controlsOutput.withYaw(rpy.z);
            }
        }
    }

    @Override
    public CarData simulate(CarData car) {
        throw new IllegalStateException("not implemented");
    }
}
