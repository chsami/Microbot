package net.runelite.client.plugins.microbot.BurgerLooter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("burgerlooter")
public interface BurgerLooterConfig extends Config {
    @ConfigItem(
        keyName = "burgerThreshold",
        name = "Threshold to Sell",
        description = "Total Red Eclipse to collect before selling"
    )
    default int burgerThreshold() { return 112; }

    @ConfigItem(
        keyName = "burgerWorldGroup1",
        name = "World Group 1",
        description = "Enable world group 1"
    )
    default boolean burgerWorldGroup1() { return true; }
    @ConfigItem(keyName = "burgerWorldGroup2", name = "World Group 2", description = "Enable world group 2")
    default boolean burgerWorldGroup2() { return true; }
    @ConfigItem(keyName = "burgerWorldGroup3", name = "World Group 3", description = "Enable world group 3")
    default boolean burgerWorldGroup3() { return true; }
    @ConfigItem(keyName = "burgerWorldGroup4", name = "World Group 4", description = "Enable world group 4")
    default boolean burgerWorldGroup4() { return true; }
    @ConfigItem(keyName = "burgerWorldGroup5", name = "World Group 5", description = "Enable world group 5")
    default boolean burgerWorldGroup5() { return true; }
    @ConfigItem(keyName = "burgerWorldGroup6", name = "World Group 6", description = "Enable world group 6")
    default boolean burgerWorldGroup6() { return true; }
    @ConfigItem(keyName = "burgerWorldGroup7", name = "World Group 7", description = "Enable world group 7")
    default boolean burgerWorldGroup7() { return true; }
    @ConfigItem(keyName = "burgerWorldGroup8", name = "World Group 8", description = "Enable world group 8")
    default boolean burgerWorldGroup8() { return true; }

    @ConfigItem(
        keyName = "postLootSleep",
        name = "Post-Loot Sleep (ms)",
        description = "How long to sleep after successfully looting an item (ms)"
    )
    default int postLootSleep() { return 500; }

    @ConfigItem(
        keyName = "lootDetectionWindow",
        name = "Loot Detection Window (ms)",
        description = "How long to check for loot before hopping (ms)"
    )
    default int lootDetectionWindow() { return 2000; }
}
