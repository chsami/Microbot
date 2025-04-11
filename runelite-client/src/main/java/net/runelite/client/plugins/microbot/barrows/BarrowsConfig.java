package net.runelite.client.plugins.microbot.barrows;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;

@ConfigGroup("barrows")
@ConfigInformation("1. Start with your ring of dueling equipped.<br /><br /> 2. Your auto-cast spell selected. <br /><br /> 3. Your chosen food in the inventory. <br /><br /> Required items: prayer potions, forgotten brews, barrows teleports, food, catalyic Runes, and a spade.<br /><br /> Spells: Wind: Blast, Wave, and Surge. <br /><br /> Special thanks to george for adding the barrows dungeon to the walker! <br /><br />")
public interface BarrowsConfig extends Config {
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
