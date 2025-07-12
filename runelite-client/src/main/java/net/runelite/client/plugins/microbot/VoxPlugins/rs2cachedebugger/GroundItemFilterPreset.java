package net.runelite.client.plugins.microbot.VoxPlugins.rs2cachedebugger;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItemModel;

/**
 * Preset filters for Ground Items in the Game Information overlay system.
 * Provides common filtering options for different item types and values.
 */
@Getter
@RequiredArgsConstructor
public enum GroundItemFilterPreset {
    ALL("All Items", "Show all ground items"),
    HIGH_VALUE("High Value (10k+)", "Show items worth 10,000+ coins"),
    MEDIUM_VALUE("Medium Value (1k-10k)", "Show items worth 1,000-10,000 coins"),
    LOW_VALUE("Low Value (<1k)", "Show items worth less than 1,000 coins"),
    VALUABLE_ONLY("Valuable Only (50k+)", "Show items worth 50,000+ coins"),
    RARE_ITEMS("Rare Items", "Show rare drops and unique items"),
    STACKABLE("Stackable", "Show only stackable items"),
    NON_STACKABLE("Non-Stackable", "Show only non-stackable items"),
    TRADEABLE("Tradeable", "Show only tradeable items"),
    UNTRADEABLE("Untradeable", "Show only untradeable items"),
    EQUIPMENT("Equipment", "Show weapons, armor, and accessories"),
    CONSUMABLES("Consumables", "Show food, potions, and consumable items"),
    RESOURCES("Resources", "Show raw materials and resources"),
    COINS("Coins", "Show coin drops only"),
    RECENTLY_SPAWNED("Recently Spawned", "Show items spawned in last 10 ticks"),
    OWNED_ITEMS("Owned Items", "Show items that belong to the player"),
    PUBLIC_ITEMS("Public Items", "Show items visible to all players"),
    WITHIN_5_TILES("Within 5 Tiles", "Show items within 5 tiles"),
    WITHIN_10_TILES("Within 10 Tiles", "Show items within 10 tiles"),
    CUSTOM("Custom", "Use custom filter criteria");

    private final String displayName;
    private final String description;

    @Override
    public String toString() {
        return displayName;
    }
    
    /**
     * Test if a ground item matches this filter preset.
     * 
     * @param item The ground item to test
     * @return true if the item matches the filter criteria
     */
    public boolean test(Rs2GroundItemModel item) {
        if (item == null) {
            return false;
        }
        
        switch (this) {
            case ALL:
                return true;
                
            case HIGH_VALUE:
                return item.getValue() >= 10000;
                
            case MEDIUM_VALUE:
                int value = item.getValue();
                return value >= 1000 && value < 10000;
                
            case LOW_VALUE:
                return item.getValue() < 1000;
                
            case VALUABLE_ONLY:
                return item.getValue() >= 50000;
                
            case RARE_ITEMS:
                // Basic check - could be enhanced with specific rare item IDs
                return item.getValue() >= 100000;
                
            case STACKABLE:
                return item.isStackable();
                
            case NON_STACKABLE:
                return !item.isStackable();
                
            case TRADEABLE:
                return item.isTradeable();
                
            case UNTRADEABLE:
                return !item.isTradeable();
                
            case EQUIPMENT:
                // Basic check for equipment - could be enhanced with item categories
                String name = item.getName();
                if (name == null) return false;
                return name.toLowerCase().contains("sword") || 
                       name.toLowerCase().contains("bow") ||
                       name.toLowerCase().contains("armor") ||
                       name.toLowerCase().contains("helm") ||
                       name.toLowerCase().contains("boots") ||
                       name.toLowerCase().contains("gloves");
                
            case CONSUMABLES:
                String consumableName = item.getName();
                if (consumableName == null) return false;
                return consumableName.toLowerCase().contains("potion") || 
                       consumableName.toLowerCase().contains("food") ||
                       consumableName.toLowerCase().contains("cake") ||
                       consumableName.toLowerCase().contains("brew");
                
            case RESOURCES:
                String resourceName = item.getName();
                if (resourceName == null) return false;
                return resourceName.toLowerCase().contains("ore") || 
                       resourceName.toLowerCase().contains("log") ||
                       resourceName.toLowerCase().contains("fish") ||
                       resourceName.toLowerCase().contains("bone");
                
            case COINS:
                return item.getId() == 995; // Coins item ID
                
            case RECENTLY_SPAWNED:
                // For now, just return true - proper implementation would need cache timing
                return true;
                
            case OWNED_ITEMS:
                return item.isOwned();
                
            case PUBLIC_ITEMS:
                return !item.isOwned();
                
            case WITHIN_5_TILES:
                return item.getDistanceFromPlayer() <= 5;
                
            case WITHIN_10_TILES:
                return item.getDistanceFromPlayer() <= 10;
                
            case CUSTOM:
                // Custom filtering should be handled by the plugin logic
                return true;
                
            default:
                return true;
        }
    }
}
