package yangbot.path;

import org.junit.jupiter.api.Test;
import rlbot.gamestate.CarState;
import rlbot.gamestate.GameInfoState;
import rlbot.gamestate.GameState;
import rlbot.gamestate.PhysicsState;
import yangbot.input.*;
import yangbot.path.builders.PathBuilder;
import yangbot.path.builders.PathSegment;
import yangbot.path.builders.SegmentedPath;
import yangbot.path.builders.segments.ArcLineArc;
import yangbot.path.builders.segments.StraightLineSegment;
import yangbot.path.builders.segments.TurnCircleSegment;
import yangbot.strategy.manuever.DriveManeuver;
import yangbot.util.CsvLogger;
import yangbot.util.Range;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Matrix2x2;
import yangbot.util.math.vector.Matrix3x3;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;
import yangbot.util.scenario.Scenario;
import yangbot.util.scenario.ScenarioLoader;

import java.awt.*;
import java.util.List;
import java.util.Locale;

public class SimplePathTests {

    private SegmentedPath path;

    private float testPath(SegmentedPath path) {
        return path.getTotalTimeEstimate();
    }

    private SegmentedPath makePath(float turnCircleSpeed, float startSpeed) {
        Vector3 startPos = new Vector3(0, 0, RLConstants.carElevation);
        Vector3 startTangent = new Vector3(1, 0, 0);
        // float startSpeed = 0;
        Vector3 endPos = new Vector3(0, 2000, RLConstants.carElevation);

        var builder = new PathBuilder(new Physics3D(startPos, startTangent.mul(startSpeed), Matrix3x3.lookAt(startTangent, new Vector3(0, 0, 1)), new Vector3()), 100);

        var neededTangent = endPos.sub(builder.getCurrentPosition()).flatten().normalized();
        float turnAngle = (float) Math.abs(builder.getCurrentTangent().flatten().angleBetween(neededTangent));
        /*if (builder.getCurrentSpeed() > 300 &&
                builder.getCurrentSpeed() < 1300 &&
                turnAngle < 90 * (Math.PI / 180) &&
                turnAngle > 30 * (Math.PI / 180) &&
                builder.getCurrentPosition().flatten().distance(endPos.flatten()) > 1400) {
            var drift = new DriftSegment(builder, endPos.sub(builder.getCurrentPosition()).normalized());
            builder.add(drift);
            neededTangent = endPos.sub(builder.getCurrentPosition()).flatten().normalized();
            turnAngle = (float) Math.abs(builder.getCurrentTangent().flatten().angleBetween(neededTangent));
        }*/

        boolean turnImpossible = false;
        if (turnAngle > 1 * (Math.PI / 180)) {
            var turn = new TurnCircleSegment(builder.getCurrentPhysics(), 1 / DriveManeuver.maxTurningCurvature(MathUtils.clip(turnCircleSpeed, 100, 2300)), endPos.flatten(), builder.getCurrentBoost(), true);
            if (turn.tangentPoint != null)
                builder.add(turn);
            else
                turnImpossible = true;
        }
        if (turnImpossible && builder.getCurrentTangent().dot(endPos.sub(builder.getCurrentPosition())) < 0.7f)
            throw new RuntimeException("Turn impossible");

        System.out.println("End speed: " + builder.getCurrentSpeed());
        builder.add(new StraightLineSegment(builder, endPos, 2300, -1, true));
        return builder.build();
    }

