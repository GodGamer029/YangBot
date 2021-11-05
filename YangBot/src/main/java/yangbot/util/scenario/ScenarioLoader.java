package yangbot.util.scenario;

import rlbot.Bot;
import rlbot.manager.BotManager;
import rlbot.pyinterop.SocketServer;
import yangbot.MainClass;
import yangbot.ProxyBot;
import yangbot.input.ControlsOutput;
import yangbot.input.DataPacket;

import java.util.concurrent.atomic.AtomicBoolean;

public class ScenarioLoader extends SocketServer {

    public static ScenarioLoader INSTANCE = null;
    private final AtomicBoolean isRunningScenario = new AtomicBoolean(false);
    private final AtomicBoolean hasStarted = new AtomicBoolean(false);
    private Scenario scenario;

    public ScenarioLoader(Integer port, BotManager botManager, Scenario scenario) {
        super(port, botManager);
        this.setScenario(scenario);
    }

    public static ScenarioLoader get() {
        return INSTANCE;
    }

    public static void loadScenario(Scenario scenario) {
        MainClass.setupForBotType(MainClass.BotType.SCENARIO, (port, botMgr) -> {
            if (INSTANCE != null)
                INSTANCE.setScenario(scenario);
            else
                INSTANCE = new ScenarioLoader(port, botMgr, scenario);

            return INSTANCE;
        }, false);
    }

    @Override
    public void start() {
        synchronized (this.hasStarted) {
            if (this.hasStarted.get())
                return;
            this.hasStarted.set(true);
        }
        super.start();
    }

    public void setScenario(Scenario s) {
        synchronized (isRunningScenario) {
            assert !isRunningScenario.get() : "Scenario already running";
            this.isRunningScenario.set(true);
            this.scenario = s;
            this.isRunningScenario.notifyAll();
        }
    }

    public void waitToCompletion() {
        waitToCompletion(0);
    }

    public boolean waitToCompletion(long timeout) {
        synchronized (this.isRunningScenario) {
            try {
                while (true) {
                    var isRunning = this.isRunningScenario.get();
                    if (!isRunning)
                        return true;
                    this.isRunningScenario.wait(timeout);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    private ControlsOutput processInput(DataPacket input) {
        if (this.scenario == null)
            return new ControlsOutput();
        var output = this.scenario.processInput(input);

        if (output == null) { // Scenario has completed
            synchronized (this.isRunningScenario) {
                this.isRunningScenario.set(false);
                this.scenario = null;
                this.isRunningScenario.notifyAll();
                System.out.println("Scenario has completed");
            }
            return new ControlsOutput();
        }

        return output;
    }

    @Override
    protected Bot initBot(int index, String botName, int team) {
        return new ProxyBot(index, this::processInput);
    }
}
