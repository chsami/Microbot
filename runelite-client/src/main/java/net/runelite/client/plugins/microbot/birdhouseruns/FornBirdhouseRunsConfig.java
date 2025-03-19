package net.runelite.client.plugins.microbot.birdhouseruns;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("FornBirdhouseRuns")
public interface FornBirdhouseRunsConfig extends Config {


    @ConfigItem(
            keyName = "inventorySetup",
            name = "InventorySetup Name",
            description = "Name of inventory setup to use",
            position = 0
    )
    default String inventorySetup() {
        return "";
    }

    @ConfigItem(
        keyName = "bank",
        name = "Go to bank",
        description = "Should we go to bank at the end of the run?",
        position = 1
    )
    default boolean goToBank() {
        return false;
    }

}
