package net.runelite.client.plugins.microbot.birdhouseruns;

import net.runelite.client.config.*;

@ConfigInformation("This plugin will run the birdhouse runs.\n" +
        "Setup an appropriate inventory setup with logs, \n" +
        "seeds, Digsite pendant, hammer and a chisel.")
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
