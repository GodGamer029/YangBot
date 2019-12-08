package yangbot;

import rlbot.Bot;
import rlbot.manager.BotManager;
import rlbot.pyinterop.SocketServer;

public class YangPythonInterface extends SocketServer {

    public YangPythonInterface(int port, BotManager botManager) {
        super(port, botManager);
    }

    @Override
    protected Bot initBot(int index, String botType, int team) {
        switch (MainClass.BOT_TYPE) {
            case PRODUCTION:
                return new YangBot(index);
            case TEST:
                return new TestBot(index);
            case TRAINING:
                return new TrainingBot(index);
        }
        throw new IllegalArgumentException("Invalid Bot Type: " + MainClass.BOT_TYPE.name());
    }
}
