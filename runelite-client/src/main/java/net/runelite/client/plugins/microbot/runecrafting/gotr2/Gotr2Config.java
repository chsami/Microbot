package net.runelite.client.plugins.microbot.runecrafting.gotr2;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.runecrafting.gotr2.data.Mode;
import net.runelite.client.plugins.microbot.runecrafting.gotr2.data.Combination;

@ConfigGroup(Gotr2Config.configGroup)
@ConfigInformation("This plugin is in preview & only supports masses. <br /> The script will not create elemental guardians. <br /> Have fun and don't get banned! <br /> If using NPC Contact to repair pouches, make sure you have Abyssal book in your bank! <br /><br /> <b>NB</b> NPC Contact pouch repair doesn't seem to work; pay Apprentice Cordelia 25 abyssal pearls and have some in your inventory for smooth sailing. ")
public interface Gotr2Config extends Config {

    String configGroup = "gotr2-combination";

    String mode = "mode";
    String maxFragmentAmount = "maxFragmentAmount";
    String maxAmountEssence = "maxAmountEssence";
    String shouldDepositRunes = "shouldDepositRunes";
    String combination = "combination";
    // debug flags were moved to Gotr2Script

    @ConfigSection(
            name = "General",
            description = "General Plugin Settings",
            position = 0
    )
    String generalSection = "general";

    @ConfigSection(
            name = "Combination",
            description = "Combination Settings",
            position = 1
    )
    String combinationSection = "combination";

    @ConfigItem(
            keyName = mode,
            name = "Mode",
            description = "Type of mode",
            position = 0,
            section = generalSection
    )
    default Mode Mode() { return Mode.BALANCED; }

    @ConfigItem(
            keyName = maxFragmentAmount,
            name = "Max. amount fragments",
            description = "Max amount fragments to collect",
            position = 1,
            section = generalSection
    )
    default int maxFragmentAmount() {
        return 100;
    }

    @ConfigItem(
            keyName = maxAmountEssence,
            name = "Max. amount essence before using portal",
            description = "If you have more than the threshold defined, the player will not use the portal",
            position = 2,
            section = generalSection
    )
    default int maxAmountEssence() {
        return 20;
    }

    @ConfigItem(
            keyName = shouldDepositRunes,
            name = "Deposit runes?",
            description = "Should you deposit runes into the deposit pool?",
            position = 3,
            section = generalSection
    )
    default boolean shouldDepositRunes() {
        return true;
    }


    @ConfigItem(
            keyName = combination,
            name = "Combination",
            description = "Which Combination rune would you like to make",
            position = 1,
            section = combinationSection
    )
    default Combination rune() {return Combination.NONE;}


}
