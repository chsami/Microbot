package net.runelite.client.plugins.microbot.VoxPlugins.rs2cachedebugger;

import net.runelite.client.config.*;

import java.awt.Color;
import java.awt.event.KeyEvent;

/**
 * Configuration for the Rs2 Cache Debugger Plugin.
 * Provides comprehensive cache debugging, overlay and filtering options for NPCs, Objects, and Ground Items.
 */
@ConfigGroup("rs2cachedebugger")
public interface Rs2CacheDebuggerConfig extends Config {
    
    // ===== CONFIG SECTIONS =====
    
    @ConfigSection(
            name = "General Settings",
            description = "General plugin settings and hotkeys",
            position = 0
    )
    String generalSection = "general";
    
    @ConfigSection(
            name = "NPC Settings",
            description = "Configure NPC overlay appearance and filtering",
            position = 1
    )
    String npcSection = "npc";
    
    @ConfigSection(
            name = "Object Settings", 
            description = "Configure object overlay appearance and filtering",
            position = 2
    )
    String objectSection = "object";
    
    @ConfigSection(
            name = "Ground Item Settings",
            description = "Configure ground item overlay appearance and filtering",
            position = 3
    )
    String groundItemSection = "groundItem";
    
    @ConfigSection(
            name = "Info Panel Settings",
            description = "Configure the information panel display",
            position = 4
    )
    String infoPanelSection = "infoPanel";
    

    
    // ===== GENERAL SETTINGS =====
    
    @ConfigItem(
            keyName = "enablePlugin",
            name = "Enable Plugin",
            description = "Master toggle for the entire plugin",
            position = 0,
            section = generalSection
    )
    default boolean enablePlugin() {
        return true;
    }
    
    @ConfigItem(
            keyName = "maxRenderDistance",
            name = "Max Render Distance",
            description = "Maximum distance to render entities (in tiles)",
            position = 1,
            section = generalSection
    )
    @Range(min = 5, max = 50)
    default int maxRenderDistance() {
        return 15;
    }
    
    @ConfigItem(
            keyName = "verboseLogging",
            name = "Verbose Logging",
            description = "Enable detailed logging for debugging",
            position = 2,
            section = generalSection
    )
    default boolean verboseLogging() {
        return false;
    }
    
    // Hotkeys
    @ConfigItem(
            keyName = "toggleNpcOverlayHotkey",
            name = "Toggle NPC Overlay",
            description = "Hotkey to toggle NPC overlay on/off",
            position = 10,
            section = generalSection
    )
    default Keybind toggleNpcOverlayHotkey() {
        return new Keybind(KeyEvent.VK_F1,0);
    }
    
    @ConfigItem(
            keyName = "toggleObjectOverlayHotkey",
            name = "Toggle Object Overlay",
            description = "Hotkey to toggle object overlay on/off",
            position = 11,
            section = generalSection
    )
    default Keybind toggleObjectOverlayHotkey() {
        return new Keybind(KeyEvent.VK_F2,0);
    }
    
    @ConfigItem(
            keyName = "toggleGroundItemOverlayHotkey",
            name = "Toggle Ground Item Overlay",
            description = "Hotkey to toggle ground item overlay on/off",
            position = 12,
            section = generalSection
    )
    default Keybind toggleGroundItemOverlayHotkey() {
        return new Keybind(KeyEvent.VK_F3,0);
    }
    
    @ConfigItem(
            keyName = "toggleInfoPanelHotkey",
            name = "Toggle Info Panel",
            description = "Hotkey to toggle information panel on/off",
            position = 13,
            section = generalSection
    )
    default Keybind toggleInfoPanelHotkey() {
        return new Keybind(KeyEvent.VK_F4,0);
    }
    
    @ConfigItem(
            keyName = "logCacheInfoHotkey",
            name = "Log Cache Info",
            description = "Hotkey to log cache information to console",
            position = 14,
            section = generalSection
    )
    default Keybind logCacheInfoHotkey() {
        return new Keybind(KeyEvent.VK_F5,0);
    }
    
    @ConfigItem(
            keyName = "showCachePerformanceMetrics",
            name = "Show Cache Performance",
            description = "Show cache hit/miss ratio and performance metrics",
            position = 15,
            section = generalSection
    )
    default boolean showCachePerformanceMetrics() {
        return false;
    }
    
    @ConfigItem(
            keyName = "logCacheStatsTicks",
            name = "Cache Stats Log Interval",
            description = "Log cache statistics every X ticks (0 = disabled)",
            position = 16,
            section = generalSection
    )
    @Range(min = 0, max = 100)
    default int logCacheStatsTicks() {
        return 0;
    }
    
