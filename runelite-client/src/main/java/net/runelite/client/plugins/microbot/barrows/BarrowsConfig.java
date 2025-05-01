package net.runelite.client.plugins.microbot.barrows;

import net.runelite.client.config.*;

@ConfigGroup("barrows")
@ConfigInformation("1. Start with your ring of dueling equipped.<br /><br /> 2. Your auto-cast spell selected. <br /><br /> 3. Your chosen food in the inventory. <br /><br /> Required items: prayer potions, forgotten brews, barrows teleports, food, catalyic Runes, and a spade.<br /><br /> Spells: Wind: Blast, Wave, and Surge. <br /><br /> Special thanks to george for adding the barrows dungeon to the walker; and Crannyy for script testing!<br /><br /> Config by Crannyy")
public interface BarrowsConfig extends Config {
    @ConfigItem(
            keyName = "targetFoodAmount",
            name = "Max Food Amount",
            description = "Max amount of food to withdraw from the bank.",
            position = 0
    )
    @Range(min = 1, max = 28)
    default int targetFoodAmount() {
        return 10;
    }

    @ConfigItem(
            keyName = "minFood",
            name = "Min Food",
            description = "Minimum amount of food to withdraw from the bank.",
            position = 1
    )
    @Range(min = 1, max = 28)
    default int minFood() {
        return 5;
    }

    @ConfigItem(
            keyName = "targetPrayerPots",
            name = "Max Prayer Potions",
            description = "Max amount of prayer potions to withdraw from the bank.",
            position = 2
    )
    @Range(min = 1, max = 10)
    default int targetPrayerPots() {
        return 8;
    }

    @ConfigItem(
            keyName = "minPrayerPots",
            name = "Min Prayer Potions",
            description = "Minimum amount of prayer potions to withdraw from the bank.",
            position = 3
    )
    @Range(min = 1, max = 10)
    default int minPrayerPots() {
        return 4;
    }

    @ConfigItem(
            keyName = "targetForgottenBrew",
            name = "Max Forgotten Brews",
            description = "Max amount of forgotten brews to withdraw from the bank.",
            position = 4
    )
    @Range(min = 1, max = 5)
    default int targetForgottenBrew() {
        return 3;
    }

    @ConfigItem(
            keyName = "minForgottenBrew",
            name = "Min Forgotten Brews",
            description = "Minimum amount of forgotten brews to withdraw from the bank.",
            position = 5
    )
    @Range(min = 1, max = 5)
    default int minForgottenBrew() {
        return 1;
    }

    @ConfigItem(
            keyName = "targetBarrowsTeleports",
            name = "Max Barrows Teleports",
            description = "Max amount of Barrows teleports to withdraw from the bank.",
            position = 6
    )
    @Range(min = 1, max = 10)
    default int targetBarrowsTeleports() {
        return 8;
    }

    @ConfigItem(
            keyName = "minBarrowsTeleports",
            name = "Min Barrows Teleports",
            description = "Minimum amount of Barrows teleports to withdraw from the bank.",
            position = 7
    )
    @Range(min = 1, max = 10)
    default int minBarrowsTeleports() {
        return 1;
    }

    @ConfigItem(
            keyName = "minRuneAmount",
            name = "Min Runes",
            description = "Minimum amount of runes before banking",
            position = 8
    )
    @Range(min = 50, max = 1000)
    default int minRuneAmount() {
        return 180;
    }

    @ConfigItem(
            keyName = "shouldGainRP",
            name = "Aim for 86+% rewards potential",
            description = "Should we gain additional RP other than the barrows brothers?",
            position = 9
    )
    default boolean shouldGainRP() {
        return false;
    }

}
