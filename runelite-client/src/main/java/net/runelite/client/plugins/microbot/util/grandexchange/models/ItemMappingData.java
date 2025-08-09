package net.runelite.client.plugins.microbot.util.grandexchange.models;

/**
 * Data class holding item mapping information from OSRS Wiki API.
 * Includes trade limits, alch values, and other item metadata.
 */
public class ItemMappingData {
    public final int itemId;
    public final String name;
    public final String examine;
    public final boolean members;
    public final int tradeLimitPer4Hours; // GE buy limit per 4 hours
    public final int value; // Store value
    public final int lowAlch; // Low alchemy value
    public final int highAlch; // High alchemy value
    public final String icon; // Icon filename
    
    public ItemMappingData(int itemId, String name, String examine, boolean members, 
                          int tradeLimitPer4Hours, int value, int lowAlch, int highAlch, String icon) {
        this.itemId = itemId;
        this.name = name != null ? name : "";
        this.examine = examine != null ? examine : "";
        this.members = members;
        this.tradeLimitPer4Hours = tradeLimitPer4Hours;
        this.value = value;
        this.lowAlch = lowAlch;
        this.highAlch = highAlch;
        this.icon = icon != null ? icon : "";
    }
    
    /**
     * Checks if this item has a trade limit restriction.
     * 
     * @return true if the item has a trade limit (limit < 1000), false for unlimited items
     */
    public boolean hasTradeLimit() {
        return tradeLimitPer4Hours > 0 && tradeLimitPer4Hours < 1000;
    }
    
    /**
     * Gets the effective trade limit for flipping calculations.
     * For items with no limit or very high limits, returns a conservative estimate.
     * 
     * @return Effective trade limit for calculations
     */
    public int getEffectiveTradeLimit() {
        if (tradeLimitPer4Hours <= 0) {
            return 100; // Conservative default for unlimited items
        } else if (tradeLimitPer4Hours >= 1000) {
            return 500; // Conservative for very high limits
        } else {
            return tradeLimitPer4Hours;
        }
    }
}