    // ===== NPC SETTINGS =====
    
    @ConfigItem(
            keyName = "enableNpcOverlay",
            name = "Enable NPC Overlay",
            description = "Show NPC overlays",
            position = 0,
            section = npcSection
    )
    default boolean enableNpcOverlay() {
        return false;
    }
    
    @ConfigItem(
            keyName = "npcRenderStyle",
            name = "NPC Render Style",
            description = "How to render NPC overlays",
            position = 1,
            section = npcSection
    )
    default RenderStyle npcRenderStyle() {
        return RenderStyle.HULL;
    }
    
    @ConfigItem(
            keyName = "npcFilterPreset",
            name = "NPC Filter Preset",
            description = "Preset filter for NPCs to display",
            position = 2,
            section = npcSection
    )
    default NpcFilterPreset npcFilterPreset() {
        return NpcFilterPreset.ALL;
    }
    
    @ConfigItem(
            keyName = "npcBorderColor",
            name = "NPC Border Color",
            description = "Color for NPC overlay borders",
            position = 3,
            section = npcSection
    )
    default Color npcBorderColor() {
        return Color.ORANGE;
    }
    
    @ConfigItem(
            keyName = "npcFillColor",
            name = "NPC Fill Color",
            description = "Color for NPC overlay fill",
            position = 4,
            section = npcSection
    )
    @Alpha
    default Color npcFillColor() {
        return new Color(255, 165, 0, 50);
    }
    
    @ConfigItem(
            keyName = "npcBorderWidth",
            name = "NPC Border Width",
            description = "Width of NPC overlay borders",
            position = 5,
            section = npcSection
    )
    @Range(min = 1, max = 10)
    default int npcBorderWidth() {
        return 2;
    }
    
    @ConfigItem(
            keyName = "npcShowNames",
            name = "Show NPC Names",
            description = "Display NPC names above them",
            position = 6,
            section = npcSection
    )
    default boolean npcShowNames() {
        return false;
    }
    
    @ConfigItem(
            keyName = "npcShowCombatLevel",
            name = "Show Combat Level",
            description = "Display NPC combat levels",
            position = 7,
            section = npcSection
    )
    default boolean npcShowCombatLevel() {
        return false;
    }
    
    @ConfigItem(
            keyName = "npcShowId",
            name = "Show NPC ID",
            description = "Display NPC game IDs",
            position = 8,
            section = npcSection
    )
    default boolean npcShowId() {
        return false;
    }
    
    @ConfigItem(
            keyName = "npcShowCoordinates",
            name = "Show World Coordinates",
            description = "Display world coordinates for NPCs",
            position = 9,
            section = npcSection
    )
    default boolean npcShowCoordinates() {
        return false;
    }
    
    @ConfigItem(
            keyName = "npcInteractingColor",
            name = "NPC Interacting Color",
            description = "Color for NPCs interacting with player",
            position = 10,
            section = npcSection
    )
    default Color npcInteractingColor() {
        return Color.RED;
    }
    
    @ConfigItem(
            keyName = "npcShowDistance",
            name = "Show Distance",
            description = "Display distance to NPCs",
            position = 9,
            section = npcSection
    )
    default boolean npcShowDistance() {
        return false;
    }
    
    @ConfigItem(
            keyName = "npcCustomFilter",
            name = "Custom NPC Filter",
            description = "Custom text filter for NPC names (leave empty to disable)",
            position = 10,
            section = npcSection
    )
    default String npcCustomFilter() {
        return "";
    }
    
    @ConfigItem(
            keyName = "npcMaxDistance",
            name = "NPC Max Distance",
            description = "Maximum distance to show NPCs (in tiles)",
            position = 11,
            section = npcSection
    )
    @Range(min = 3, max = 30)
    default int npcMaxDistance() {
        return 15;
    }
    
    // ===== OBJECT SETTINGS =====
    
    @ConfigItem(
            keyName = "enableObjectOverlay",
            name = "Enable Object Overlay",
            description = "Show object overlays",
            position = 0,
            section = objectSection
    )
    default boolean enableObjectOverlay() {
        return false;
    }
    
    @ConfigItem(
            keyName = "objectRenderStyle",
            name = "Object Render Style",
            description = "How to render object overlays",
            position = 1,
            section = objectSection
    )
    default RenderStyle objectRenderStyle() {
        return RenderStyle.HULL;
    }
    
