package yangbot;

import rlbot.Bot;
import rlbot.ControllerState;
import rlbot.cppinterop.RLBotDll;
import rlbot.flat.BallPrediction;
import rlbot.flat.GameTickPacket;
import rlbot.flat.PredictionSlice;
import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.DataPacket;
import yangbot.input.GameData;
import yangbot.input.fieldinfo.BoostManager;
import yangbot.input.fieldinfo.BoostPad;
import yangbot.manuever.AerialManuver;
import yangbot.manuever.LowGravKickoffManuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.ControlsOutput;
import yangbot.util.MathUtils;
import yangbot.vector.Matrix3x3;
import yangbot.vector.Vector3;

import java.awt.*;

public class YangBot implements Bot {

    public static AdvancedRenderer renderer = null;

    enum State {
        RESET,
        INIT,
        RUN,
        KICKOFF,
        WAIT
    }

    private final int playerIndex;

    private State state = State.RESET;
    private float timer = -1.0f;
    private float lastTick = -1;

    private LowGravKickoffManuver kickoffManuver = null;
    private AerialManuver aerialManuver = null;

    private boolean headingOut = false;

    public YangBot(int playerIndex) {
        this.playerIndex = playerIndex;
    }

    private ControlsOutput processInput(DataPacket input) {
        if(lastTick > 0)
            timer += Math.min(input.gameInfo.secondsElapsed() - lastTick, 0.5f);
        float dt = Math.max(input.gameInfo.secondsElapsed() - lastTick, 1/60f);

        CarData car = input.car;
        BallData ball = input.ball;
        GameData.current().update(input.car, input.ball, input.allCars, input.gameInfo);

        drawDebugLines(input, car);
        AdvancedRenderer renderer = AdvancedRenderer.forBotLoop(this);
        ControlsOutput output = new ControlsOutput();

        State nextState = state;

        switch(state){
            case RESET:
                timer = 0.0f;
                if(ball.velocity.isZero())
                    nextState = State.KICKOFF;
                else
                    nextState = State.INIT;
                kickoffManuver = new LowGravKickoffManuver();
                break;
            case KICKOFF:
                kickoffManuver.step(dt, output);
                if(kickoffManuver.isDone())
                    nextState = State.INIT;
                break;
            case WAIT:
                if(timer >= 0.5f)
                    nextState = State.INIT;
                break;
            case INIT:
            {
                if(input.gameInfo.isKickoffPause())
                    nextState = State.RESET;

                Vector3 targetGoal = new Vector3(0, -Math.signum(car.team - 0.5f) * 2900, 400);
                Vector3 ownGoal = new Vector3(0, Math.signum(car.team - 0.5f) * 2900, 400);
                float carToGoalDist = (float) car.position.add(car.velocity.mul(0.2f)).distance(targetGoal);
                float ballToGoalDist = (float) ball.position.add(ball.velocity.mul(0.2f)).distance(targetGoal);
                aerialManuver = new AerialManuver();

                headingOut = false;
                // Heading out
                if(carToGoalDist + 200 < ballToGoalDist && ball.velocity.magnitude() > 700 && Math.abs(car.position.y - ownGoal.y) > 800){
                    headingOut = true;
                    System.out.println("Aight ima head out");
                    float closest = 10000000;
                    Vector3 closeer = null;
                    for(float tim = 0; tim < 8; tim += 1/15f){
                        aerialManuver.arrivalTime = tim + car.elapsedSeconds;
                        //Math.signum(car.team - 0.5f) *
                        aerialManuver.target = new Vector3(0, MathUtils.clip(ball.position.y + (ball.position.y - car.position.y) + ball.velocity.y * 0.5f, -3000, 3000), 650);
                        aerialManuver.target_orientation = Matrix3x3.lookAt(new Vector3(0, 0, -1), new Vector3(0, Math.signum(-aerialManuver.target.y), 0));
                        CarData simulation = aerialManuver.simulate(car);
                        float dist = (float)simulation.position.distance(aerialManuver.target);
                        if(dist < closest){
                            closest = dist;
                            closeer = simulation.position;
                        }
                        if(dist <= 50){
                            aerialManuver.arrivalTime += 0.3f;
                            nextState = State.RUN;
                            break;
                        }
                    }
                    if(closeer != null){
                        renderer.drawCentered3dCube(Color.red, closeer, 500);
                        renderer.drawCentered3dCube(Color.yellow, aerialManuver.target, 100);
                    }
                }else{
                    // Not heading out
                    try{
                        BallPrediction prediction = RLBotDll.getBallPrediction();
                        float closest = 1000000;
                        Vector3 closeer = null;
                        for(int i = 5; i < prediction.slicesLength(); i += 3){
                            PredictionSlice slice = prediction.slices(i);

                            Vector3 pos = new Vector3(slice.physics().location());
                            aerialManuver.arrivalTime = slice.gameSeconds();
                            aerialManuver.target = pos.add(targetGoal.sub(pos).normalized().mul(-85f));
                            aerialManuver.target_orientation = Matrix3x3.lookAt(car.position.sub(pos).normalized(), new Vector3(0, 0, 1));

                            CarData simulation = aerialManuver.simulate(car);
                            float dist = (float)simulation.position.distance(aerialManuver.target);
                            if(dist < closest){
                                closest = dist;
                                closeer = simulation.position;
                            }
                            if(dist <= 50){
                                nextState = State.RUN;
                                break;
                            }
                        }

                        if(closeer != null){
                            renderer.drawCentered3dCube(Color.red, closeer, 500);
                            renderer.drawCentered3dCube(Color.yellow, aerialManuver.target, 100);
                        }
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }

                break;
            }
            case RUN:
                //aerialManuver.target_orientation = Matrix3x3.lookAt(aerialManuver.target, new Vector3(0, 0, 1));
            {
                CarData simulation = aerialManuver.simulate(car);
                float dist = (float)simulation.position.distance(aerialManuver.target);
                if(dist >= 100){
                    nextState = State.INIT;
                    break;
                }
            }


                if(!headingOut){
                    try{
                        Vector3 targetGoal = new Vector3(0, -Math.signum(car.team - 0.5f) * 2900, 400);
                        BallPrediction prediction = RLBotDll.getBallPrediction();
                        boolean foundIt = false;
                        for(int i = 0; i < prediction.slicesLength(); i ++) {
                            Vector3 pos = new Vector3(prediction.slices(i).physics().location());
                            pos = pos.add(targetGoal.sub(pos).normalized().mul(-85f));
                            float dist = (float) aerialManuver.target.distance(pos);
                            if(dist <= 30 && Math.abs(prediction.slices(i).gameSeconds() - aerialManuver.arrivalTime) <= 0.3f){
                                if(dist <= 20 && Math.abs(prediction.slices(i).gameSeconds() - aerialManuver.arrivalTime) <= 0.15f ){
                                   // aerialManuver.target = pos;
                                    //aerialManuver.arrivalTime = prediction.slices(i).gameSeconds();
                                }
                                foundIt = true;
                                break;
                            }
                        }
                        if(!foundIt)
                            nextState = State.INIT;
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }else{
                    Vector3 ownGoal = new Vector3(0, Math.signum(car.team - 0.5f) * 2900, 400);

                    if(Math.abs(car.position.y - ownGoal.y) < 800)
                        nextState = State.INIT;
                }
                aerialManuver.step(dt, output);

                if(aerialManuver.isDone() || aerialManuver.arrivalTime <= car.elapsedSeconds || (car.hasWheelContact && !output.holdJump())){
                    nextState = State.INIT;
                    break;
                }

                renderer.drawCentered3dCube(Color.green, aerialManuver.target, 50);
                renderer.drawString2d(String.format("Time left: %.1f", aerialManuver.arrivalTime - car.elapsedSeconds), Color.white, new Point(10, 560), 1, 1);
                renderer.drawString2d(String.format("Heading Out: %s", headingOut ? "true" : "false"), Color.white, new Point(10, 580), 1, 1);

                break;
        }

        state = nextState;

        // Print Throttle info
        {
            renderer.drawString2d("State: "+state.name(), Color.WHITE, new Point(10, 270), 2, 2);
            renderer.drawString2d(String.format("Yaw: %.1f", output.getYaw()), Color.WHITE, new Point(10, 310), 1, 1);
            renderer.drawString2d(String.format("Pitch: %.1f", output.getPitch()), Color.WHITE, new Point(10, 330), 1, 1);
            renderer.drawString2d(String.format("Roll: %.1f", output.getRoll()), Color.WHITE, new Point(10, 350), 1, 1);
            renderer.drawString2d(String.format("Steer: %.2f", output.getSteer()), Color.WHITE, new Point(10, 370), 1, 1);
            renderer.drawString2d(String.format("Throttle: %.2f", output.getThrottle()), Color.WHITE, new Point(10, 390), 1, 1);
        }

        return output;
    }

    /**
     * This is a nice example of using the rendering feature.
     */
    private void drawDebugLines(DataPacket input, CarData myCar) {
        // Here's an example of rendering debug data on the screen.
        AdvancedRenderer renderer = AdvancedRenderer.forBotLoop(this);
        YangBot.renderer = renderer;
        for(BoostPad boost : BoostManager.getAllBoosts()){
            if(!boost.isActive())
                renderer.drawString3d("Active in "+boost.boostAvailableIn(), Color.CYAN, boost.getLocation(), 1, 1);
        }

        renderer.drawString2d("BallP: "+input.ball.position, Color.WHITE, new Point(10, 150), 1, 1);
        renderer.drawString2d("BallV: "+input.ball.velocity, Color.WHITE, new Point(10, 170), 1, 1);
        renderer.drawString2d("Car: "+myCar.position, Color.WHITE, new Point(10, 190), 1, 1);
        renderer.drawString2d(String.format("CarSpeedXY: %.1f", myCar.velocity.flatten().magnitude()), Color.WHITE, new Point(10, 210), 1, 1);
        renderer.drawString2d("Ang: "+myCar.angularVelocity, Color.WHITE, new Point(10, 230), 1, 1);
        renderer.drawString2d("Nose: "+myCar.forward(), Color.WHITE, new Point(10, 250), 1, 1);
    }


    @Override
    public int getIndex() {
        return this.playerIndex;
    }

    /**
     * This is the most important function. It will automatically get called by the framework with fresh data
     * every frame. Respond with appropriate controls!
     */
    public float all = 0;
    public int count = 0;
    public int realCount = 0;

    @Override
    public ControllerState processInput(GameTickPacket packet) {


        if (packet.playersLength() <= playerIndex || packet.ball() == null || !packet.gameInfo().isRoundActive()) {
            // Just return immediately if something looks wrong with the data. This helps us avoid stack traces.
            return new ControlsOutput();
        }
        AdvancedRenderer r = AdvancedRenderer.forBotLoop(this);
        r.startPacket();

        // Update the boost manager and tile manager with the latest data
        BoostManager.loadGameTickPacket(packet);

        // Translate the raw packet data (which is in an unpleasant format) into our custom DataPacket class.
        // The DataPacket might not include everything from GameTickPacket, so improve it if you need to!
        DataPacket dataPacket = new DataPacket(packet, playerIndex);

        // Do the actual logic using our dataPacket.

        long ms = System.nanoTime();
        ControlsOutput controlsOutput = processInput(dataPacket);
        realCount++;
        if(realCount >= 100){
            //all += ((System.nanoTime() - ms) / 1000000f);
            count++;
            //System.out.println("It took "+(all / count));
        }

        lastTick = dataPacket.gameInfo.secondsElapsed();

        r.finishAndSendIfDifferent();
        return controlsOutput;
    }

    public void retire() {
        System.out.println("Retiring sample bot " + playerIndex);
    }
}
