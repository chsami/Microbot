package net.runelite.client.plugins.microbot.birdhouseruns;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;

@ConfigInformation("This plugin will run the birdhouse runs.\n" +
        "Setup an appropriate inventory setup with logs, \n" +
        "seeds, Digsite pendant, hammer and a chisel.")
@ConfigGroup("FornBirdhouseRuns")
public interface FornBirdhouseRunsConfig extends Config {
    @ConfigItem(
            keyName = "inventorySetup",
            name = "Inventory Setup",
            description = "Inventory setup to use",
            position = 0
    )
    default InventorySetup inventorySetup() {
        return null;
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
