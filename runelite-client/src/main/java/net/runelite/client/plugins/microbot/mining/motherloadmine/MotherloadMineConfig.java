package net.runelite.client.plugins.microbot.mining.motherloadmine;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("MotherloadMine")
public interface MotherloadMineConfig extends Config {
    @ConfigItem(
            keyName = "PickAxeInInventory",
            name = "Pick Axe In Inventory?",
            description = "Pick Axe in inventory?",
            position = 0
    )
    default boolean pickAxeInInventory() {
        return false;
    }

    // Mine upstairs
    @ConfigItem(
            keyName = "MineUpstairs",
            name = "Mine Upstairs?",
            description = "Mine upstairs?",
            position = 1
    )
    default boolean mineUpstairs() {
        return false;
    }

    // Upstairs hopper unlocked
    @ConfigItem(
            keyName = "UpstairsHopperUnlocked",
            name = "Upstairs Hopper Unlocked?",
            description = "Upstairs hopper unlocked?",
            position = 2
    )
    default boolean upstairsHopperUnlocked() {
        return false;
    }
}
