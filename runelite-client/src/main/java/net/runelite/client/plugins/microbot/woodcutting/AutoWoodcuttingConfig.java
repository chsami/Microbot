package net.runelite.client.plugins.microbot.woodcutting;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingResetOptions;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingTree;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingWalkBack;

@ConfigGroup(AutoWoodcuttingConfig.configGroup)
@ConfigInformation(
        "<html>" +
                "<p>This script automatically cuts trees and handles the logs based on your settings.</p>" +
                "<p>Forestry support implemented by Yuof and TaF</p>" +
                "<p>If forestry is enabled, remember to use one of the forestry worlds for best results</p>" +
                "</html>")
public interface AutoWoodcuttingConfig extends Config {
    String configGroup = "AutoWoodcutting";
    @ConfigSection(
            name = "General",
            description = "General",
            position = 0
    )
    String generalSection = "general";
    @ConfigSection(
            name = "Reset",
            description = "Options for clearing logs from inventory",
            position = 1
    )
    String resetSection = "reset";

    @ConfigSection(
            name = "Forestry",
            description = "Forestry events",
            position = 2,
            closedByDefault = true
    )
    String forestrySection = "forestry";

    @ConfigItem(
            keyName = "Tree",
            name = "Tree",
            description = "Choose the tree",
            position = 0,
            section = generalSection
    )
    default WoodcuttingTree TREE() {
        return WoodcuttingTree.TREE;
    }

    @ConfigItem(
            keyName = "DistanceToStray",
            name = "Distance to Stray",
            description = "Set how far you can travel from your initial position in tiles",
            position = 1,
            section = generalSection
    )
    default int distanceToStray() {
        return 20;
    }

    @ConfigItem(
            keyName = "Hop",
            name = "Autohop when player detected",
            description = "Auto hop when a nearby player is detected",
            position = 2,
            section = generalSection
    )
    default boolean hopWhenPlayerDetected() {
        return false;
    }

    @ConfigItem(
            keyName = "Firemake",
            name = "Firemake only",
            description = "Turns into an Auto Firemaker only mode , start plugin initially at desired firemaking starting position , tested only at GE - North East ",
            position = 4,
            section = generalSection
    )
    default boolean firemakeOnly() {
        return false;
    }

    @ConfigItem(
            keyName = "HardwoodTreePatch",
            name = "Woodcut at Hardwood Tree Patch",
            description = "Woodcut at Hardwood Tree Patch",
            position = 5,
            section = generalSection
    )
    default boolean HardwoodTreePatch() {
        return false;
    }

    @ConfigItem(
            keyName = "LootNests",
            name = "Loot Bird Nests",
            description = "Loot bird nests from trees and events",
            position = 6,
            section = generalSection
    )
    default boolean lootBirdNests() { return true; }

    @ConfigItem(
            keyName = "LootSeeds",
            name = "Loot Seeds",
            description = "Loot seeds from events",
            position = 7,
            section = generalSection
    )
    default boolean lootSeeds() { return true; }

    @ConfigItem(
            keyName = "LootMyItemsOnly",
            name = "Loot my items only",
            description = "Only loot your items (Ironman)",
            position = 8,
            section = generalSection
    )
    default boolean lootMyItemsOnly() { return false;}

    @ConfigItem(
            keyName = "ItemAction",
            name = "Item Action",
            description = "Task to perform with logs",
            position = 0,
            section = resetSection
    )
    default WoodcuttingResetOptions resetOptions() {
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
        return "logs,sturdy beehive parts,petal garland,golden pheasant egg,pheasant tail feathers,fox whistle,key,nest,fruit";
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
            keyName = "ItemsToKeep",
            name = "Items to keep when dropping (Comma separated)",
            description = "Items to keep in inventory",
            position = 3,
            section = resetSection
    )
    default String itemsToKeep() {
        return "axe,tinderbox,crystal shard,demon tear,petal garland,golden pheasant egg,pheasant tail feathers,fox whistle,key";
    }

    @ConfigItem(
            keyName = "WalkBack",
            name = "Walk Back",
            description = "Walk back the initial spot or last cut down",
            position = 4,
            section = resetSection
    )
    default WoodcuttingWalkBack walkBack() {
        return WoodcuttingWalkBack.LAST_LOCATION;
    }

    @ConfigItem(
            keyName = "enableForestry",
            name = "Enable forestry",
            description = "Enable forestry features",
            position = 0,
            section = forestrySection
    )
    default boolean enableForestry() {
        return false;
    }

     @ConfigItem(
             keyName = "eggEvent",
             name = "Enable Egg Event",
             description = "Enable the Egg forestry event",
             position = 1,
             section = forestrySection
     )
     default boolean eggEvent() {
         return true;
     }

     @ConfigItem(
             keyName = "entlingsEvent",
             name = "Enable Entlings Event",
             description = "Enable the Entlings forestry event",
             position = 2,
             section = forestrySection
     )
     default boolean entlingsEvent() {
         return true;
     }

     @ConfigItem(
             keyName = "flowersEvent",
             name = "Enable Flowers Event",
             description = "Enable the Flowers forestry event",
             position = 3,
             section = forestrySection,
             hidden = true //TODO: Remove this when the event is implemented
     )
     default boolean flowersEvent() {
         return false;
     }

     @ConfigItem(
             keyName = "foxEvent",
             name = "Enable Fox Event",
             description = "Enable the Fox forestry event",
             position = 4,
             section = forestrySection
     )
     default boolean foxEvent() {
         return true;
     }

     @ConfigItem(
             keyName = "hivesEvent",
             name = "Enable Hives Event",
             description = "Enable the Hives forestry event",
             position = 5,
             section = forestrySection
     )
     default boolean hivesEvent() {
         return true;
     }

     @ConfigItem(
             keyName = "leprechaunEvent",
             name = "Enable Leprechaun Event",
             description = "Enable the Leprechaun forestry event",
             position = 6,
             section = forestrySection
     )
     default boolean leprechaunEvent() {
         return true;
     }

     @ConfigItem(
             keyName = "ritualEvent",
             name = "Enable Ritual Event",
             description = "Enable the Ritual forestry event",
             position = 7,
             section = forestrySection
     )
     default boolean ritualEvent() {
         return true;
     }

     @ConfigItem(
             keyName = "rootEvent",
             name = "Enable Root Event",
             description = "Enable the Root forestry event",
             position = 8,
             section = forestrySection
     )
     default boolean rootEvent() {
         return true;
     }

     @ConfigItem(
             keyName = "saplingEvent",
             name = "Enable Struggling Sapling Event",
             description = "Enable the Struggling Sapling forestry event",
             position = 9,
             section = forestrySection
     )
     default boolean saplingEvent() {
         return true;
     }
}
