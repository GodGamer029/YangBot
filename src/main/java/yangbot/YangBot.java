package yangbot;

import rlbot.Bot;
import rlbot.ControllerState;
import rlbot.flat.GameTickPacket;
import yangbot.input.BallData;
import yangbot.input.CarData;
import yangbot.input.DataPacket;
import yangbot.input.GameData;
import yangbot.input.fieldinfo.BoostManager;
import yangbot.manuever.LowGravKickoffManuver;
import yangbot.util.AdvancedRenderer;
import yangbot.util.ControlsOutput;

import java.awt.*;

public class YangBot implements Bot {

    enum State {
        RESET,
        INIT,
        RUN,
        KICKOFF
    }

    private final int playerIndex;

    private State state = State.RESET;
    private float timer = -1.0f;
    private float lastTick = -1;

    private LowGravKickoffManuver kickoffManuver = null;

    public YangBot(int playerIndex) {
        this.playerIndex = playerIndex;
    }

    private ControlsOutput processInput(DataPacket input) {
        float dt = Math.max(input.gameInfo.secondsElapsed() - lastTick, 1/60f);

        if(lastTick > 0)
            timer += Math.min(dt, 0.5f);

        CarData car = input.car;
        BallData ball = input.ball;
        GameData.current().update(input.car, input.ball, input.allCars, input.gameInfo);

        drawDebugLines(input, car);
        AdvancedRenderer renderer = AdvancedRenderer.forBotLoop(this);
        ControlsOutput output = new ControlsOutput();

        switch(state){
            case RESET:
                timer = 0.0f;
                if(ball.velocity.isZero())
                    state = State.KICKOFF;
                else
                    state = State.INIT;
                kickoffManuver = new LowGravKickoffManuver();
                break;
            case KICKOFF:
                kickoffManuver.step(dt, output);
                if(kickoffManuver.isDone())
                    state = State.INIT;
                break;
            case INIT:
            {
                if(input.gameInfo.isKickoffPause())
                    state = State.RESET;

                break;
            }
            case RUN:
            {
                break;
            }
        }

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
