package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.EqualsAndHashCode;
import net.runelite.api.Quest;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementPriority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementType;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.TaskContext;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.TransportRouteAnalysis;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.event.Level;

/**
 * Enhanced SpellbookRequirement that extends from the base Requirement class.
 * Integrates with the RequirementCollection system and provides comprehensive
 * spellbook switching functionality.
 * 
 * This requirement manages spellbook switching operations including:
 * - Checking current spellbook state
 * - Determining if switching is required before/after script execution
 * - Handling different switching methods (altar prayer, NPC dialogue, Magic cape)
 * - Managing travel to switching locations
 * - Restoring original spellbook after completion
 */
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class SpellbookRequirement extends Requirement {
    
    
    
    @Getter
    private final Rs2Spellbook requiredSpellbook; // Spellbook required for this task
        
    
    /**
     * Full constructor for SpellbookRequirement
     * 
     * @param requiredSpellbook The spellbook required for optimal plugin performance
     * @param TaskContext When this spellbook requirement should be applied (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @param priority Priority level of this requirement
     * @param rating Effectiveness rating (1-10, 10 being most effective)
     * @param description Human-readable description of the requirement
     */
    public SpellbookRequirement(
            Rs2Spellbook requiredSpellbook,
            TaskContext taskContext,
            RequirementPriority priority,
            int rating,
            String description) {
        
        super(RequirementType.GAME_CONFIG, 
              priority, 
              rating, 
              description != null ? description : generateDefaultDescription(requiredSpellbook, taskContext),
              requiredSpellbook != null ? Collections.singletonList(requiredSpellbook.getValue()) : Collections.emptyList(),
              taskContext);
        
        this.requiredSpellbook = requiredSpellbook;
       
    }
    
    /**
     * Simplified constructor with default settings (applies to both pre and post schedule)
     * 
     * @param requiredSpellbook The spellbook required for the task
     * @param priority Priority level of this requirement
     * @param rating Effectiveness rating (1-10)
     */
    public SpellbookRequirement(Rs2Spellbook requiredSpellbook, TaskContext taskContext, RequirementPriority priority, int rating) {
        this(requiredSpellbook, taskContext, priority, rating, null);
    }
    
    /**
     * Constructor for specific schedule context
     * 
     * @param requiredSpellbook The spellbook required for the task
     * @param TaskContext When this requirement should be applied
     */
    public SpellbookRequirement(Rs2Spellbook requiredSpellbook, TaskContext taskContext) {
        this(requiredSpellbook, taskContext, RequirementPriority.MANDATORY, 10, null);
    }
    


    @Override
    public String getName() {
        return requiredSpellbook != null ? requiredSpellbook.name() + " Spellbook" : "Unknown Spellbook";
    }
    
    /**
     * Generate a default description based on the required spellbook and schedule context
     * 
     * @param spellbook The required spellbook
     * @param context When the requirement applies
     * @return A descriptive string explaining the requirement
     */
    private static String generateDefaultDescription(Rs2Spellbook spellbook, TaskContext context) {
        if (spellbook == null) {
            return "No specific spellbook required";
        }
        
        String contextDescription = "";
        switch (context) {
            case PRE_SCHEDULE:
                contextDescription = "before script execution";
                break;
            case POST_SCHEDULE:
                contextDescription = "after script completion";
                break;
            case BOTH:
                contextDescription = "for optimal plugin performance";
                break;
        }
        
        return String.format("Requires %s spellbook %s. %s", 
                           spellbook.name(), 
                           contextDescription,
                           spellbook.getDescription());
    }
    
    /**
     * Checks if the player is using the required spellbook.
     * 
     * @return true if no spellbook is required or the player is using the required spellbook,
     *         false otherwise
     */
    public boolean hasRequiredSpellbook() {
        if (requiredSpellbook == null) {
            return true; // No spellbook requirement
        }
        
        return Rs2Spellbook.getCurrentSpellbook() == requiredSpellbook;
    }
    public boolean isFulfilled() {
        // Check if the required spellbook is currently active
        return hasRequiredSpellbook();
    }   
    
    /**
     * Checks if the required spellbook is available to the player (unlocked).
     * 
     * @return true if the required spellbook is available, false otherwise
     */
    public boolean isRequiredSpellbookAvailable() {
        if (requiredSpellbook == null) {
            return true; // No spellbook requirement
        }
        
        return requiredSpellbook.isUnlocked();
    }
    
    /**
     * Attempts to switch to the required spellbook if needed.
     * This method should be called before starting the script if TaskContext includes PRE_SCHEDULE.
     * 
     * @return true if the switch was successful or no switch was needed, false otherwise
     */
    private boolean switchToRequiredSpellbook(CompletableFuture<Boolean> scheduledFuture) {
        if (requiredSpellbook == null ){
            return true; // No switch needed
        }
        
        if (hasRequiredSpellbook()) {
            return true; // Already using required spellbook
        }
        
        if (!isRequiredSpellbookAvailable()) {            
            log.error("Required spellbook {} is not unlocked, cannot switch", requiredSpellbook.name());
            return false;
        }
        if (!travelToSwitchLocation(requiredSpellbook)) {            
            log.error("Failed to travel to {} spellbook switch location at location {}", requiredSpellbook.name(), requiredSpellbook.getSwitchLocation());
            return false;
        }        
        log.info("Switching to required spellbook: {}....", requiredSpellbook.name());
        // Use the enhanced spellbook switching functionality
        return requiredSpellbook.switchTo();
    }
    
    /**
     * Switches the player back to their original spellbook after the script completes.
     * This method should be called after the script finishes if TaskContext includes POST_SCHEDULE.
     * 
     * @return true if the switch was successful or no switch was needed, false otherwise
     */
    public static boolean switchBackToSpellbook(Rs2Spellbook originalSpellbook) {
        if (Microbot.getClient() == null) {                
            return false;
        }
        if (Microbot.getClient().isClientThread()) {
            log.info("Please run fulfillRequirement() on a non-client thread.");
            return false;
        }
        if (originalSpellbook == null) {
            return true; // No switch needed
        }
        
        if (Rs2Spellbook.getCurrentSpellbook() == originalSpellbook) {
            return true; // Already using original spellbook
        }
        if (!originalSpellbook.isUnlocked()) {
            log.error("Original spellbook {} is not unlocked, cannot switch back", originalSpellbook.name());
            return false;
        }
        if (!travelToSwitchLocation(originalSpellbook)) {
            log.error("Failed to travel to {} spellbook switch location at location {}", originalSpellbook.name(), originalSpellbook.getSwitchLocation());
            return false;
        }
        log.info("Switching back to original spellbook: {}....", originalSpellbook.name());
        // Use the enhanced spellbook switching functionality
        return originalSpellbook.switchTo();
    }
    
    /**
     * Helper method to travel to the spellbook switching location.
     * Uses intelligent pathfinding to determine whether to go directly or via bank first
     * for transport items. Analyzes transport requirements and route efficiency.
     * 
     * @param targetSpellbook The spellbook to switch to
     * @return true if travel was successful, false otherwise
     */
    private static boolean travelToSwitchLocation(Rs2Spellbook targetSpellbook) {
        WorldPoint location = targetSpellbook.getSwitchLocation();
        
        if (location == null) {
            Microbot.status = "No switch location defined for " + targetSpellbook.name() + " spellbook";
            return false;
        }
        
        // Check if we're already at or very close to the target location
        WorldPoint currentLocation = Rs2Player.getWorldLocation();
        if (currentLocation != null && currentLocation.distanceTo(location) <= 3) {
            Microbot.status = "Already near " + targetSpellbook.name() + " spellbook switch location";
            return true;
        }
        
        try {
            // Use intelligent transport strategy to determine best route
            Microbot.status = "Analyzing route to " + targetSpellbook.name() + " spellbook location...";
            
            // Analyze transport requirements for the destination
            List<Transport> missingTransports = Rs2Walker.getTransportsForDestination(location, true);
            List<Integer> missingItemIds = Rs2Walker.getMissingTransportItemIds(missingTransports);
            
            if (!missingItemIds.isEmpty()) {
                Microbot.status = String.format("Found %d missing transport items for %s spellbook location", 
                    missingItemIds.size(), targetSpellbook.name());
                
                // Compare direct vs banking routes
                TransportRouteAnalysis comparison = Rs2Walker.compareRoutes(location);
                
                if (comparison.isDirectIsFaster()) {
                    Microbot.status = String.format("Direct route to %s location is faster (%s)", 
                        targetSpellbook.name(), comparison.getAnalysis());
                } else {
                    Microbot.status = String.format("Banking route to %s location is more efficient (%s)", 
                        targetSpellbook.name(), comparison.getAnalysis());
                }
            } else {
                Microbot.status = "No transport items needed, traveling directly to " + targetSpellbook.name() + " location";
            }
            
            // Execute the travel using intelligent strategy
            if (!Rs2Walker.walkWithBankedTransports(location, false)) {
                Microbot.status = "Failed to initiate travel to " + targetSpellbook.name() + " spellbook location";
                return false;
            }
            
            Microbot.status = "Traveling to " + targetSpellbook.name() + " spellbook switch location";
            return true;
            
        } catch (Exception e) {
            Microbot.status = "Error during travel planning to " + targetSpellbook.name() + " location: " + e.getMessage();
            log.warn("Error in travelToSwitchLocation for {}: {}", targetSpellbook.name(), e.getMessage());
            
            // Fallback to simple walkTo
            if (!Rs2Walker.walkTo(location)) {
                Microbot.status = "Fallback travel failed to " + targetSpellbook.name() + " spellbook location";
                return false;
            }
            
            return true;
        }
    }
    
   
    
    /**
     * Implements the abstract fulfillRequirement method from the base Requirement class.
     * Attempts to fulfill this spellbook requirement by switching spellbooks as needed.
     * 
     * @param executorService The ScheduledExecutorService on which fulfillment is running
     * @return true if the requirement was successfully fulfilled, false otherwise
     */
    @Override
    public boolean fulfillRequirement(CompletableFuture<Boolean> scheduledFuture) {
        try {
            if (Microbot.getClient() == null) {                
                return false;
            }
            if (Microbot.getClient().isClientThread()) {
                log.error("Please run fulfillRequirement() on a non-client thread.");
                return false;
            }
            // Check if the requirement is already fulfilled
            if (hasRequiredSpellbook()) {
                return true;
            }
            
            // Check if the required spellbook is available to the player
            if (!isRequiredSpellbookAvailable()) {
                if (isMandatory()) {
                    log.error("MANDATORY spellbook requirement cannot be fulfilled: " + getName() + " - Spellbook not unlocked");
                    return false;
                } else {
                    log.warn("RECOMMENDED spellbook requirement skipped: " + getName() + " - Spellbook not unlocked");
                    return true; // Non-mandatory requirements return true if spellbook isn't available
                }
            }
            
            // Determine action based on schedule context
            boolean success = false;            
            success = switchToRequiredSpellbook(scheduledFuture);
           
            
            if (!success && isMandatory()) {
                Microbot.log("MANDATORY spellbook requirement failed: " + getName());
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            Microbot.log("Error fulfilling spellbook requirement " + getName() + ": " + e.getMessage());
            return !isMandatory(); // Don't fail mandatory requirements due to exceptions
        }
    }
    
    /**
     * Check if the required spellbook is currently available (unlocked via quest completion)
     * 
     * @return true if the spellbook is unlocked and can be used
     */
    public boolean isSpellbookUnlocked() {
        return requiredSpellbook.isUnlocked();
    }
    
    /**
     * Get the quest required to unlock this spellbook (if any)
     * 
     * @return Quest required for spellbook access, or null if no quest required
     */
    public Quest getRequiredQuest() {
        return requiredSpellbook.getRequiredQuest();
    }
    
    /**
     * Get the location where spellbook switching can be performed
     * 
     * @return WorldPoint of the spellbook switching location
     */
    public WorldPoint getSwitchLocation() {
        return requiredSpellbook.getSwitchLocation();
    }
    
    /**
     * Get a description of the spellbook switching method and location
     * 
     * @return String describing how to switch to this spellbook
     */
    public String getSwitchDescription() {
        return requiredSpellbook.getDescription();
    }
}
