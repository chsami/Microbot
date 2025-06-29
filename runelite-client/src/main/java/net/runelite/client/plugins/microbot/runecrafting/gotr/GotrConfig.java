package net.runelite.client.plugins.microbot.runecrafting.gotr;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.plugins.microbot.runecrafting.gotr.data.Mode;

@ConfigGroup("gotr")
@ConfigInformation("This plugin is in preview & only supports masses. <br /> The script will not create elemental guardians. <br /> Have fun and don't get banned! <br /> If using NPC Contact to repair pouches, make sure you have Abyssal book in your bank! <br /><br /> <b>NB</b> NPC Contact pouch repair doesn't seem to work; pay Apprentice Cordelia 25 abyssal pearls and have some in your inventory for smooth sailing. ")
public interface GotrConfig extends Config {

    @ConfigItem(
            keyName = "Mode",
            name = "Mode",
            description = "Type of mode",
            position = 0
    )
    default Mode Mode() {
        return Mode.BALANCED;
    }

    @ConfigItem(
            keyName = "maxFragmentAmount",
            name = "Max. amount fragments",
            description = "Max amount fragments to collect",
            position = 1
    )
    default int maxFragmentAmount() {
        return 100;
    }

    @ConfigItem(
            keyName = "maxAmountEssence",
            name = "Max. amount essence before using portal",
            description = "If you have more than the threshold defined, the player will not use the portal",
            position = 2
    )
    default int maxAmountEssence() {
        return 20;
    }

    @ConfigItem(
            keyName = "shouldDepositRunes",
            name = "Deposit runes?",
            description = "Should you deposit runes into the deposit pool?",
            position = 3
    )
    default boolean shouldDepositRunes() {
        return true;
    }
}
