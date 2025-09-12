package net.runelite.client.plugins.microbot.util.world;

/**
 * Enum for world selection modes in break handler.
 */
public enum WorldSelectionMode {
    CURRENT_PREFERRED_WORLD("Use preferred world"),
    RANDOM_WORLD("Random accessible world"), 
    BEST_POPULATION("Best population balance"),
    BEST_PING("Best ping performance"),
    REGIONAL_RANDOM("Random from preferred region");
    
    private final String description;
    
    WorldSelectionMode(String description) {
        this.description = description;
    }
    
    @Override
    public String toString() {
        return description;
    }
}