package net.runelite.client.plugins.microbot.herbrun;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;

@ConfigInformation("This plugin will run the herb run.\n" +
        "Setup an appropriate inventory setup with seeds, \n" +
        "teleports for the enabled locations, \n" +
        "rake, spade, seed dibber \n" +
        "and ultracompost or bottomless compost.\n" +
        "Credits to liftedmango and See1Duck")
@ConfigGroup("Herbrun")
public interface HerbrunConfig extends Config {

    @ConfigSection(
            name = "Settings",
            description = "Settings",
            position = 0
    )
    String settingsSection = "Settings";

    @ConfigItem(
            keyName = "inventorySetup",
            name = "Inventory Setup",
            description = "Inventory setup to use",
            position = 1,
            section = settingsSection
    )
    default InventorySetup inventorySetup() {
        return null;
    }

    @ConfigItem(
            keyName = "goToBank",
            name = "Go to bank",
            description = "Go to closest bank after run",
            position = 2,
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
            keyName = "enableArdouge",
            name = "Enable Ardouge Patch",
            description = "Enable Ardouge patch in herb run",
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
            position = 1
    )
    String locationSection = "Location";

}
