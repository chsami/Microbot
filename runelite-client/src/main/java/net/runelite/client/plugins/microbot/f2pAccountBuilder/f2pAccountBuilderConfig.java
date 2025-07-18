package net.runelite.client.plugins.microbot.f2pAccountBuilder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;

@ConfigGroup("accountBuilder")
@ConfigInformation("F2P Account Builder<br></br><br></br> Start your account off with 50-100k <br></br><br></br> Start the script and it'll decide what it wants to do. It'll change activity randomly between 10-40 minutes<br></br><br></br>Currently supports:<br></br><br></br>Fishing<br></br>Woodcutting<br></br>Crafting (Leather, Necklace)<br></br>Mining<br></br>Smelting (Bronze, Silver)<br></br>Firemaking<br></br>Cooking<br></br><br></br>If you're code savvy feel free to jump in!")
public interface f2pAccountBuilderConfig extends Config {
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
