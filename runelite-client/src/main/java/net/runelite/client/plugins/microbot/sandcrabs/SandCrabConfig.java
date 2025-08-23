package net.runelite.client.plugins.microbot.sandcrabs;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;

@ConfigGroup("SandCrabPlugin")
public interface SandCrabConfig extends Config {

    @ConfigItem(
            keyName = "Use Food",
            name = "Use Food",
            description = "Use Food?",
            position = 0
    )
    default boolean useFood()
    {
        return false;
    }
    @ConfigItem(
            keyName = "Food",
            name = "Food",
            description = "type of food",
            position = 1
    )
    default Rs2Food food()
    {
        return Rs2Food.MONKFISH;
    }

    @ConfigItem(
            keyName = "threeNpcs",
            name = "Three Sand Crab Spots",
            description = "Only use the sandcrabs spots that have 3 npcs",
            position = 2
    )
    default boolean threeNpcs()
    {
        return false;
    }

    @ConfigItem(
            keyName = "inventorySetup",
            name = "Inventory Setup",
            description = "Inventory Setup to use for sand crabs?",
            position = 3
    )
    default InventorySetup inventorySetup() { return null; }

}