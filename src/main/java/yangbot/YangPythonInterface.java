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
        if (MainClass.useTestBot)
            return new TestBot(index);
        else
            return new YangBot(index);
    }
}
