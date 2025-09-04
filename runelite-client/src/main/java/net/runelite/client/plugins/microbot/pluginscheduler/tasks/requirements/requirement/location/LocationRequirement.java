package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.EqualsAndHashCode;
import net.runelite.api.Constants;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementPriority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementType;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.TaskContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.Requirement;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.walker.TransportRouteAnalysis;
import net.runelite.client.plugins.microbot.util.world.Rs2WorldUtil;
import net.runelite.client.plugins.microbot.util.world.WorldHoppingConfig;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

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
    /**  -1 indicate not a spefic world
     *  The world hop to the targeted game world if specified.
     * 
    */
    private final int world;
     /**
     * Configuration for world hopping behavior with exponential backoff and retry limits.
     */
    @Setter
    private WorldHoppingConfig worldHoppingConfig = WorldHoppingConfig.createDefault();
    @Override
    public String getName() {
        LocationOption location = getBestAvailableLocation();
        String bestLocationString = location != null ? String.format("Location %s (%d, %d, %d)", location.getName(),
                               location.getWorldPoint().getX(), 
                               location.getWorldPoint().getY(), 
                               location.getWorldPoint().getPlane()) : "Unknown Location";
        
        
        if (targetLocations.size() == 1) {
            
        
            
            return String.format("\n\tSingle Location -> %s", bestLocationString);
        } else {

            return String.format("Multi-Location (%d options), best location ->  %s", targetLocations.size(), bestLocationString);
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
     * @param TaskContext When this requirement should be fulfilled
     * @param priority Priority level of this requirement
     * @param rating Effectiveness rating (1-10)
     * @param description Human-readable description
     */
    public LocationRequirement(
            List<LocationOption> targetLocations,            
            int acceptableDistance,
            boolean useTransports,                        
            int world,
            TaskContext taskContext,
            RequirementPriority priority,
            int rating,
            String description) {
        
        super(RequirementType.PLAYER_STATE, 
              priority, 
              rating, 
              description != null ? description : generateDefaultDescription(targetLocations, taskContext),
              Collections.emptyList(), // Location requirements don't use item IDs
              taskContext);
        
        this.targetLocations = new ArrayList<>(targetLocations);
        this.acceptableDistance = acceptableDistance;
        this.useTransports = useTransports;  
        this.world = world; // Default to no specific world      
    }
    
    /**
     * Constructor for single location requirement (backwards compatibility).
     * 
     * @param targetLocation The world point to travel to
     * @param acceptableDistance Distance tolerance for considering requirement fulfilled
     * @param useTransports Whether to use teleports and other transport methods     
     * @param locationName Custom name for this location
     * @param TaskContext When this requirement should be fulfilled
     * @param priority Priority level of this requirement
     * @param rating Effectiveness rating (1-10)
     * @param description Human-readable description
     */
    public LocationRequirement(
            WorldPoint targetLocation,
            String locationName,
            boolean membersOnly,
            int acceptableDistance,
            boolean useTransports,                        
            int world,
            TaskContext taskContext,
            RequirementPriority priority,
            int rating,
            String description) {
        this(Arrays.asList(new LocationOption(targetLocation,locationName, membersOnly)), // Default to non-members for single location
             acceptableDistance, useTransports, world,taskContext, priority, rating, description);
    }
    
    /**
     * Simplified constructor with common defaults.
     * 
     * @param targetLocation The world point to travel to
     * @param useTransports Whether to use teleports and other transport methods
     * @param locationName Custom name for this location
     * @param TaskContext When this requirement should be fulfilled
     * @param priority Priority level of this requirement
     */
    public LocationRequirement(
            WorldPoint targetLocation,
            String locationName,
            boolean membersOnly,
            int acceptableDistance,
            boolean useTransports, 
            int world,           
            TaskContext taskContext,
            RequirementPriority priority) {
        this(targetLocation,locationName, membersOnly,acceptableDistance, useTransports ,world, taskContext, priority, 8, null);
    }
    
    /**
     * Basic constructor for mandatory location requirements.
     * 
     * @param targetLocation The world point to travel to
     * @param useTransports Whether to use teleports and other transport methods
     * @param locationName Custom name for this location
     * @param TaskContext When this requirement should be fulfilled
     */
    public LocationRequirement(
            WorldPoint targetLocation,
            String locationName,
            boolean membersOnly,
            int acceptableDistance, 
            boolean useTransports,  
            int world,           
            TaskContext taskContext) {
        this(targetLocation, locationName,membersOnly,acceptableDistance,useTransports, world,taskContext, RequirementPriority.MANDATORY);
    }
    
    /**
     * Constructor for bank locations using existing bank infrastructure.
     * 
     * @param bankLocation The bank location to use as target
     * @param acceptableDistance Distance tolerance for considering requirement fulfilled
     * @param useTransports Whether to use teleports and other transport methods
     * @param TaskContext When this requirement should be fulfilled
     * @param priority Priority level of this requirement
     */
    public LocationRequirement(
            BankLocation bankLocation,
            int acceptableDistance,
            boolean useTransports,
            int world,
            TaskContext taskContext,
            RequirementPriority priority) {
            this(Arrays.asList(new LocationOption(bankLocation.getWorldPoint(),
                                                    bankLocation.toString(),
                                                    bankLocation.isMembers())),
                acceptableDistance, useTransports, world,taskContext, priority, 5, bankLocation.getClass().getSimpleName() + " Bank Location Requirement");             
        }
    public LocationRequirement(
            BankLocation bankLocation,            
            boolean useTransports,
            int world,
            TaskContext taskContext,
            RequirementPriority priority) {
                this(bankLocation, 15, useTransports, world,taskContext, priority);
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
        
        if (world != -1 && isWorldHopRequired()){
            log.error("Player is not in the required world: {} (current: {})", world, Rs2Player.getWorld());
            return false; // Player is not in the required world
        }
        // Check if player is at any accessible location
        for (LocationOption location : targetLocations) {
            if (location.hasRequirements() && 
                currentLocation.distanceTo(location.getWorldPoint()) <= acceptableDistance) {
                log.debug("Player is at required location: {} (distance: {})", location.getName(), currentLocation.distanceTo(location.getWorldPoint()));
                return true;
            }
        }
        List<Integer> distanceIntegers = targetLocations.stream()
                .map(loc -> loc.getWorldPoint().distanceTo(currentLocation))
                .collect(Collectors.toList());
        log.debug("Player is not at any required location.\n\tCurrent location: {}\n\tRequired locations: {}\n\tdistances to each location: {}", currentLocation, targetLocations,  distanceIntegers);
        
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
    @Override
    public boolean isFulfilled() {
        // Check if the player is at any of the required locations
        return isAtRequiredLocation();
    }
    
    /**
     * Implements the abstract fulfillRequirement method from the base Requirement class.
     * Attempts to fulfill this location requirement by traveling to the target location.
     * 
     * @param executorService The ScheduledExecutorService on which this requirement fulfillment is running
     * @return true if the requirement was successfully fulfilled, false otherwise
     */
    @Override
    public boolean fulfillRequirement(CompletableFuture<Boolean> scheduledFuture) {
        boolean currentUseWithBankedItems = Microbot.getConfigManager().getConfiguration(ShortestPathPlugin.CONFIG_GROUP, "walkWithBankedTransports", Boolean.class);
        Microbot.getConfigManager().setConfiguration(ShortestPathPlugin.CONFIG_GROUP, "walkWithBankedTransports", useTransports);
        try {
            if (Microbot.getClient() == null || Microbot.getClient().isClientThread()) {
                log.error("Cannot fulfill location requirement outside client thread");
                return false; // Cannot fulfill outside client thread
            }
            
          
            log.info("Attempting to fulfill location requirement: {}", getName());
            // Check if the requirement is already fulfilled
            if (isAtRequiredLocation()) {
                return true;
            }
            
          
            log.info("Checking if location is reachable for requirement: {}", getName());
            // Check if the location is reachable
            if (!isLocationReachable()) {
                if (isMandatory()) {
                   log.error("MANDATORY location requirement cannot be fulfilled: " + getName() + " - Location not reachable");
                    return false;
                } else {
                    log.error("OPTIONAL/RECOMMENDED location requirement skipped: " + getName() + " - Location not reachable");
                    return true; // Non-mandatory requirements return true if location isn't reachable
                }
            }
            log.info("Location is reachable, proceeding to travel for requirement: {}", getName());

            
            // Attempt to travel to the location
            boolean success = travelToLocation(scheduledFuture);
            
            if (!success && isMandatory()) {
                log.error("MANDATORY location requirement failed: " + getName());
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Error fulfilling location requirement " + getName() + ": " + e.getMessage());
            return !isMandatory(); // Don't fail mandatory requirements due to exceptions
        }finally {
            // Restore the original setting for banked transports
            Microbot.getConfigManager().setConfiguration(ShortestPathPlugin.CONFIG_GROUP, "walkWithBankedTransports", currentUseWithBankedItems);
        }
    }
    
    /**
     * Attempts to travel to the best available target location using Rs2Walker with movement watchdog.
     * Creates its own executor service for the watchdog that gets cleaned up when done.
     * 
     * @param scheduledFuture The CompletableFuture to monitor for cancellation
     * @return true if the travel was successful, false otherwise
     */
    private boolean travelToLocation(CompletableFuture<Boolean> scheduledFuture) {
        ScheduledExecutorService travelExecutorService = null;
        ScheduledFuture<?> watchdogFuture = null;
        final int MAX_RETRIES = 3; // Maximum retries for walking to the location
        AtomicBoolean watchdogTriggered = new AtomicBoolean(false);
        if (world !=-1 && !Rs2WorldUtil.canAccessWorld(world)){
            log.warn("Cannot access world {} for requirement: {}", world, getName());
            return false; // Cannot proceed if world is not accessible
        }
        if( world != -1 && isWorldHopRequired()){
            boolean successWorldHop = Rs2WorldUtil.hopWorld(scheduledFuture, world, 1, worldHoppingConfig);
            if (!successWorldHop) {
                log.warn("World hop failed for requirement: {}", getName());
                return false; // World hop failed, cannot proceed
            }   
        }
        try {          
            LocationOption bestLocation = getBestAvailableLocation();
            if (bestLocation == null) {
                log.warn("No available location found for requirement: {}", getName());
                return false;
            }
            
            WorldPoint targetLocation = bestLocation.getWorldPoint();
            
            // Create a dedicated executor service for this travel operation
            travelExecutorService = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "LocationRequirement-Travel-" + getName());
                thread.setDaemon(true); // Daemon thread so it doesn't prevent JVM shutdown
                return thread;
            });
            
            // Start movement watchdog with our own executor service
            watchdogFuture = startMovementWatchdog(travelExecutorService, scheduledFuture, watchdogTriggered, getName());
            if (watchdogFuture != null && !watchdogFuture.isDone()) {
                log.debug("Movement watchdog started for location: {}", getName());
            
                
            }

            // Check if we need to get transport items from bank
            boolean walkResult = false;
            
            for (int retries = 0; retries < MAX_RETRIES; retries++) {
                if  (walkResult==true || (scheduledFuture != null && scheduledFuture.isDone()) || watchdogTriggered.get()){
                    break; // Exit loop if we successfully walked or if the future is cancelled
                }
                Rs2Walker.setTarget(null); // Reset target in Rs2Walker
                if (useTransports) {
                    // This would be enhanced to check for specific transport items
                    // For now, just ensure we have access to basic travel
                    walkResult = Rs2Walker.walkWithBankedTransports(targetLocation,acceptableDistance,false);
                } else {
                    // Use Rs2Walker to travel to the location
                    walkResult = Rs2Walker.walkTo(targetLocation,acceptableDistance);
                }                
            }
            
            if(isAtRequiredLocation()) {
                log.debug("\nSuccessfully reached required location: {}", getName());
                return true; // Already at the required location
            }
            
            if (walkResult && !watchdogTriggered.get()) {       
                sleepUntil(()-> !Rs2Player.isMoving() , 5000);
                return isAtRequiredLocation() && !watchdogTriggered.get();
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error traveling to location " + getName() + ": " + e.getMessage());
            return false;
        } finally {
            // Always clean up the watchdog first
            if (watchdogFuture != null && !watchdogFuture.isDone()) {
                watchdogFuture.cancel(true);
            }
            
            // Then shutdown the executor service
            if (travelExecutorService != null) {
                travelExecutorService.shutdown();
                try {
                    // Wait a bit for graceful shutdown
                    if (!travelExecutorService.awaitTermination(2, TimeUnit.SECONDS)) {
                        // Force shutdown if tasks don't complete quickly
                        travelExecutorService.shutdownNow();
                        if (!travelExecutorService.awaitTermination(1, TimeUnit.SECONDS)) {
                            log.warn("Travel executor service did not terminate cleanly for {}", getName());
                        }
                    }
                } catch (InterruptedException e) {
                    // Restore interrupted status and force shutdown
                    Thread.currentThread().interrupt();
                    travelExecutorService.shutdownNow();
                }
            }
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
    private boolean isWorldHopRequired() {
        // If world is -1, no specific world hop is required
        if (world == -1) {
            return false;
        }
        
        // Check if the current world matches the target world
        return Rs2Player.getWorld() != world;
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
        sb.append("Schedule Context:\t").append(taskContext.name()).append("\n");
        sb.append("Acceptable Distance:\t").append(acceptableDistance).append(" tiles\n");
        sb.append("Use Transports:\t\t").append(useTransports ? "Yes" : "No").append("\n");
        sb.append("World:\t\t\t").append(world != -1 ? world : "Any world").append("\n");
        sb.append("canAccessWorld:\t").append(Rs2WorldUtil.canAccessWorld(world) ? "Yes" : "No").append("\n");
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
       /**
     * Generates a default description based on the target locations and context.
     * 
     * @param locations The target locations
     * @param context When the requirement should be fulfilled
     * @return A descriptive string explaining the requirement
     */
    public static String generateDefaultDescription(List<LocationOption> locations, TaskContext context) {
        if (locations == null || locations.isEmpty()) {
            return "Unknown Location Requirement";
        }
        
        String contextPrefix = (context == TaskContext.PRE_SCHEDULE) ? "Pre-task" : "Post-task";
        
        if (locations.size() == 1) {
            LocationOption location = locations.get(0);
            WorldPoint wp = location.getWorldPoint();
            String bestLocationString = location.getName() != null ? 
                location.getName() : 
                String.format("(%d, %d, %d)", wp.getX(), wp.getY(), wp.getPlane());
            
            return String.format("%s location requirement: %s", contextPrefix, bestLocationString);
        } else {
            // Multiple locations - show best available
            LocationOption bestLocation = locations.get(0); // First available location
            WorldPoint wp = bestLocation.getWorldPoint();
            String bestLocationString = bestLocation.getName() != null ? 
                bestLocation.getName() : 
                String.format("(%d, %d, %d)", wp.getX(), wp.getY(), wp.getPlane());
            
            return String.format("%s multi-location requirement (%d options), primary: %s", 
                    contextPrefix, locations.size(), bestLocationString);
        }
    }

    /**
     * Checks if the player has moved out of a defined area around the last position.
     * This is more robust than checking single coordinates as it accounts for small movements.
     * 
     * @param lastPosition The last recorded position
     * @param currentPosition The current position
     * @param areaRadius The radius of the area to check
     * @return true if the player has moved significantly outside the area
     */
    public static boolean hasMovedOutOfArea(WorldPoint lastPosition, WorldPoint currentPosition, int areaRadius) {
        if (lastPosition == null || currentPosition == null) {
            return false;
        }
        
        // Calculate distance between positions
        int distance = lastPosition.distanceTo(currentPosition);
        return distance > areaRadius;
    }
    
    /**
     * Starts a movement watchdog that monitors player position and stops walking if no movement is detected.
     * 
     * @param executorService The executor service to run the watchdog on
     * @param scheduledFuture The future to monitor for cancellation
     * @param watchdogTriggered Atomic boolean to signal when watchdog triggers
     * @param taskName Name of the task for logging purposes
     * @return The scheduled future for the watchdog task
     */
    public static ScheduledFuture<?> startMovementWatchdog(ScheduledExecutorService executorService, 
                                                          CompletableFuture<Boolean> scheduledFuture, 
                                                          AtomicBoolean watchdogTriggered,
                                                          String taskName) {
        AtomicReference<WorldPoint> lastPosition = new AtomicReference<>(Rs2Player.getWorldLocation());
        AtomicReference<Long> lastMovementTime = new AtomicReference<>(System.currentTimeMillis());        
        long watchdogCheckInterval_ms = Constants.GAME_TICK_LENGTH; // Check every game tick
        return executorService.scheduleAtFixedRate(() -> {
            try {
                // Check for cancellation first
                if (scheduledFuture != null && scheduledFuture.isCancelled()) {
                    log.info("Movement watchdog cancelled for: {}", taskName);
                    watchdogTriggered.set(true);
                    Rs2Walker.setTarget(null);
                    throw new RuntimeException("Watchdog cancelled - stopping task");
                }
                
                WorldPoint currentPosition = Rs2Player.getWorldLocation();
                if (currentPosition == null) {
                    watchdogTriggered.set(true);                        
                    // Stop walking by clearing the target
                    Rs2Walker.setTarget(null);
                    throw new RuntimeException("Watchdog cancelled - stopping task");
                }
                
                WorldPoint lastPos = lastPosition.get();
                if (lastPos == null) {
                    lastPosition.set(currentPosition);
                    lastMovementTime.set(System.currentTimeMillis());
                    return;
                }
                log.debug("Current position: {}, Last position: {}", currentPosition, lastPos);
                // Check if player has moved significantly (using area detection for robustness)
                boolean hasMovedSignificantly = hasMovedOutOfArea(lastPos, currentPosition, 2);
                
                if (hasMovedSignificantly || Rs2Bank.isOpen() || Rs2Shop.isOpen()) {
                    // Player has moved, update last movement time and position
                    lastPosition.set(currentPosition);
                    lastMovementTime.set(System.currentTimeMillis());
                } else {
                    // Player hasn't moved significantly, check timeout
                    long timeSinceLastMovement = System.currentTimeMillis() - lastMovementTime.get();
                    log.debug ("Time since last movement: {} ms", timeSinceLastMovement);
                    if (timeSinceLastMovement > watchdogCheckInterval_ms*10) { // more than 5 times the check interval
                        log.warn("Movement watchdog triggered - no significant movement detected for 1 minute");
                        watchdogTriggered.set(true);
                        
                        // Stop walking by clearing the target
                        Rs2Walker.setTarget(null);
                        
                        // Cancel this watchdog
                        throw new RuntimeException("Watchdog triggered - stopping task");
                    }
                }
            } catch (Exception e) {
                log.warn("Watchdog error: {}", e.getMessage());
                watchdogTriggered.set(true);
                Rs2Walker.setTarget(null);
                throw e; // Re-throw to stop the scheduled task
            }
        }, 0, watchdogCheckInterval_ms, TimeUnit.MILLISECONDS); // Check every ms
    }
}
