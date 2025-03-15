package net.runelite.client.plugins.microbot.bee.Baking;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("baking")
public interface BakingConfig extends Config {

    @ConfigItem(
            name = "Guide",
            keyName = "guide",
            position = 0,
            description = "This plugin's goal is to burn the most valuable f2p foods to sell on the burnt marker. It also doubles as a great one click progressive cooker. 1. Start from level 1 cooking in the Clan Hall by the Bank 2. From level 1-40 cooking minimum required ingredients are 420 pots of flour, 420 buckets of water, 420 bowls of water, 420 potatoes, 420 Cooked Meat or Cooked Chicken. From 40 cooking onwards the script will only bake cakes. The most optimal for burnt cakes is 1200 of each cake ingredient."
    )
    default String guide() {
        return "only baking";
    }

    @ConfigItem(
            name = "Cooking Activity",
            keyName = "cookingActivity",
            position = 1,
            description = "Choose AutoCooking Activity"
    )
    default String cookingActivity() {
        return "Burn Baking";
    }

    @ConfigItem(
            name = "Item to Cook",
            keyName = "itemToCook",
            position = 0,
            description = "Item to cook"
    )
    default String cookingItem() {
        return "Uncooked Cake";
    }

    @ConfigItem(
            keyName = "desiredCookingLevel",
            name = "Desired Cooking Level",
            description = "Enter the cooking level you'd like to aim for"
    )
    default int cookingLevel() {
        return 56; // Default level, can be changed by the user
    }
}
