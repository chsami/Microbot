package net.runelite.client.plugins.microbot.frosty.bloods;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.frosty.bloods.enums.HomeTeleports;
import net.runelite.client.plugins.microbot.frosty.bloods.enums.Teleports;

@ConfigGroup("Frosty")
@ConfigInformation(
        "• This plugin will craft runes at True Blood altar <br />" +
        "• IF using POH, ensure you have pool and fairy ring <br />" +
        "• IF not using POH, have Ardougne cloak, house tabs and Ring of Duelings in bank <br />" +
        "• <b> Ensure your last destination is DLS on fairy ring </b> <br />" +
        "• Ensure you have a Colossal pouch <br />" +
        "• Ensure you have Tiara or a bound Hat of the Eye equipped <br />" +
        "• Ensure you have a RunePouch <b> with runes for NPC contact </b> for pouch repair <br />" +
        "• Start at Crafting guild or Ferox Enclave lobby <br />"

)
public interface BloodsConfig extends Config {
    @ConfigSection(
            name = "Settings",
            description = "Settings",
            position = 2
    )
    String settingsSection = "Settings";

    @ConfigItem(
            keyName = "Use POH",
            name = "Use POH",
            description = "Check if you have fairy ring and pool in POH",
            position = 6,
            section = settingsSection
    )
    default boolean usePoh() {
        return false;
    }
}