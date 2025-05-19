package net.runelite.client.plugins.microbot.cooking;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.cooking.enums.*;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;

@ConfigGroup("autocooking")
public interface AutoCookingConfig extends Config {

    @ConfigSection(
            name = "General",
            description = "General Cooking Settings",
            position = 0
    )
    String generalSection = "general";

    @ConfigSection(
            name = "Cooking",
            description = "Cooking",
            position = 1
    )
    String cookingSection = "cooking";

    @ConfigItem(
            name = "Guide",
            keyName = "guide",
            position = 0,
            description = "",
            section = generalSection
    )
    default String guide() {
        return "This plugin allows for semi-AFK cooking, start the script with an empty inventory\n" +
                "1. Ensure to prepare your bank with ingredients\n" +
                "2. Use nearest cooking location will override the configured cooking location & choose the nearest from the configured locations\n\n" +
                "At the moment, only cooked fish are supported. Other cooking activities will be added in the future";
    }

    @ConfigItem(
            name = "Cooking Activity",
            keyName = "cookingActivity",
            position = 1,
            description = "Choose AutoCooking Activity",
            section = generalSection
    )
    default CookingActivity cookingActivity() {
        return CookingActivity.COOKING;
    }

    @ConfigSection(
            name = "Burn Baking",
            description = "Burn Baking Settings",
            position = 3,
            closedByDefault = true
    )
    String burnBakingSection = "burnBakingSection";

    @ConfigItem(
            keyName = "burnBakingDescription",
            name = "About Burn Baking",
            description = "Information about this plugin",
            position = 1,
            section = burnBakingSection
    )
    default String burnBakingDescription() {
        return "The goal with burn baking is to burn the most valuable foods from a low cooking level in F2P:\n\n" +
                "1. To Burn Bake, in Cooking Activity, select Burn Baking and select below your desired cooking level.\n" +
                "2. Start from level 1 cooking in the Clan Hall by the Bank, burn baking exclusively works in the Clan hall.\n" +
                "3. From level 1-40 cooking minimum required ingredients are 420 pots of flour, 420 buckets of water, " +
                "420 bowls of water, 420 potatoes and 420 Cooked Meat or Cooked Chicken.\n" +
                "4. From 40 cooking onwards the script will only bake cakes. The most optimal for burnt cakes is 1200 " +
                "of each cake ingredient from level 40 cooking.\n" +
                "NOTE: HAVE AN EVEN AMOUNT OF EACH INGREDIENT FOR EACH PRODUCT";
    }

    @ConfigItem(
            keyName = "desiredCookingLevel",
            name = "Desired Cooking Level",
            description = "Enter the cooking level you'd like to aim for when burn baking",
            position = 2,
            section = burnBakingSection
    )
    default int cookingLevel() {
        return 56; // Default level, can be changed by the user
    }

    @ConfigItem(
            name = "Item to Cook",
            keyName = "itemToCook",
            position = 0,
            description = "Item to cook",
            section = cookingSection
    )
    default CookingItem cookingItem() {
        return CookingItem.RAW_SHRIMP;
    }

    @ConfigItem(
            name = "Humidify Item",
            keyName = "humidifyItem",
            position = 1,
            description = "The item you wish to use to make to humidify the dough",
            section = cookingSection
    )
    default HumidifyItem humidifyItem() {
        return HumidifyItem.JUG;
    }

    @ConfigItem(
            name = "Location",
            keyName = "cookingLocation",
            position = 2,
            description = "Location to cook",
            section = cookingSection
    )
    default CookingLocation cookingLocation() {
        return CookingLocation.COOKS_KITCHEN;
    }

    @ConfigItem(
            name = "Use Nearest Cooking Location",
            keyName = "useNearestCookingLocation",
            position = 3,
            description = "Use Nearest Cooking location (this overrides the specified cooking location)",
            section = cookingSection
    )
    default boolean useNearestCookingLocation() {
        return false;
    }

    @ConfigItem(
            name = "Drop Burnt Items",
            keyName = "shouldDropBurntItems",
            position = 4,
            description = "Should drop burnt items when resetting inventory",
            section = cookingSection
    )
    default boolean shouldDropBurntItems() {
        return true;
    }

    @ConfigItem(
            name = "DropOrder",
            keyName = "dropOrder",
            position = 5,
            description = "The order in which to drop items",
            section = cookingSection
    )
    default InteractOrder getDropOrder() {
        return InteractOrder.STANDARD;
    }

}
