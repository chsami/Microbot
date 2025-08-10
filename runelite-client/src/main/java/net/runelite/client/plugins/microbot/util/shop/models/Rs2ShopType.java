package  net.runelite.client.plugins.microbot.util.shop.models;

/**
 * Enum representing different types of shops in OSRS.
 * Used to categorize shops and apply appropriate logic for each type.
 */
public enum Rs2ShopType {
    // Trading and General
    GENERAL_STORE("General Store"),
    GRAND_EXCHANGE("Grand Exchange"),
    
    // Combat and Equipment
    SLAYER_SHOP("Slayer Shop"),
    ARCHERY_SHOP("Archery Shop"),
    WEAPON_SHOP("Weapon Shop"),
    ARMOUR_SHOP("Armour Shop"),
    SHIELD_SHOP("Shield Shop"),
    
    // Magic and Runes
    MAGIC_SHOP("Magic Shop"),
    RUNE_SHOP("Rune Shop"),
    
    // Crafting and Supplies
    CRAFTING_SHOP("Crafting Shop"),
    SMITHING_SHOP("Smithing Shop"),
    MINING_SHOP("Mining Shop"),
    
    // Food and Provisions
    FOOD_SHOP("Food Shop"),
    FISHING_SHOP("Fishing Shop"),
    
    // Clothing and Appearance
    CLOTHES_SHOP("Clothes Shop"),
    
    // Services and Special
    FARMING_SHOP("Farming Shop"),
    PUB("Pub"),
    
    // Specialty Stores
    GEM_SHOP("Gem Shop"),
    JEWELRY_SHOP("Jewelry Shop"),
    
    // Unknown or Custom
    OTHER("Other");
    
    private final String name;
    
    Rs2ShopType(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    /**
     * Determines if this shop type supports world hopping for stock management.
     * Grand Exchange doesn't need world hopping since it has unlimited stock.
     * 
     * @return true if world hopping is beneficial for this shop type
     */
    public boolean supportsWorldHopping() {
        return this != GRAND_EXCHANGE;
    }
    
    /**
     * Determines if this shop type uses dynamic pricing based on stock levels.
     * Grand Exchange uses real-time market prices instead of stock-based pricing.
     * 
     * @return true if the shop uses stock-based pricing
     */
    public boolean usesDynamicPricing() {
        return this != GRAND_EXCHANGE;
    }
    
    /**
     * Gets the default stock check interval for this shop type in milliseconds.
     * 
     * @return stock check interval in milliseconds
     */
    public long getStockCheckInterval() {
        switch (this) {
            case GRAND_EXCHANGE:
                return 0; // No stock checking needed
            case SLAYER_SHOP:
            case ARCHERY_SHOP:
                return 5000; // 5 seconds for specialty shops
            default:
                return 3000; // 3 seconds for regular shops
        }
    }

}
