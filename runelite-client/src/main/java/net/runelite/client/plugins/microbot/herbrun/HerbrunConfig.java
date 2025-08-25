package net.runelite.client.plugins.microbot.herbrun;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;

@ConfigInformation("Automated Herb Runs across all patches<br/><br/>" +
        "<b>Two Setup Options:</b><br/>" +
        "1. Inventory Setup: Use your custom inventory configuration<br/>" +
        "2. Auto Banking: Let the plugin handle everything!<br/><br/>" +
        "<b>Auto Banking withdraws:</b><br/>" +
        "• Farming tools (rake, spade, seed dibber, magic secateurs)<br/>" +
        "• Teleportation runes (law, air, earth, fire, water)<br/>" +
        "• Your selected herb seeds<br/>" +
        "• Your selected compost type<br/>" +
        "• Ectophial (if Morytania is enabled)<br/><br/>" +
        "Credits to liftedmango and See1Duck")
@ConfigGroup("Herbrun")
public interface HerbrunConfig extends Config {

    @ConfigSection(
            name = "Inventory Setup Method",
            description = "Choose between inventory setup or auto banking",
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
            name = "Auto Banking Settings",
            description = "Configure automatic banking options",
            position = 1
    )
    String autoSection = "autobanking";

    @ConfigItem(
            keyName = "herbSeedType",
            name = "Herb Seed Type",
            description = "Choose which herb seeds to plant",
            section = autoSection,
            position = 0
    )
    default HerbSeedType herbSeedType() {
        return HerbSeedType.RANARR;
    }

    @ConfigItem(
            keyName = "compostType",
            name = "Compost Type",
            description = "Type of compost to use (select NONE to disable composting)",
            section = autoSection,
            position = 1
    )
    default CompostType compostType() {
        return CompostType.ULTRA;
    }

    @ConfigItem(
            keyName = "allowPartialRuns",
            name = "Allow Partial Runs",
            description = "Allow herb runs with fewer seeds than patches available",
            section = autoSection,
            position = 2
    )
    default boolean allowPartialRuns() {
        return false;
    }

    @ConfigItem(
            keyName = "dropEmptyBuckets",
            name = "Drop Empty Buckets",
            description = "Drop empty buckets after applying compost to patches",
            section = autoSection,
            position = 3
    )
    default boolean dropEmptyBuckets() {
        return true;
    }

    @ConfigSection(
            name = "General Settings",
            description = "General plugin settings",
            position = 2
    )
    String settingsSection = "settings";

    @ConfigItem(
            keyName = "goToBank",
            name = "Bank After Run",
            description = "Go to closest bank after completing the herb run",
            position = 0,
            section = settingsSection
    )
    default boolean goToBank() {
        return true;
    }

    @ConfigItem(
            keyName = "enableTrollheim",
            name = "Enable Trollheim Patch",
            description = "Enable Trollheim patch in herb run",
            position = 1,
            section = locationSection
    )
    default boolean enableTrollheim() {
        return true;
    }

    @ConfigItem(
            keyName = "enableCatherby",
            name = "Enable Catherby Patch",
            description = "Enable Catherby patch in herb run",
            position = 2,
            section = locationSection
    )
    default boolean enableCatherby() {
        return true;
    }

    @ConfigItem(
            keyName = "enableMorytania",
            name = "Enable Morytania Patch",
            description = "Enable Morytania patch in herb run",
            position = 3,
            section = locationSection
    )
    default boolean enableMorytania() {
        return true;
    }

    @ConfigItem(
            keyName = "enableVarlamore",
            name = "Enable Varlamore Patch",
            description = "Enable Varlamore patch in herb run",
            position = 4,
            section = locationSection
    )
    default boolean enableVarlamore() {
        return true;
    }

    @ConfigItem(
            keyName = "enableHosidius",
            name = "Enable Hosidius Patch",
            description = "Enable Hosidius patch in herb run",
            position = 5,
            section = locationSection
    )
    default boolean enableHosidius() {
        return true;
    }

    @ConfigItem(
            keyName = "enableArdougne",
            name = "Enable Ardougne Patch",
            description = "Enable Ardougne patch in herb run",
            position = 6,
            section = locationSection
    )
    default boolean enableArdougne() {
        return true;
    }

    @ConfigItem(
            keyName = "enableFalador",
            name = "Enable Falador Patch",
            description = "Enable Falador patch in herb run",
            position = 7,
            section = locationSection
    )
    default boolean enableFalador() {
        return true;
    }

    @ConfigItem(
            keyName = "enableWeiss",
            name = "Enable Weiss Patch",
            description = "Enable Weiss patch in herb run",
            position = 8,
            section = locationSection
    )
    default boolean enableWeiss() {
        return true;
    }

    @ConfigItem(
            keyName = "enableGuild",
            name = "Enable Farming Guild Patch",
            description = "Enable Farming Guild patch in herb run",
            position = 9,
            section = locationSection
    )
    default boolean enableGuild() {
        return true;
    }

    //    @ConfigItem(
//            keyName = "enableHarmony",
//            name = "Enable Harmony Island Patch",
//            description = "Enable Harmony Island patch in herb run",
//            position = 9,
//            section = locationSection
//    )
//    default boolean enableHarmony() {
//        return false;
//    }
    @ConfigSection(
            name = "Location toggles",
            description = "Location toggles",
            position = 3
    )
    String locationSection = "Location";

}
