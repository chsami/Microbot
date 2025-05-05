package net.runelite.client.plugins.microbot.VoxPlugins.schedulable.example;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;

import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;

@ConfigGroup("SchedulableExample")
public interface SchedulableExampleConfig extends Config {
        @ConfigSection(
                name = "Start Conditions",
                description = "Conditions for when the plugin is allowed to start",
                position = 0
        )
        String startConditionSection = "startConditions";
        
        @ConfigSection(
                name = "Location Start Conditions",
                description = "Location-based conditions for when the plugin is allowed to start",
                position = 1,
                closedByDefault = false        
        )
        String locationStartConditionSection = "locationStartConditions";
    
        // Location Start Condition Settings
        @ConfigItem(
                keyName = "enableLocationStartCondition",
                name = "Enable Location Start Condition",
                description = "Enable location-based start condition",
                position = 0,
                section = locationStartConditionSection
        )
        default boolean enableLocationStartCondition() {
                return false;
        }

        @ConfigItem(
                keyName = "locationStartType",
                name = "Location Type",
                description = "Type of location condition to use for starting the plugin",
                position = 1,
                section = locationStartConditionSection
        )
        default LocationStartType locationStartType() {
                return LocationStartType.BANK;
        }

        @ConfigItem(
                keyName = "bankStartLocation",
                name = "Bank Location",
                description = "Bank location where the plugin should start",
                position = 2,
                section = locationStartConditionSection
        )
        default BankLocation bankStartLocation() {
                return BankLocation.GRAND_EXCHANGE;
        }
        @Range(
            min = 10,
            max = 100
        )
        @ConfigItem(
                keyName = "bankDistance",
                name = "Bank Distance (tiles)",
                description = "Maximum distance from bank to start the plugin",
                position = 3,                                
                section = locationStartConditionSection
        )
        default int bankDistance() {
                return 20;
        }

        @ConfigItem(
                keyName = "customAreaActive",
                name = "Custom Area Active",
                description = "Whether a custom area has been defined using the hotkey",
                position = 4,
                section = locationStartConditionSection,
                hidden = true
        )
        default boolean customAreaActive() {
                return false;
        }

        void setCustomAreaActive(boolean active);

        @ConfigItem(
                keyName = "areaMarkHotkey",
                name = "Area Mark Hotkey",
                description = "Hotkey to mark current position as center of custom area (press again to clear)",
                position = 5,
                section = locationStartConditionSection
        )
        default Keybind areaMarkHotkey() {
                return Keybind.NOT_SET;
        }

        @ConfigItem(
                keyName = "customAreaRadius",
                name = "Custom Area Radius (tiles)",
                description = "Radius of the custom area around the marked position",
                position = 6,
                section = locationStartConditionSection
        )
        default int customAreaRadius() {
                return 10;
        }

        @ConfigItem(
                keyName = "customAreaCenter",
                name = "Custom Area Center",
                description = "Center point of the custom area",
                position = 7,
                section = locationStartConditionSection,
                hidden = true
        )
        default WorldPoint customAreaCenter() {
                return null;
        }

        void setCustomAreaCenter(WorldPoint center);

        // Enum for location start types
        enum LocationStartType {
                BANK("Bank Location"),
                CUSTOM_AREA("Custom Area");
                
                private final String name;
                
                LocationStartType(String name) {
                this.name = name;
                }
                
                @Override
                public String toString() {
                return name;
                }
        }


    @ConfigSection(
        name = "Stop Conditions",
        description = "Conditions for when the plugin should stop",
        position = 101
    )
    String stopSection = "stopConditions";
    
    @ConfigSection(
        name = "Time Conditions",
        description = "Time-based conditions for stopping the plugin",
        position = 102,
        closedByDefault = false       
    )
    String timeConditionSection = "timeConditions";
    
    @ConfigSection(
        name = "Loot Item Conditions",
        description = "Conditions related to looted items",
        position = 103,
        closedByDefault = false
    )
    String lootItemConditionSection = "lootItemConditions";
    
    @ConfigSection(
        name = "Gathered Resource Conditions",
        description = "Conditions related to gathered resources (mining, fishing, etc.)",
        position = 104,
        closedByDefault = false
    )
    String gatheredResourceConditionSection = "gatheredResourceConditions";
    
    @ConfigSection(
        name = "Process Item Conditions",
        description = "Conditions related to processed items (crafting, smithing, etc.)",
        position = 105,
        closedByDefault = false
    )
    String processItemConditionSection = "processItemConditions";
    @ConfigSection(
        name = "NPC Conditions",
        description = "Conditions related to NPCs",
        position = 106,
        closedByDefault = false        
    )
    String npcConditionSection = "npcConditions";
    
    
    // Time Condition Settings
    @ConfigItem(
        keyName = "enableTimeCondition",
        name = "Enable Time Condition",
        description = "Enable time-based stop condition",
        position = 0,
        section = timeConditionSection
    )
    default boolean enableTimeCondition() {
        return true;
    }
    
