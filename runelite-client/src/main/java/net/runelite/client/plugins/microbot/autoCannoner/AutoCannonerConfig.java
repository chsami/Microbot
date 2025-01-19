package net.runelite.client.plugins.microbot.autoCannoner;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("autoCannoner")
@ConfigInformation(
        "<h2 style='color: #05e1f5;'>ChillX's Auto Cannoner</h2> <br />" +
                "• This plugin automatically reloads and fixes your cannon <br />" +
                "• Ensure you have cannonballs in inventory <br />" +
                "• Will continue until out of cannonballs or stopped manually <br />"
)
public interface AutoCannonerConfig extends Config {
    @ConfigItem(
            keyName = "Min Cannonballs",
            name = "Min Cannonballs",
            description = "Minimum cannonballs before reload",
            position = 0
    )
    default int minBalls()
    {
        return 6;
    }

    @ConfigItem(
            keyName = "Max Cannonballs",
            name = "Max Cannonballs",
            description = "Maximum cannonballs before reload",
            position = 0
    )
    default int maxBalls()
    {
        return 11;
    }
}
