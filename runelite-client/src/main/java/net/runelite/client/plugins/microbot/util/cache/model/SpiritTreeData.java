package net.runelite.client.plugins.microbot.util.cache.model;

import lombok.Data;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.CropState;
import net.runelite.client.plugins.microbot.util.farming.SpiritTree;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Data structure to hold spirit tree farming patch information with contextual and temporal tracking.
 * Includes information about patch state, travel availability, when last detected,
 * and detection method tracking.
 */
@Data
public class SpiritTreeData {
    
    private final SpiritTree patch;
    private final CropState cropState;
    private final boolean availableForTravel;
    private final long lastUpdated; // UTC timestamp when this state was last detected
    private final WorldPoint playerLocation; // Player location when the state was detected
    private final boolean detectedViaWidget; // Whether this state was detected via spirit tree widget
    private final boolean detectedViaGameObject; // Whether this state was detected via game object interaction
    private final Integer farmingLevel; // Player's farming level when this was detected
    
    /**
     * Creates a new SpiritTreeData instance with current timestamp and minimal context.
     * 
     * @param patch The spirit tree patch
     * @param cropState The current crop state (null for built-in trees)
     * @param availableForTravel Whether the tree is available for travel
     */
    public SpiritTreeData(SpiritTree patch, CropState cropState, boolean availableForTravel) {
        this(patch, cropState, availableForTravel, System.currentTimeMillis(), null, 
             false, false, null);
    }
    
    /**
     * Creates a new SpiritTreeData instance with detection method tracking.
     * 
     * @param patch The spirit tree patch
     * @param cropState The current crop state (null for built-in trees)
     * @param availableForTravel Whether the tree is available for travel
     * @param detectedViaWidget Whether detected via spirit tree widget
     * @param detectedViaGameObject Whether detected via game object interaction
     */
    public SpiritTreeData(SpiritTree patch, CropState cropState, boolean availableForTravel,
                         boolean detectedViaWidget, boolean detectedViaGameObject) {
        this(patch, cropState, availableForTravel, System.currentTimeMillis(), null,
             detectedViaWidget, detectedViaGameObject, null);
    }
    
    /**
     * Creates a new SpiritTreeData instance with contextual information.
     * 
     * @param patch The spirit tree patch
     * @param cropState The current crop state (null for built-in trees)
     * @param availableForTravel Whether the tree is available for travel
     * @param playerLocation The player's world location when detected
     * @param detectedViaWidget Whether detected via spirit tree widget
     * @param detectedViaGameObject Whether detected via game object interaction
     * @param farmingLevel Player's farming level when detected
     */
    public SpiritTreeData(SpiritTree patch, CropState cropState, boolean availableForTravel,
                         WorldPoint playerLocation, boolean detectedViaWidget, boolean detectedViaGameObject, Integer farmingLevel) {
        this(patch, cropState, availableForTravel, System.currentTimeMillis(), playerLocation,
             detectedViaWidget, detectedViaGameObject, farmingLevel);
    }
    
    /**
     * Creates a new SpiritTreeData instance with full temporal and contextual tracking.
     * 
     * @param patch The spirit tree patch
     * @param cropState The current crop state (null for built-in trees)
     * @param availableForTravel Whether the tree is available for travel
     * @param lastUpdated UTC timestamp when this data was created/updated
     * @param playerLocation The player's world location when detected
     * @param detectedViaWidget Whether detected via spirit tree widget
     * @param detectedViaGameObject Whether detected via game object interaction
     * @param farmingLevel Player's farming level when detected
     */
    public SpiritTreeData(SpiritTree patch, CropState cropState, boolean availableForTravel, long lastUpdated,
                         WorldPoint playerLocation, boolean detectedViaWidget, boolean detectedViaGameObject, Integer farmingLevel) {
        this.patch = patch;
        this.cropState = cropState;
        this.availableForTravel = availableForTravel;
        this.lastUpdated = lastUpdated;
        this.playerLocation = playerLocation;
        this.detectedViaWidget = detectedViaWidget;
        this.detectedViaGameObject = detectedViaGameObject;
        this.farmingLevel = farmingLevel;
    }
    
