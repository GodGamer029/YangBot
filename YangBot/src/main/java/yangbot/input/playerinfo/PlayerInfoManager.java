package yangbot.input.playerinfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerInfoManager {

    private static final Map<String, PlayerInfo> playerInfoMap = new ConcurrentHashMap<>();

    public static PlayerInfo getFor(String name) {
        playerInfoMap.computeIfAbsent(name, PlayerInfo::new);
        return playerInfoMap.get(name);
    }
}
