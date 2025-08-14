package net.runelite.client.plugins.microbot.util.world;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldType;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleepGaussian;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Rs2WorldUtil provides advanced static utilities for world selection, accessibility checks,
 * population and ping analysis, and intelligent world hopping for the Microbot framework.
 * <p>
 * Features:
 * <ul>
 *     <li>Determines accessible worlds based on membership, restrictions, and world types</li>
 *     <li>Finds worlds by population, ping, or region, with exclusion and candidate filtering</li>
 *     <li>Supports robust world hopping with retry logic and exponential backoff</li>
 *     
 *     <li>Integrates with Microbot's login and break handler systems for seamless automation</li>
 *     <li>Offers comprehensive world statistics for debugging and analysis</li>
 * </ul>
 * <p>
 * All methods are static and thread-safe for use across plugins and scripts.
 */

@Slf4j
public class Rs2WorldUtil {
    
    /**
     * Checks if the player can access the specified world based on membership status,
     * world type restrictions, and other accessibility factors.
     * 
     * @param worldId The world ID to check accessibility for
     * @return true if the player can access the world, false otherwise
     */
    public static boolean canAccessWorld(int worldId) {
        try {
            if (worldId == -1){
                return false;
            }
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
                    log.info("World hop task cancelled, exiting");
                    return false; // Exit if task was cancelled
                }
              
                
                
                log.info("Attempting world hop {} to world {}", attemptsPerWorld, world);
               ;                
                sleepUntil(() -> Microbot.hopToWorld(world), 6000);
                sleepUntil(() -> !Microbot.isHopping(), 6000);
                if (Microbot.isHopping()) {
                    Microbot.status = "Failed to hop to world " + world + " (attempt " + attemptsPerWorld + ")";
                    log.warn("World hop attempt {} to world {} failed", attemptsPerWorld, world);
                    // Use exponential backoff delay from WorldHoppingConfig
                    long retryDelay = worldHoppingConfig.getHopDelay(attemptsPerWorld+ currentAttempts);
                    log.warn("Retrying in " + retryDelay + "ms with exponential backoff");
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
     * Enhanced world hopping utility that finds the best world and handles retries.
     * This method addresses the issues in the current Login.java implementation by:
     * 1. Properly respecting membership status and current region  
     * 2. Using retry mechanism with different world selection
     * 3. Checking world accessibility before attempting hops
     * 4. Implementing exponential backoff with WorldHoppingConfig
     * 
     * @param scheduledFuture The future to monitor for cancellation
     * @param currentAttempts Current number of hop attempts for backoff calculation
     * @param worldHoppingConfig Configuration for world hopping behavior
     * @param excludeWorlds Set of world IDs to exclude from selection (failed attempts)
     * @return true if world hop was successful, false otherwise
     */
    public static boolean hopToNextBestWorld(CompletableFuture<?> scheduledFuture, 
                                           int currentAttempts, 
                                           WorldHoppingConfig worldHoppingConfig,
                                           java.util.Set<Integer> excludeWorlds) {
        try {
            Microbot.status = "Finding next best world for hopping";
            
            // Check if we can hop at all
            if (!Microbot.isLoggedIn()) {
                log.warn("Cannot hop worlds - player not logged in");
                return false;
            }
            
            if (Microbot.isHopping()) {
                log.warn("Already hopping worlds - waiting for completion");
                sleepUntil(() -> !Microbot.isHopping(), 10000);
                return !Microbot.isHopping(); // Return true if hopping completed
            }
            
            // Get current player context for world filtering
            boolean isPlayerMember = Rs2Player.isMember();
            WorldRegion currentRegion = getCurrentPlayerRegion();
            
            // Find suitable world with improved filtering
            int targetWorld = findBestAccessibleWorld(isPlayerMember, currentRegion, excludeWorlds);
            
            if (targetWorld == -1) {
                log.warn("No suitable world found for hopping");
                return false;
            }
            
            // Verify world accessibility one more time
            if (!canAccessWorld(targetWorld)) {
                log.warn("Selected world {} is not accessible", targetWorld);
                excludeWorlds.add(targetWorld); // Add to exclude list
                return false;
            }
            
            // Wait for world service to be ready
            if (!waitForWorldServiceReady()) {
                log.warn("World service not ready for hopping");
                return false;
            }
            
            log.info("Attempting to hop to selected world: {}", targetWorld);
            
            // Use the enhanced hop method
            return hopWorld(scheduledFuture, targetWorld, currentAttempts, worldHoppingConfig);
            
        } catch (Exception e) {
            log.error("Error in hopToNextBestWorld: {}", e.getMessage());
            Microbot.logStackTrace("LocationRequirementUtil.hopToNextBestWorld", e);
            return false;
        }
    }
    
    /**
     * Finds the best accessible world for the player, excluding problematic worlds.
     * This method improves upon Login.java by:
     * - Properly filtering by membership and region
     * - Excluding previously failed worlds
     * - Preferring lower population worlds for better performance
     * 
     * @param isPlayerMember Whether player has membership
     * @param currentRegion Current player region (can be null for any region)
     * @param excludeWorlds Set of world IDs to exclude
     * @return World ID of best available world, or -1 if none found
     */
    private static int findBestAccessibleWorld(boolean isPlayerMember, 
                                             WorldRegion currentRegion,
                                             java.util.Set<Integer> excludeWorlds) {
        try {
            WorldResult worldResult = Microbot.getWorldService().getWorlds();
            if (worldResult == null) {
                log.warn("World service unavailable for world selection");
                return -1;
            }
            
            boolean isInSeasonalWorld = Microbot.getClient().getWorldType().contains(net.runelite.api.WorldType.SEASONAL);
            int currentWorldId = Rs2Player.getWorld();
            
            // Filter available worlds with enhanced logic
            List<World> suitableWorlds = worldResult.getWorlds().stream()
                    .filter(world -> world.getId() != currentWorldId) // Don't hop to current world
                    .filter(world -> !excludeWorlds.contains(world.getId())) // Exclude failed attempts
                    .filter(world -> isWorldAccessible(world, isPlayerMember, isInSeasonalWorld))
                    .filter(world -> currentRegion == null || world.getRegion() == currentRegion) // Respect region preference
                    .collect(Collectors.toList());
            
            if (suitableWorlds.isEmpty()) {
                log.warn("No suitable worlds found matching criteria");
                return -1;
            }
            
            // Sort by population (prefer medium populated worlds - not too empty, not too full)
            suitableWorlds.sort((w1, w2) -> {
                int pop1 = w1.getPlayers();
                int pop2 = w2.getPlayers();
                
                // Prefer worlds with 200-800 players (good balance)
                int ideal1 = Math.abs(pop1 - 500);
                int ideal2 = Math.abs(pop2 - 500);
                
                return Integer.compare(ideal1, ideal2);
            });
            
            // Return the best world
            World bestWorld = suitableWorlds.get(0);
            log.debug("Selected world {} with {} players in region {}", 
                     bestWorld.getId(), bestWorld.getPlayers(), bestWorld.getRegion());
            
            return bestWorld.getId();
            
        } catch (Exception e) {
            log.error("Error finding best accessible world: {}", e.getMessage());
            return -1;
        }
    }
    
    /**
     * Gets the current player's region based on their location.
     * This helps maintain region consistency when world hopping.
     * 
     * @return Current player region, or null if cannot determine
     */
    private static WorldRegion getCurrentPlayerRegion() {
        try {
            // This is a simplified approach - in a real implementation you might
            // want to determine region based on player location or other factors
            WorldResult worldResult = Microbot.getWorldService().getWorlds();
            if (worldResult == null) return null;
            
            int currentWorld = Rs2Player.getWorld();
            return worldResult.getWorlds().stream()
                    .filter(world -> world.getId() == currentWorld)
                    .map(world -> world.getRegion())
                    .findFirst()
                    .orElse(null);
                    
        } catch (Exception e) {
            log.debug("Could not determine current region: {}", e.getMessage());
            return null; // Allow any region if we can't determine current
        }
    }
    
    /**
     * Waits for the world service to become ready and responsive.
     * This prevents world hopping attempts when the service is unavailable.
     * 
     * @return true if world service is ready, false otherwise
     */
    private static boolean waitForWorldServiceReady() {
        try {
            return sleepUntil(() -> {
                WorldResult result = Microbot.getWorldService().getWorlds();
                return result != null && !result.getWorlds().isEmpty();
            }, 5000);
        } catch (Exception e) {
            log.warn("Error waiting for world service: {}", e.getMessage());
            return false;
        }
    }
    
    
    
    /**
     * Measures the ping to a specific world server by attempting a connection.
     * This provides network latency measurement for intelligent world selection.
     * 
     * @param world The world to measure ping to
     * @return The ping in milliseconds, or -1 if measurement failed
     */
    public static long measureWorldPing(World world) {
        try {
            if (world == null || world.getAddress() == null) {
                return -1;
            }
            
            String address = world.getAddress();
            // Extract hostname from full address if needed
            if (address.contains(":")) {
                address = address.substring(0, address.indexOf(":"));
            }
            
            long startTime = System.currentTimeMillis();
            
            // Attempt a TCP connection to measure latency
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress(address, 43594), 3000); // 3 second timeout
                long endTime = System.currentTimeMillis();
                long ping = endTime - startTime;
                
                log.debug("Measured ping to world {} ({}): {}ms", world.getId(), address, ping);
                return ping;
                
            } catch (java.io.IOException e) {
                log.debug("Failed to measure ping to world {} ({}): {}", world.getId(), address, e.getMessage());
                return -1;
            }
            
        } catch (Exception e) {
            log.warn("Error measuring ping to world {}: {}", world.getId(), e.getMessage());
            return -1;
        }
    }
    
