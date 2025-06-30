package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.EqualsAndHashCode;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.Priority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementType;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.ScheduleContext;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.TransportRouteAnalysis;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Represents a location requirement for pre and post schedule tasks.
 * This requirement ensures the player is at a specific location before or after script execution.
 * 
 * LocationRequirement integrates with Rs2Walker for intelligent pathfinding and travel,
 * supporting both simple location checks and complex travel operations.
 * 
 * Enhanced to support multiple target locations with quest and skill requirements.
 */
@Slf4j
@Getter
@EqualsAndHashCode(callSuper = true)
public class LocationRequirement extends Requirement {
    
    /**
     * Container class for location data with requirements.
     */
    @Getter
    public static class LocationOption {
        private final WorldPoint worldPoint;
        private final String name;
        private final Map<Quest, QuestState> requiredQuests;
        private final Map<Skill, Integer> requiredSkills;
        
        public LocationOption(WorldPoint worldPoint, String name) {
            this(worldPoint, name, new HashMap<>(), new HashMap<>());
        }
        
        public LocationOption(WorldPoint worldPoint, String name, 
                            Map<Quest, QuestState> requiredQuests, 
                            Map<Skill, Integer> requiredSkills) {
            this.worldPoint = worldPoint;
            this.name = name;
            this.requiredQuests = requiredQuests != null ? new HashMap<>(requiredQuests) : new HashMap<>();
            this.requiredSkills = requiredSkills != null ? new HashMap<>(requiredSkills) : new HashMap<>();
        }
        
        /**
         * Checks if the player meets all requirements for this location.
         */
        public boolean hasRequirements() {
            // Check quest requirements
            for (Map.Entry<Quest, QuestState> questReq : requiredQuests.entrySet()) {
                QuestState currentState = Rs2Player.getQuestState(questReq.getKey());
                QuestState requiredState = questReq.getValue();
                
                // If required state is FINISHED, player must have finished
                if (requiredState == QuestState.FINISHED && currentState != QuestState.FINISHED) {
                    return false;
                }
                // If required state is IN_PROGRESS, player must have started (IN_PROGRESS or FINISHED)
                if (requiredState == QuestState.IN_PROGRESS && 
                    currentState != QuestState.IN_PROGRESS && currentState != QuestState.FINISHED) {
                    return false;
                }
            }
            
            // Check skill requirements
            for (Map.Entry<Skill, Integer> skillReq : requiredSkills.entrySet()) {
                if (!Rs2Player.getSkillRequirement(skillReq.getKey(), skillReq.getValue())) {
                    return false;
                }
            }
            
            return true;
        }
        
        @Override
        public String toString() {
            return name + " (" + worldPoint.getX() + ", " + worldPoint.getY() + ", " + worldPoint.getPlane() + ")";
        }
    }
    
    /**
     * List of possible target locations for this requirement.
     */
    private final List<LocationOption> targetLocations;
    
    /**
     * The acceptable distance from the target location to consider the requirement fulfilled.
     * Default is 5 tiles for most operations.
     */
    private final int acceptableDistance;
    
    /**
     * Whether to use transport methods (teleports, boats, etc.) when traveling to this location.
     * Default is true for efficient travel.
     */
    private final boolean useTransports;
    
    /**
     * Custom name for this location requirement (optional).
     * If not provided, will use coordinates as name.
     */
    private final String locationName;
    
    @Override
    public String getName() {
        if (locationName != null && !locationName.isEmpty()) {
            return locationName;
        }
        if (targetLocations.size() == 1) {
            LocationOption location = targetLocations.get(0);
            return String.format("Location (%d, %d, %d)", 
                               location.getWorldPoint().getX(), 
                               location.getWorldPoint().getY(), 
                               location.getWorldPoint().getPlane());
        } else {
            return String.format("Multi-Location (%d options)", targetLocations.size());
        }
    }
    
    /**
     * Gets the best available location option based on current player requirements and position.
     * Prioritizes accessible locations, then proximity to player.
     */
    public LocationOption getBestAvailableLocation() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        
        // Filter to only accessible locations
        List<LocationOption> accessibleLocations = targetLocations.stream()
                .filter(LocationOption::hasRequirements)
                .collect(Collectors.toList());
        
        if (accessibleLocations.isEmpty()) {
            log.warn("No accessible locations found for requirement: {}", getName());
            // Return the first location even if not accessible (for error reporting)
            return targetLocations.isEmpty() ? null : targetLocations.get(0);
        }
        
        if (accessibleLocations.size() == 1) {
            return accessibleLocations.get(0);
        }
        
