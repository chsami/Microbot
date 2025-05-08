package net.runelite.client.plugins.microbot.maxxin.housethieving;

import net.runelite.client.config.*;

@ConfigGroup(HouseThievingConfig.configGroup)
@ConfigInformation(
        "Plugin to thief houses in Varlamore. <br/>" +
        "Only requirement is completion of Children of the Sun quest to access Varlamore. <br/>"
)
public interface HouseThievingConfig extends Config {
    String configGroup = "house-thieving";

    String maxHouseKeys = "maxHouseKeys";
    String minHouseKeys = "minHouseKeys";
    String pickpocketWaitTime = "pickpocketWaitTime";

    @ConfigSection(
            name = "House Thieving Settings",
            description = "Settings for house thieving in Varlamore",
            position = 0
    )
    String generalSection = "general";

    @ConfigItem(
            keyName = maxHouseKeys,
            name = "Max House Keys",
            description = "Number of house keys to get before starting house thieving",
            position = 1,
            section = generalSection
    )
    default int maxHouseKeys() {
        return 20;
    }

    @ConfigItem(
            keyName = minHouseKeys,
            name = "Minimum House Keys",
            description = "Number of house keys to use before switching to pickpocketing",
            position = 1,
            section = generalSection
    )
    default int minHouseKeys() {
        return 2;
    }

    @ConfigItem(
            keyName = pickpocketWaitTime,
            name = "Pickpocket Wait Time",
            description = "How long to wait for wealthy citizen pickpocket event before world hopping",
            position = 1,
            section = generalSection
    )
    default int pickpocketWaitTime() {
        return 90;
    }
}
