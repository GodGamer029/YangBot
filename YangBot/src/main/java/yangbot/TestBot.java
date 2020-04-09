package yangbot;

import javafx.util.Pair;
import rlbot.Bot;
import rlbot.ControllerState;
import rlbot.flat.GameTickPacket;
import yangbot.input.*;
import yangbot.input.fieldinfo.BoostManager;
import yangbot.path.Curve;
import yangbot.path.Navigator;
import yangbot.strategy.manuever.DodgeManeuver;
import yangbot.strategy.manuever.FollowPathManeuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.YangBallPrediction;
import yangbot.util.math.MathUtils;
import yangbot.util.math.vector.Vector2;
import yangbot.util.math.vector.Vector3;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class TestBot implements Bot {

    private final int playerIndex;

    private State state = State.RESET;
    private float timer = -1.0f;
    private float lastTick = -1;
    private boolean hasSetPriority = false;

    private DodgeManeuver simDodge;
    private float lastSpeed = 0;
    private FollowPathManeuver pathManeuver;
    private Curve curve;
    private Navigator nav;

    public TestBot(int playerIndex) {
        this.playerIndex = playerIndex;
    }

    private ControlsOutput processInput(DataPacket input) {
        float dt = Math.max(input.gameInfo.secondsElapsed() - lastTick, RLConstants.tickFrequency);

        if (lastTick > 0)
            timer += Math.min(dt, 0.1f);

        AdvancedRenderer renderer = AdvancedRenderer.forBotLoop(this);
        CarData car = input.car;
        BallData ball = input.ball;
        GameData.current().update(input.car, new ImmutableBallData(input.ball), input.allCars, input.gameInfo, dt, renderer);

        GameData.current().getBallPrediction().draw(renderer, Color.RED, 3);
        CarData controlCar = input.allCars.stream().filter((c) -> c.team != car.team).findFirst().orElse(car);

        drawDebugLines(input, controlCar);
        final int teamSign = controlCar.team * 2 - 1;
        final Vector2 enemyGoal = new Vector2(0, -teamSign * (RLConstants.goalDistance + 1000));
        final YangBallPrediction ballPrediction = YangBallPrediction.get();
        ControlsOutput output = new ControlsOutput();

        switch (state) {
            case RESET: {
                timer = 0.0f;
                nav = new Navigator(Navigator.PathAlgorithm.ASTAR);
                state = State.INIT;
                pathManeuver = new FollowPathManeuver();
               /* RLBotDll.setGameState(new GameState()
                        .withGameInfoState(new GameInfoState().withWorldGravityZ(0.00001f).withGameSpeed(1f))
                        .withBallState(new BallState().withPhysics(new PhysicsState()
                                        .withLocation(new DesiredVector3(133.2f, 10f, 95f)) // 320
                                        .withVelocity(new DesiredVector3(20f, 0f, -1f))
                                        .withAngularVelocity(new DesiredVector3(0f, 0f, 0f))
                                )
                        )
                        .withCarState(this.playerIndex, new CarState().withPhysics(new PhysicsState()
                                .withLocation(new DesiredVector3(0f, 0f, 370f))
                                .withRotation(new DesiredRotation(0f, (float) Math.PI / 2f, (float) Math.PI / -2f))
                                .withVelocity(new DesiredVector3(0f, 0f, -1f))
                                .withAngularVelocity(new DesiredVector3(0f, 0f, 0f))
                        )).buildPacket());
                RLConstants.gravity = new Vector3(0, 0, 0.00001f);*/
                break;
            }
            case INIT: {
                timer = 2;

                /*if(Navigator.isLoaded())
                    this.state = State.RUN;*/

                double dist = ball.position.flatten().sub(controlCar.position.flatten()).normalized().dot(controlCar.velocity.flatten());
                renderer.drawString2d(String.format("Dist: %.1f", dist), Color.WHITE, new Point(300, 300), 2, 2);

                break;
            }
            case RUN: {

                if (timer > 0.2f) {
                    nav.analyzeSurroundings(controlCar);
                    timer = 0;
                }

                /*int firstNodeDir = nav.findClosestNode(new Vector3(0, 1800, 0), new Vector3(1, 1, 0).normalized()).getKey();
                int secondNodeDir = nav.findClosestNode(new Vector3(300, 2100, 0), new Vector3(1, 1, 0).normalized()).getKey();

                int firstNode = firstNodeDir / Navigator.numDirectionDistinctions;
                int secondNode = secondNodeDir / Navigator.numDirectionDistinctions;

                {
                    int begin = Navigator.navigationGraph.offsets[firstNodeDir];
                    int end = Navigator.navigationGraph.offsets[firstNodeDir + 1];
                    float weight = -1;

                    for (int j = begin; j < end; j++) {
                        if(Navigator.navigationGraph.destinations[j] == secondNodeDir){
                            weight = Navigator.navigationGraph.weights[j];
                            break;
                        }
                    }

                    renderer.drawCentered3dCube(Color.YELLOW, Navigator.navigationNodes[firstNode], 50);
                    renderer.drawLine3d(new Color(1, 1, 1), Navigator.navigationNodes[firstNode], Navigator.navigationNodes[firstNode].add(Navigator.navigationNormals[firstNode].mul(100)));
                    renderer.drawLine3d(Color.BLUE, Navigator.navigationNodes[firstNode], Navigator.navigationNodes[firstNode].add(Navigator.navigationTangents[firstNodeDir].mul(100)));

                    renderer.drawCentered3dCube(Color.YELLOW, Navigator.navigationNodes[secondNode], 50);
                    renderer.drawLine3d(new Color(1, 1, 1), Navigator.navigationNodes[secondNode], Navigator.navigationNodes[secondNode].add(Navigator.navigationNormals[secondNode].mul(100)));
                    renderer.drawLine3d(Color.BLUE, Navigator.navigationNodes[secondNode], Navigator.navigationNodes[secondNode].add(Navigator.navigationTangents[secondNodeDir].mul(100)));


                    renderer.drawString3d(String.format("%.2f\nDist:%.1f", weight, Navigator.navigationNodes[firstNode].distance(Navigator.navigationNodes[secondNode])), Color.WHITE, MathUtils.lerp(Navigator.navigationNodes[firstNode], Navigator.navigationNodes[secondNode], 0.5f), 1, 1);
                }*/

                /*List<Pair<Integer, Vector3>> nodes = nav.findClosestNodes(controlCar.position, 20);
                for(var pos : nodes){

                    renderer.drawCentered3dCube(Color.YELLOW, pos.getValue(), 50);
                    renderer.drawLine3d(new Color(1, 1, 1), pos.getValue(), pos.getValue().add(Navigator.navigationNormals[pos.getKey()].mul(100)));
                }*/


                Vector3 destination = ballPrediction.getFrameAtRelativeTime((float) Math.min(2, car.position.sub(ball.position).magnitude() / Math.max(1, car.velocity.magnitude()))).get().ballData.position;
                ;
                Vector3 destTangent = enemyGoal.withZ(0).sub(destination).normalized();
                {

                    int destinationNode = -1;
                    float minimum = 1000000.0f;
                    for (int i = 0; i < Navigator.navigationNodes.length; i++) {
                        float distance = (float) destination.sub(Navigator.navigationNodes[i]).magnitude();
                        if (distance < minimum) {
                            destinationNode = i;
                            minimum = distance;
                        }
                    }

                    int destination_direction = -1;
                    float maximum_alignment = -2.0f;
                    for (int j = 0; j < Navigator.numDirectionDistinctions; j++) {
                        float alignment = (float) destTangent.dot(Navigator.navigationTangents[destinationNode * Navigator.numDirectionDistinctions + j]);
                        if (alignment > maximum_alignment) {
                            destination_direction = j;
                            maximum_alignment = alignment;
                        }
                    }

                    int source_id = nav.source_node * Navigator.numDirectionDistinctions + nav.source_direction;
                    int dest_id = destinationNode * Navigator.numDirectionDistinctions + destination_direction;

                    if (nav.pathAlgorithm == Navigator.PathAlgorithm.ASTAR) {
                        long ms = System.nanoTime();
                        nav.navigation_paths = Navigator.navigationGraph.astar_sssp(source_id, dest_id, 8F);
                        System.out.println("Navigator: astar_sssp took " + ((System.nanoTime() - ms) * 0.000001f) + "ms");
                    }

                    Vector3 p = Navigator.navigationNodes[destinationNode];
                    Vector3 t = Navigator.navigationTangents[dest_id];
                    Vector3 n = Navigator.navigationNormals[dest_id / Navigator.numDirectionDistinctions];

                    destination = destination.sub(n.mul(destination.sub(p).dot(n)));
                    List<Pair<Integer, Curve.ControlPoint>> ctrl_pts = new ArrayList<>();

                    ctrl_pts.add(new Pair<>(dest_id, new Curve.ControlPoint(p, t, n)));

                    for (int i = 0; i < 64; i++) {

                        // find the navigation node and tangent that brings me here
                        int old_dest_id = dest_id;
                        dest_id = nav.navigation_paths[dest_id];

                        // if it exists, add another control point to the path
                        if (dest_id != -1) {

                            if (dest_id / Navigator.numDirectionDistinctions == old_dest_id / Navigator.numDirectionDistinctions)
                                break;

                            p = Navigator.navigationNodes[dest_id / Navigator.numDirectionDistinctions];
                            t = Navigator.navigationTangents[dest_id];
                            n = Navigator.navigationNormals[dest_id / Navigator.numDirectionDistinctions];

                            ctrl_pts.add(new Pair<>(dest_id, new Curve.ControlPoint(p, t, n)));

                            // if we reach the navigation node for the car,
                            // handle that case differently, and exit the loop
                            if (dest_id == source_id) break;

                            // otherwise, the path is unreachable
                        } else {
                            //System.out.println("Negative path "+dest_id+" "+i);
                            break;
                        }
                    }

                    if (ctrl_pts.size() > 1) {
                        AdvancedRenderer coolBoi = new AdvancedRenderer(540);
                        coolBoi.startPacket();
                        Collections.reverse(ctrl_pts);
                        Vector3 dx1 = nav.source.sub(ctrl_pts.get(0).getValue().point);
                        Vector3 dt1 = controlCar.forward().sub(ctrl_pts.get(0).getValue().tangent);

                        Vector3 dx2 = destination.sub(ctrl_pts.get(ctrl_pts.size() - 1).getValue().point);
                        Vector3 dt2 = destTangent.sub(ctrl_pts.get(ctrl_pts.size() - 1).getValue().tangent);

                        //Curve curv = new Curve(ctrl_pts, dx1, dt1, dx2, dt2, nav.source, destination);
                        float total = 0;
                        for (int i = 0; i < ctrl_pts.size() - 1; i++) {
                            renderer.drawLine3d(Color.YELLOW, ctrl_pts.get(i).getValue().point, ctrl_pts.get(i + 1).getValue().point);
                            renderer.drawLine3d(Color.RED, ctrl_pts.get(i).getValue().point, ctrl_pts.get(i).getValue().point.add(ctrl_pts.get(i).getValue().normal.mul(50)));
                            renderer.drawLine3d(Color.BLUE, ctrl_pts.get(i).getValue().point.add(ctrl_pts.get(i).getValue().normal.mul(50)), ctrl_pts.get(i).getValue().point.add(ctrl_pts.get(i).getValue().normal.mul(50)).add(ctrl_pts.get(i).getValue().tangent.mul(100)));

                            // Find edge
                            int begin = Navigator.navigationGraph.offsets[ctrl_pts.get(i).getKey()];
                            int end = Navigator.navigationGraph.offsets[ctrl_pts.get(i).getKey() + 1];
                            float weight = -1;

                            for (int j = begin; j < end; j++) {
                                if (Navigator.navigationGraph.destinations[j] == ctrl_pts.get(i + 1).getKey()) {
                                    weight = Navigator.navigationGraph.weights[j];
                                    break;
                                }
                            }
                            if (weight > 0)
                                total += weight;

                            List<Curve.ControlPoint> points = new ArrayList<>();
                            points.add(ctrl_pts.get(i).getValue());
                            points.add(ctrl_pts.get(i + 1).getValue());
                            Curve c = new Curve(points);
                            c.draw(coolBoi);

                            /*if(i == 0){
                                this.pathManeuver.path = c;
                                //this.pathManeuver.arrivalTime = car.elapsedSeconds + weight;
                                this.pathManeuver.step(dt, output);
                                this.pathManeuver.draw(renderer, car);
                            }*/

                            renderer.drawString3d(String.format(Locale.US, "%.2fs", weight), Color.WHITE, MathUtils.lerp(ctrl_pts.get(i).getValue().point.add(ctrl_pts.get(i).getValue().normal.mul(10)), ctrl_pts.get(i + 1).getValue().point.add(ctrl_pts.get(i + 1).getValue().normal.mul(10)), 0.5f).toRlbot(), 1, 1);
                        }
                        renderer.drawString2d(String.format(Locale.US, "Total: %.2fs", total), Color.WHITE, new Point(500, 200), 2, 2);

                        //curv.draw(coolBoi);
                        coolBoi.finishAndSendIfDifferent();
                    }
                }

                break;
            }
        }

        // Print Throttle info
        {
            renderer.drawString2d("State: " + state.name(), Color.WHITE, new Point(10, 270), 2, 2);
            renderer.drawString2d(String.format("Yaw: %.1f", output.getYaw()), Color.WHITE, new Point(10, 350), 1, 1);
            renderer.drawString2d(String.format("Pitch: %.1f", output.getPitch()), Color.WHITE, new Point(10, 370), 1, 1);
            renderer.drawString2d(String.format("Roll: %.1f", output.getRoll()), Color.WHITE, new Point(10, 390), 1, 1);
            renderer.drawString2d(String.format("Steer: %.2f", output.getSteer()), Color.WHITE, new Point(10, 410), 1, 1);
            renderer.drawString2d(String.format("Throttle: %.2f", output.getThrottle()), Color.WHITE, new Point(10, 430), 1, 1);
        }

        return output;
    }

    /**
     * This is a nice example of using the rendering feature.
     */
    private void drawDebugLines(DataPacket input, CarData myCar) {
        AdvancedRenderer renderer = AdvancedRenderer.forBotLoop(this);

        renderer.drawString2d("BallP: " + input.ball.position, Color.WHITE, new Point(10, 150), 1, 1);
        renderer.drawString2d("BallV: " + input.ball.velocity, Color.WHITE, new Point(10, 170), 1, 1);
        renderer.drawString2d("Car: " + myCar.position, Color.WHITE, new Point(10, 190), 1, 1);
        renderer.drawString2d(String.format("CarSpeedXY: %.1f", myCar.velocity.flatten().magnitude()), Color.WHITE, new Point(10, 210), 1, 1);
        renderer.drawString2d("Ang: " + myCar.angularVelocity, Color.WHITE, new Point(10, 230), 1, 1);
        //renderer.drawString2d("Nose: " + myCar.forward(), Color.WHITE, new Point(10, 250), 1, 1);
        //renderer.drawString2d("CarF: " + myCar.forward(), Color.WHITE, new Point(10, 250), 1, 1);
        float accel = (float) (myCar.velocity.dot(myCar.forward()) - lastSpeed);
        lastSpeed = (float) myCar.velocity.dot(myCar.forward());
        accel -= CarData.driveForceForward(new ControlsOutput().withThrottle(1), (float) myCar.velocity.dot(myCar.forward()), 0, 0);
        renderer.drawString2d(String.format("Accel: %.1f", accel), Color.WHITE, new Point(10, 250), 1, 1);

    }

    @Override
    public int getIndex() {
        return this.playerIndex;
    }

    @Override
    public ControllerState processInput(GameTickPacket packet) {
        if (!hasSetPriority) {
            hasSetPriority = true;
            Thread.currentThread().setPriority(10);
        }

        if (packet.playersLength() <= playerIndex || packet.ball() == null)
            return new ControlsOutput();

        if (!packet.gameInfo().isRoundActive()) {
            GameData.timeOfMatchStart = packet.gameInfo().secondsElapsed();
            return new ControlsOutput();
        }

        if (GameData.timeOfMatchStart < 0)
            GameData.timeOfMatchStart = packet.gameInfo().secondsElapsed();

        AdvancedRenderer r = AdvancedRenderer.forBotLoop(this);
        r.startPacket();

        BoostManager.loadGameTickPacket(packet);

        DataPacket dataPacket = new DataPacket(packet, playerIndex);

        ControlsOutput controlsOutput = processInput(dataPacket);

        lastTick = dataPacket.gameInfo.secondsElapsed();
        //lastGameTime = dataPacket.gameInfo.gameTimeRemaining();

        r.finishAndSendIfDifferent();
        return controlsOutput;
    }

    @Override
    public void retire() {
        System.out.println("Retiring Test bot " + playerIndex);
    }

    enum State {
        RESET,
        INIT,
        RUN
    }
}
