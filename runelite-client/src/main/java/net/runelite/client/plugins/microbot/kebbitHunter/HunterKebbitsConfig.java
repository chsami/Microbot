package net.runelite.client.plugins.microbot.kebbitHunter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigInformation("<html>"
        + "Kebbit script by VIP"
        + "<p>This plugin automates Kebbit hunting in the Piscatoris Falconry area.</p>\n"
        + "<p>Requirements:</p>\n"
        + "<ol>\n"
        + "    <li>Appropriate Hunter level for your chosen Kebbit type</li>\n"
        + "    <li>Access to the Piscatoris Falconry area</li>\n"
        + "    <li>Rent a falcon from the NPC Matthias</li>\n"
        + "</ol>\n"
        + "<p>Configure sleep timings and Kebbit type in the settings for optimal performance.</p>\n"
        + "<p>Use the overlay option to display falcon status and hunter information on screen.</p>"
        + "</html>")
@ConfigGroup("hunterkebbits")
public interface HunterKebbitsConfig extends Config {

    @ConfigItem(
            keyName = "showOverlay",
            name = "Show Overlay",
            description = "Toggle the overlay display",
            position = 0
    )
    default boolean showOverlay() {
        return true;
    }

    @ConfigItem(
            keyName = "kebbitType",
            name = "Kebbit Type",
            description = "Select which Kebbit to hunt",
            position = 1
    )
    default KebbitHunting kebbitType() {
        return KebbitHunting.SPOTTED;
    }

    @ConfigItem(
            keyName = "progressiveHunting",
            name = "Progressive Hunting",
            description = "Automatically upgrade hunted Kebbits as your level increases",
            position = 2
    )
    default boolean progressiveHunting() {
        return false;
    }

    @ConfigItem(
            keyName = "minSleepAfterCatch",
            name = "Min Catch Delay",
            description = "Minimum wait time after catching (ms)",
            position = 3
    )
    default int minSleepAfterCatch() {
        return 7500;
    }

    @ConfigItem(
            keyName = "maxSleepAfterCatch",
            name = "Max Catch Delay",
            description = "Maximum wait time after catching (ms)",
            position = 4
    )
    default int maxSleepAfterCatch() {
        return 8400;
    }

    @ConfigItem(
            keyName = "MinSleepAfterHuntingKebbit",
            name = "Min Hunt Delay",
            description = "Minimum wait time between hunting attempts (ms)",
            position = 5
    )
    default int MinSleepAfterHuntingKebbit() {
        return 4000;
    }

    @ConfigItem(
            keyName = "MaxSleepAfterHuntingKebbit",
            name = "Max Hunt Delay",
            description = "Maximum wait time between hunting attempts (ms)",
            position = 6
    )
    default int MaxSleepAfterHuntingKebbit() {
        return 5400;
    }
}