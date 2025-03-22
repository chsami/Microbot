package net.runelite.client.plugins.microbot.npcTanner;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("NPC Tanner")
@ConfigInformation("Before you start. In Runelite Plugins Go to Menu Entry Swapper -> UI Swaps and make sure Tan is selected.<br/> This script supports: Cowhide, Green dragonhide, Blue dragonhide, Red Dragonhide, and Black dragonhide.")
public interface npcTannerConfig extends Config {
    @ConfigItem(
            keyName = "tanLeather",
            name = "Tan Soft Leather",
            description = "Select default tanning to soft leather instead of hard leather",
            position = 2
    )
    default boolean tanLeather() {
        return false;
    }
}
