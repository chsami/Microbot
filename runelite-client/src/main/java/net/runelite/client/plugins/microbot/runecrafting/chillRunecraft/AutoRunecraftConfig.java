package net.runelite.client.plugins.microbot.runecrafting.chillRunecraft;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("autoRunecraft")
@ConfigInformation(
        "<h2 style='color: #05e1f5;'>ChillX's Auto Runecrafter</h2> <br />" +
        "• This plugin automatically crafts runes at the selected altar <br />" +
        "• Ensure you have pure essence and the correct tiara / talisman in the bank <br />" +
        "• Will continue until out of essence or stopped manually <br />"
)
public interface AutoRunecraftConfig extends Config
{
    @ConfigItem(
            keyName = "Altar",
            name = "Altar",
            description = "Choose the altar to RC at",
            position = 0
    )
    default Altars ALTAR()
    {
        return Altars.AIR_ALTAR;
    }
}
