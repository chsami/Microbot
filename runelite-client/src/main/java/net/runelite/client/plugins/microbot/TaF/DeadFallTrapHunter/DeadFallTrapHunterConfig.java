package net.runelite.client.plugins.microbot.TaF.DeadFallTrapHunter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.plugins.microbot.kaas.pyrefox.enums.MeatPouch;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;

@ConfigInformation("<html>"
        + "Deadfall hunter script by TaF"
        + "<p>This plugin automates hunting with Deadfall traps all over Gielinor.</p>\n"
        + "<p>Requirements:</p>\n"
        + "<ol>\n"
        + "    <li>Appropriate Hunter level for your chosen creature type</li>\n"
        + "    <li>Knife in bank or inventory</li>\n"
        + "    <li>Axe in bank or inventory</li>\n"
        + "</ol>\n"
        + "<p>Other valuable items:</p>\n"
        + "<ol>\n"
        + "    <li>Kandarin headgear for a chance at extra logs being cut</li>\n"
        + "    <li>Graceful for banking/travel</li>\n"
        + "    <li>Guild hunter outfit for increased catchrates</li>\n"
        + "</ol>\n"
        + "<p>Configure sleep timings in the settings for optimal performance.</p>\n"
        + "<p>Use the overlay option to display trap status and hunter information on screen.</p>"
        + "</html>")
@ConfigGroup("DeadfallHunter")
public interface DeadFallTrapHunterConfig extends Config {

    @ConfigItem(
            position = 0,
            keyName = "deadFallTrapHunting",
            name = "Creature to hunt",
            description = "Select which creature to hunt"
    )
    default DeadFallTrapHunting deadFallTrapHunting() {
        return DeadFallTrapHunting.PYRE_FOX;
    }

    @ConfigItem(
            position = 1,
            keyName = "axeInInventory",
            name = "Use axe in inventory?",
            description = "Use axe in inventory?"
    )
    default boolean axeInInventory() {
        return true;
    }

    @ConfigItem(
            position = 2,
            keyName = "UseMeatPouch",
            name = "Use meat pouch?",
            description = "Do you have a meat pouch?"
    )
    default boolean UseMeatPouch() {
        return true;
    }

    @ConfigItem(
            position = 3,
            keyName = "MeatPouch",
            name = "Meat pouch",
            description = "Which meat pouch should the script use?"
    )
    default MeatPouch MeatPouch() {
        return MeatPouch.LARGE_MEAT_POUCH;
    }

    @ConfigItem(
            position = 4,
            keyName = "EatAtBank",
            name = "Eat at bank",
            description = "Auto eats food at the bank to fill up your hitpoints."
    )
    default boolean AutoEat() {
        return true;
    }

    @ConfigItem(
            position = 5,
            keyName = "FoodToEatAtBank",
            name = "Food to eat at bank",
            description = "What food should we eat at the bank?"
    )
    default Rs2Food FoodToEatAtBank() {
        return Rs2Food.LOBSTER;
    }

    @ConfigItem(
            position = 6,
            keyName = "runToBankHP",
            name = "Run to bank at HP",
            description = "Run to the bank to eat <= hitpoints"
    )
    default int runToBankHP() {
        return 25;
    }

    @ConfigItem(
            position = 7,
            keyName = "progressiveHunting",
            name = "Automatically select best creature to hunt.",
            description = "This will override the selected creature. Furthermore, it will move you to the next location when you meet the requirements."
    )
    default boolean progressiveHunting() {
        return false;
    }

    @ConfigItem(
            position = 8,
            keyName = "xpMode",
            name = "XP Mode",
            description = "Disables looting and banking to maximize XP per hour"
    )
    default boolean xpMode() {
        return false;
    }

    @ConfigItem(
            position = 9,
            keyName = "showOverlay",
            name = "Show Overlay",
            description = "Displays overlay with traps and status"
    )
    default boolean showOverlay() {
        return true;
    }

    @ConfigItem(
            position = 10,
            keyName = "MinSleepAfterCatch",
            name = "Min. Sleep After Catch - Recommended minimum 7500ms",
            description = "Min sleep after catch"
    )
    default int minSleepAfterCatch() {
        return 4500;
    }

    @ConfigItem(
            position = 11,
            keyName = "MaxSleepAfterCatch",
            name = "Max. Sleep After Catch",
            description = "Max sleep after catch"
    )
    default int maxSleepAfterCatch() {
        return 7000;
    }

    @ConfigItem(
            position = 12,
            keyName = "MinSleepAfterLay",
            name = "Min. Sleep After Lay - Recommended minimum 4000ms",
            description = "Min sleep after lay"
    )
    default int minSleepAfterLay() {
        return 2000;
    }

    @ConfigItem(
            position = 13,
            keyName = "MaxSleepAfterLay",
            name = "Max. Sleep After Lay",
            description = "Max sleep after lay"
    )
    default int maxSleepAfterLay() {
        return 5000;
    }


}