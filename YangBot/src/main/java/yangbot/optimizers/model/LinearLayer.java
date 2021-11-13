package yangbot.optimizers.model;

import net.jafama.FastMath;

public class LinearLayer {

    private float[][] weights;
    private float[] biases;
    private Activation activation;

    public LinearLayer(float[][] weights, float[] biases, Activation activation) {
        this.weights = weights;
        this.biases = biases;
        this.activation = activation;
    }

    private float sigmoid(float x) {
        return 1 / (1 + (float) FastMath.exp(-1 * x));
    }

    private float mish(float x) {
        return x * (float) (FastMath.tanh(FastMath.log(1 + FastMath.exp(x))));
    }

    private float activation(float x) {
        switch (activation) {
            case MISH:
                return mish(x);
            case SIGMOID:
                return sigmoid(x);
        }
        return x;
    }

    public float[] forward(float[] inp) {
        assert inp.length == weights[0].length;
        float[] output = new float[weights.length];
        for (int i = 0; i < output.length; i++) {
            for (int b = 0; b < inp.length; b++) {
                output[i] += inp[b] * weights[i][b];
            }
            output[i] += biases[i];
            output[i] = activation(output[i]);
        }
        return output;
    }

    public enum Activation {
        LINEAR,
        MISH,
        SIGMOID
    }
}
