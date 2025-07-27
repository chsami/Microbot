package net.runelite.client.plugins.microbot.VoxPlugins.rs2cachedebugger;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2ObjectModel;

/**
 * Preset filters for Objects in the Game Information overlay system.
 * Provides common filtering options for different object types.
 */
@Getter
@RequiredArgsConstructor
public enum ObjectFilterPreset {
    ALL("All Objects", "Show all objects"),
    INTERACTABLE("Interactable", "Show only interactable objects"),
    BANKS("Banks", "Show bank booths and chests"),
    DOORS("Doors", "Show doors and gates"),
    STAIRS("Stairs", "Show stairs and ladders"),
    TREES("Trees", "Show all trees"),
    ROCKS("Rocks", "Show mining rocks"),
    FISHING_SPOTS("Fishing Spots", "Show fishing spots"),
    ALTARS("Altars", "Show prayer and magic altars"),
    FURNACES("Furnaces", "Show furnaces and forges"),
    ANVILS("Anvils", "Show anvils and smithing objects"),
    COOKING("Cooking", "Show cooking ranges and fires"),
    DECORATIVE("Decorative", "Show decorative objects only"),
    WALLS("Walls", "Show wall objects"),
    GROUND_OBJECTS("Ground Objects", "Show ground-level objects"),
    RECENTLY_SPAWNED("Recently Spawned", "Show objects spawned in last 10 ticks"),
    WITHIN_5_TILES("Within 5 Tiles", "Show objects within 5 tiles"),
    WITHIN_10_TILES("Within 10 Tiles", "Show objects within 10 tiles"),
    HIGH_VALUE("High Value", "Show valuable interaction objects"),
    CUSTOM("Custom", "Use custom filter criteria");

    private final String displayName;
    private final String description;

    @Override
    public String toString() {
        return displayName;
    }
    
    /**
     * Test if an object matches this filter preset.
     * 
     * @param object The object to test
     * @return true if the object matches the filter criteria
     */
    public boolean test(Rs2ObjectModel object) {
        if (object == null) {
            return false;
        }
        
        switch (this) {
            case ALL:
                return true;
                
            case INTERACTABLE:
                // Check if object has any actions
                String[] actions = object.getActions();
                return actions != null && actions.length > 0;
                
            case BANKS:
                String name = object.getName();
                if (name == null) return false;
                return name.toLowerCase().contains("bank") || 
                       name.toLowerCase().contains("chest") ||
                       name.toLowerCase().contains("deposit");
                
            case DOORS:
                String doorName = object.getName();
                if (doorName == null) return false;
                return doorName.toLowerCase().contains("door") || 
                       doorName.toLowerCase().contains("gate");
                
            case STAIRS:
                String stairName = object.getName();
                if (stairName == null) return false;
                return stairName.toLowerCase().contains("stair") || 
                       stairName.toLowerCase().contains("ladder");
                
            case TREES:
                String treeName = object.getName();
                if (treeName == null) return false;
                return treeName.toLowerCase().contains("tree") || 
                       treeName.toLowerCase().contains("log");
                
            case ROCKS:
                String rockName = object.getName();
                if (rockName == null) return false;
                return rockName.toLowerCase().contains("rock") || 
                       rockName.toLowerCase().contains("ore") ||
                       rockName.toLowerCase().contains("vein");
                
            case FISHING_SPOTS:
                String fishName = object.getName();
                if (fishName == null) return false;
                return fishName.toLowerCase().contains("fishing") || 
                       fishName.toLowerCase().contains("spot");
                
            case ALTARS:
                String altarName = object.getName();
                if (altarName == null) return false;
                return altarName.toLowerCase().contains("altar");
                
            case FURNACES:
                String furnaceName = object.getName();
                if (furnaceName == null) return false;
                return furnaceName.toLowerCase().contains("furnace") || 
                       furnaceName.toLowerCase().contains("forge");
                
            case ANVILS:
                String anvilName = object.getName();
                if (anvilName == null) return false;
                return anvilName.toLowerCase().contains("anvil");
                
            case COOKING:
                String cookName = object.getName();
                if (cookName == null) return false;
                return cookName.toLowerCase().contains("range") || 
                       cookName.toLowerCase().contains("fire") ||
                       cookName.toLowerCase().contains("stove");
                
            case DECORATIVE:
                return object.getObjectType() == Rs2ObjectModel.ObjectType.DECORATIVE_OBJECT;
                
            case WALLS:
                return object.getObjectType() == Rs2ObjectModel.ObjectType.WALL_OBJECT;
                
            case GROUND_OBJECTS:
                return object.getObjectType() == Rs2ObjectModel.ObjectType.GROUND_OBJECT;
                
            case RECENTLY_SPAWNED:
                // For now, just return true - proper implementation would need cache timing
                return true;
                
            case WITHIN_5_TILES:
                return object.getDistanceFromPlayer() <= 5;
                
            case WITHIN_10_TILES:
                return object.getDistanceFromPlayer() <= 10;
                
            case HIGH_VALUE:
                // Basic check for commonly valuable objects
                String valueName = object.getName();
                if (valueName == null) return false;
                return valueName.toLowerCase().contains("bank") || 
                       valueName.toLowerCase().contains("shop") ||
                       valueName.toLowerCase().contains("altar");
                
            case CUSTOM:
                // Custom filtering should be handled by the plugin logic
                return true;
                
            default:
                return true;
        }
    }
}
