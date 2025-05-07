package net.runelite.client.plugins.microbot.bee.salamanders;

import net.runelite.client.config.*;

@ConfigInformation("<html>"
        + "Salamander script by Bee & TaF"
        + "<p>This plugin automates salamander hunting all over Gielinor.</p>\n"
        + "<p>Requirements:</p>\n"
        + "<ol>\n"
        + "    <li>Appropriate Hunter level for your chosen salamander type</li>\n"
        + "    <li>Small fishing nets in bank or inventory</li>\n"
        + "    <li>Rope in bank or inventory</li>\n"
        + "</ol>\n"
        + "<p>Configure sleep timings and salamander type in the settings for optimal performance.</p>\n"
        + "<p>Use the overlay option to display trap status and hunter information on screen.</p>"
        + "</html>")
@ConfigGroup("Salamander")
public interface SalamanderConfig extends Config {

    @ConfigItem(
            position = 0,
            keyName = "salamanderHunting",
            name = "Salamander to hunt",
            description = "Select which salamander to hunt"
    )
    default SalamanderHunting salamanderHunting() {
        return SalamanderHunting.GREEN;
    }

    @ConfigItem(
            position = 1,
            keyName = "progressiveHunting",
            name = "Automatically select best salamander to hunt.",
            description = "This will override the selected salamander. Furthermore, it will move you to the next location when you meet the requirements."
    )
    default boolean progressiveHunting() {
        return false;
    }

    @ConfigItem(
            position = 2,
            keyName = "showOverlay",
            name = "Show Overlay",
            description = "Displays overlay with traps and status"
    )
    default boolean showOverlay() {
        return true;
    }

    @ConfigItem(
            position = 3,
            keyName = "withdrawNumber",
            name = "Number of nets/ropes to withdraw",
            description = "Number of nets/ropes to withdraw from bank"
    )
    @Range(
            min = 3,
            max = 13
    )
    default int withdrawNumber() {
        return 8;
    }

    @ConfigItem(
            position = 4,
            keyName = "MinSleepAfterCatch",
            name = "Min. Sleep After Catch - Recommended minimum 7500ms",
            description = "Min sleep after catch"
    )
    default int minSleepAfterCatch() {
        return 7500;
    }

    @ConfigItem(
            position = 5,
            keyName = "MaxSleepAfterCatch",
            name = "Max. Sleep After Catch",
            description = "Max sleep after catch"
    )
    default int maxSleepAfterCatch() {
        return 8400;
    }

    @ConfigItem(
            position = 6,
            keyName = "MinSleepAfterLay",
            name = "Min. Sleep After Lay - Recommended minimum 4000ms",
            description = "Min sleep after lay"
    )
    default int minSleepAfterLay() {
        return 4000;
    }

    @ConfigItem(
            position = 7,
            keyName = "MaxSleepAfterLay",
            name = "Max. Sleep After Lay",
            description = "Max sleep after lay"
    )
    default int maxSleepAfterLay() {
        return 5400;
    }


}