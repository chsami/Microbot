package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.EqualsAndHashCode;


import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementPriority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementType;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.ScheduleContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.Requirement;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import net.runelite.client.plugins.microbot.util.walker.TransportRouteAnalysis;

import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.shop.models.WorldHoppingConfig;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
            
        
            
            return String.format("Single Location: %s", bestLocationString);
        } else {

            return String.format("Multi-Location (%d options), best location: %s", targetLocations.size(), bestLocationString);
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
            int world,
            ScheduleContext scheduleContext,
            RequirementPriority priority,
            int rating,
            String description) {
        
        super(RequirementType.PLAYER_STATE, 
              priority, 
              rating, 
              description != null ? description : LocationRequirementUtil.generateDefaultDescription(targetLocations, scheduleContext),
              Collections.emptyList(), // Location requirements don't use item IDs
              scheduleContext);
        
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
     * @param scheduleContext When this requirement should be fulfilled
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
            ScheduleContext scheduleContext,
            RequirementPriority priority,
            int rating,
            String description) {
        this(Arrays.asList(new LocationOption(targetLocation,locationName, membersOnly)), // Default to non-members for single location
             acceptableDistance, useTransports, world,scheduleContext, priority, rating, description);
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
            String locationName,
            boolean membersOnly,
            int acceptableDistance,
            boolean useTransports, 
            int world,           
            ScheduleContext scheduleContext,
            RequirementPriority priority) {
        this(targetLocation,locationName, membersOnly,acceptableDistance, useTransports ,world, scheduleContext, priority, 8, null);
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
            String locationName,
            boolean membersOnly,
            int acceptableDistance, 
            boolean useTransports,  
            int world,           
            ScheduleContext scheduleContext) {
        this(targetLocation, locationName,membersOnly,acceptableDistance,useTransports, world,scheduleContext, RequirementPriority.MANDATORY);
    }
    
    /**
     * Constructor for bank locations using existing bank infrastructure.
     * 
     * @param bankLocation The bank location to use as target
     * @param acceptableDistance Distance tolerance for considering requirement fulfilled
     * @param useTransports Whether to use teleports and other transport methods
     * @param scheduleContext When this requirement should be fulfilled
     * @param priority Priority level of this requirement
     */
    public LocationRequirement(
            BankLocation bankLocation,
            int acceptableDistance,
            boolean useTransports,
            int world,
            ScheduleContext scheduleContext,
            RequirementPriority priority) {
            this(Arrays.asList(new LocationOption(bankLocation.getWorldPoint(),
                                                    bankLocation.toString(),
                                                    bankLocation.isMembers())),
                acceptableDistance, useTransports, world,scheduleContext, priority, 5, bankLocation.getClass().getSimpleName() + " Bank Location Requirement");             
        }
    public LocationRequirement(
            BankLocation bankLocation,            
            boolean useTransports,
            int world,
            ScheduleContext scheduleContext,
            RequirementPriority priority) {
                this(bankLocation, 15, useTransports, world,scheduleContext, priority);
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
            log.info("Player is not in the required world: {} (current: {})", world, Rs2Player.getWorld());
            return false; // Player is not in the required world
        }
        // Check if player is at any accessible location
        for (LocationOption location : targetLocations) {
            if (location.hasRequirements() && 
                currentLocation.distanceTo(location.getWorldPoint()) <= acceptableDistance) {
                log.info("Player is at required location: {} (distance: {})", location.getName(), currentLocation.distanceTo(location.getWorldPoint()));
                return true;
            }
        }
        List<Integer> distanceIntegers = targetLocations.stream()
                .map(loc -> loc.getWorldPoint().distanceTo(currentLocation))
                .collect(Collectors.toList());
        log.info("Player is not at any required location.\n\tCurrent location: {},\n\t Required locations: {}\n\t distances to each location: {}", currentLocation, targetLocations,  distanceIntegers);
        
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
            boolean success = travelToLocation(scheduledFuture);
            
            if (!success && isMandatory()) {
                Microbot.log("MANDATORY location requirement failed: " + getName());
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            Microbot.log("Error fulfilling location requirement " + getName() + ": " + e.getMessage());
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
        AtomicBoolean watchdogTriggered = new AtomicBoolean(false);
        if (world !=-1 && !LocationRequirementUtil.canAccessWorld(world)){
            log.warn("Cannot access world {} for requirement: {}", world, getName());
            return false; // Cannot proceed if world is not accessible
        }
        if( world != -1 && isWorldHopRequired()){
            boolean successWorldHop = LocationRequirementUtil.hopWorld(scheduledFuture, world, 1, worldHoppingConfig);
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
            watchdogFuture = LocationRequirementUtil.startMovementWatchdog(travelExecutorService, scheduledFuture, watchdogTriggered, getName());
            if (watchdogFuture != null && !watchdogFuture.isDone()) {
                log.info("Movement watchdog started for location: {}", getName());
            
                
            }

            // Check if we need to get transport items from bank
            boolean walkResult = false;
            if (useTransports) {
                // This would be enhanced to check for specific transport items
                // For now, just ensure we have access to basic travel
                walkResult = Rs2Walker.walkWithBankedTransports(targetLocation,acceptableDistance,false);
            } else {
                // Use Rs2Walker to travel to the location
                walkResult = Rs2Walker.walkTo(targetLocation,acceptableDistance);
            }
            
            if(isAtRequiredLocation()) {
                log.info("Successfully reached required location: {}", getName());
                return true; // Already at the required location
            }
            
            if (walkResult && !watchdogTriggered.get()) {       
                sleepUntil(()-> !Rs2Player.isMoving() , 5000);
                return isAtRequiredLocation() && !watchdogTriggered.get();
            }
            
            return false;
            
        } catch (Exception e) {
            Microbot.log("Error traveling to location " + getName() + ": " + e.getMessage());
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
        sb.append("Schedule Context:\t").append(scheduleContext.name()).append("\n");
        sb.append("Acceptable Distance:\t").append(acceptableDistance).append(" tiles\n");
        sb.append("Use Transports:\t\t").append(useTransports ? "Yes" : "No").append("\n");
        sb.append("World:\t\t\t").append(world != -1 ? world : "Any world").append("\n");
        sb.append("canAccessWorld:\t").append(LocationRequirementUtil.canAccessWorld(world) ? "Yes" : "No").append("\n");
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
