package yangbot;

import rlbot.Bot;
import rlbot.manager.BotManager;
import rlbot.pyinterop.SocketServer;

public class YangPythonInterface extends SocketServer {

    public YangPythonInterface(int port, BotManager botManager) {
        super(port, botManager);
    }

    @Override
    protected Bot initBot(int index, String botName, int team) {
        if (botName.startsWith("YangBot")) {
            switch (MainClass.BOT_TYPE) {
                case PROD:
                    return new YangBot(index);
                case TEST:
                    return new TestBot(index);
                case TRAINING:
                    return new TrainingBot(index);
                case TRAINING_TEST:
                    return new TrainingTestBot(index);
            }
            throw new IllegalArgumentException("Invalid Bot Type: " + MainClass.BOT_TYPE.name());
        }
        if (botName.startsWith("Vibe")) {
            switch (MainClass.BOT_TYPE) {
                case PROD:
                    return new VibeBot(index);
            }
            throw new IllegalArgumentException("Invalid Bot Type: " + MainClass.BOT_TYPE.name());
        }
        throw new IllegalArgumentException("Unknown Bot Name: " + botName);
    }
}
