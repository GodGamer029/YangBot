package yangbot.input.playerinfo;

import yangbot.input.CarData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerInfoManager {

    private static final Map<String, PlayerInfo> playerInfoMap = new ConcurrentHashMap<>();
    private static final Map<String, BotInfo> botFamilyMap = new ConcurrentHashMap<>();

    public static PlayerInfo getFor(CarData car) {
        String combName = car.strippedName + "(" + car.playerIndex + ")";
        playerInfoMap.computeIfAbsent(combName, PlayerInfo::new);
        return playerInfoMap.get(combName);
    }

    public static BotInfo getForBotFamily(CarData car) {
        String combName = car.strippedName;
        if (!car.isBot)
            combName = "Player";

        botFamilyMap.computeIfAbsent(combName, BotInfo::new);
        return botFamilyMap.get(combName);
    }

    public static void reset() {
        playerInfoMap.clear();
        botFamilyMap.remove("Player");
    }
}