    @ConfigItem(
        keyName = "minRuntime",
        name = "Minimum Runtime (minutes)",
        description = "Minimum time to run before stopping",
        position = 1,
        section = timeConditionSection
    )
    default int minRuntime() {
        return 1;
    }

    @ConfigItem(
        keyName = "maxRuntime",
        name = "Maximum Runtime (minutes)",
        description = "Maximum time to run before stopping",
        position = 2,
        section = timeConditionSection
    )
    default int maxRuntime() {
        return 2;
    }

    // Loot Item Condition Settings
    @ConfigItem(
        keyName = "enableLootItemCondition",
        name = "Enable Loot Item Condition",
        description = "Enable condition to stop based on looted items",
        position = 0,
        section = lootItemConditionSection
    )
    default boolean enableLootItemCondition() {
        return true;
    }
    
    @ConfigItem(
        keyName = "lootItems",
        name = "Loot Items to Track",
        description = "Comma separated list of items. Supports regex patterns (.*bones.*)",
        position = 1,
        section = lootItemConditionSection
    )
    default String lootItems() {
        return "Logs";
    }

    @ConfigItem(
        keyName = "itemsToLootLogical",
        name = "Or(False)/And(True)",
        description = "Logical operator for items to loot: False=OR, True=AND",
        position = 2,
        section = lootItemConditionSection
    )
    default boolean itemsToLootLogical() {
        return false;
    }
    
    @ConfigItem(
        keyName = "minItems",
        name = "Minimum Items",
        description = "Minimum number of items to loot before stopping",
        position = 3,
        section = lootItemConditionSection
    )
    default int minItems() {
        return 5;
    }

    @ConfigItem(
        keyName = "maxItems",
        name = "Maximum Items",
        description = "Maximum number of items to loot before stopping",
        position = 4,
        section = lootItemConditionSection
    )
    default int maxItems() {
        return 10;
    }
    
    @ConfigItem(
        keyName = "includeNoted",
        name = "Include Noted Items",
        description = "Include noted items in loot tracking",
        position = 5,
        section = lootItemConditionSection
    )
    default boolean includeNoted() {
        return false;
    }
    
    @ConfigItem(
        keyName = "allowNoneOwner",
        name = "Allow None Owner",
        description = "Allow items not owned by the player (e.g. items which are spawned)",
        position = 6,
        section = lootItemConditionSection
    )
    default boolean allowNoneOwner() {
        return false;
    }
    
    // Gathered Resource Condition Settings
    @ConfigItem(
        keyName = "enableGatheredResourceCondition",
        name = "Enable Gathered Resource Condition",
        description = "Enable condition to stop based on gathered resources",
        position = 0,
        section = gatheredResourceConditionSection
    )
    default boolean enableGatheredResourceCondition() {
        return false;
    }
    
    @ConfigItem(
        keyName = "gatheredResources",
        name = "Resources to Track",
        description = "Comma separated list of resources to track (e.g. logs,ore,fish)",
        position = 1,
        section = gatheredResourceConditionSection
    )
    default String gatheredResources() {
        return "logs";
    }
    
    @ConfigItem(
        keyName = "resourcesLogical",
        name = "Or(False)/And(True)",
        description = "Logical operator for resources: False=OR, True=AND",
        position = 2,
        section = gatheredResourceConditionSection
    )
    default boolean resourcesLogical() {
        return false;
    }
    
    @ConfigItem(
        keyName = "minResources",
        name = "Minimum Resources",
        description = "Minimum number of resources to gather before stopping",
        position = 3,
        section = gatheredResourceConditionSection
    )
    default int minResources() {
        return 10;
    }
    
    @ConfigItem(
        keyName = "maxResources",
        name = "Maximum Resources",
        description = "Maximum number of resources to gather before stopping",
        position = 4,
        section = gatheredResourceConditionSection
    )
    default int maxResources() {
        return 15;
    }
    
    @ConfigItem(
        keyName = "includeResourceNoted",
        name = "Include Noted Resources",
        description = "Include noted resources in tracking",
        position = 5,
        section = gatheredResourceConditionSection
    )
    default boolean includeResourceNoted() {
        return false;
    }
    
    // Process Item Condition Settings
    @ConfigItem(
        keyName = "enableProcessItemCondition",
        name = "Enable Process Item Condition",
        description = "Enable condition to stop based on processed items",
        position = 0,
        section = processItemConditionSection
    )
    default boolean enableProcessItemCondition() {
        return false;
    }
    
    @ConfigItem(
        keyName = "trackingMode",
        name = "Tracking Mode",
        description = "How to track item processing (source items consumed or target items produced)",
        position = 1,
        section = processItemConditionSection
    )
    default ProcessTrackingMode trackingMode() {
        return ProcessTrackingMode.SOURCE_CONSUMPTION;
    }
    
    @ConfigItem(
        keyName = "sourceItems",
        name = "Source Items",
        description = "Comma separated list of source items (e.g. logs,ore)",
        position = 2,
        section = processItemConditionSection
    )
    default String sourceItems() {
        return "logs";
    }
    
