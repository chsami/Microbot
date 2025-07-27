package net.runelite.client.plugins.microbot.util.cache.model;

import lombok.Data;
import net.runelite.api.coords.WorldPoint;

import java.util.Collections;
import java.util.List;

/**
 * Data structure to hold varbit information with contextual and temporal tracking.
 * Includes information about when the varbit changed, where the player was,
 * and what entities were nearby when the change occurred.
 */
@Data
public class VarbitData {
    
    private final int value;
    private final long lastUpdated; // Timestamp when this varbit was last updated
    private final Integer previousValue; // Previous value before the update (null if unknown)
    private final WorldPoint playerLocation; // Player location when the varbit changed
    private final List<Integer> nearbyNpcIds; // NPC IDs that were nearby when the change occurred
    private final List<Integer> nearbyObjectIds; // Object IDs that were nearby when the change occurred
    
    /**
     * Creates a new VarbitData instance with current timestamp and no context.
     * 
     * @param value The varbit value
     */
    public VarbitData(int value) {
        this(value, System.currentTimeMillis(), null, null, Collections.emptyList(), Collections.emptyList());
    }
    
    /**
     * Creates a new VarbitData instance with previous value tracking.
     * 
     * @param value The varbit value
     * @param previousValue The previous value (null if unknown)
     */
    public VarbitData(int value, Integer previousValue) {
        this(value, System.currentTimeMillis(), previousValue, null, Collections.emptyList(), Collections.emptyList());
    }
    
    /**
     * Creates a new VarbitData instance with full contextual information.
     * 
     * @param value The varbit value
     * @param previousValue The previous value (null if unknown)
     * @param playerLocation The player's world location when the change occurred
     * @param nearbyNpcIds List of nearby NPC IDs
     * @param nearbyObjectIds List of nearby object IDs
     */
    public VarbitData(int value, Integer previousValue, WorldPoint playerLocation, 
                     List<Integer> nearbyNpcIds, List<Integer> nearbyObjectIds) {
        this(value, System.currentTimeMillis(), previousValue, playerLocation, nearbyNpcIds, nearbyObjectIds);
    }
    
    /**
     * Creates a new VarbitData instance with full temporal and contextual tracking.
     * 
     * @param value The varbit value
     * @param lastUpdated Timestamp when this data was created/updated
     * @param previousValue The previous value (null if unknown)
     * @param playerLocation The player's world location when the change occurred
     * @param nearbyNpcIds List of nearby NPC IDs
     * @param nearbyObjectIds List of nearby object IDs
     */
    public VarbitData(int value, long lastUpdated, Integer previousValue, WorldPoint playerLocation,
                     List<Integer> nearbyNpcIds, List<Integer> nearbyObjectIds) {
        this.value = value;
        this.lastUpdated = lastUpdated;
        this.previousValue = previousValue;
        this.playerLocation = playerLocation;
        this.nearbyNpcIds = nearbyNpcIds != null ? Collections.unmodifiableList(nearbyNpcIds) : Collections.emptyList();
        this.nearbyObjectIds = nearbyObjectIds != null ? Collections.unmodifiableList(nearbyObjectIds) : Collections.emptyList();
    }
    
    /**
     * Creates a new VarbitData with updated value while preserving previous state.
     * 
     * @param newValue The new varbit value
     * @param playerLocation The player's current location
     * @param nearbyNpcIds List of nearby NPC IDs
     * @param nearbyObjectIds List of nearby object IDs
     * @return A new VarbitData instance with the current value as previous value
     */
    public VarbitData withUpdate(int newValue, WorldPoint playerLocation, 
                               List<Integer> nearbyNpcIds, List<Integer> nearbyObjectIds) {
        return new VarbitData(newValue, this.value, playerLocation, nearbyNpcIds, nearbyObjectIds);
    }
    
    /**
     * Checks if this varbit data represents a value change.
     * 
     * @return true if the value changed from the previous value
     */
    public boolean hasValueChanged() {
        return previousValue != null && value != previousValue;
    }
    
    /**
     * Gets the change in value since the last update.
     * 
     * @return the value difference, or 0 if no previous value
     */
    public int getValueChange() {
        return previousValue != null ? value - previousValue : 0;
    }
    
    /**
     * Checks if this varbit change occurred at a specific location.
     * 
     * @param location The location to check
     * @return true if the change occurred at the specified location
     */
    public boolean occurredAt(WorldPoint location) {
        return playerLocation != null && playerLocation.distanceTo(location)<10; // Allow some tolerance
    }
    
    /**
     * Checks if a specific NPC was nearby when this varbit changed.
     * 
     * @param npcId The NPC ID to check for
     * @return true if the NPC was nearby during the change
     */
    public boolean hadNearbyNpc(int npcId) {
        return nearbyNpcIds.contains(npcId);
    }
    
    /**
     * Checks if a specific object was nearby when this varbit changed.
     * 
     * @param objectId The object ID to check for
     * @return true if the object was nearby during the change
     */
    public boolean hadNearbyObject(int objectId) {
        return nearbyObjectIds.contains(objectId);
    }
}
