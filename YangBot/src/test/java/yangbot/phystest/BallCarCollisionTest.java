package yangbot.phystest;

import org.junit.jupiter.api.Test;
import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.ControlsOutput;
import yangbot.input.RLConstants;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector3;

public class BallCarCollisionTest {

    @Test
    public void test1() {
        BallData ball = new BallData(new Vector3(-58.119998931884766, -119.63999938964844, 1499.8599853515625), new Vector3(907.5209350585938, 1871.260986328125, -288.7510070800781), new Vector3(-3.2726099491119385, 3.9519100189208984, 3.109910011291504));
        CarData carData = new CarData(new Vector3(-40.90999984741211, 19.399999618530273, 1437.6300048828125), new Vector3(-493.031005859375, -6.070999622344971, -255.79100036621094),
                new Vector3(1.3209099769592285, -3.977409839630127, 3.561509847640991), Matrix3x3.eulerToRotation(new Vector3(-0.8501129746437073, -2.1748013496398926, -0.17487381398677826)));

        YangBallPrediction ballPred = null;
        {
            BallData simBall = new BallData(ball);
            CarData simCar = new CarData(carData);
            for (int i = 0; i < 5; i++) {
                final Vector3 contactPoint = simCar.hitbox.getClosestPointOnHitbox(simCar.position, simBall.position);
                System.out.println(simBall.collidesWith(simCar) + " dist: " + contactPoint.sub(simBall.position).magnitude());

                simBall.stepWithCollide(RLConstants.tickFrequency, simCar);
                simCar.step(new ControlsOutput(), RLConstants.tickFrequency);

                System.out.println(simBall.toString());
            }
            ballPred = simBall.makeBallPrediction(RLConstants.tickFrequency, 0.5f);
        }

        /*
        YangBallPrediction finalBallPred = ballPred;
        ScenarioLoader.loadScenario(new Scenario.Builder()
                .withGameState(new GameState()
                .withBallState(new BallState().withPhysics(
                    new PhysicsState()
                            .withLocation(ball.position.toDesiredVector())
                            .withVelocity(ball.velocity.toDesiredVector())
                            .withAngularVelocity(ball.angularVelocity.toDesiredVector())
                    )).withCarState(0, new CarState().withPhysics(new PhysicsState()
                            .withLocation(carData.position.toDesiredVector())
                            .withRotation(carData.orientation.toEuler().toDesiredRotation())
                            .withVelocity(carData.velocity.toDesiredVector())
                            .withAngularVelocity(carData.angularVelocity.toDesiredVector())
                        ))
                .withGameInfoState(new GameInfoState().withWorldGravityZ(-0.0001f).withPaused(false).withGameSpeed(0.01f))
                )
                .withRun((a, b) -> {
                    System.out.println(GameData.current().getBallData().velocity);
                    if(b > 0.3f)
                        return Scenario.RunState.RESET;

                    var red = GameData.current().getAdvancedRenderer();
                    finalBallPred.draw(red, Color.MAGENTA, 1);
                    GameData.current().getBallPrediction().draw(red, Color.BLUE, 1);

                    return Scenario.RunState.CONTINUE;
                })
                .build());
        assert ScenarioLoader.get().waitToCompletion(4000);*/
    }

}