    /**
     * Finds the world with the best (lowest) ping that is still accessible.
     * Uses ping measurement to determine the optimal world for network performance.
     * 
     * @return The world ID with the best ping, or -1 if no suitable world is found
     */
    public static int getBestPingWorld() {
        try {
            WorldResult worldResult = Microbot.getWorldService().getWorlds();
            if (worldResult == null) {
                log.warn("Failed to fetch world list for ping analysis");
                return -1;
            }
            
            boolean isPlayerMember = Rs2Player.isMember();
            boolean isInSeasonalWorld = Microbot.getClient().getWorldType().contains(net.runelite.api.WorldType.SEASONAL);
            
            // Filter to accessible worlds first
            List<World> accessibleWorlds = worldResult.getWorlds().stream()
                    .filter(world -> isWorldAccessible(world, isPlayerMember, isInSeasonalWorld))
                    .collect(Collectors.toList());
            
            if (accessibleWorlds.isEmpty()) {
                log.warn("No accessible worlds found for ping analysis");
                return -1;
            }
            
            // Measure ping for each accessible world and find the best
            World bestPingWorld = null;
            long bestPing = Long.MAX_VALUE;
            
            for (World world : accessibleWorlds) {
                long ping = measureWorldPing(world);
                if (ping > 0 && ping < bestPing) {
                    bestPing = ping;
                    bestPingWorld = world;
                }
            }
            
            if (bestPingWorld == null) {
                log.warn("Could not measure ping to any accessible worlds");
                // Fallback to most populated world
                return getMostPopulatedAccessibleWorld();
            }
            
            log.info("Best ping world: {} with {}ms ping and {} players", 
                    bestPingWorld.getId(), bestPing, bestPingWorld.getPlayers());
            
            return bestPingWorld.getId();
            
        } catch (Exception e) {
            log.error("Error finding best ping world: {}", e.getMessage());
            return -1;
        }
    }
    
