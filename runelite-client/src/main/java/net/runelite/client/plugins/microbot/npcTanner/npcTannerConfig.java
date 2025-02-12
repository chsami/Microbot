package net.runelite.client.plugins.microbot.npcTanner;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;

@ConfigGroup("NPC Tanner")
@ConfigInformation("Before you start. In Runelite Plugins Go to Menu Entry Swapper -> UI Swaps and make sure Tan is selected.<br/> This script supports: Cowhide, Green dragonhide, Blue dragonhide, Red Dragonhide, and Black dragonhide.")
public interface npcTannerConfig extends Config {
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