    @ConfigItem(
        keyName = "targetItems",
        name = "Target Items",
        description = "Comma separated list of target items (e.g. bow,shield)",
        position = 3,
        section = processItemConditionSection
    )
    default String targetItems() {
        return "bow";
    }
    
    @ConfigItem(
        keyName = "minProcessedItems",
        name = "Minimum Processed Items",
        description = "Minimum number of items to process before stopping",
        position = 4,
        section = processItemConditionSection
    )
    default int minProcessedItems() {
        return 5;
    }
    
    @ConfigItem(
        keyName = "maxProcessedItems",
        name = "Maximum Processed Items",
        description = "Maximum number of items to process before stopping",
        position = 5,
        section = processItemConditionSection
    )
    default int maxProcessedItems() {
        return 10;
    }
        // NPC Kill Count Condition Settings
        @ConfigItem(
        keyName = "enableNpcKillCountCondition",
        name = "Enable NPC Kill Count Condition",
        description = "Enable condition to stop based on NPC kill count",
        position = 0,
        section = npcConditionSection
    )
    default boolean enableNpcKillCountCondition() {
        return false;
    }
    @ConfigItem(
        keyName = "npcNames",
        name = "NPCs to Track",
        description = "Comma separated list of NPC names to track kills for. Supports regex patterns.",
        position = 1,
        section = npcConditionSection
    )
    default String npcNames() {
        return "goblin";
    }
    
    @ConfigItem(
        keyName = "npcLogical",
        name = "Or(False)/And(True)",
        description = "Logical operator for NPCs: False=OR (any NPC satisfies), True=AND (all NPCs must be killed)",
        position = 2,
        section = npcConditionSection
    )
    default boolean npcLogical() {
        return false;
    }
    
    @ConfigItem(
        keyName = "minKills",
        name = "Minimum Kills",
        description = "Minimum number of NPCs to kill before stopping",
        position = 3,
        section = npcConditionSection
    )
    default int minKills() {
        return 5;
    }
    
    @ConfigItem(
        keyName = "maxKills",
        name = "Maximum Kills",
        description = "Maximum number of NPCs to kill before stopping",
        position = 4,
        section = npcConditionSection
    )
    default int maxKills() {
        return 10;
    }
    
    @ConfigItem(
        keyName = "killsPerType",
        name = "Count Per NPC Type",
        description = "If true, need to kill the specified count of EACH NPC type. If false, count total kills across all types.",
        position = 5,
        section = npcConditionSection
    )
    default boolean killsPerType() {
        return true;
    }
    
    
    // Location tracking
    @ConfigItem(
        keyName = "lastLocation",
        name = "Last Location",
        description = "Last tracked location",
        hidden = true
    )
    default WorldPoint lastLocation() {
        return null;
    }    
    default void setLastLocation(WorldPoint location){
        if (location != null) {
            if (Microbot.getConfigManager() != null) {
                Microbot.getConfigManager().setConfiguration("SchedulableExample", "lastLocation", location);
            }
        }

    }
    
    // Enum for process tracking modes
    enum ProcessTrackingMode {
        SOURCE_CONSUMPTION,
        TARGET_PRODUCTION,
        EITHER,
        BOTH
    }

    @ConfigSection(
        name = "Debug Options",
        description = "Options for testing and debugging",
        position = 200,
        closedByDefault = true
    )
    String debugSection = "debugSection";

    @ConfigItem(
        keyName = "finishPluginNotSuccessfulHotkey",
        name = "Finish Plugin Not-Successful Hotkey",
        description = "Press this hotkey to manually trigger the PluginScheduleEntryFinishedEvent for testing not successful completion",
        position = 0,
        section = debugSection
    )
    default Keybind finishPluginNotSuccessfulHotkey() {
        return new Keybind(KeyEvent.VK_F2, 0);
    }

    @ConfigItem(
        keyName = "finishPluginSuccessfulHotkey",
        name = "Finish Plugin Hotkey",
        description = "Press this hotkey to manually trigger the PluginScheduleEntryFinishedEvent for testing successful completion",
        position = 0,
        section = debugSection
    )
    default Keybind finishPluginSuccessfulHotkey() {
        return new Keybind(KeyEvent.VK_F3, 0);
    }


    @ConfigItem(
        keyName = "finishReason",
        name = "Finish Reason",
        description = "The reason to report when finishing the plugin",
        position = 2,
        section = debugSection
    )
    default String finishReason() {
        return "Task completed successfully";
    }

    @ConfigItem(
        keyName = "lockConditionHotkey",
        name = "Lock Condition Hotkey",
        description = "Press this hotkey to toggle the lock condition (prevents plugin from being stopped)",
        position = 3,
        section = debugSection
    )
    default Keybind lockConditionHotkey() {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
        keyName = "lockDescription",
        name = "Lock Reason",
        description = "Description of why the plugin is locked",
        position = 4,
        section = debugSection
    )
    default String lockDescription() {
        return "Plugin in critical state - do not stop";
    }
}