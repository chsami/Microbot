package net.runelite.client.plugins.microbot.TaF.TzhaarVenatorBow;

import lombok.Getter;
import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;

@ConfigInformation(
        "This plugin kills Tzhaars with a Venator Bow within the inner TzHaar city"
                + "Have all your combat items already equipped and ready to go before starting the script."
                + "<p>Have the following in your bank and inventory setup:</p>\n"
                + "<ol>\n"
                + "    <li>Prayer potions</li>\n"
                + "    <li>Emergency food</li>\n"
                + "    <li>Ranged potions</li>\n"
                + "    <li>Telegrab runes if option enabled</li>\n"
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
            keyName = "withdrawPrayerPotsCount",
            name = "Number of prayer potions to withdraw",
            description = "How many prayer pots should the script withdraw from the bank - If set to 0, it will withdraw inventory space -2",
            section = bankingAndSuppliesSection,
            position = 0
    )
    @Range(
            min = 0,
            max = 28
    )
    default int withdrawPrayerPotsCount() {
        return 0;
    }

    @ConfigItem(
            keyName = "withdrawRangePotsCount",
            name = "Number of range potions to withdraw",
            description = "How many range pots should the script withdraw from the bank, if set to 0, it will skip withdrawing anything",
            section = bankingAndSuppliesSection,
            position = 1
    )
    @Range(
            min = 0,
            max = 10
    )
    default int withdrawRangePotsCount() {
        return 4;
    }

    @ConfigItem(
            keyName = "foodToWithdraw",
            name = "Emergency food",
            description = "Which food to withdraw from the bank",
            section = bankingAndSuppliesSection,
            position = 2
    )
    default Rs2Food foodToWithdraw() {
        return Rs2Food.SHARK;
    }
    @ConfigItem(
            keyName = "minEatPercent",
            name = "Minimum Health Percent",
            description = "Percentage of health below which the bot will eat food",
            section = bankingAndSuppliesSection,
            position = 3
    )
    default int minEatPercent() {
        return 50;
    }

    @ConfigItem(
            keyName = "minPrayerPercent",
            name = "Minimum Prayer Percent",
            description = "Percentage of prayer points below which the bot will drink a prayer potion",
            section = bankingAndSuppliesSection,
            position = 4
    )
    default int minPrayerPercent() {
        return 30;
    }

    @ConfigItem(
            keyName = "boostedStatsThreshold",
            name = "% Boosted Stats Threshold",
            description = "The threshold for using a potion when the boosted stats are below the maximum.",
            section = bankingAndSuppliesSection,
            position = 5
    )
    @Range(
            min = 1,
            max = 100
    )
    default int boostedStatsThreshold() {
        return 5;
    }
}
