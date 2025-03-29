package net.runelite.client.plugins.microbot.frosty.bloods;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.frosty.bloods.enums.HomeTeleports;
import net.runelite.client.plugins.microbot.frosty.bloods.enums.Teleports;

@ConfigGroup("Frosty")
@ConfigInformation(
        "• This plugin will craft runes at True Blood altar <br />" +
        "• Ensure you have a pool in POH as well as a fairy ring <br />" +
        "• <b> Ensure your last destination is DLS on fairy ring </b> <br />" +
        "• Ensure you have a Colossal pouch <br />" +
        "• Ensure you have Tiara or a bound Hat of the Eye equipped <br />" +
        "• Ensure you have a RunePouch <b> with runes for NPC contact </b> for pouch repair <br />" +
        "• Start at Crafting guild or Castle Wars lobby <br />"
)
public interface BloodsConfig extends Config {
    @ConfigSection(
            name = "Settings",
            description = "Settings",
            position = 2
    )
    String settingsSection = "Settings";
    @ConfigItem(
            keyName = "teleports",
            name = "Teleports",
            description = "If checked, we bank using Crafting Guild bank",
            position = 1,
            section = settingsSection
    )
    default Teleports teleports() {return Teleports.CRAFTING_CAPE;
    }
    @ConfigItem(
            keyName = "home teleports",
            name = "Going home",
            description = "Method of getting to POH",
            position = 2,
            section = settingsSection
    )
    default HomeTeleports homeTeleports() { return HomeTeleports.CONSTRUCTION_CAPE;}

    @ConfigItem(
            keyName = "useBloodEssence",
            name = "Use Blood Essence",
            description = "Check this if you want to use Blood Essence during runecrafting.",
            position = 3,
            section = settingsSection
    )
    default boolean useBloodEssence() {
        return false;
    }

    @ConfigItem(
            keyName = "use Dramen staff",
            name = "Use Dramen staff",
            description = "Check this if you are using Dramen staff",
            position = 4,
            section = settingsSection
    )
    default boolean useDramenStaff() {
        return false;
    }

}
