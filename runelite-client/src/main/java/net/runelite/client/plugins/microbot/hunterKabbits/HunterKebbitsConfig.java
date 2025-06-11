package net.runelite.client.plugins.microbot.hunterKabbits;

import net.runelite.client.config.*;

@ConfigGroup("Hunter Kebbits")
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
public interface HunterKebbitsConfig extends Config {

    /**
     * Manually select the type of Kebbit to hunt.
     * This option is overridden if progressive hunting is enabled.
     *
     * @return The selected Kebbit type.
     */
    @ConfigItem(
            keyName = "kebbitType",
            name = "Kebbit Type",
            description = "Choose which kebbit to hunt",
            position = 0
    )
    default KebbitHunting kebbitType() {
        return KebbitHunting.SPOTTED;
    }

    /**
     * Automatically determines the best Kebbit to hunt based on current Hunter level.
     * Overrides the manually selected Kebbit type.
     *
     * @return True if progressive hunting is enabled, false otherwise.
     */
    @ConfigItem(
            position = 1,
            keyName = "progressiveHunting",
            name = "Automatically select best Kebbit to hunt.",
            description = "This will override the selected Kebbit. Furthermore, it will move you to the next location when you meet the requirements."
    )
    default boolean progressiveHunting() {
        return false;
    }

    /**
     * Toggles the on-screen overlay showing plugin status or stats.
     *
     * @return True to show the overlay, false to hide it.
     */
    @ConfigItem(
            position = 2,
            keyName = "showOverlay",
            name = "Show Overlay",
            description = "Displays the overlay"
    )
    default boolean showOverlay() {
        return true;
    }

    /**
     * Minimum delay in milliseconds after a successful catch before the next action.
     * Helps mimic human behavior.
     *
     * @return Minimum sleep time after retrieving the falcon.
     */
    @ConfigItem(
            position = 4,
            keyName = "MinSleepAfterCatch",
            name = "Min. Sleep After Catch - Recommended minimum 7500ms",
            description = "Min sleep after catch"
    )
    default int minSleepAfterCatch() {
        return 7500;
    }

    /**
     * Maximum delay in milliseconds after a successful catch before the next action.
     *
     * @return Maximum sleep time after retrieving the falcon.
     */
    @ConfigItem(
            position = 5,
            keyName = "MaxSleepAfterCatch",
            name = "Max. Sleep After Catch",
            description = "Max sleep after catch"
    )
    default int maxSleepAfterCatch() {
        return 8400;
    }

    /**
     * Minimum delay in milliseconds after sending the falcon to catch a Kebbit.
     * Prevents rapid retries and simulates natural delay.
     *
     * @return Minimum sleep time after sending the falcon.
     */
    @ConfigItem(
            position = 6,
            keyName = "MinSleepAfterHuntingKebbit",
            name = "Min. Sleep After sending Kyr - Recommended minimum 4000ms",
            description = "Min sleep before Send Kyr to fly"
    )
    default int MinSleepAfterHuntingKebbit() {
        return 4000;
    }

    /**
     * Maximum delay in milliseconds after sending the falcon to catch a Kebbit.
     *
     * @return Maximum sleep time after sending the falcon.
     */
    @ConfigItem(
            position = 7,
            keyName = "MaxSleepAfterHuntingKebbit",
            name = "Max. Sleep After sending Kyr",
            description = "Max sleep before Send Kyr to fly"
    )
    default int MaxSleepAfterHuntingKebbit() {
        return 5400;
    }

    @ConfigItem(
            position = 8,
            keyName = "buryBones",
            name = "Bury Bones",
            description = "If enabled, bones will be buried automatically instead of dropped."

    )
    default boolean buryBones()
    {
        return false; // Standard: false, also droppen.
    }
}
