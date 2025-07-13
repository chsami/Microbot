package net.runelite.client.plugins.microbot.TaF.stonechests;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigInformation("This plugin automates thieving from stone chests in the Lizardman Temple. It will picklock the chests and handle inventory management. Make sure to have a lockpick & antipoisons in your inventory.")
@ConfigGroup("Stone chest thiever")
public interface StoneChestThieverConfig extends Config {
    @ConfigItem(
            position = 1,
            keyName = "cutGems",
            name = "Cut sapphire and ruby gems?",
            description = "Cuts and drops the sapphire and ruby gems you get from the chests. Make sure you have a chisel in your inventory."
    )
    default boolean cutGems() {
        return true;
    }
}
