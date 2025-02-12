package net.runelite.client.plugins.microbot.qualityoflife.enums;

import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.fletching.enums.FletchingItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public enum FletchingLogs {
    LOG("Logs", ItemID.LOGS),
    OAK("Oak logs", ItemID.OAK_LOGS),
    WILLOW("Willow logs", ItemID.WILLOW_LOGS),
    MAPLE("Maple logs", ItemID.MAPLE_LOGS),
    YEW("Yew logs", ItemID.YEW_LOGS),
    MAGIC("Magic logs", ItemID.MAGIC_LOGS),
    REDWOOD("Redwood logs", ItemID.REDWOOD_LOGS);

    private final String name;
    private final int id;

    FletchingLogs(String name, int id) {
        this.name = name;
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }

    private static final Map<FletchingLogs, Map<FletchingItem, Integer>> LEVEL_REQUIREMENTS = new EnumMap<>(FletchingLogs.class);

    static {
        Map<FletchingItem, Integer> itemRequirements;

        // LOG
        itemRequirements = new EnumMap<>(FletchingItem.class);
        itemRequirements.put(FletchingItem.ARROW_SHAFT, 1);
        itemRequirements.put(FletchingItem.SHORT, 5);
        itemRequirements.put(FletchingItem.LONG, 10);
        itemRequirements.put(FletchingItem.STOCK, 9);
        LEVEL_REQUIREMENTS.put(FletchingLogs.LOG, itemRequirements);

        // OAK
        itemRequirements = new EnumMap<>(FletchingItem.class);
        itemRequirements.put(FletchingItem.ARROW_SHAFT, 15);
        itemRequirements.put(FletchingItem.SHORT, 20);
        itemRequirements.put(FletchingItem.LONG, 25);
        itemRequirements.put(FletchingItem.STOCK, 24);
        itemRequirements.put(FletchingItem.SHIELD, 27);
        LEVEL_REQUIREMENTS.put(FletchingLogs.OAK, itemRequirements);

        // WILLOW
        itemRequirements = new EnumMap<>(FletchingItem.class);
        itemRequirements.put(FletchingItem.ARROW_SHAFT, 30);
        itemRequirements.put(FletchingItem.SHORT, 35);
        itemRequirements.put(FletchingItem.LONG, 40);
        itemRequirements.put(FletchingItem.STOCK, 39);
        itemRequirements.put(FletchingItem.SHIELD, 42);
        LEVEL_REQUIREMENTS.put(FletchingLogs.WILLOW, itemRequirements);

        // MAPLE
        itemRequirements = new EnumMap<>(FletchingItem.class);
        itemRequirements.put(FletchingItem.ARROW_SHAFT, 45);
        itemRequirements.put(FletchingItem.SHORT, 50);
        itemRequirements.put(FletchingItem.LONG, 55);
        itemRequirements.put(FletchingItem.STOCK, 54);
        itemRequirements.put(FletchingItem.SHIELD, 57);
        LEVEL_REQUIREMENTS.put(FletchingLogs.MAPLE, itemRequirements);

        // YEW
        itemRequirements = new EnumMap<>(FletchingItem.class);
        itemRequirements.put(FletchingItem.ARROW_SHAFT, 60);
        itemRequirements.put(FletchingItem.SHORT, 65);
        itemRequirements.put(FletchingItem.LONG, 70);
        itemRequirements.put(FletchingItem.STOCK, 69);
        itemRequirements.put(FletchingItem.SHIELD, 72);
        LEVEL_REQUIREMENTS.put(FletchingLogs.YEW, itemRequirements);

        // MAGIC
        itemRequirements = new EnumMap<>(FletchingItem.class);
        itemRequirements.put(FletchingItem.ARROW_SHAFT, 75);
        itemRequirements.put(FletchingItem.SHORT, 80);
        itemRequirements.put(FletchingItem.LONG, 85);
        itemRequirements.put(FletchingItem.STOCK, 78);
        itemRequirements.put(FletchingItem.SHIELD, 87);
        LEVEL_REQUIREMENTS.put(FletchingLogs.MAGIC, itemRequirements);

        // REDWOOD
        itemRequirements = new EnumMap<>(FletchingItem.class);
        itemRequirements.put(FletchingItem.ARROW_SHAFT, 90);
        itemRequirements.put(FletchingItem.SHIELD, 92);
        LEVEL_REQUIREMENTS.put(FletchingLogs.REDWOOD, itemRequirements);
    }

    public static int calculateLevelRequirement(FletchingLogs log, FletchingItem item) {
        Map<FletchingItem, Integer> itemRequirements = LEVEL_REQUIREMENTS.get(log);
        if (itemRequirements != null) {
            Integer level = itemRequirements.get(item);
            if (level != null) {
                return level;
            }
        }
        return 1; // Default level if not found
    }

    public static List<Rs2ItemModel> getFletchableLogs(FletchingItem type) {
        return Rs2Inventory.all(item -> {
            for (FletchingLogs log : FletchingLogs.values()) {
                if (item.getId() == log.getId() && calculateLevelRequirement(log, type) <= Rs2Player.getRealSkillLevel(Skill.FLETCHING)){
                    return true;
                }
            }
            return false;
        });
    }
}
