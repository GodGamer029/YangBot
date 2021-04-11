package yangbot.optimizers.model;

import yangbot.input.BallData;
import yangbot.input.Physics3D;
import yangbot.util.Tuple;

public class ModelUtils {

    public static float[] physicsToNormalizedState(Physics3D ph) {
        var pos = ph.position;
        var vel = ph.velocity;
        var ang = ph.angularVelocity;
        return new float[]{pos.x / 4100f, pos.y / 6000f, pos.z / 2000f, vel.x / 4000f, vel.y / 4000, vel.z / 4000, ang.x / 6f, ang.y / 6f, ang.z / 6f};
    }

    // < side | time >
    public static Tuple<Float, Float> interpretYangOutput(float[] output) {
        float sum = 0;
        for (int i = 0; i < output.length - 1; i++)
            sum += 0.001f + output[i];


        float t = 0;
        for (int i = 0; i < output.length - 1; i++)
            t += i * (output[i] / sum);

        return new Tuple<>(output[output.length - 1], t);
    }

    public static Tuple<Float, Float> ballToPrediction(BallData ballData) {
        return ModelUtils.interpretYangOutput(SimpleYangNet.BALL_STATE_PREDICTOR.forward(ModelUtils.physicsToNormalizedState(ballData.toPhysics3d())));
    }

    public static void preloadAllModels() {
        SimpleYangNet.BALL_STATE_PREDICTOR.toString();
    }

}
