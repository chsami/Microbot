package net.runelite.client.plugins.microbot.util.world;

import net.runelite.http.api.worlds.WorldRegion;

/**
 * Enum for region preferences in break handler world selection.
 */
public enum RegionPreference {
    ANY_REGION("Any region", null),
    UNITED_STATES("United States", WorldRegion.UNITED_STATES_OF_AMERICA),
    UNITED_KINGDOM("United Kingdom", WorldRegion.UNITED_KINGDOM), 
    AUSTRALIA("Australia", WorldRegion.AUSTRALIA),
    GERMANY("Germany", WorldRegion.GERMANY);
    
    private final String displayName;
    private final WorldRegion worldRegion;
    
    RegionPreference(String displayName, WorldRegion worldRegion) {
        this.displayName = displayName;
        this.worldRegion = worldRegion;
    }
    
    public WorldRegion getWorldRegion() {
        return worldRegion;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}