    @ConfigItem(
            keyName = "objectFilterPreset",
            name = "Object Filter Preset",
            description = "Preset filter for objects to display",
            position = 2,
            section = objectSection
    )
    default ObjectFilterPreset objectFilterPreset() {
        return ObjectFilterPreset.INTERACTABLE;
    }
    
    @ConfigItem(
            keyName = "objectBorderColor",
            name = "Default Object Border Color",
            description = "Color for object overlay borders",
            position = 3,
            section = objectSection
    )
    default Color objectBorderColor() {
        return Color.BLUE;
    }
    
    @ConfigItem(
            keyName = "objectFillColor",
            name = "Object Fill Color",
            description = "Color for object overlay fill",
            position = 4,
            section = objectSection
    )
    @Alpha
    default Color objectFillColor() {
        return new Color(0, 0, 255, 50);
    }
    
    @ConfigItem(
            keyName = "objectBorderWidth",
            name = "Object Border Width",
            description = "Width of object overlay borders",
            position = 5,
            section = objectSection
    )
    @Range(min = 1, max = 10)
    default int objectBorderWidth() {
        return 2;
    }
    
    @ConfigItem(
            keyName = "objectShowNames",
            name = "Show Object Names",
            description = "Display object names",
            position = 6,
            section = objectSection
    )
    default boolean objectShowNames() {
        return false;
    }
    
    @ConfigItem(
            keyName = "objectShowId",
            name = "Show Object ID",
            description = "Display object game IDs and type",
            position = 7,
            section = objectSection
    )
    default boolean objectShowId() {
        return false;
    }
    
    @ConfigItem(
            keyName = "objectShowCoordinates",
            name = "Show World Coordinates",
            description = "Display world coordinates for objects",
            position = 8,
            section = objectSection
    )
    default boolean objectShowCoordinates() {
        return false;
    }
    
    @ConfigItem(
            keyName = "objectMaxDistance",
            name = "Object Max Distance",
            description = "Maximum distance to show objects (in tiles)",
            position = 7,
            section = objectSection
    )
    @Range(min = 3, max = 30)
    default int objectMaxDistance() {
        return 15;
    }
    
    @ConfigItem(
            keyName = "objectCustomFilter",
            name = "Custom Object Filter",
            description = "Custom text filter for object names (leave empty to disable)",
            position = 8,
            section = objectSection
    )
    default String objectCustomFilter() {
        return "";
    }
    
    // Different object type colors
    @ConfigItem(
            keyName = "bankColor",
            name = "Bank Color",
            description = "Color for bank objects",
            position = 10,
            section = objectSection
    )
    default Color bankColor() {
        return Color.GREEN;
    }
    
    @ConfigItem(
            keyName = "altarColor", 
            name = "Altar Color",
            description = "Color for altar objects",
            position = 11,
            section = objectSection
    )
    default Color altarColor() {
        return Color.CYAN;
    }
    
    @ConfigItem(
            keyName = "resourceColor",
            name = "Resource Color", 
            description = "Color for resource objects (trees, rocks)",
            position = 12,
            section = objectSection
    )
    default Color resourceColor() {
        return Color.YELLOW;
    }
    
    @ConfigItem(
            keyName = "gameObjectColor",
            name = "GameObject Color",
            description = "Color for regular GameObjects",
            position = 13,
            section = objectSection
    )
    default Color gameObjectColor() {
        return Color.BLUE;
    }
    
    @ConfigItem(
            keyName = "wallObjectColor",
            name = "WallObject Color",
            description = "Color for wall objects",
            position = 14,
            section = objectSection
    )
    default Color wallObjectColor() {
        return new Color(0, 0, 139); // Dark blue
    }
    
    @ConfigItem(
            keyName = "decorativeObjectColor",
            name = "DecorativeObject Color",
            description = "Color for decorative objects",
            position = 15,
            section = objectSection
    )
    default Color decorativeObjectColor() {
        return new Color(173, 216, 230); // Light blue
    }
    
    @ConfigItem(
            keyName = "groundObjectColor",
            name = "GroundObject Color",
            description = "Color for ground objects",
            position = 16,
            section = objectSection
    )
    default Color groundObjectColor() {
        return new Color(0, 128, 0); // Dark green
    }
    
    @ConfigItem(
            keyName = "enableObjectTypeColoring",
            name = "Enable Object Type Coloring",
            description = "Use different colors for different object types",
            position = 17,
            section = objectSection
    )
    default boolean enableObjectTypeColoring() {
        return true;
    }
    
