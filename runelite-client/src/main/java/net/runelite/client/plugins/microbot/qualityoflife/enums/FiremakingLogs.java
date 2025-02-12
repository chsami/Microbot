package net.runelite.client.plugins.microbot.qualityoflife.enums;

import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.List;

public enum FiremakingLogs {
    LOG("Logs", ItemID.LOGS, 1),
    OAK("Oak logs", ItemID.OAK_LOGS, 15),
    WILLOW("Willow logs", ItemID.WILLOW_LOGS, 30),
    TEAK("Teak logs", ItemID.TEAK_LOGS, 35),
    MAPLE("Maple logs", ItemID.MAPLE_LOGS, 45),
    MAHOGANY("Mahogany logs", ItemID.MAHOGANY_LOGS, 50),
    YEW("Yew logs", ItemID.YEW_LOGS, 60),
    MAGIC("Magic logs", ItemID.MAGIC_LOGS, 75),
    REDWOOD("Redwood logs", ItemID.REDWOOD_LOGS, 90);

    private final String name;
    private final int id;
    private final int levelRequirement;

    FiremakingLogs(String name, int id, int levelRequirement) {
        this.name = name;
        this.id = id;
        this.levelRequirement = levelRequirement;
    }

    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }

    public int getLevelRequirement() {
        return levelRequirement;
    }

    public static FiremakingLogs getById(int id) {
        for (FiremakingLogs log : values()) {
            if (log.getId() == id) {
                return log;
            }
        }
        return null;
    }

    public static List<Rs2ItemModel> getLogs() {
        return Rs2Inventory.all( log -> {
            for (FiremakingLogs firemakingLog : values()) {
                if (firemakingLog.getId() == log.getId() && firemakingLog.getLevelRequirement() <= Rs2Player.getRealSkillLevel(Skill.FIREMAKING)) {
                    return true;
                }
            }
            return false;
        });
    }
}
