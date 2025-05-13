package net.runelite.client.plugins.microbot.animatedarmour;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;

@ConfigGroup("animatedarmour")
public interface AnimatedArmourConfig extends Config {
            @ConfigItem(
            name = "Guide",
            keyName = "guide",
            position = 0,
            description = ""

    )
    default String guide() {
        return "Reanimates and kills armour in warriors guild, grabs tokens.\n" +
                " make sure you have armour (and optionally food) in inventory.\n" +
                "turn on auto retaliate and ground items runelite plugin";
    }

    @ConfigItem(
            name = "Food",
            keyName = "food",
            position = 1,
            description = "Food fetch from bank"
    )
    default Rs2Food food() {
        return Rs2Food.SALMON;
    }

    @ConfigItem(
            name = "foodAmount",
            keyName = "foodAmount",
            position = 1,
            description = "Food amount to fetch from bank"
    )
    default int foodAmount() {
        return 0;
    }
}
