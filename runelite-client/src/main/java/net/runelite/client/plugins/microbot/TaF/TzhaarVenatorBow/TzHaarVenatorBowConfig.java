package net.runelite.client.plugins.microbot.TaF.TzhaarVenatorBow;

import lombok.Getter;
import net.runelite.client.config.*;

@ConfigInformation(
        "This plugin kills Tzhaars with a Venator Bow within the inner TzHaar city"
                + "Have all your combat items already equipped and ready to go before starting the script."
                + "<p>Have the following in your bank and inventory setup:</p>\n"
                + "<ol>\n"
                + "    <li>Prayer potions</li>\n"
                + "    <li>Emergency Sharks</li>\n"
                + "    <li>Ranged potions</li>\n"
                + "    <li>Telegrab runes if option enabled</li>\n"
                + "    <li>Ancient Essence / Arrows for resupplying</li>\n"
                + "</ol>\n"
                + "<br/>"
                + "It will walk to a spot where no magicians can reach you and engage the surrounding TzHaars<br/>"
                + "</html>")
@ConfigGroup("TzHaarVenator")
public interface TzHaarVenatorBowConfig extends Config {
    @ConfigSection(
            name = "Looting",
            description = "Settings for item looting",
            position = 1
    )
    String lootingSection = "looting";
    @ConfigSection(
            name = "Food and Potions",
            description = "Settings for banking and required supplies",
            position = 2
    )
    String bankingAndSuppliesSection = "bankingAndSupplies";
    @ConfigSection(
            name = "Gear Settings",
            description = "Specify gear options",
            position = 3
    )
    String gearSettingsSection = "gearSettings";

    @ConfigItem(
            keyName = "teleGrabLoot",
            name = "Telegrab loot",
            description = "If enabled and runes are available, the bot will telegrab loot",
            section = lootingSection,
            position = 0
    )
    default boolean teleGrabLoot() {
        return false;
    }

    @ConfigItem(
            keyName = "minEatPercent",
            name = "Minimum Health Percent",
            description = "Percentage of health below which the bot will eat food",
            section = bankingAndSuppliesSection,
            position = 0
    )
    default int minEatPercent() {
        return 50;
    }

    @ConfigItem(
            keyName = "minPrayerPercent",
            name = "Minimum Prayer Percent",
            description = "Percentage of prayer points below which the bot will drink a prayer potion",
            section = bankingAndSuppliesSection,
            position = 1
    )
    default int minPrayerPercent() {
        return 30;
    }

    @ConfigItem(
            keyName = "rangingPotionType",
            name = "Ranging Potion Type",
            description = "Select the type of ranging potion to use",
            section = bankingAndSuppliesSection,
            position = 2
    )
    default RangingPotionType rangingPotionType() {
        return RangingPotionType.RANGING;
    }

    @ConfigItem(
            keyName = "boostedStatsThreshold",
            name = "% Boosted Stats Threshold",
            description = "The threshold for using a potion when the boosted stats are below the maximum.",
            section = bankingAndSuppliesSection,
            position = 3
    )
    @Range(
            min = 1,
            max = 100
    )
    default int boostedStatsThreshold() {
        return 5;
    }

    @ConfigItem(
            keyName = "arrowType",
            name = "Arrow Type",
            description = "Specify the type of arrow to use",
            section = gearSettingsSection,
            position = 0
    )
    default String arrowType() {
        return "Rune arrow";
    }

    @Getter
    enum RangingPotionType {
        RANGING,
        DIVINE_RANGING,
        BASTION
    }
}
