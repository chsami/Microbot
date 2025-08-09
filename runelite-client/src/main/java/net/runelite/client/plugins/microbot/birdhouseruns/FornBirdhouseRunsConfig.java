package net.runelite.client.plugins.microbot.birdhouseruns;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.sticktothescript.common.enums.LogType;

@ConfigInformation("Automated Birdhouse Runs on Fossil Island<br/><br/>" +
        "<b>Requirements:</b><br/>" +
        "• Bone Voyage quest completed<br/>" +
        "• Hunter level 9+ (higher for better birdhouses)<br/>" +
        "• Crafting level 15+ (higher for better birdhouses)<br/><br/>" +
        "<b>Two Setup Options:</b><br/>" +
        "1. Inventory Setup: Use your custom inventory configuration<br/>" +
        "2. Auto Banking: Let the plugin handle everything!<br/><br/>" +
        "<b>Auto Banking withdraws:</b><br/>" +
        "• 1 Chisel & 1 Hammer<br/>" +
        "• 1 Digsite pendant (uses lowest charges first)<br/>" +
        "• 4 Logs (your choice)<br/>" +
        "• 40 Seeds (automatically selects cheapest available)<br/><br/>" +
        "The plugin visits all 4 birdhouse locations in optimal order!")
@ConfigGroup("FornBirdhouseRuns")
public interface FornBirdhouseRunsConfig extends Config {
    @ConfigSection(
            name = "Inventory Setup Method",
            description = "Use a pre-configured inventory setup",
            position = 0
    )
    String inventorySection = "inventory";

    @ConfigItem(
            keyName = "useInventorySetup",
            name = "Use Inventory Setup",
            description = "Enable to use RuneLite inventory setups | Disable for automatic banking",
            section = inventorySection,
            position = 0
    )
    default boolean useInventorySetup() {
        return false;
    }

    @ConfigItem(
            keyName = "inventorySetup",
            name = "Inventory Setup Name",
            description = "Select your pre-configured inventory setup",
            section = inventorySection,
            position = 1
    )
    default InventorySetup inventorySetup() {
        return null;
    }

    @ConfigSection(
            name = "Automatic Banking Method",
            description = "Let the plugin handle banking for you",
            position = 1
    )
    String autoSection = "auto";

    @ConfigItem(
            keyName = "logType",
            name = "Log Type",
            description = "Choose which logs to craft birdhouses with",
            section = autoSection,
            position = 0
    )
    default LogType logType() {
        return LogType.NORMAL_LOGS;
    }

    @ConfigSection(
            name = "Run Options",
            description = "Additional plugin behavior",
            position = 2
    )
    String optionsSection = "options";

    @ConfigItem(
            keyName = "bank",
            name = "Bank After Run",
            description = "Automatically bank and empty bird nests when finished",
            section = optionsSection,
            position = 0
    )
    default boolean goToBank() {
        return false;
    }

}