    @ConfigItem(
            keyName = "enableObjectCategoryColoring", 
            name = "Enable Object Category Coloring",
            description = "Use different colors for object categories (bank, altar, resource)",
            position = 18,
            section = objectSection
    )
    default boolean enableObjectCategoryColoring() {
        return true;
    }
    
    // Object type toggles
    @ConfigItem(
            keyName = "showGameObjects",
            name = "Show GameObjects",
            description = "Show regular game objects (type 10)",
            position = 19,
            section = objectSection
    )
    default boolean showGameObjects() {
        return true;
    }
    
    @ConfigItem(
            keyName = "showWallObjects",
            name = "Show WallObjects",
            description = "Show wall objects (type 1)",
            position = 20,
            section = objectSection
    )
    default boolean showWallObjects() {
        return true;
    }
    
    @ConfigItem(
            keyName = "showDecorativeObjects",
            name = "Show DecorativeObjects",
            description = "Show decorative objects (type 3)",
            position = 21,
            section = objectSection
    )
    default boolean showDecorativeObjects() {
        return true;
    }
    
    @ConfigItem(
            keyName = "showGroundObjects",
            name = "Show GroundObjects",
            description = "Show ground objects (type 2)",
            position = 22,
            section = objectSection
    )
    default boolean showGroundObjects() {
        return true;
    }
    
    // ===== GROUND ITEM SETTINGS =====
    
    @ConfigItem(
            keyName = "enableGroundItemOverlay",
            name = "Enable Ground Item Overlay",
            description = "Show ground item overlays",
            position = 0,
            section = groundItemSection
    )
    default boolean enableGroundItemOverlay() {
        return false;
    }
    
    @ConfigItem(
            keyName = "groundItemRenderStyle",
            name = "Ground Item Render Style", 
            description = "How to render ground item overlays",
            position = 1,
            section = groundItemSection
    )
    default RenderStyle groundItemRenderStyle() {
        return RenderStyle.TILE;
    }
    
    @ConfigItem(
            keyName = "groundItemFilterPreset",
            name = "Ground Item Filter Preset",
            description = "Preset filter for ground items to display",
            position = 2,
            section = groundItemSection
    )
    default GroundItemFilterPreset groundItemFilterPreset() {
        return GroundItemFilterPreset.HIGH_VALUE;
    }
    
    @ConfigItem(
            keyName = "groundItemBorderColor",
            name = "Ground Item Border Color",
            description = "Color for ground item overlay borders",
            position = 3,
            section = groundItemSection
    )
    default Color groundItemBorderColor() {
        return Color.GREEN;
    }
    
    @ConfigItem(
            keyName = "groundItemFillColor", 
            name = "Ground Item Fill Color",
            description = "Color for ground item overlay fill",
            position = 4,
            section = groundItemSection
    )
    @Alpha
    default Color groundItemFillColor() {
        return new Color(0, 255, 0, 50);
    }
    
    @ConfigItem(
            keyName = "groundItemBorderWidth",
            name = "Ground Item Border Width",
            description = "Width of ground item overlay borders",
            position = 5,
            section = groundItemSection
    )
    @Range(min = 1, max = 10)
    default int groundItemBorderWidth() {
        return 2;
    }
    
    @ConfigItem(
            keyName = "groundItemShowNames",
            name = "Show Item Names",
            description = "Display ground item names",
            position = 6,
            section = groundItemSection
    )
    default boolean groundItemShowNames() {
        return true;
    }
    
    @ConfigItem(
            keyName = "groundItemShowValues",
            name = "Show Item Values",
            description = "Display ground item values",
            position = 7,
            section = groundItemSection
    )
    default boolean groundItemShowValues() {
        return true;
    }
    
    @ConfigItem(
            keyName = "groundItemShowId",
            name = "Show Item ID",
            description = "Display ground item game IDs",
            position = 8,
            section = groundItemSection
    )
    default boolean groundItemShowId() {
        return false;
    }
    
    @ConfigItem(
            keyName = "groundItemShowCoordinates",
            name = "Show World Coordinates",
            description = "Display world coordinates for ground items",
            position = 9,
            section = groundItemSection
    )
    default boolean groundItemShowCoordinates() {
        return false;
    }
    
    @ConfigItem(
            keyName = "groundItemMaxDistance",
            name = "Ground Item Max Distance",
            description = "Maximum distance to show ground items (in tiles)",
            position = 8,
            section = groundItemSection
    )
    @Range(min = 3, max = 30)
    default int groundItemMaxDistance() {
        return 15;
    }
    
