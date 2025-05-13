package net.runelite.client.plugins.microbot.gemMining;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;

@ConfigGroup("Mining")
@ConfigInformation("Start in the Shilo Village underground mine with your gem bag in your inventory and a charged amulet of glory equipped.<br /><br />")
public interface GemConfig extends Config {
/*    @ConfigItem(
            keyName = "Ore",
            name = "Ore",
            description = "Choose the ore",
            position = 0
    )
    default List<String> ORE()
    {
        return Rocks.TIN;
    }*/
}