package yangbot.optimizers.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class DeciderYangNet extends SimpleYangNet {

    public static DeciderYangNet GAME_STATE_PREDICTOR;

    static {
        GAME_STATE_PREDICTOR = DeciderYangNet.readFrom("dec_net.ptyang", new int[][]{
                        {20 * 2 + 9, 64},
                        {64, 32},
                        {32, 16},
                        {16, 1},

                        {20 + 9, 20},
                        {20, 1}
                },
                new LinearLayer.Activation[]{ // value
                        LinearLayer.Activation.MISH,
                        LinearLayer.Activation.MISH,
                        LinearLayer.Activation.MISH,
                        LinearLayer.Activation.SIGMOID},
                new LinearLayer.Activation[]{ // decider
                        LinearLayer.Activation.MISH,
                        LinearLayer.Activation.SIGMOID}
                );
    }

    private LinearLayer.Activation[] vActs, dActs;

    public DeciderYangNet(byte[] model, int[][] sizes, LinearLayer.Activation[] vAct, LinearLayer.Activation[] dAct) {
        this.vActs = vAct;
        this.dActs = dAct;
        List<LinearLayer.Activation> actList = new ArrayList<>();
        actList.addAll(Arrays.asList(vAct));
        actList.addAll(Arrays.asList(dAct));
        this.initialize(model, sizes, actList.toArray(new LinearLayer.Activation[0]));
    }

    public float[] deciderForward(float[] input){
        for(int i = 0; i < dActs.length; i++)
            input = this.layers.get(i + vActs.length).forward(input);
        return input;
    }

    @Override
    public float[] forward(float[] input) {
        for(int i = 0; i < vActs.length; i++)
            input = this.layers.get(i).forward(input);
        return input;
    }

    public static DeciderYangNet readFrom(String resourcePath, int[][] sizes, LinearLayer.Activation[] vAct, LinearLayer.Activation[] dAct) {
        try {
            final ClassLoader cl = ClassLoader.getSystemClassLoader();
            var para = Objects.requireNonNull(cl.getResourceAsStream(resourcePath));
            byte[] allData = para.readNBytes(para.available());
            return new DeciderYangNet(allData, sizes, vAct, dAct);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