    @ConfigItem(
            keyName = "groundItemCustomFilter",
            name = "Custom Ground Item Filter",
            description = "Custom text filter for ground item names (leave empty to disable)",
            position = 9,
            section = groundItemSection
    )
    default String groundItemCustomFilter() {
        return "";
    }
    
    @ConfigItem(
            keyName = "minimumItemValue",
            name = "Minimum Item Value",
            description = "Minimum value to show ground items (in coins)",
            position = 9,
            section = groundItemSection
    )
    default int minimumItemValue() {
        return 1000;
    }
    
    @ConfigItem(
            keyName = "groundItemShowQuantity",
            name = "Show Item Quantity",
            description = "Display quantity for stackable ground items",
            position = 12,
            section = groundItemSection
    )
    default boolean groundItemShowQuantity() {
        return true;
    }
    
    @ConfigItem(
            keyName = "groundItemShowDespawnTimer",
            name = "Show Despawn Timer",
            description = "Display countdown timer until item despawns",
            position = 13,
            section = groundItemSection
    )
    default boolean groundItemShowDespawnTimer() {
        return false;
    }
    
    @ConfigItem(
            keyName = "groundItemShowOwnership",
            name = "Show Ownership Indicator",
            description = "Display ownership status for ground items",
            position = 14,
            section = groundItemSection
    )
    default boolean groundItemShowOwnership() {
        return false;
    }
    
    @ConfigItem(
            keyName = "groundItemValueBasedColors",
            name = "Value-Based Colors",
            description = "Use different colors based on item value",
            position = 15,
            section = groundItemSection
    )
    default boolean groundItemValueBasedColors() {
        return false;
    }
    
    @ConfigItem(
            keyName = "groundItemLowValueThreshold",
            name = "Low Value Threshold",
            description = "Threshold for low value items (in GP)",
            position = 16,
            section = groundItemSection
    )
    @Range(min = 1, max = 1000000)
    default int groundItemLowValueThreshold() {
        return 1000;
    }
    
    @ConfigItem(
            keyName = "groundItemMediumValueThreshold", 
            name = "Medium Value Threshold",
            description = "Threshold for medium value items (in GP)",
            position = 17,
            section = groundItemSection
    )
    @Range(min = 1000, max = 10000000)
    default int groundItemMediumValueThreshold() {
        return 10000;
    }
    
    @ConfigItem(
            keyName = "groundItemHighValueThreshold",
            name = "High Value Threshold", 
            description = "Threshold for high value items (in GP)",
            position = 18,
            section = groundItemSection
    )
    @Range(min = 10000, max = 100000000)
    default int groundItemHighValueThreshold() {
        return 100000;
    }
    
    // ===== INFO PANEL SETTINGS =====
    
    @ConfigItem(
            keyName = "enableInfoPanel",
            name = "Enable Info Panel",
            description = "Enable the information panel",
            position = 0,
            section = infoPanelSection
    )
    default boolean enableInfoPanel() {
        return false;
    }
    
    @ConfigItem(
            keyName = "showInfoPanel",
            name = "Show Info Panel",
            description = "Display information panel with cache statistics",
            position = 1,
            section = infoPanelSection
    )
    default boolean showInfoPanel() {
        return false;
    }
    
    @ConfigItem(
            keyName = "infoPanelShowNpcs",
            name = "Show NPC Info",
            description = "Show NPC information in panel",
            position = 1,
            section = infoPanelSection
    )
    default boolean infoPanelShowNpcs() {
        return true;
    }
    
    @ConfigItem(
            keyName = "infoPanelShowObjects",
            name = "Show Object Info",
            description = "Show object information in panel",
            position = 2,
            section = infoPanelSection
    )
    default boolean infoPanelShowObjects() {
        return true;
    }
    
    @ConfigItem(
            keyName = "infoPanelShowGroundItems",
            name = "Show Ground Item Info",
            description = "Show ground item information in panel",
            position = 3,
            section = infoPanelSection
    )
    default boolean infoPanelShowGroundItems() {
        return true;
    }
    
    @ConfigItem(
            keyName = "infoPanelShowCacheStats",
            name = "Show Cache Statistics",
            description = "Show cache hit/miss statistics",
            position = 4,
            section = infoPanelSection
    )
    default boolean infoPanelShowCacheStats() {
        return true;
    }
    
    @ConfigItem(
            keyName = "infoPanelRefreshRate",
            name = "Panel Refresh Rate",
            description = "How often to refresh the info panel (in ticks)",
            position = 5,
            section = infoPanelSection
    )
    @Range(min = 1, max = 10)
    default int infoPanelRefreshRate() {
        return 5;
    }
    

}
