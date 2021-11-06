package yangbot.optimizers.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SimpleYangNet {

    public static SimpleYangNet BALL_STATE_PREDICTOR;

    static {
        BALL_STATE_PREDICTOR = SimpleYangNet.readFrom("simple_net.ptyang", new int[]{9, 64, 32, 32, 9},
                new LinearLayer.Activation[]{
                        LinearLayer.Activation.MISH,
                        LinearLayer.Activation.MISH,
                        LinearLayer.Activation.MISH,
                        LinearLayer.Activation.SIGMOID});
    }

    protected List<LinearLayer> layers;

    protected SimpleYangNet() {
    }

    public SimpleYangNet(byte[] model, int[] sizes, LinearLayer.Activation[] activations) {
        this.initialize(model, sizes, activations);
    }

    public void initialize(byte[] model, int[] sizes, LinearLayer.Activation[] activations){
        int[][] betterSizes = new int[sizes.length - 1][2];
        for(int i = 0; i < betterSizes.length; i++){
            betterSizes[i][0] = sizes[i];
            betterSizes[i][1] = sizes[i + 1];
        }
        this.initialize(model, betterSizes, activations);
    }

    public void initialize(byte[] model, int[][] sizes, LinearLayer.Activation[] activations){
        assert sizes.length > 0;
        assert model.length > 0;

        layers = new ArrayList<>();
        int modelIndex = 0;
        int layerIndex = 0;
        while (modelIndex + 1 < model.length) {
            float[][] weigths = new float[sizes[layerIndex][1]][sizes[layerIndex][0]];
            float[] biases = new float[sizes[layerIndex][1]];

            assert model.length >= modelIndex + 4 * weigths.length * weigths[0].length + 4 * biases.length : model.length + " " + modelIndex + " " + weigths.length + " " + weigths[0].length + " " + biases.length;

            for (int i = 0; i < weigths.length; i++) {
                for (int b = 0; b < weigths[0].length; b++) {
                    float f = Float.intBitsToFloat(
                            (model[modelIndex] & 255) +
                                    ((model[modelIndex + 1] & 255) << 8) +
                                    ((model[modelIndex + 2] & 255) << 16) +
                                    ((model[modelIndex + 3] & 255) << 24));
                    weigths[i][b] = f;
                    modelIndex += 4;
                }
            }
            for (int b = 0; b < biases.length; b++) {
                float f = Float.intBitsToFloat(
                        (model[modelIndex] & 255) +
                                ((model[modelIndex + 1] & 255) << 8) +
                                ((model[modelIndex + 2] & 255) << 16) +
                                ((model[modelIndex + 3] & 255) << 24));
                biases[b] = f;
                modelIndex += 4;
            }

            layers.add(new LinearLayer(weigths, biases, activations[layerIndex]));
            layerIndex++;
        }
        assert modelIndex == model.length;
    }

    public static SimpleYangNet readFrom(String resourcePath, int[] sizes, LinearLayer.Activation[] activations) {
        try {
            final ClassLoader cl = ClassLoader.getSystemClassLoader();
            var para = Objects.requireNonNull(cl.getResourceAsStream(resourcePath));
            byte[] allData = para.readNBytes(para.available());
            return new SimpleYangNet(allData, sizes, activations);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public float[] forward(float[] input) {
        for (var l : layers)
            input = l.forward(input);
        return input;
    }
}
