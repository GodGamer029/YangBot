package yangbot.util.math.vector;

import java.util.Arrays;

public class Matrix2x2 {
    private float[] data = new float[2 * 2];

    public Matrix2x2() {
        Arrays.fill(data, 0);
    }

    @SuppressWarnings("CopyConstructorMissesField")
    public Matrix2x2(Matrix2x2 o) {
        System.arraycopy(o.data, 0, this.data, 0, this.data.length);
    }

    public static Matrix2x2 fromRotation(float theta) {
        Matrix2x2 mat = new Matrix2x2();

        mat.assign(0, 0, (float) Math.cos(theta));
        mat.assign(0, 1, (float) -Math.sin(theta));
        mat.assign(1, 0, (float) Math.sin(theta));
        mat.assign(1, 1, (float) Math.cos(theta));

        return mat;
    }

    public void assign(int row, int column, float value) {
        this.data[row + column * 2] = value;
    }

    public float get(int row, int column) {
        return this.data[row + column * 2];
    }

    public Vector2 dot(Vector2 vec) {
        float[] Av = new float[2];

        for (int i = 0; i < 2; i++) {
            Av[i] = 0;
            for (int j = 0; j < 2; j++) {
                Av[i] += this.get(i, j) * vec.get(j);
            }
        }

        return new Vector2(Av[0], Av[1]);
    }
}
