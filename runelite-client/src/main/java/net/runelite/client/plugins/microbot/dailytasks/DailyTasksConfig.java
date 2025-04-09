package net.runelite.client.plugins.microbot.dailytasks;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;

@ConfigGroup(DailyTasksPlugin.CONFIG_GROUP)
public interface DailyTasksConfig extends Config {

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
            keyName = "goToBank",
            name = "Go to bank",
            description = "Go to closest bank after run",
            position = 1
    )
    default boolean goToBank() {
        return true;
    }

    @ConfigSection(
            name = "Tasks",
            description = "Configure which daily tasks to complete",
            position = 1
    )
    String tasksSection = "tasks";

    @ConfigItem(
            keyName = "collectHerbBoxes",
            name = "Collect Herb Boxes",
            description = "Collect daily herb boxes from NMZ",
            position = 0,
            section = tasksSection,
            hidden = true
    )
    default boolean collectHerbBoxes() {
        return true;
    }

    @ConfigItem(
            keyName = "collectStaves",
            name = "Collect Battlestaves",
            description = "Collect daily discounted battlestaves from Zaff",
            position = 1,
            section = tasksSection
    )
    default boolean collectStaves() {
        return true;
    }

    @ConfigItem(
            keyName = "collectEssence",
            name = "Collect Pure Essence",
            description = "Collect daily pure essence from Wizard Cromperty",
            position = 2,
            section = tasksSection
    )
    default boolean collectEssence() {
        return true;
    }

//    @ConfigItem(
//            keyName = "collectRunes",
//            name = "Collect Runes",
//            description = "Collect daily free runes from Aubury",
//            position = 3,
//            section = tasksSection
//    )
//    default boolean collectRunes() {
//        return true;
//    }

    @ConfigItem(
            keyName = "collectFlax",
            name = "Convert Flax",
            description = "Convert daily flax from the Flax keeper to bow string",
            position = 5,
            section = tasksSection
    )
    default boolean collectFlax() {
        return true;
    }

//    @ConfigItem(
//            keyName = "collectBonemeal",
//            name = "Collect Bonemeal",
//            description = "Collect daily bonemeal and slime from Robin",
//            position = 7,
//            section = tasksSection
//    )
//    default boolean collectBonemeal() {
//        return true;
//    }
//
//    @ConfigItem(
//            keyName = "collectDynamite",
//            name = "Collect Dynamite",
//            description = "Collect daily dynamite from Thirus",
//            position = 8,
//            section = tasksSection
//    )
//    default boolean collectDynamite() {
//        return true;
//    }

    @ConfigItem(
            keyName = "handleMiscellania",
            name = "Handle Miscellania",
            description = "Handle Miscellania daily tasks",
            position = 9,
            section = tasksSection,
            hidden = true
    )
    default boolean handleMiscellania() {
        return true;
    }
}
