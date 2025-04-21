package net.runelite.client.plugins.microbot.frosty.frostyrc;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.frosty.frostyrc.enums.RuneType;


@ConfigGroup("Frosty")
@ConfigInformation(
        "• This plugin will craft Blood and Wrath runes<br />" +
        "• <b>IF using Farming Cape, it must be used with POH</b> />" +
        "• IF making wrath runes, Myth cape must be in inventory, <b> not </b> equipped<br />" +
        "• IF using POH, ensure you have pool and fairy ring <br />" +
        "• IF not using POH, have Ardougne cloak, house tabs and Ring of Duelings(8) in bank <br />" +
        "• <b> Ensure your last destination is DLS on fairy ring </b> <br />" +
        "• Ensure you have a Colossal pouch <br />" +
        "• Ensure you have Tiara or a bound Hat of the Eye equipped <br />" +
        "• Ensure you have a RunePouch <b> with runes for NPC contact </b> for pouch repair <br />" +
        "• Start at Crafting guild or Ferox Enclave lobby <br />"

)
public interface RcConfig extends Config {
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
            position = 1,
            section = settingsSection
    )
    default boolean usePoh() {
        return false;
    }

    @ConfigItem(
            keyName = "rune type",
            name = "Rune type",
            description = "Select which type of rune to craft",
            position = 2,
            section = settingsSection
    )
    default RuneType runeType() {return RuneType.BLOOD;}


}