    /**
     * Creates a new SpiritTreeData with updated availability while preserving other data.
     * 
     * @param newAvailability The new travel availability status
     * @param detectedViaWidget Whether detected via widget
     * @param detectedViaGameObject Whether detected via game object
     * @param playerLocation Current player location
     * @param farmingLevel Current farming level
     * @return A new SpiritTreeData instance with updated availability
     */
    public SpiritTreeData withUpdatedAvailability(boolean newAvailability, boolean detectedViaWidget, 
                                                 boolean detectedViaGameObject, WorldPoint playerLocation, Integer farmingLevel) {
        return new SpiritTreeData(this.patch, this.cropState, newAvailability, playerLocation,
                                detectedViaWidget, detectedViaGameObject, farmingLevel);
    }
    
    /**
     * Creates a new SpiritTreeData with updated crop state.
     * 
     * @param newCropState The new crop state
     * @param playerLocation Current player location
     * @param farmingLevel Current farming level
     * @return A new SpiritTreeData instance with updated crop state
     */
    public SpiritTreeData withUpdatedCropState(CropState newCropState, WorldPoint playerLocation, 
                                              Integer farmingLevel) {
        // Update availability based on new crop state
        boolean newAvailability = isAvailableBasedOnCropState(newCropState);
        
        return new SpiritTreeData(this.patch, newCropState, newAvailability, playerLocation,
                                false, true, farmingLevel);
    }
    
    /**
     * Determines travel availability based on crop state.
     * 
     * @param cropState The crop state to evaluate
     * @return true if the tree should be available for travel
     */
    private boolean isAvailableBasedOnCropState(CropState cropState) {
        if (cropState == null) {
            return true; // Built-in trees are always available if quest requirements are met
        }
        
        // Farmable trees are available when healthy and grown
        return cropState == CropState.HARVESTABLE || cropState == CropState.UNCHECKED;
    }
    
    /**
     * Checks if this spirit tree data represents a travel availability change.
     * 
     * @param previousData The previous data to compare against
     * @return true if travel availability changed
     */
    public boolean hasAvailabilityChanged(SpiritTreeData previousData) {
        if (previousData == null) {
            return true; // First detection is considered a change
        }
        return this.availableForTravel != previousData.availableForTravel;
    }
    
    /**
     * Checks if this spirit tree data represents a crop state change.
     * 
     * @param previousData The previous data to compare against
     * @return true if crop state changed
     */
    public boolean hasCropStateChanged(SpiritTreeData previousData) {
        if (previousData == null) {
            return this.cropState != null; // First detection of farmable tree
        }
        
        if (this.cropState == null && previousData.cropState == null) {
            return false; // Both are built-in trees
        }
        
        if (this.cropState == null || previousData.cropState == null) {
            return true; // One is built-in, other is farmable
        }
        
        return !this.cropState.equals(previousData.cropState);
    }
    
    /**
     * Checks if this detection occurred at a specific location.
     * 
     * @param location The location to check
     * @return true if the detection occurred at the specified location
     */
    public boolean occurredAt(WorldPoint location) {
        return playerLocation != null && playerLocation.distanceTo(location)<10; // Allow some tolerance
    }
    
    /**
     * Gets a human-readable timestamp of when this was last updated.
     * 
     * @return Formatted timestamp string
     */
    public String getFormattedLastUpdated() {
        return Instant.ofEpochMilli(lastUpdated)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    
    /**
     * Gets the age of this data in milliseconds.
     * 
     * @return Age in milliseconds since last update
     */
    public long getAgeMillis() {
        return System.currentTimeMillis() - lastUpdated;
    }
    
    /**
     * Checks if this data is considered stale based on age.
     * 
     * @param maxAgeMillis Maximum acceptable age in milliseconds
     * @return true if the data is older than the specified age
     */
    public boolean isStale(long maxAgeMillis) {
        return getAgeMillis() > maxAgeMillis;
    }
    
    /**
     * Gets a summary string for debugging purposes.
     * 
     * @return Summary string containing key information
     */
    public String getSummary() {
        return String.format("SpiritTreeData{patch=%s, state=%s, available=%s, age=%dms, via=%s}",
                patch.name(), 
                cropState != null ? cropState.name() : "BUILT_IN",
                availableForTravel,
                getAgeMillis(),
                detectedViaWidget ? "WIDGET" : (detectedViaGameObject ? "OBJECT" : "UNKNOWN"));
    }
}
