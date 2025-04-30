package net.runelite.client.plugins.microbot.woodcutting;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingResetOptions;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingTree;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingWalkBack;

@ConfigGroup("Woodcutting")
public interface AutoWoodcuttingConfig extends Config {
    @ConfigSection(
            name = "General",
            description = "General",
            position = 0
    )
    String generalSection = "general";

    @ConfigItem(
            keyName = "Tree",
            name = "Tree",
            description = "Choose the tree",
            position = 0,
            section = generalSection
    )
    default WoodcuttingTree TREE()
    {
        return WoodcuttingTree.TREE;
    }

    @ConfigItem(
            keyName = "DistanceToStray",
            name = "Distance to Stray",
            description = "Set how far you can travel from your initial position in tiles",
            position = 1,
            section = generalSection
    )
    default int distanceToStray()
    {
        return 20;
    }

    @ConfigItem(
            keyName = "Hop",
            name = "Autohop when player detected",
            description = "Auto hop when a nearby player is detected",
            position = 2,
            section = generalSection
    )
    default boolean hopWhenPlayerDetected()
    {
        return false;
    }

    @ConfigItem(
            keyName = "Firemake",
            name = "Firemake only",
            description = "Turns into an Auto Firemaker only mode , start plugin initially at desired firemaking starting position , tested only at GE - North East ",
            position = 3,
            section = generalSection
    )
    default boolean firemakeOnly()
    {
        return false;
    }


    @ConfigSection(
            name = "Reset",
            description = "Options for clearing logs from inventory",
            position = 1
    )
    String resetSection = "reset";

    @ConfigItem(
            keyName = "ItemAction",
            name = "Item Action",
            description = "Task to perform with logs",
            position = 0,
            section = resetSection
    )
    default WoodcuttingResetOptions resetOptions()
    {
        return WoodcuttingResetOptions.DROP;
    }

    @ConfigItem(
            keyName = "ItemsToBank",
            name = "Items to bank (Comma seperated)",
            description = "Items to bank",
            position = 1,
            section = resetSection
    )
    default String itemsToBank() {
        return "logs";
    }
    
    @ConfigItem(
            keyName = "dropOrder",
            name = "Drop Order",
            description = "Order to drop items",
            position = 2,
            section = resetSection
    )
    default InteractOrder interactOrder() {
        return InteractOrder.STANDARD; 
    }

    @ConfigItem(
            keyName = "WalkBack",
            name = "Walk Back",
            description = "Walk back the initial spot or last cut down",
            position = 3,
            section = resetSection
    )
    default WoodcuttingWalkBack walkBack()
    {
        return WoodcuttingWalkBack.LAST_LOCATION;
    }
}