    @Test
    public void execSegment() {
        var startPos = new Vector2(1298.81, 2851.78);
        var startTangent = new Vector2(0.49, 0.87f).normalized();
        var startSpeed = 2299.51f;
        PathSegment seg = new TurnCircleSegment(new Physics2D(startPos, startTangent.mul(startSpeed), Matrix2x2.lookAt(startTangent), 0), 274.503f, new Vector2(2000, 3000), 100, true);

        Scenario s = new Scenario.Builder()
                .withTransitionDelay(0.08f)
                .withGameState(new GameState()
                        .withGameInfoState(new GameInfoState().withGameSpeed(0.1f))
                        .withCarState(0, new CarState().withBoostAmount(100f).withPhysics(new PhysicsState()
                                .withLocation(startPos.sub(startTangent.mul(startSpeed * 0.1f)).withZ(RLConstants.carElevation).toDesiredVector())
                                .withVelocity(startTangent.mul(startSpeed).withZ(0).toDesiredVector())
                                .withRotation(Matrix3x3.lookAt(startTangent.withZ(0)).toEuler().toDesiredRotation())
                                .withAngularVelocity(new Vector3().toDesiredVector())
                        )))
                .withInit(c -> {
                    path = new SegmentedPath(List.of(seg));
                    seg.getTimeEstimate();
                })
                .withRun((output, timer) -> {
                    final var gameData = GameData.current();
                    final var car = gameData.getCarData();
                    float dt = gameData.getDt();

                    var rend = gameData.getAdvancedRenderer();
                    if (!path.isDone())
                        path.step(dt, output);
                    path.draw(rend);

                    rend.drawString2d(String.format("Yaw: %.1f", output.getYaw()), Color.WHITE, new Point(10, 440), 1, 1);
                    rend.drawString2d(String.format("Pitch: %.1f", output.getPitch()), Color.WHITE, new Point(10, 460), 1, 1);
                    rend.drawString2d(String.format("Roll: %.1f", output.getRoll()), Color.WHITE, new Point(10, 480), 1, 1);
                    rend.drawString2d(String.format("Steer: %.2f", output.getSteer()), Color.WHITE, new Point(10, 500), 1, 1);
                    rend.drawString2d(String.format("Throttle: %.2f", output.getThrottle()), output.getThrottle() < 0 ? Color.RED : Color.WHITE, new Point(10, 520), 1, 1);

                    return timer > 2.5f ? Scenario.RunState.COMPLETE : Scenario.RunState.CONTINUE;
                })
                .withOnComplete((f) -> {

                })
                .build();

        ScenarioLoader.loadScenario(s);
        assert ScenarioLoader.get().waitToCompletion(4000);
    }

    @Test
    public void testTurnSegmentConvergence() {
        Range.of(100, 2000).stepBy(100).forEachRemaining(yVal -> {
            // float circleRadius, Vector2 endPos, float startBoost, boolean allowBoost
            TurnCircleSegment circleSegment = new TurnCircleSegment(new Physics2D(new Vector2(0, 0), new Vector2(0, 100), Matrix2x2.lookAt(new Vector2(0, 1)), 0),
                    1 / DriveManeuver.maxTurningCurvature(2200), new Vector2(50, yVal), 100, true);
            if (circleSegment.tangentPoint == null) {
                System.out.println("Could not construct turncircle for " + yVal);
                return;
            }
            circleSegment.getTimeEstimate();
        });
    }

    @Test
    public void testDrawSegment() {
        Scenario s = new Scenario.Builder()
                .withTransitionDelay(0.1f)
                .withGameState(new GameState()
                        .withGameInfoState(new GameInfoState().withGameSpeed(1f))
                        .withCarState(0, new CarState()))
                .withInit(c -> {

                })
                .withRun((output, timer) -> {
                    final var gameData = GameData.current();
                    final var car = gameData.getCarData();
                    float dt = gameData.getDt();

                    var rend = gameData.getAdvancedRenderer();

                    var startPos = new Vector2(1866.4, 1014.4);
                    var turnCircleStartPos = new Vector2(2110.6, 1615.2);
                    var circlePos = new Vector2(1742, 1765.1);
                    var tangentPoint = new Vector2(2137.3, 1810.7);
                    float ccw = -1;

                    var startDir = turnCircleStartPos.sub(circlePos).normalized();
                    var endDir = tangentPoint.sub(circlePos).normalized();

                    float startAngle = (float) startDir.angle();
                    float corrAng = (float) startDir.angleBetween(endDir);
                    if (Math.signum(-ccw) != Math.signum(corrAng))
                        corrAng = (float) (Math.signum(-ccw) * (Math.PI - Math.abs(corrAng) + Math.PI));
                    float endAngle = startAngle + corrAng;

                    rend.drawCircle(Color.YELLOW, circlePos.withZ(20), (float) circlePos.distance(tangentPoint), startAngle, endAngle);
                    rend.drawLine3d(Color.YELLOW, startPos.withZ(20), turnCircleStartPos.withZ(20));

                    return timer > 10f ? Scenario.RunState.COMPLETE : Scenario.RunState.CONTINUE;
                })
                .withOnComplete((f) -> {

                })
                .build();

        ScenarioLoader.loadScenario(s);
        assert ScenarioLoader.get().waitToCompletion(4000);
    }

    @Test
    public void testTurnSpeedTime() {
        Locale.setDefault(Locale.US);
        float startSpeed = 500;
        Range.of(100, 2300).stepWith(5).forEachRemaining(turnSpeed -> {

            var path = makePath(turnSpeed, startSpeed);
            float time = testPath(path);
            System.out.printf("StartSpeed: %f TurnSpeed: %f time: %.2f\n", startSpeed, turnSpeed, time);

        });

    }

    private SegmentedPath path2 = null;