    /**
     * Finds the world with the most players while excluding specific worlds.
     * Enhanced version of getMostPopulatedAccessibleWorld() with exclusion support.
     * 
     * @param excludeWorlds List of world IDs to exclude from selection
     * @return The world ID with the most players (excluding specified worlds), or -1 if no suitable world is found
     */
    public static int getMostPopulatedWorldExclude(List<Integer> excludeWorlds) {
        try {
            WorldResult worldResult = Microbot.getWorldService().getWorlds();
            if (worldResult == null) {
                log.warn("Failed to fetch world list for population analysis");
                return -1;
            }
            
            boolean isPlayerMember = Rs2Player.isMember();
            boolean isInSeasonalWorld = Microbot.getClient().getWorldType().contains(net.runelite.api.WorldType.SEASONAL);
            
            // Convert exclude list to set for faster lookups
            java.util.Set<Integer> excludeSet = excludeWorlds != null ? 
                new java.util.HashSet<>(excludeWorlds) : 
                Collections.emptySet();
            
            // Filter to accessible worlds, excluding specified worlds, and sort by player count (highest first)
            List<World> accessibleWorlds = worldResult.getWorlds().stream()
                    .filter(world -> !excludeSet.contains(world.getId()))
                    .filter(world -> isWorldAccessible(world, isPlayerMember, isInSeasonalWorld))
                    .sorted(Comparator.comparingInt(World::getPlayers).reversed())
                    .collect(Collectors.toList());
            
            if (accessibleWorlds.isEmpty()) {
                log.warn("No accessible worlds found for population analysis (excluding {} worlds)", excludeSet.size());
                return -1;
            }
            
            World mostPopulated = accessibleWorlds.get(0);
            log.debug("Most populated accessible world (excluding {} worlds): {} with {} players", 
                    excludeSet.size(), mostPopulated.getId(), mostPopulated.getPlayers());
            
            return mostPopulated.getId();
            
        } catch (Exception e) {
            log.error("Error finding most populated world with exclusions: {}", e.getMessage());
            return -1;
        }
    }
    
    /**
     * Overloaded version of getMostPopulatedWorldExclude for convenience with array input.
     * 
     * @param excludeWorlds Array of world IDs to exclude from selection
     * @return The world ID with the most players (excluding specified worlds), or -1 if no suitable world is found
     */
    public static int getMostPopulatedWorldExclude(int... excludeWorlds) {
        List<Integer> excludeList = java.util.Arrays.stream(excludeWorlds)
                .boxed()
                .collect(Collectors.toList());
        return getMostPopulatedWorldExclude(excludeList);
    }
    
