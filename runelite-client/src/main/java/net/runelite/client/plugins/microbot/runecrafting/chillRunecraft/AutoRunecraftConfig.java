package net.runelite.client.plugins.microbot.runecrafting.chillRunecraft;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("autoRunecraft")
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
