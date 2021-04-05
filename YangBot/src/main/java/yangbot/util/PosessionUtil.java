package yangbot.util;

import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.GameData;
import yangbot.util.math.MathUtils;

import java.util.Optional;

public class PosessionUtil {

    // positive = we have possession
    public static Optional<Float> getPossession(GameData gameData, YangBallPrediction ballPrediction) {

        final MutableTuple<Float, CarData> ourTimeToBall = new MutableTuple<>(999f, null);

        // calculate possession for our team
        gameData.getAllCars().stream()
                .filter(c -> c.team == gameData.getCarData().team)
                .filter(c -> c.getPlayerInfo().isActiveShooter())
                .forEach(c -> {
                    float time = timeToBall(c, ballPrediction);
                    if (time < ourTimeToBall.getKey()) {
                        ourTimeToBall.set(time, c);
                    }
                });

        if (ourTimeToBall.getValue() == null || ourTimeToBall.getValue().playerIndex != gameData.getCarData().playerIndex)
            return Optional.empty(); // mate has possession

        final MutableTuple<Float, CarData> theirTimeToBall = new MutableTuple<>(999f, null);

        gameData.getAllCars().stream()
                .filter(c -> c.team != gameData.getCarData().team)
                .forEach(c -> {
                    float time = timeToBall(c, ballPrediction);
                    if (time < theirTimeToBall.getKey()) {
                        theirTimeToBall.set(time, c);
                    }
                });

        if (theirTimeToBall.getValue() == null)
            return Optional.of(5f); // they can't reach the ball

        return Optional.of(theirTimeToBall.getKey() - ourTimeToBall.getKey());
    }

    public static float timeToBall(CarData car, YangBallPrediction ballPrediction) {
        float dt = 0.05f;
        float t = dt;
        float distDriven = 0;
        var testCar = car.toCar2D();
        for (; t < Math.min(4, ballPrediction.relativeTimeOfLastFrame()); t += dt) {
            var current = ballPrediction.getFrameAtRelativeTime(t);
            if (current.isEmpty())
                break;
            var ball = current.get().ballData;

            distDriven += testCar.simulateDriveTimeForward(dt, true);
            //if(ball.position.z > DriveDodgeStrikeAbstraction.MAX_STRIKE_HEIGHT)
            //    continue;

            float dist = (float) ball.position.flatten().distance(car.position.flatten());
            dist -= BallData.COLLISION_RADIUS;
            dist -= car.hitbox.getForwardExtent();
            dist -= 20; // magic

            if (dist <= distDriven)
                break;
        }

        if (t >= ballPrediction.relativeTimeOfLastFrame() - 0.5f || t >= 3.8f)
            return ballPrediction.relativeTimeOfLastFrame() + 2;

        var targetBallFrame = ballPrediction.getFrameAtRelativeTime(t).get();
        float turnTime = MathUtils.remapClip(
                (float) Math.abs(car.forward().flatten().correctionAngle(targetBallFrame.ballData.position.flatten().sub(car.position.flatten()))),
                0, (float) Math.PI,
                0, 1.38f
        );

        return t + turnTime;
    }


}
