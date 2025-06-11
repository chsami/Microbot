package net.runelite.client.plugins.microbot.mining.motherloadmine;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.plugins.microbot.mining.motherloadmine.enums.MLMMiningSpot;
import net.runelite.client.plugins.microbot.mining.motherloadmine.enums.MLMMiningSpotList;

@ConfigGroup("MotherloadMine")
public interface MotherloadMineConfig extends Config {

    @ConfigItem(
            keyName = "guide",
            name = "How to use",
            description = "How to use this plugin",
            position = 0
    )
    default String GUIDE() {
        return "1. Have a hammer in your inventory or equipped \n2. Start near the bank chest in motherload mine";
    }

    @ConfigItem(
            keyName = "PickAxeInInventory",
            name = "Pick Axe In Inventory?",
            description = "Pick Axe in inventory?",
            position = 1
    )
    default boolean pickAxeInInventory() {
        return false;
    }

    // Mine upstairs
    @ConfigItem(
            keyName = "MineUpstairs",
            name = "Mine Upstairs?",
            description = "Mine upstairs?",
            position = 2
    )
    default boolean mineUpstairs() {
        return false;
    }

    // Upstairs hopper unlocked
    @ConfigItem(
            keyName = "UpstairsHopperUnlocked",
            name = "Upstairs Hopper Unlocked?",
            description = "Upstairs hopper unlocked?",
            position = 3
    )
    default boolean upstairsHopperUnlocked() {
        return false;
    }

    // Mining Area Selection
    @ConfigItem(
            keyName = "miningArea",
            name = "Mining Area",
            description = "Choose the specific area to mine in Motherload Mine",
            position = 4
    )
    default MLMMiningSpotList miningArea() {
        return MLMMiningSpotList.ANY;
    }
}
