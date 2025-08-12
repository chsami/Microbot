package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.ScheduleContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.shop.models.WorldHoppingConfig;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.api.Constants;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleepGaussian;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Utility class for location-related static methods that can be shared across different components.
 * This class contains world accessibility checks, world filtering, and world population analysis.
 */
@Slf4j
public class LocationRequirementUtil {
    
    /**
     * Checks if the player can access the specified world based on membership status,
     * world type restrictions, and other accessibility factors.
     * 
     * @param worldId The world ID to check accessibility for
     * @return true if the player can access the world, false otherwise
     */
    public static boolean canAccessWorld(int worldId) {
        try {
            WorldResult worldResult = Microbot.getWorldService().getWorlds();
            if (worldResult == null) {
                log.warn("Failed to fetch world list for accessibility check");
                return false;
            }
            
            // Find the specific world
            World targetWorld = worldResult.getWorlds().stream()
                    .filter(world -> world.getId() == worldId)
                    .findFirst()
                    .orElse(null);
            
            if (targetWorld == null) {
                log.warn("World {} not found in world list", worldId);
                return false;
            }
            
            boolean isPlayerMember = Rs2Player.isMember();
            boolean isInSeasonalWorld = Microbot.getClient().getWorldType().contains(net.runelite.api.WorldType.SEASONAL);
            
            return isWorldAccessible(targetWorld, isPlayerMember, isInSeasonalWorld);
            
        } catch (Exception e) {
            log.error("Error checking world accessibility for world {}: {}", worldId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Determines if a specific world is accessible based on player membership status,
     * seasonal world status, and world type restrictions.
     * Uses the same filtering logic as Login.getRandomWorld() for consistency.
     * 
     * @param world The world to check
     * @param isPlayerMember Whether the player is a member
     * @param isInSeasonalWorld Whether the player is currently in a seasonal world
     * @return true if the world is accessible, false otherwise
     */
    public static boolean isWorldAccessible(World world, boolean isPlayerMember, boolean isInSeasonalWorld) {
        // Check for restricted world types (same as Login.java filtering)
        if (world.getTypes().contains(WorldType.PVP) ||
            world.getTypes().contains(WorldType.HIGH_RISK) ||
            world.getTypes().contains(WorldType.BOUNTY) ||
            world.getTypes().contains(WorldType.SKILL_TOTAL) ||
            world.getTypes().contains(WorldType.LAST_MAN_STANDING) ||
            world.getTypes().contains(WorldType.QUEST_SPEEDRUNNING) ||
            world.getTypes().contains(WorldType.BETA_WORLD) ||
            world.getTypes().contains(WorldType.DEADMAN) ||
            world.getTypes().contains(WorldType.PVP_ARENA) ||
            world.getTypes().contains(WorldType.TOURNAMENT) ||
            world.getTypes().contains(WorldType.NOSAVE_MODE) ||
            world.getTypes().contains(WorldType.LEGACY_ONLY) ||
            world.getTypes().contains(WorldType.EOC_ONLY) ||
            world.getTypes().contains(WorldType.FRESH_START_WORLD)) {
            return false;
        }
        
        // Check player count limits
        if (world.getPlayers() >= 2000 || world.getPlayers() < 0) {
            return false;
        }
        
        // Check seasonal world compatibility (strict matching as in Login.java)
        if (isInSeasonalWorld != world.getTypes().contains(WorldType.SEASONAL)) {
            return false;
        }
        // Ensure the world is not seasonal if player is in not in a seasonal world
        if (!isInSeasonalWorld == world.getTypes().contains(WorldType.SEASONAL)) {
            return false;
        }
        
        // Check membership requirements
        boolean isWorldMembers = world.getTypes().contains(WorldType.MEMBERS);
        if (isWorldMembers && !isPlayerMember) {
            return false; // Members world but player is not a member
        }
        
        // Non-members can access both members and non-members worlds if they are members
        // F2P players can only access F2P worlds
        return true;
    }
    
    /**
     * Finds the world with the most players that is still accessible and not full.
     * Uses the same filtering logic as isWorldAccessible() for consistency.
     * 
     * @return The world ID with the most players, or -1 if no suitable world is found
     */
    public static int getMostPopulatedAccessibleWorld() {
        try {
            WorldResult worldResult = Microbot.getWorldService().getWorlds();
            if (worldResult == null) {
                log.warn("Failed to fetch world list for population analysis");
                return -1;
            }
            
            boolean isPlayerMember = Rs2Player.isMember();
            boolean isInSeasonalWorld = Microbot.getClient().getWorldType().contains(net.runelite.api.WorldType.SEASONAL);
            
            // Filter to accessible worlds and sort by player count (highest first)
            List<World> accessibleWorlds = worldResult.getWorlds().stream()
                    .filter(world -> isWorldAccessible(world, isPlayerMember, isInSeasonalWorld))
                    .sorted(Comparator.comparingInt(World::getPlayers).reversed())
                    .collect(Collectors.toList());
            
            if (accessibleWorlds.isEmpty()) {
                log.warn("No accessible worlds found for population analysis");
                return -1;
            }
            
            World mostPopulated = accessibleWorlds.get(0);
            log.debug("Most populated accessible world: {} with {} players", 
                    mostPopulated.getId(), mostPopulated.getPlayers());
            
            return mostPopulated.getId();
            
        } catch (Exception e) {
            log.error("Error finding most populated accessible world: {}", e.getMessage());
            return -1;
        }
    }
    
    /**
     * Finds the world with the most players from a specific list of worlds that is still accessible and not full.
     * 
     * @param candidateWorlds Array of world IDs to choose from
     * @return The world ID with the most players from the candidates, or -1 if no suitable world is found
     */
    public static int getMostPopulatedWorldFromList(int[] candidateWorlds) {
        try {
            WorldResult worldResult = Microbot.getWorldService().getWorlds();
            if (worldResult == null) {
                log.warn("Failed to fetch world list for population analysis");
                return -1;
            }
            
            boolean isPlayerMember = Rs2Player.isMember();
            boolean isInSeasonalWorld = Microbot.getClient().getWorldType().contains(net.runelite.api.WorldType.SEASONAL);
            
            // Convert candidate worlds array to a list for easier filtering
            List<Integer> candidateList = java.util.Arrays.stream(candidateWorlds)
                    .boxed()
                    .collect(Collectors.toList());
            
            // Filter to candidate worlds that are also accessible, and sort by player count (highest first)
            List<World> accessibleCandidateWorlds = worldResult.getWorlds().stream()
                    .filter(world -> candidateList.contains(world.getId()))
                    .filter(world -> isWorldAccessible(world, isPlayerMember, isInSeasonalWorld))
                    .sorted(Comparator.comparingInt(World::getPlayers).reversed())
                    .collect(Collectors.toList());
            
            if (accessibleCandidateWorlds.isEmpty()) {
                log.warn("No accessible worlds found from candidate list");
                return -1;
            }
            
            World mostPopulated = accessibleCandidateWorlds.get(0);
            log.debug("Most populated accessible world from candidates: {} with {} players", 
                    mostPopulated.getId(), mostPopulated.getPlayers());
            
            return mostPopulated.getId();
            
        } catch (Exception e) {
            log.error("Error finding most populated world from candidate list: {}", e.getMessage());
            return -1;
        }
    }
    
    /**
     * Gets a list of accessible worlds sorted by player count (highest to lowest).
     * Useful for getting multiple world options or analyzing world populations.
     * 
     * @param maxWorlds Maximum number of worlds to return (0 for all)
     * @return List of world IDs sorted by player count, empty list if none found
     */
    public static List<Integer> getAccessibleWorldsByPopulation(int maxWorlds) {
        try {
            WorldResult worldResult = Microbot.getWorldService().getWorlds();
            if (worldResult == null) {
                log.warn("Failed to fetch world list for population analysis");
                return Collections.emptyList();
            }
            
            boolean isPlayerMember = Rs2Player.isMember();
            boolean isInSeasonalWorld = Microbot.getClient().getWorldType().contains(net.runelite.api.WorldType.SEASONAL);
            
            // Filter to accessible worlds and sort by player count (highest first)
            List<World> accessibleWorlds = worldResult.getWorlds().stream()
                    .filter(world -> isWorldAccessible(world, isPlayerMember, isInSeasonalWorld))
                    .sorted(Comparator.comparingInt(World::getPlayers).reversed())
                    .collect(Collectors.toList());
            
            // Limit to requested number of worlds if specified
            if (maxWorlds > 0 && accessibleWorlds.size() > maxWorlds) {
                accessibleWorlds = accessibleWorlds.subList(0, maxWorlds);
            }
            
            return accessibleWorlds.stream()
                    .map(World::getId)
                    .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Error getting accessible worlds by population: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Generates a default description based on the target locations and context.
     * 
     * @param locations The target locations
     * @param context When the requirement should be fulfilled
     * @return A descriptive string explaining the requirement
     */
    public static String generateDefaultDescription(List<LocationOption> locations, ScheduleContext context) {
        if (locations == null || locations.isEmpty()) {
            return "Unknown Location Requirement";
        }
        
        String contextPrefix = (context == ScheduleContext.PRE_SCHEDULE) ? "Pre-task" : "Post-task";
        
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
     * Enhanced world hopping using WorldHoppingConfig patterns with exponential backoff.
     * Integrates with the sophisticated world hopping system for better reliability.
     * 
     * @param scheduledFuture The future to monitor for cancellation
     * @param world The target world to hop to
     * @param currentAttempts The number of attempts already made (for exponential backoff)
     * @param worldHoppingConfig Configuration for world hopping behavior
     * @return true if world hop was successful, false otherwise
     */
    public static boolean hopWorld(CompletableFuture<?> scheduledFuture, int world, Integer currentAttempts, WorldHoppingConfig worldHoppingConfig) {
        try {
            Microbot.status = "Stock level inadequate - hopping worlds with smart retry logic";
            Rs2Shop.closeShop();
            Rs2Bank.closeBank();
            sleepUntil(()->!Rs2Player.isInteracting(), 3000); // Ensure we are not interacting with anything
            int attemptsPerWorld = 0;
            int maxAttemptsPerWorld = worldHoppingConfig.getMaxAttemptsPerWorld();
            // Get next world using configured strategy            
            
            while (attemptsPerWorld < maxAttemptsPerWorld) {
                attemptsPerWorld++;
                if (scheduledFuture != null && scheduledFuture.isCancelled()) {
                    Microbot.status = "Task cancelled, stopping world hopping";
                    log.info("World hop task cancelled, exiting");
                    return false; // Exit if task was cancelled
                }
              
                
                Microbot.status = "Attempting world hop " + attemptsPerWorld + "/" + maxAttemptsPerWorld + " to world " + world;
                log.info("Attempting world hop {} to world {}", attemptsPerWorld, world);
                Microbot.hopToWorld(world);
                sleepUntil(() -> Microbot.hopToWorld(world), 5000);
                sleepUntil(() -> !Microbot.isHopping(), 6000);
                if (Microbot.isHopping()) {
                    Microbot.status = "Failed to hop to world " + world + " (attempt " + attemptsPerWorld + ")";
                    log.warn("World hop attempt {} to world {} failed", attemptsPerWorld, world);
                    // Use exponential backoff delay from WorldHoppingConfig
                    long retryDelay = worldHoppingConfig.getHopDelay(attemptsPerWorld+ currentAttempts);
                    Microbot.status = "Retrying in " + retryDelay + "ms with exponential backoff";
                    sleepGaussian((int) retryDelay, (int) (retryDelay*( 0.1))); // 10% variance
                    continue;
                }                
                // Wait for hop to complete with proper state checking                                
                sleepUntil(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN, 6000);                                
                if ( Rs2Player.getWorld() != world){                    
                    Microbot.status = "World hop to " + world + " failed (current world: " + Rs2Player.getWorld() + ")";
                    log.warn("World hop to {} failed, current world is {}", world, Rs2Player.getWorld());
                    long retryDelay = worldHoppingConfig.getHopDelay(attemptsPerWorld+currentAttempts);
                    sleepGaussian((int) retryDelay, (int) (retryDelay * 0.1));
                    continue;
                }
               
                
                Microbot.status = "Successfully hopped to world " + world + " (attempt " + attemptsPerWorld + ")";
                log.info("Successfully hopped to world {} after {} attempts", world, attemptsPerWorld);
                // Additional wait for world to stabilize (base delay)
                sleepGaussian(worldHoppingConfig.getBaseHopDelay(), worldHoppingConfig.getBaseHopDelay() / 3);
                
                return true;
            }
            
            Microbot.status = "Failed to hop worlds after " + attemptsPerWorld  + " attempts";
            return false;
            
        } catch (Exception e) {
            Microbot.logStackTrace(".hopWorld", e);
            return false;
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
                log.info("Current position: {}, Last position: {}", currentPosition, lastPos);
                // Check if player has moved significantly (using area detection for robustness)
                boolean hasMovedSignificantly = hasMovedOutOfArea(lastPos, currentPosition, 2);
                
                if (hasMovedSignificantly || Rs2Bank.isOpen() || Rs2Shop.isOpen()) {
                    // Player has moved, update last movement time and position
                    lastPosition.set(currentPosition);
                    lastMovementTime.set(System.currentTimeMillis());
                } else {
                    // Player hasn't moved significantly, check timeout
                    long timeSinceLastMovement = System.currentTimeMillis() - lastMovementTime.get();
                    log.info ("Time since last movement: {} ms", timeSinceLastMovement);
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
