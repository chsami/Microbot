package net.runelite.client.plugins.microbot.maxxin.housethieving;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;

@ConfigGroup(HouseThievingConfig.configGroup)
@ConfigInformation(
        "Plugin to thief houses in Varlamore. Requirements are 50 Thieving and completion of Children of the Sun quest to access Varlamore. <br/>" +
        "The plugin will pickpocket until max house keys gathered and then thieve houses until the min house keys remain. <br/>" +
        "It will steal from wealthy citizens near Aurelia until a distracted citizen event occurs, prioritize that. <br/>" +
        "Using Rogue's outfit is recommended, and food is recommended while dodgy necklaces are optional.<br/>" +
        "World hop can be enabled to not require food at all, but maybe slower in gathering house keys.<br/>"
)
public interface HouseThievingConfig extends Config {
    String configGroup = "house-thieving";

    String maxHouseKeys = "maxHouseKeys";
    String minHouseKeys = "minHouseKeys";
    String pickpocketFoodAmount = "pickpocketFoodAmount";
    String foodSelection = "foodSelection";
    String foodEatPercentage = "foodEatPercentage";
    String useDodgyNecklace = "useDodgyNecklace";
    String dodgyNecklaceAmount = "dodgyNecklaceAmount";
    String worldHopForDistractedCitizens = "worldHopForDistractedCitizens";
    String worldHopWaitTime = "worldHopWaitTime";

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
            position = 2,
            section = generalSection
    )
    default int minHouseKeys() {
        return 2;
    }

    @ConfigItem(
            keyName = pickpocketFoodAmount,
            name = "Pickpocket Food Amount",
            description = "How much food to bring for wealthy citizen pickpocketing",
            position = 3,
            section = generalSection
    )
    default int pickpocketFoodAmount() {
        return 20;
    }

    @ConfigItem(
            keyName = foodSelection,
            name = "Food Selection",
            description = "Food to eat when pickpocketing",
            position = 4,
            section = generalSection
    )
    default Rs2Food foodSelection() {
        return Rs2Food.LOBSTER;
    }

    @ConfigItem(
            keyName = foodEatPercentage,
            name = "Food Eat Percentage",
            description = "Health percentage to eat at when damaged by pickpocketing",
            position = 5,
            section = generalSection
    )
    default int foodEatPercentage() {
        return 60;
    }

    @ConfigItem(
            keyName = useDodgyNecklace,
            name = "Use Dodgy Necklace",
            description = "Use dodgy necklace when pickpocketing",
            position = 6,
            section = generalSection
    )
    default boolean useDodgyNecklace() {
        return false;
    }

    @ConfigItem(
            keyName = dodgyNecklaceAmount,
            name = "Dodgy Necklace Amount",
            description = "Dodgy necklace amount",
            position = 7,
            section = generalSection
    )
    default int dodgyNecklaceAmount() {
        return 5;
    }

    @ConfigItem(
            keyName = worldHopForDistractedCitizens,
            name = "World Hop For Distracted Citizens",
            description = "World hop for Aurelia distracted citizen event",
            position = 8,
            section = generalSection
    )
    default boolean worldHopForDistractedCitizens() {
        return false;
    }

    @ConfigItem(
            keyName = worldHopWaitTime,
            name = "World Hop Wait Time",
            description = "How long to wait before world hopping for distracted ctizens",
            position = 9,
            section = generalSection
    )
    default int worldHopWaitTime() {
        return 5;
    }
}