    @Test
    public void testLut(){
        Scenario s = new Scenario.Builder()
                .withTransitionDelay(0.1f)
                .withGameState(new GameState()
                        .withGameInfoState(new GameInfoState().withGameSpeed(1f))
                        .withCarState(0, new CarState()))
                .withInit(c -> {

                })
                .withRun((output, timer) -> {
                    final var gameData = GameData.current();
                    final var car = gameData.getCarData();
                    float dt = gameData.getDt();

                    var rend = gameData.getAdvancedRenderer();

                    var startPos = new Vector2(1866.4, 1014.4);
                    var turnCircleStartPos = new Vector2(2110.6, 1615.2);
                    var circlePos = new Vector2(1742, 1765.1);
                    var tangentPoint = new Vector2(2137.3, 1810.7);
                    float ccw = -1;

                    var startDir = turnCircleStartPos.sub(circlePos).normalized();
                    var endDir = tangentPoint.sub(circlePos).normalized();

                    float startAngle = (float) startDir.angle();
                    float corrAng = (float) startDir.angleBetween(endDir);
                    if (Math.signum(-ccw) != Math.signum(corrAng))
                        corrAng = (float) (Math.signum(-ccw) * (Math.PI - Math.abs(corrAng) + Math.PI));
                    float endAngle = startAngle + corrAng;

                    rend.drawCircle(Color.YELLOW, circlePos.withZ(20), (float) circlePos.distance(tangentPoint), startAngle, endAngle);
                    rend.drawLine3d(Color.YELLOW, startPos.withZ(20), turnCircleStartPos.withZ(20));

                    return timer > 10f ? Scenario.RunState.COMPLETE : Scenario.RunState.CONTINUE;
                })
                .withOnComplete((f) -> {

                })
                .build();

        ScenarioLoader.loadScenario(s);
        assert ScenarioLoader.get().waitToCompletion(4000);
    }

    private float lastVel = 0;
    private CsvLogger logger;

    @Test
    public void testArcLineArc() {
        var startPos = new Vector2(1298.81, 2000.78);
        var startTangent = new Vector2(0.49, 0.87f).normalized();
        var startSpeed = 1800.51f;
        var endPos = new Vector2(0, -2000);
        var endTangent = new Vector2(1, 0).normalized();
        var seg = new ArcLineArc(new Physics2D(startPos, startTangent.mul(startSpeed), Matrix2x2.lookAt(startTangent), 0),
               100, endPos, endTangent, 10 + startSpeed * 0.25f, 1 / DriveManeuver.maxTurningCurvature(2300), 50, 1 / DriveManeuver.maxTurningCurvature(2300));
        seg.setArrivalSpeed(CarData.MAX_VELOCITY);

        Scenario s = new Scenario.Builder()
                .withTransitionDelay(0.08f)
                .withGameState(new GameState()
                        .withGameInfoState(new GameInfoState().withGameSpeed(0.2f))
                        .withCarState(0, new CarState().withBoostAmount(100f).withPhysics(new PhysicsState()
                                .withLocation(startPos.sub(startTangent.mul(startSpeed * 0.1f)).withZ(RLConstants.carElevation).toDesiredVector())
                                .withVelocity(startTangent.mul(startSpeed * 1.05f).withZ(0).toDesiredVector())
                                .withRotation(Matrix3x3.lookAt(startTangent.withZ(0)).toEuler().toDesiredRotation())
                                .withAngularVelocity(new Vector3().toDesiredVector())
                        )))
                .withInit(c -> {
                    path = new SegmentedPath(List.of(seg));
                    seg.getTimeEstimate();
                    logger = new CsvLogger(new String[]{"t", "accel", "pred"});
                })
                .withRun((output, timer) -> {
                    final var gameData = GameData.current();
                    final var car = gameData.getCarData();
                    float dt = gameData.getDt();

                    var rend = gameData.getAdvancedRenderer();
                    if (!path.isDone() && car.hasWheelContact)
                        path.step(dt, output);
                    else
                        return Scenario.RunState.COMPLETE;

                    if(path.isDone())
                        System.out.println("Dist to target: "+car.position.flatten().distance(endPos));

                    var cp = car.position.add(0, 0, 50);
                    rend.drawLine3d(Color.WHITE, cp, cp.add(car.forward().mul(100)));
                    rend.drawLine3d(Color.GREEN, cp, cp.add(car.velocity.normalized().mul(100)));

                    path.draw(rend);

                    rend.drawControlsOutput(output, 440);
                    return timer > 8 || path.isDone() ? Scenario.RunState.COMPLETE : Scenario.RunState.CONTINUE;
                })
                .withOnComplete((f) -> {
                    logger.save("..\\data\\turntime.csv");
                })
                .build();

        ScenarioLoader.loadScenario(s);
        assert ScenarioLoader.get().waitToCompletion(4000);
    }

}