    /**
     * Gets a list of worlds sorted by ping (best to worst) with optional exclusions.
     * Useful for getting multiple world options ordered by network performance.
     * 
     * @param maxWorlds Maximum number of worlds to return (0 for all)
     * @param excludeWorlds List of world IDs to exclude from selection
     * @return List of world IDs sorted by ping (best first), empty list if none found
     */
    public static List<Integer> getAccessibleWorldsByPing(int maxWorlds, List<Integer> excludeWorlds) {
        try {
            WorldResult worldResult = Microbot.getWorldService().getWorlds();
            if (worldResult == null) {
                log.warn("Failed to fetch world list for ping analysis");
                return Collections.emptyList();
            }
            
            boolean isPlayerMember = Rs2Player.isMember();
            boolean isInSeasonalWorld = Microbot.getClient().getWorldType().contains(net.runelite.api.WorldType.SEASONAL);
            
            // Convert exclude list to set for faster lookups
            java.util.Set<Integer> excludeSet = excludeWorlds != null ? 
                new java.util.HashSet<>(excludeWorlds) : 
                Collections.emptySet();
            
            // Filter to accessible worlds and measure ping for each
            List<World> accessibleWorlds = worldResult.getWorlds().stream()
                    .filter(world -> !excludeSet.contains(world.getId()))
                    .filter(world -> isWorldAccessible(world, isPlayerMember, isInSeasonalWorld))
                    .collect(Collectors.toList());
            
            if (accessibleWorlds.isEmpty()) {
                log.warn("No accessible worlds found for ping analysis");
                return Collections.emptyList();
            }
            
            // Measure ping for each world and sort by ping
            List<java.util.Map.Entry<World, Long>> worldPings = new java.util.ArrayList<>();
            
            for (World world : accessibleWorlds) {
                long ping = measureWorldPing(world);
                if (ping > 0) { // Only include worlds with successful ping measurement
                    worldPings.add(new java.util.AbstractMap.SimpleEntry<>(world, ping));
                }
            }
            
            // Sort by ping (lowest first)
            worldPings.sort(java.util.Map.Entry.comparingByValue());
            
            // Limit to requested number of worlds if specified
            if (maxWorlds > 0 && worldPings.size() > maxWorlds) {
                worldPings = worldPings.subList(0, maxWorlds);
            }
            
            return worldPings.stream()
                    .map(entry -> entry.getKey().getId())
                    .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Error getting accessible worlds by ping: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Overloaded version of getAccessibleWorldsByPing without exclusions.
     * 
     * @param maxWorlds Maximum number of worlds to return (0 for all)
     * @return List of world IDs sorted by ping (best first), empty list if none found
     */
    public static List<Integer> getAccessibleWorldsByPing(int maxWorlds) {
        return getAccessibleWorldsByPing(maxWorlds, null);
    }
    
    /**
     * Enhanced version of getMostPopulatedWorldFromList that also considers ping.
     * Finds the world with the best balance of population and ping performance.
     * 
     * @param candidateWorlds Array of world IDs to choose from
     * @param preferPing Whether to prefer lower ping over higher population
     * @return The world ID with the best balance, or -1 if no suitable world is found
     */
    public static int getMostPopulatedWorldFromListWithPing(int[] candidateWorlds, boolean preferPing) {
        try {
            WorldResult worldResult = Microbot.getWorldService().getWorlds();
            if (worldResult == null) {
                log.warn("Failed to fetch world list for analysis");
                return -1;
            }
            
            boolean isPlayerMember = Rs2Player.isMember();
            boolean isInSeasonalWorld = Microbot.getClient().getWorldType().contains(net.runelite.api.WorldType.SEASONAL);
            
            // Convert candidate worlds array to a list for easier filtering
            List<Integer> candidateList = java.util.Arrays.stream(candidateWorlds)
                    .boxed()
                    .collect(Collectors.toList());
            
            // Filter to candidate worlds that are also accessible
            List<World> accessibleCandidateWorlds = worldResult.getWorlds().stream()
                    .filter(world -> candidateList.contains(world.getId()))
                    .filter(world -> isWorldAccessible(world, isPlayerMember, isInSeasonalWorld))
                    .collect(Collectors.toList());
            
            if (accessibleCandidateWorlds.isEmpty()) {
                log.warn("No accessible worlds found from candidate list");
                return -1;
            }
            
            if (preferPing) {
                // Sort by ping first, then by population as tiebreaker
                World bestWorld = accessibleCandidateWorlds.stream()
                        .min((w1, w2) -> {
                            long ping1 = measureWorldPing(w1);
                            long ping2 = measureWorldPing(w2);
                            
                            if (ping1 <= 0 && ping2 <= 0) {
                                // Neither has measurable ping, fallback to population
                                return Integer.compare(w2.getPlayers(), w1.getPlayers());
                            } else if (ping1 <= 0) {
                                return 1; // w2 is better (has measurable ping)
                            } else if (ping2 <= 0) {
                                return -1; // w1 is better (has measurable ping)
                            } else {
                                // Both have measurable ping, prefer lower ping
                                int pingComparison = Long.compare(ping1, ping2);
                                if (pingComparison != 0) {
                                    return pingComparison;
                                }
                                // Ping is equal, prefer higher population as tiebreaker
                                return Integer.compare(w2.getPlayers(), w1.getPlayers());
                            }
                        })
                        .orElse(null);
                
                if (bestWorld != null) {
                    log.debug("Best ping world from candidates: {} with {} players", 
                            bestWorld.getId(), bestWorld.getPlayers());
                    return bestWorld.getId();
                }
            } else {
                // Original behavior - sort by population first
                accessibleCandidateWorlds.sort(Comparator.comparingInt(World::getPlayers).reversed());
                World mostPopulated = accessibleCandidateWorlds.get(0);
                
                log.debug("Most populated world from candidates: {} with {} players", 
                        mostPopulated.getId(), mostPopulated.getPlayers());
                return mostPopulated.getId();
            }
            
            return -1;
            
        } catch (Exception e) {
            log.error("Error finding best world from candidate list with ping consideration: {}", e.getMessage());
            return -1;
        }
    }
    
    /**
     * Backward-compatible overload that maintains original behavior.
     * 
     * @param candidateWorlds Array of world IDs to choose from
     * @return The world ID with the most players from the candidates, or -1 if no suitable world is found
     */
    public static int getMostPopulatedWorldFromListWithPing(int[] candidateWorlds) {
        return getMostPopulatedWorldFromListWithPing(candidateWorlds, false);
    }
    
    /**
     * Gets a random accessible world for login, optimized for break handler and login systems.
     * This method provides intelligent world selection that respects all game restrictions.
     * 
     * @param avoidEmptyWorlds Whether to avoid worlds with population < 50
     * @param avoidOvercrowdedWorlds Whether to avoid worlds with population > 1800
     * @return Random accessible world ID, or -1 if no suitable world found
     */
    public static int getRandomAccessibleWorld(boolean avoidEmptyWorlds, boolean avoidOvercrowdedWorlds) {
        try {
            WorldResult worldResult = Microbot.getWorldService().getWorlds();
            if (worldResult == null) {
                log.warn("Failed to fetch world list for random world selection");
                return -1;
            }
            
            boolean isPlayerMember = Rs2Player.isMember();
            boolean isInSeasonalWorld = Microbot.getClient().getWorldType().contains(net.runelite.api.WorldType.SEASONAL);
            
            // filter to accessible worlds with configurable population filtering
            List<World> accessibleWorlds = worldResult.getWorlds().stream()
                    .filter(world -> isWorldAccessible(world, isPlayerMember, isInSeasonalWorld))
                    .filter(world -> applyPopulationFilters(world, avoidEmptyWorlds, avoidOvercrowdedWorlds))
                    .collect(Collectors.toList());
            
            if (accessibleWorlds.isEmpty()) {
                log.warn("No accessible worlds found for random selection");
                return -1;
            }
            
            // randomly select from accessible worlds
            java.util.Random random = new java.util.Random();
            World selectedWorld = accessibleWorlds.get(random.nextInt(accessibleWorlds.size()));
            
            log.debug("Random accessible world selected: {} with {} players", 
                    selectedWorld.getId(), selectedWorld.getPlayers());
            
            return selectedWorld.getId();
            
        } catch (Exception e) {
            log.error("Error selecting random accessible world: {}", e.getMessage());
            return -1;
        }
    }
    
    /**
     * Gets a random accessible world for login (backward compatibility).
     * Uses default population filtering (avoid empty and overcrowded worlds).
     * 
     * @return Random accessible world ID, or -1 if no suitable world found
     */
    public static int getRandomAccessibleWorld() {
        return getRandomAccessibleWorld(true, true);
    }
    
    /**
     * Gets a random accessible world from a specific region for login.
     * Useful for maintaining regional consistency during login.
     * 
     * @param preferredRegion The preferred world region (null for any region)
     * @param avoidEmptyWorlds Whether to avoid worlds with population < 50
     * @param avoidOvercrowdedWorlds Whether to avoid worlds with population > 1800
     * @return Random accessible world ID from the region, or -1 if no suitable world found
     */
    public static int getRandomAccessibleWorldFromRegion(WorldRegion preferredRegion, 
                                                         boolean avoidEmptyWorlds, boolean avoidOvercrowdedWorlds) {
        try {
            WorldResult worldResult = Microbot.getWorldService().getWorlds();
            if (worldResult == null) {
                log.warn("Failed to fetch world list for regional world selection");
                return -1;
            }
            
            boolean isPlayerMember = Rs2Player.isMember();
            boolean isInSeasonalWorld = Microbot.getClient().getWorldType().contains(net.runelite.api.WorldType.SEASONAL);
            
            // filter to accessible worlds in the specified region with configurable population filtering
            List<World> accessibleWorlds = worldResult.getWorlds().stream()
                    .filter(world -> isWorldAccessible(world, isPlayerMember, isInSeasonalWorld))
                    .filter(world -> preferredRegion == null || world.getRegion() == preferredRegion)
                    .filter(world -> applyPopulationFilters(world, avoidEmptyWorlds, avoidOvercrowdedWorlds))
                    .collect(Collectors.toList());
            
            if (accessibleWorlds.isEmpty()) {
                log.warn("No accessible worlds found in region: {}", preferredRegion);
                // fallback to any accessible world if no regional worlds available
                return getRandomAccessibleWorld(avoidEmptyWorlds, avoidOvercrowdedWorlds);
            }
            
            // randomly select from accessible worlds in the region
            java.util.Random random = new java.util.Random();
            World selectedWorld = accessibleWorlds.get(random.nextInt(accessibleWorlds.size()));
            
            log.debug("Random accessible world selected from region {}: {} with {} players", 
                    preferredRegion, selectedWorld.getId(), selectedWorld.getPlayers());
            
            return selectedWorld.getId();
            
        } catch (Exception e) {
            log.error("Error selecting random accessible world from region: {}", e.getMessage());
            return getRandomAccessibleWorld(avoidEmptyWorlds, avoidOvercrowdedWorlds); // fallback
        }
    }
    
    /**
     * Gets a random accessible world from a specific region for login (backward compatibility).
     * Uses default population filtering (avoid empty and overcrowded worlds).
     * 
     * @param preferredRegion The preferred world region (null for any region)
     * @return Random accessible world ID from the region, or -1 if no suitable world found
     */
    public static int getRandomAccessibleWorldFromRegion(WorldRegion preferredRegion) {
        return getRandomAccessibleWorldFromRegion(preferredRegion, true, true);
    }
    
    /**
     * Gets the best accessible world for login based on multiple criteria.
     * Prioritizes worlds with moderate population, good ping, and regional preference.
     * 
     * @param preferPing Whether to prioritize ping over population
     * @param preferredRegion Preferred world region (null for any region)
     * @param avoidEmptyWorlds Whether to avoid worlds with population < 50
     * @param avoidOvercrowdedWorlds Whether to avoid worlds with population > 1800
     * @return Best accessible world ID, or -1 if no suitable world found
     */
    public static int getBestAccessibleWorldForLogin(boolean preferPing, WorldRegion preferredRegion, 
                                                    boolean avoidEmptyWorlds, boolean avoidOvercrowdedWorlds) {
        try {
            WorldResult worldResult = Microbot.getWorldService().getWorlds();
            if (worldResult == null) {
                log.warn("Failed to fetch world list for best world selection");
                return -1;
            }
            
            boolean isPlayerMember = Rs2Player.isMember();
            boolean isInSeasonalWorld = Microbot.getClient().getWorldType().contains(net.runelite.api.WorldType.SEASONAL);
            
            // filter to accessible worlds with configurable population filtering
            List<World> accessibleWorlds = worldResult.getWorlds().stream()
                    .filter(world -> isWorldAccessible(world, isPlayerMember, isInSeasonalWorld))
                    .filter(world -> applyPopulationFilters(world, avoidEmptyWorlds, avoidOvercrowdedWorlds))
                    .collect(Collectors.toList());
            
            if (accessibleWorlds.isEmpty()) {
                log.warn("No accessible worlds found for best world selection");
                return -1;
            }
            
            // separate regional and non-regional worlds
            List<World> regionalWorlds = accessibleWorlds.stream()
                    .filter(world -> preferredRegion != null && world.getRegion() == preferredRegion)
                    .collect(Collectors.toList());
            
            List<World> candidateWorlds = !regionalWorlds.isEmpty() ? regionalWorlds : accessibleWorlds;
            
            if (preferPing) {
                // find world with best ping
                World bestWorld = candidateWorlds.stream()
                        .min((w1, w2) -> {
                            long ping1 = measureWorldPing(w1);
                            long ping2 = measureWorldPing(w2);
                            
                            if (ping1 <= 0 && ping2 <= 0) {
                                // neither has measurable ping, fallback to population scoring
                                return Integer.compare(getPopulationScore(w1), getPopulationScore(w2));
                            } else if (ping1 <= 0) {
                                return 1; // w2 is better (has measurable ping)
                            } else if (ping2 <= 0) {
                                return -1; // w1 is better (has measurable ping)
                            } else {
                                return Long.compare(ping1, ping2); // prefer lower ping
                            }
                        })
                        .orElse(null);
                
                if (bestWorld != null) {
                    log.debug("Best ping world selected: {} with {} players in region {}", 
                            bestWorld.getId(), bestWorld.getPlayers(), bestWorld.getRegion());
                    return bestWorld.getId();
                }
            } else {
                // find world with best population balance
                World bestWorld = candidateWorlds.stream()
                        .min(Comparator.comparingInt(Rs2WorldUtil::getPopulationScore))
                        .orElse(null);
                
                if (bestWorld != null) {
                    log.debug("Best population world selected: {} with {} players in region {}", 
                            bestWorld.getId(), bestWorld.getPlayers(), bestWorld.getRegion());
                    return bestWorld.getId();
                }
            }
            
            log.warn("No suitable world found with specified criteria");
            return -1;
            
        } catch (Exception e) {
            log.error("Error selecting best accessible world for login: {}", e.getMessage());
            return -1;
        }
    }
    
    /**
     * Gets the best accessible world for login based on multiple criteria (backward compatibility).
     * Uses default population filtering (avoid empty and overcrowded worlds).
     * 
     * @param preferPing Whether to prioritize ping over population
     * @param preferredRegion Preferred world region (null for any region)
     * @return Best accessible world ID, or -1 if no suitable world found
     */
    public static int getBestAccessibleWorldForLogin(boolean preferPing, WorldRegion preferredRegion) {
        return getBestAccessibleWorldForLogin(preferPing, preferredRegion, true, true);
    }
    
    /**
     * Applies population filters to a world based on configuration.
     * 
     * @param world The world to check
     * @param avoidEmptyWorlds Whether to avoid worlds with population < 50
     * @param avoidOvercrowdedWorlds Whether to avoid worlds with population > 1800
     * @return true if the world passes the population filters, false otherwise
     */
    private static boolean applyPopulationFilters(World world, boolean avoidEmptyWorlds, boolean avoidOvercrowdedWorlds) {
        int population = world.getPlayers();
        
        if (avoidEmptyWorlds && population < 50) {
            return false;
        }
        
        if (avoidOvercrowdedWorlds && population > 1800) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Calculates a population score for world selection.
     * Lower scores indicate more desirable worlds.
     * 
     * @param world The world to score
     * @return Population score (lower is better)
     */
    private static int getPopulationScore(World world) {
        int population = world.getPlayers();
        
        // ideal population range: 200-800 players
        int idealMin = 200;
        int idealMax = 800;
        
        if (population >= idealMin && population <= idealMax) {
            // perfect range - prefer worlds closer to middle
            int idealMid = (idealMin + idealMax) / 2;
            return Math.abs(population - idealMid);
        } else if (population < idealMin) {
            // too empty - penalize based on how empty
            return (idealMin - population) * 2; // double penalty for empty worlds
        } else {
            // too full - penalize based on how full
            return (population - idealMax) * 3; // triple penalty for overcrowded worlds
        }
    }
    
    /**
     * Gets comprehensive world statistics for debugging and analysis.
     * 
     * @return Map containing world statistics by category
     */
    public static java.util.Map<String, Object> getWorldStatistics() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        
        try {
            WorldResult worldResult = Microbot.getWorldService().getWorlds();
            if (worldResult == null) {
                stats.put("error", "World service unavailable");
                return stats;
            }
            
            boolean isPlayerMember = Rs2Player.isMember();
            boolean isInSeasonalWorld = Microbot.getClient().getWorldType().contains(net.runelite.api.WorldType.SEASONAL);
            
            List<World> allWorlds = worldResult.getWorlds();
            List<World> accessibleWorlds = allWorlds.stream()
                    .filter(world -> isWorldAccessible(world, isPlayerMember, isInSeasonalWorld))
                    .collect(Collectors.toList());
            
            stats.put("totalWorlds", allWorlds.size());
            stats.put("accessibleWorlds", accessibleWorlds.size());
            stats.put("playerIsMember", isPlayerMember);
            stats.put("playerIsInSeasonalWorld", isInSeasonalWorld);
            
            // population statistics
            if (!accessibleWorlds.isEmpty()) {
                List<Integer> populations = accessibleWorlds.stream()
                        .map(World::getPlayers)
                        .sorted()
                        .collect(Collectors.toList());
                
                stats.put("minPopulation", populations.get(0));
                stats.put("maxPopulation", populations.get(populations.size() - 1));
                stats.put("avgPopulation", populations.stream().mapToInt(Integer::intValue).average().orElse(0.0));
                
                // population distribution
                long emptyWorlds = populations.stream().filter(pop -> pop < 50).count();
                long lowWorlds = populations.stream().filter(pop -> pop >= 50 && pop < 200).count();
                long idealWorlds = populations.stream().filter(pop -> pop >= 200 && pop <= 800).count();
                long highWorlds = populations.stream().filter(pop -> pop > 800 && pop < 1500).count();
                long fullWorlds = populations.stream().filter(pop -> pop >= 1500).count();
                
                stats.put("emptyWorlds", emptyWorlds);
                stats.put("lowPopulationWorlds", lowWorlds);
                stats.put("idealPopulationWorlds", idealWorlds);
                stats.put("highPopulationWorlds", highWorlds);
                stats.put("fullWorlds", fullWorlds);
            }
            
            // regional distribution
            java.util.Map<WorldRegion, Long> regionCounts = accessibleWorlds.stream()
                    .collect(java.util.stream.Collectors.groupingBy(World::getRegion, java.util.stream.Collectors.counting()));
            stats.put("worldsByRegion", regionCounts);
            
        } catch (Exception e) {
            stats.put("error", e.getMessage());
            log.error("Error gathering world statistics: {}", e.getMessage());
        }
        
        return stats;
    }
    
    /**
     * Enhanced login utility that selects an appropriate world for login.
     * Integrates with the Login system to provide intelligent world selection for break handler.
     * 
     * @param useRandomWorld Whether to use random world selection
     * @param preferredRegion Preferred world region (null for current region or any)
     * @param avoidEmptyWorlds Whether to avoid worlds with population < 50
     * @param avoidOvercrowdedWorlds Whether to avoid worlds with population > 1800
     * @return true if login was successful, false otherwise
     */
    public static boolean performLogin(boolean useRandomWorld, WorldRegion preferredRegion, 
                                     boolean avoidEmptyWorlds, boolean avoidOvercrowdedWorlds) {
        try {
            if (Microbot.isLoggedIn()) {
                log.debug("Already logged in, skipping login");
                return true;
            }
            
            int targetWorld = -1;
            
            if (useRandomWorld) {
                if (preferredRegion != null) {
                    targetWorld = getRandomAccessibleWorldFromRegion(preferredRegion, avoidEmptyWorlds, avoidOvercrowdedWorlds);
                } else {
                    targetWorld = getRandomAccessibleWorld(avoidEmptyWorlds, avoidOvercrowdedWorlds);
                }
            } else {
                // use best world selection for optimal experience
                targetWorld = getBestAccessibleWorldForLogin(false, preferredRegion, avoidEmptyWorlds, avoidOvercrowdedWorlds);
            }
            
            if (targetWorld == -1) {
                log.warn("Failed to find suitable world for login");
                // fallback to default login without world specification
                new Login();
                return true; // let default login handle world selection
            }
            
            log.info("Performing intelligent login to world: {}", targetWorld);
            
            // use Login constructor with specific world
            new Login(targetWorld);
            
            // wait for login to complete
            boolean loginSuccess = sleepUntil(() -> Microbot.isLoggedIn(), 10000);
            
            if (loginSuccess) {
                int actualWorld = Rs2Player.getWorld();
                log.info("Login successful - Target world: {}, Actual world: {}", targetWorld, actualWorld);
                return true;
            } else {
                log.warn("Login timeout - may still be in progress");
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error during intelligent login: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Enhanced login utility that selects an appropriate world for login (backward compatibility).
     * Uses default population filtering (avoid empty and overcrowded worlds).
     * 
     * @param useRandomWorld Whether to use random world selection
     * @param preferredRegion Preferred world region (null for current region or any)
     * @return true if login was successful, false otherwise
     */
    public static boolean performLogin(boolean useRandomWorld, WorldRegion preferredRegion) {
        return performLogin(useRandomWorld, preferredRegion, true, true);
    }
    
    /**
     * Validates that the current world is still accessible and appropriate.
     * Useful for checking world status after login or during long sessions.
     * 
     * @return true if current world is still suitable, false otherwise
     */
    public static boolean validateCurrentWorld() {
        try {
            if (!Microbot.isLoggedIn()) {
                return false; // can't validate if not logged in
            }
            
            int currentWorldId = Rs2Player.getWorld();
            boolean isAccessible = canAccessWorld(currentWorldId);
            
            if (!isAccessible) {
                log.warn("Current world {} is no longer accessible", currentWorldId);
                return false;
            }
            
            // check world population
            WorldResult worldResult = Microbot.getWorldService().getWorlds();
            if (worldResult != null) {
                World currentWorld = worldResult.getWorlds().stream()
                        .filter(world -> world.getId() == currentWorldId)
                        .findFirst()
                        .orElse(null);
                
                if (currentWorld != null) {
                    int population = currentWorld.getPlayers();
                    if (population >= 2000) {
                        log.warn("Current world {} is overcrowded with {} players", currentWorldId, population);
                        return false;
                    }
                    
                    log.debug("Current world {} validated - {} players", currentWorldId, population);
                }
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Error validating current world: {}", e.getMessage());
            return false;
        }
    }
}