        // If player location is available, find closest accessible location
        if (playerLocation != null) {
            return accessibleLocations.stream()
                    .min((loc1, loc2) -> Integer.compare(
                            playerLocation.distanceTo(loc1.getWorldPoint()),
                            playerLocation.distanceTo(loc2.getWorldPoint())
                    ))
                    .orElse(accessibleLocations.get(0));
        }
        
        // Fall back to first accessible location
        return accessibleLocations.get(0);
    }
    
    /**
     * Gets the best available location option based on a reference point.
     * Prioritizes accessible locations, then proximity to reference point.
     */
    public LocationOption getBestAvailableLocation(WorldPoint referencePoint) {
        // Filter to only accessible locations
        List<LocationOption> accessibleLocations = targetLocations.stream()
                .filter(LocationOption::hasRequirements)
                .collect(Collectors.toList());
        
        if (accessibleLocations.isEmpty()) {
            log.warn("No accessible locations found for requirement: {}", getName());
            return targetLocations.isEmpty() ? null : targetLocations.get(0);
        }
        
        if (accessibleLocations.size() == 1) {
            return accessibleLocations.get(0);
        }
        
        // Find closest accessible location to reference point
        return accessibleLocations.stream()
                .min((loc1, loc2) -> Integer.compare(
                        referencePoint.distanceTo(loc1.getWorldPoint()),
                        referencePoint.distanceTo(loc2.getWorldPoint())
                ))
                .orElse(accessibleLocations.get(0));
    }
    
    /**
     * Full constructor for LocationRequirement with multiple locations.
     * 
     * @param targetLocations List of possible target locations with requirements
     * @param acceptableDistance Distance tolerance for considering requirement fulfilled
     * @param useTransports Whether to use teleports and other transport methods     
     * @param locationName Custom name for this location requirement
     * @param scheduleContext When this requirement should be fulfilled
     * @param priority Priority level of this requirement
     * @param rating Effectiveness rating (1-10)
     * @param description Human-readable description
     */
    public LocationRequirement(
            List<LocationOption> targetLocations,
            int acceptableDistance,
            boolean useTransports,            
            String locationName,
            ScheduleContext scheduleContext,
            Priority priority,
            int rating,
            String description) {
        
        super(RequirementType.PLAYER_STATE, 
              priority, 
              rating, 
              description != null ? description : generateDefaultDescription(targetLocations, scheduleContext),
              Collections.emptyList(), // Location requirements don't use item IDs
              scheduleContext);
        
        this.targetLocations = new ArrayList<>(targetLocations);
        this.acceptableDistance = acceptableDistance;
        this.useTransports = useTransports;
        this.locationName = locationName;
    }
    
    /**
     * Constructor for single location requirement (backwards compatibility).
     * 
     * @param targetLocation The world point to travel to
     * @param acceptableDistance Distance tolerance for considering requirement fulfilled
     * @param useTransports Whether to use teleports and other transport methods     
     * @param locationName Custom name for this location
     * @param scheduleContext When this requirement should be fulfilled
     * @param priority Priority level of this requirement
     * @param rating Effectiveness rating (1-10)
     * @param description Human-readable description
     */
    public LocationRequirement(
            WorldPoint targetLocation,
            int acceptableDistance,
            boolean useTransports,            
            String locationName,
            ScheduleContext scheduleContext,
            Priority priority,
            int rating,
            String description) {
        this(Arrays.asList(new LocationOption(targetLocation, locationName != null ? locationName : "Location")),
             acceptableDistance, useTransports, locationName, scheduleContext, priority, rating, description);
    }
    
    /**
     * Simplified constructor with common defaults.
     * 
     * @param targetLocation The world point to travel to
     * @param useTransports Whether to use teleports and other transport methods
     * @param locationName Custom name for this location
     * @param scheduleContext When this requirement should be fulfilled
     * @param priority Priority level of this requirement
     */
    public LocationRequirement(
            WorldPoint targetLocation,
            boolean useTransports, 
            String locationName,
            ScheduleContext scheduleContext,
            Priority priority) {
        this(targetLocation, 5, useTransports, locationName, scheduleContext, priority, 8, null);
    }
    
    /**
     * Basic constructor for mandatory location requirements.
     * 
     * @param targetLocation The world point to travel to
     * @param useTransports Whether to use teleports and other transport methods
     * @param locationName Custom name for this location
     * @param scheduleContext When this requirement should be fulfilled
     */
    public LocationRequirement(
            WorldPoint targetLocation,
            boolean useTransports, 
            String locationName,
            ScheduleContext scheduleContext) {
        this(targetLocation, useTransports, locationName, scheduleContext, Priority.MANDATORY);
    }
    
    /**
     * Constructor for bank locations using existing bank infrastructure.
     * 
     * @param bankLocation The bank location to use as target
     * @param useTransports Whether to use teleports and other transport methods
     * @param scheduleContext When this requirement should be fulfilled
     * @param priority Priority level of this requirement
     */
    public LocationRequirement(
            BankLocation bankLocation,
            boolean useTransports,
            ScheduleContext scheduleContext,
            Priority priority) {
        this(bankLocation.getWorldPoint(), 
             useTransports,
             bankLocation.toString(), 
             scheduleContext, 
             priority);
    }

    /**
     * Generates a default description based on the target locations and context.
     * 
     * @param locations The target locations
     * @param context When the requirement should be fulfilled
     * @return A descriptive string explaining the requirement
     */
    private static String generateDefaultDescription(List<LocationOption> locations, ScheduleContext context) {
        String contextStr = context == ScheduleContext.PRE_SCHEDULE ? "before script execution" : 
                           context == ScheduleContext.POST_SCHEDULE ? "after script completion" : 
                           "during script execution";
        
        if (locations.size() == 1) {
            LocationOption location = locations.get(0);
            return String.format("Must be at location (%d, %d, %d) %s", 
                               location.getWorldPoint().getX(), 
                               location.getWorldPoint().getY(), 
                               location.getWorldPoint().getPlane(), 
                               contextStr);
        } else {
            return String.format("Must be at one of %d possible locations %s", 
                               locations.size(), contextStr);
        }
    }
    
    /**
     * Checks if the player is currently at any of the required locations.
     * 
     * @return true if the player is within acceptable distance of any valid target location
     */
    public boolean isAtRequiredLocation() {
        WorldPoint currentLocation = Rs2Player.getWorldLocation();
        if (currentLocation == null) {
            return false;
        }
        
        // Check if player is at any accessible location
        for (LocationOption location : targetLocations) {
            if (location.hasRequirements() && 
                currentLocation.distanceTo(location.getWorldPoint()) <= acceptableDistance) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Checks if at least one required location is reachable using available methods.
     * This method uses Rs2Walker to determine reachability without actually traveling.
     * 
     * @return true if at least one location can be reached, false otherwise
     */
    public boolean isLocationReachable() {
        // Check if any accessible location is reachable
        for (LocationOption location : targetLocations) {
            if (location.hasRequirements()) {
                try {
                    if (Rs2Walker.canReach(location.getWorldPoint(), true)) {
                        return true;
                    }
                } catch (Exception e) {
                    log.warn("Error checking reachability for location {}: {}", location, e.getMessage());
                }
            }
        }
        
        // If no accessible locations are reachable, check if any location is reachable (for non-mandatory requirements)
        if (!isMandatory()) {
            for (LocationOption location : targetLocations) {
                try {
                    if (Rs2Walker.canReach(location.getWorldPoint(), true)) {
                        return true;
                    }
                } catch (Exception e) {
                    log.warn("Error checking reachability for location {}: {}", location, e.getMessage());
                }
            }
        }
        
        return false;
    }
    
    /**
     * Implements the abstract fulfillRequirement method from the base Requirement class.
     * Attempts to fulfill this location requirement by traveling to the target location.
     * 
     * @return true if the requirement was successfully fulfilled, false otherwise
     */
    @Override
    public boolean fulfillRequirement() {
        try {
            if (Microbot.getClient() == null || Microbot.getClient().isClientThread()) {
                log.info("Cannot fulfill location requirement outside client thread");
                return false; // Cannot fulfill outside client thread
            }
            // Check if the requirement is already fulfilled
            if (isAtRequiredLocation()) {
                return true;
            }
            
            // Check if the location is reachable
            if (!isLocationReachable()) {
                if (isMandatory()) {
                    Microbot.log("MANDATORY location requirement cannot be fulfilled: " + getName() + " - Location not reachable");
                    return false;
                } else {
                    Microbot.log("OPTIONAL/RECOMMENDED location requirement skipped: " + getName() + " - Location not reachable");
                    return true; // Non-mandatory requirements return true if location isn't reachable
                }
            }
            
            // Attempt to travel to the location
            boolean success = travelToLocation();
            
            if (!success && isMandatory()) {
                Microbot.log("MANDATORY location requirement failed: " + getName());
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            Microbot.log("Error fulfilling location requirement " + getName() + ": " + e.getMessage());
            return !isMandatory(); // Don't fail mandatory requirements due to exceptions
        }
    }
    
    /**
     * Attempts to travel to the best available target location using Rs2Walker.
     * 
     * @return true if the travel was successful, false otherwise
     */
    private boolean travelToLocation() {
        try {
            LocationOption bestLocation = getBestAvailableLocation();
            if (bestLocation == null) {
                log.warn("No available location found for requirement: {}", getName());
                return false;
            }
            
            WorldPoint targetLocation = bestLocation.getWorldPoint();
            
            // Check if we need to get transport items from bank
            boolean walkResult = false;
            if (useTransports) {
                // This would be enhanced to check for specific transport items
                // For now, just ensure we have access to basic travel
                walkResult = Rs2Walker.walkWithBankedTransports(targetLocation);
            } else {
                // Use Rs2Walker to travel to the location
                walkResult = Rs2Walker.walkTo(targetLocation);
            }
            
            if (walkResult) {
                // Wait for arrival with timeout
                sleepUntil(this::isAtRequiredLocation, 30000);
                return isAtRequiredLocation();
            }
            
            return false;
            
        } catch (Exception e) {
            Microbot.log("Error traveling to location " + locationName + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets the estimated travel time to the best available location in game ticks.
     * This is a rough estimate based on distance and available transport methods.
     * 
     * @return Estimated travel time in game ticks, or -1 if cannot estimate
     */
    public int getEstimatedTravelTime() {
        WorldPoint currentLocation = Rs2Player.getWorldLocation();
        if (currentLocation == null) {
            return -1;
        }
        
        LocationOption bestLocation = getBestAvailableLocation();
        if (bestLocation == null) {
            return -1;
        }
        
        WorldPoint targetLocation = bestLocation.getWorldPoint();
        TransportRouteAnalysis result = Rs2Walker.compareRoutes(targetLocation); // Ensure Rs2Walker has the latest pathfinding data
        
        int distance = Integer.MAX_VALUE; // Default to a large value if no path found
        if (result != null && (result.isDirectIsFaster() || !useTransports)) {
            distance = result.getDirectDistance(); // Use direct distance if it's faster
        } else if (result != null && useTransports) {
            distance = result.getBankingRouteDistance(); // Otherwise, use banking route distance
        }
        
        // Walking only, slower travel
        return (distance / 2); // when running we can move 2 tiles per game tick, so 2 tiles per Constants.GAME_TICK
    }
    
    /**
     * Returns a detailed display string with location requirement information.
     * 
     * @return A formatted string containing location requirement details
     */
    @Override
    public String displayString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Location Requirement Details ===\n");
        sb.append("Name:\t\t\t").append(getName()).append("\n");
        sb.append("Type:\t\t\t").append(getRequirementType().name()).append("\n");
        sb.append("Priority:\t\t").append(getPriority().name()).append("\n");
        sb.append("Rating:\t\t\t").append(getRating()).append("/10\n");
        sb.append("Schedule Context:\t").append(scheduleContext.name()).append("\n");
        sb.append("Acceptable Distance:\t").append(acceptableDistance).append(" tiles\n");
        sb.append("Use Transports:\t\t").append(useTransports ? "Yes" : "No").append("\n");
        sb.append("Description:\t\t").append(getDescription() != null ? getDescription() : "No description").append("\n");
        
        // Add location details
        sb.append("\n--- Available Locations (").append(targetLocations.size()).append(") ---\n");
        for (int i = 0; i < targetLocations.size(); i++) {
            LocationOption location = targetLocations.get(i);
            sb.append("Location ").append(i + 1).append(":\t\t").append(location.getName()).append("\n");
            sb.append("  Coordinates:\t\t").append(String.format("(%d, %d, %d)", 
                                                                location.getWorldPoint().getX(), 
                                                                location.getWorldPoint().getY(), 
                                                                location.getWorldPoint().getPlane())).append("\n");
            sb.append("  Accessible:\t\t").append(location.hasRequirements() ? "Yes" : "No").append("\n");
            
            if (!location.getRequiredQuests().isEmpty()) {
                sb.append("  Required Quests:\t");
                location.getRequiredQuests().entrySet().stream()
                        .forEach(entry -> sb.append(entry.getKey().name()).append(" (").append(entry.getValue().name()).append(") "));
                sb.append("\n");
            }
            
            if (!location.getRequiredSkills().isEmpty()) {
                sb.append("  Required Skills:\t");
                location.getRequiredSkills().entrySet().stream()
                        .forEach(entry -> sb.append(entry.getKey().name()).append(" ").append(entry.getValue()).append(" "));
                sb.append("\n");
            }
        }
        
        // Add current status
        sb.append("\n--- Current Status ---\n");
        sb.append("Currently at Location:\t").append(isAtRequiredLocation() ? "Yes" : "No").append("\n");
        sb.append("Location Reachable:\t").append(isLocationReachable() ? "Yes" : "No").append("\n");
        sb.append("Estimated Travel Time:\t").append(getEstimatedTravelTime()).append(" seconds\n");
        
        LocationOption bestLocation = getBestAvailableLocation();
        if (bestLocation != null) {
            sb.append("Best Available Location:\t").append(bestLocation.toString()).append("\n");
        }
        
        return sb.toString();
    }
}
