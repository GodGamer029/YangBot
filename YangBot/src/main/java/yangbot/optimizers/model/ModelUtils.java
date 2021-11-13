package yangbot.optimizers.model;

import yangbot.input.*;
import yangbot.util.Tuple;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector3;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ModelUtils {

    public static float[] encodeBall(ImmutableBallData b) {
        var pos = b.position;
        var vel = b.velocity;
        var ang = b.angularVelocity;
        return new float[]{pos.x / 4100f, pos.y / 6000f, pos.z / 2000f, vel.x / 4000f, vel.y / 4000, vel.z / 4000, ang.x / 6f, ang.y / 6f, ang.z / 6f};
    }

    public static float[] encodeCar(CarData c, ImmutableBallData b) {
        var pos = c.position;
        var vel = c.velocity;
        var ang = c.angularVelocity;
        var forward = c.forward();
        var right = c.right();
        var cToB = b.position.sub(c.position);
        var cToBNorm = cToB.normalized();
        return new float[]{
                pos.x / 4100f, pos.y / 6000f, pos.z / 2000f,
                vel.x / 2300f, vel.y / 2300, vel.z / 2300,
                ang.x / 5.5f, ang.y / 5.5f, ang.z / 5.5f,
                forward.x, forward.y, forward.z,
                right.x, right.y, right.z,
                c.boost / 100.f,
                cToBNorm.x, cToBNorm.y, cToBNorm.z, (float)(Math.sqrt(cToB.magnitudeF()) / Math.sqrt(6000))
        };
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

    public static Tuple<Float, Float> ballToPrediction(ImmutableBallData ballData) {
        return ModelUtils.interpretYangOutput(SimpleYangNet.BALL_STATE_PREDICTOR.forward(ModelUtils.encodeBall(ballData)));
    }

    private static float[] combine(float[] one, float[] two){
        float[] alloc = new float[one.length + two.length];
        System.arraycopy(one, 0, alloc, 0, one.length);
        System.arraycopy(two, 0, alloc, one.length, two.length);
        return alloc;
    }

    public static float getImportanceOfCar(CarData c, ImmutableBallData b){
        var ballEnc = ModelUtils.encodeBall(b);
        var carEnc = ModelUtils.encodeCar(c, b);
        return DeciderYangNet.GAME_STATE_PREDICTOR.deciderForward(ModelUtils.combine(carEnc, ballEnc))[0];
    }

    public static float gameStateToPrediction(GameData g, boolean allTeammates, boolean allEnemies){
        var myTeam = GameData.current().getCarData().team;
        var ballEnc = ModelUtils.encodeBall(g.getBallData());
        Map<Integer, float[]> carData = new HashMap<>();
        g.getAllCars().forEach(c -> carData.put(c.playerIndex, ModelUtils.encodeCar(c, g.getBallData())));
        var carImportances = g.getAllCars().stream()
                .filter(c -> !c.isDemolished)
                .map(c -> {
                    var data = ModelUtils.combine(carData.get(c.playerIndex), ballEnc);
                    float importance = DeciderYangNet.GAME_STATE_PREDICTOR.deciderForward(data)[0];
                    return new Tuple<>(c, importance);
                })
                .collect(Collectors.toList());
        var t0 = carImportances.stream()
                .filter(c -> c.getKey().team == 0 && !c.getKey().isDemolished)
                .sorted(Collections.reverseOrder(Comparator.comparingDouble(Tuple::getValue)))
                .map(Tuple::getKey).limit((myTeam == 0 ? allTeammates : allEnemies) ? 3 : 1).collect(Collectors.toList());
        var t1 = carImportances.stream()
                .filter(c -> c.getKey().team == 1 && !c.getKey().isDemolished)
                .sorted(Collections.reverseOrder(Comparator.comparingDouble(Tuple::getValue)))
                .map(Tuple::getKey).limit((myTeam == 1 ? allTeammates : allEnemies) ? 3 : 1).collect(Collectors.toList());

        if(t0.isEmpty() || t1.isEmpty()){
            assert !t0.isEmpty() || !t1.isEmpty();
            int team = t0.isEmpty() ? -1 : 1;
            var sample = new CarData(new Vector3(0, team * -(RLConstants.goalDistance + 200), RLConstants.carElevation), new Vector3(), new Vector3(), Matrix3x3.lookAt(new Vector3(1, 0, 0)));
            sample.playerIndex = -999;
            carData.put(-999, ModelUtils.encodeCar(sample, g.getBallData()));
            if(t0.isEmpty())
                t0 = List.of(sample);
            else
                t1 = List.of(sample);
        }
        Function<Tuple<Integer, Integer>, Float> predictOne = tuple -> {
            var data = ModelUtils.combine(ballEnc, carData.get(tuple.getKey()));
            data = ModelUtils.combine(data, carData.get(tuple.getValue()));
            return DeciderYangNet.GAME_STATE_PREDICTOR.forward(data)[0];
        };

        float val = 0;
        int numVal = 0;
        for(var e0 : t0) {
            for (var e1 : t1) {
                val += predictOne.apply(new Tuple<>(e0.playerIndex, e1.playerIndex));
                numVal++;
            }
        }

        assert numVal > 0;

        return val / numVal;
    }

    public static void preloadAllModels() {
        SimpleYangNet.BALL_STATE_PREDICTOR.toString();
        DeciderYangNet.GAME_STATE_PREDICTOR.toString();
    }

}
