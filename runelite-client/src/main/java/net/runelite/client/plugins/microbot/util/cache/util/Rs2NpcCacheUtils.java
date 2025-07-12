package net.runelite.client.plugins.microbot.util.cache.util;

import net.runelite.api.Client;
import net.runelite.api.NPCComposition;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.cache.Rs2NpcCache;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Cache-based utility class for NPC operations.
 * Provides comprehensive utilities for finding and filtering NPCs using the cache system.
 * This is a cache-based alternative to Rs2Npc for persistent NPC tracking.
 */
public class Rs2NpcCacheUtils {

    // ============================================
    // Primary Player-Based Utility Methods
    // ============================================

    /**
     * Gets all NPCs within a specified radius from the player's current location.
     * This is the method called by toggleNearestNpcTracking() and similar functions.
     * 
     * @param radius Maximum distance in tiles from player
     * @return Stream of NPCs within the specified radius from player
     */
    public static Stream<Rs2NpcModel> getNearBy(int radius) {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return Stream.empty();
        }
        return findWithinDistance(npc -> true, playerLocation, radius);
    }

    /**
     * Gets all NPCs within a specified radius from the player's current location,
     * sorted by distance from closest to furthest.
     * 
     * @param radius Maximum distance in tiles from player
     * @return Stream of NPCs within the specified radius, sorted by distance
     */
    public static Stream<Rs2NpcModel> getNearBySorted(int radius) {
        return getNearBy(radius)
                .sorted(Comparator.comparingInt(Rs2NpcModel::getDistanceFromPlayer));
    }

    /**
     * Gets the nearest NPC to the player within the specified radius.
     * 
     * @param radius Maximum distance in tiles from player
     * @return Optional containing the nearest NPC, or empty if none found
     */
    public static Optional<Rs2NpcModel> getNearestWithinRadius(int radius) {
        return getNearBy(radius)
                .min(Comparator.comparingInt(Rs2NpcModel::getDistanceFromPlayer));
    }

    // ============================================
    // Action-Based Utility Methods (Rs2Npc compatibility)
    // ============================================

    /**
     * Creates a predicate that matches NPCs with a specific action.
     * 
     * @param action The action to check for (e.g., "Talk-to", "Bank", "Trade")
     * @return Predicate for action matching
     */
    public static Predicate<Rs2NpcModel> hasAction(String action) {
        return npc -> {
            try {
                NPCComposition baseComposition = npc.getComposition();
                NPCComposition transformedComposition = npc.getTransformedComposition();
                
                List<String> baseActions = baseComposition != null ? 
                    Arrays.asList(baseComposition.getActions()) : Collections.emptyList();
                List<String> transformedActions = transformedComposition != null ? 
                    Arrays.asList(transformedComposition.getActions()) : Collections.emptyList();
                
                return baseActions.contains(action) || transformedActions.contains(action);
            } catch (Exception e) {
                return false;
            }
        };
    }

    /**
     * Finds NPCs with a specific action.
     * 
     * @param action The action to search for
     * @return Stream of NPCs with the specified action
     */
    public static Stream<Rs2NpcModel> findWithAction(String action) {
        return findAll(hasAction(action));
    }

    /**
     * Finds the nearest NPC with a specific action.
     * Equivalent to Rs2Npc.getNearestNpcWithAction().
     * 
     * @param action The action to search for
     * @return Optional containing the nearest NPC with the action
     */
    public static Optional<Rs2NpcModel> findNearestWithAction(String action) {
        return findClosest(hasAction(action));
    }

    /**
     * Finds NPCs with a specific action within distance from player.
     * 
     * @param action The action to search for
     * @param distance Maximum distance in tiles
     * @return Stream of NPCs with the action within distance
     */
    public static Stream<Rs2NpcModel> findWithActionWithinDistance(String action, int distance) {
        return findAll(npc -> hasAction(action).test(npc) && npc.isWithinDistanceFromPlayer(distance));
    }

    /**
     * Finds the nearest NPC with a specific action within distance from player.
     * 
     * @param action The action to search for
     * @param distance Maximum distance in tiles
     * @return Optional containing the nearest NPC with the action within distance
     */
    public static Optional<Rs2NpcModel> findNearestWithActionWithinDistance(String action, int distance) {
        return findClosest(npc -> hasAction(action).test(npc) && npc.isWithinDistanceFromPlayer(distance));
    }

    /**
     * Gets the first available action from a list of possible actions for an NPC.
     * Equivalent to Rs2Npc.getAvailableAction().
     * 
     * @param npc The NPC to check
     * @param possibleActions List of actions to check for
     * @return The first available action, or null if none found
     */
    public static String getAvailableAction(Rs2NpcModel npc, List<String> possibleActions) {
        if (npc == null || possibleActions == null) return null;
        
        try {
            NPCComposition baseComposition = npc.getComposition();
            NPCComposition transformedComposition = npc.getTransformedComposition();
            
            List<String> baseActions = baseComposition != null ? 
                Arrays.asList(baseComposition.getActions()) : Collections.emptyList();
            List<String> transformedActions = transformedComposition != null ? 
                Arrays.asList(transformedComposition.getActions()) : Collections.emptyList();
            
            for (String action : possibleActions) {
                if (baseActions.contains(action) || transformedActions.contains(action)) {
                    return action;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    // ============================================
    // Specialized NPC Type Utilities
    // ============================================

    /**
     * Finds pet NPCs (NPCs with "Dismiss" action that are interacting with player).
     * Equivalent to Rs2Npc pet detection logic.
     * 
     * @return Stream of pet NPCs
     */
    public static Stream<Rs2NpcModel> findPets() {
        return findAll(npc -> {
            try {
                NPCComposition npcComposition = npc.getComposition();
                if (npcComposition == null) return false;
                
                List<String> npcActions = Arrays.asList(npcComposition.getActions());
                if (npcActions.isEmpty()) return false;
                
                return npcActions.contains("Dismiss") && 
                       Objects.equals(npc.getInteracting(), Microbot.getClient().getLocalPlayer());
            } catch (Exception e) {
                return false;
            }
        });
    }

    /**
     * Finds the closest pet NPC.
     * 
     * @return Optional containing the closest pet NPC
     */
    public static Optional<Rs2NpcModel> findClosestPet() {
        return findClosest(npc -> {
            try {
                NPCComposition npcComposition = npc.getComposition();
                if (npcComposition == null) return false;
                
                List<String> npcActions = Arrays.asList(npcComposition.getActions());
                if (npcActions.isEmpty()) return false;
                
                return npcActions.contains("Dismiss") && 
                       Objects.equals(npc.getInteracting(), Microbot.getClient().getLocalPlayer());
            } catch (Exception e) {
                return false;
            }
        });
    }

    /**
     * Finds bank NPCs (NPCs with "Bank" action).
     * Equivalent to Rs2Npc bank detection logic.
     * 
     * @return Stream of bank NPCs
     */
    public static Stream<Rs2NpcModel> findBankNpcs() {
        return findWithAction("Bank");
    }

    /**
     * Finds the closest bank NPC.
     * 
     * @return Optional containing the closest bank NPC
     */
    public static Optional<Rs2NpcModel> findClosestBankNpc() {
        return findNearestWithAction("Bank");
    }

    /**
     * Finds shop NPCs (NPCs with "Trade" action).
     * 
     * @return Stream of shop NPCs
     */
    public static Stream<Rs2NpcModel> findShopNpcs() {
        return findWithAction("Trade");
    }

    /**
     * Finds the closest shop NPC.
     * 
     * @return Optional containing the closest shop NPC
     */
    public static Optional<Rs2NpcModel> findClosestShopNpc() {
        return findNearestWithAction("Trade");
    }

    // ============================================
    // Cache-Based NPC Retrieval Methods
    // ============================================

    /**
     * Gets an NPC by its index.
     * 
     * @param index The NPC index
     * @return Optional containing the NPC model if found
     */
    public static Optional<Rs2NpcModel> getByIndex(int index) {
        try {
            return Rs2NpcCache.getNpcByIndex(index);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Gets NPCs by their game ID.
     * 
     * @param npcId The NPC ID
     * @return Stream of matching NPCs
     */
    public static Stream<Rs2NpcModel> getById(int npcId) {
        try {
            return Rs2NpcCache.getNpcsById(npcId);
        } catch (Exception e) {
            return Stream.empty();
        }
    }

    /**
     * Gets the first NPC matching the criteria.
     * 
     * @param npcId The NPC ID
     * @return Optional containing the first matching NPC
     */
    public static Optional<Rs2NpcModel> getFirst(int npcId) {
        try {
            return Rs2NpcCache.getFirstNpcById(npcId);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Gets all cached NPCs.
     * 
     * @return Stream of all NPCs
     */
    public static Stream<Rs2NpcModel> getAll() {
        try {
            return Rs2NpcCache.getAllNpcs();
        } catch (Exception e) {
            return Stream.empty();
        }
    }

    // Advanced cache-based finding utilities

    /**
     * Finds the first NPC matching a predicate.
     * 
     * @param predicate The predicate to match
     * @return Optional containing the first matching NPC
     */
    public static Optional<Rs2NpcModel> find(Predicate<Rs2NpcModel> predicate) {
        try {
            return Rs2NpcCache.getAllNpcs()
                .filter(predicate)
                .findFirst();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Finds all NPCs matching a predicate.
     * 
     * @param predicate The predicate to match
     * @return Stream of matching NPCs
     */
    public static Stream<Rs2NpcModel> findAll(Predicate<Rs2NpcModel> predicate) {
        try {
            return Rs2NpcCache.getAllNpcs().filter(predicate);
        } catch (Exception e) {
            return Stream.empty();
        }
    }

    /**
     * Finds the closest NPC matching a predicate.
     * 
     * @param predicate The predicate to match
     * @return Optional containing the closest matching NPC
     */
    public static Optional<Rs2NpcModel> findClosest(Predicate<Rs2NpcModel> predicate) {
        try {
            return Rs2NpcCache.getAllNpcs()
                .filter(predicate)
                .min(Comparator.comparingInt(Rs2NpcModel::getDistanceFromPlayer));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Finds the first NPC within distance from player by ID.
     * 
     * @param npcId The NPC ID
     * @param distance Maximum distance in tiles
     * @return Optional containing the first matching NPC within distance
     */
    public static Optional<Rs2NpcModel> findWithinDistance(int npcId, int distance) {
        return find(npc -> npc.getId() == npcId && npc.isWithinDistanceFromPlayer(distance));
    }

    /**
     * Finds all NPCs within distance from player by ID.
     * 
     * @param npcId The NPC ID
     * @param distance Maximum distance in tiles
     * @return Stream of matching NPCs within distance
     */
    public static Stream<Rs2NpcModel> findAllWithinDistance(int npcId, int distance) {
        return findAll(npc -> npc.getId() == npcId && npc.isWithinDistanceFromPlayer(distance));
    }

    /**
     * Finds the closest NPC by ID within distance from player.
     * 
     * @param npcId The NPC ID
     * @param distance Maximum distance in tiles
     * @return Optional containing the closest matching NPC within distance
     */
    public static Optional<Rs2NpcModel> findClosestWithinDistance(int npcId, int distance) {
        return findClosest(npc -> npc.getId() == npcId && npc.isWithinDistanceFromPlayer(distance));
    }

    /**
     * Finds NPCs within distance from an anchor point.
     * 
     * @param predicate The predicate to match
     * @param anchor The anchor point
     * @param distance Maximum distance in tiles
     * @return Stream of matching NPCs within distance from anchor
     */
    public static Stream<Rs2NpcModel> findWithinDistance(Predicate<Rs2NpcModel> predicate, WorldPoint anchor, int distance) {
        return findAll(npc -> predicate.test(npc) && npc.getWorldLocation().distanceTo(anchor) <= distance);
    }

    /**
     * Finds the closest NPC to an anchor point.
     * 
     * @param predicate The predicate to match
     * @param anchor The anchor point
     * @return Optional containing the closest matching NPC to anchor
     */
    public static Optional<Rs2NpcModel> findClosest(Predicate<Rs2NpcModel> predicate, WorldPoint anchor) {
        return findAll(predicate)
            .min(Comparator.comparingInt(npc -> npc.getWorldLocation().distanceTo(anchor)));
    }

    /**
     * Finds the closest NPC to an anchor point within distance.
     * 
     * @param predicate The predicate to match
     * @param anchor The anchor point
     * @param distance Maximum distance in tiles
     * @return Optional containing the closest matching NPC to anchor within distance
     */
    public static Optional<Rs2NpcModel> findClosest(Predicate<Rs2NpcModel> predicate, WorldPoint anchor, int distance) {
        return findWithinDistance(predicate, anchor, distance)
            .min(Comparator.comparingInt(npc -> npc.getWorldLocation().distanceTo(anchor)));
    }

    // Name-based finding utilities

    /**
     * Creates a predicate that matches NPCs whose name contains the given string (case-insensitive).
     * 
     * @param npcName The name to match (partial or full)
     * @param exact Whether to match exactly or contain
     * @return Predicate for name matching
     */
    public static Predicate<Rs2NpcModel> nameMatches(String npcName, boolean exact) {
        String lower = npcName.toLowerCase();
        return npc -> {
            String name = npc.getName();
            if (name == null) return false;
            return exact ? name.equalsIgnoreCase(npcName) : name.toLowerCase().contains(lower);
        };
    }

    /**
     * Creates a predicate that matches NPCs whose name contains the given string (case-insensitive).
     * 
     * @param npcName The name to match (partial)
     * @return Predicate for name matching
     */
    public static Predicate<Rs2NpcModel> nameMatches(String npcName) {
        return nameMatches(npcName, false);
    }

    /**
     * Finds the first NPC by name.
     * 
     * @param npcName The NPC name
     * @param exact Whether to match exactly or contain
     * @return Optional containing the first matching NPC
     */
    public static Optional<Rs2NpcModel> findByName(String npcName, boolean exact) {
        return find(nameMatches(npcName, exact));
    }

    /**
     * Finds the first NPC by name (partial match).
     * 
     * @param npcName The NPC name
     * @return Optional containing the first matching NPC
     */
    public static Optional<Rs2NpcModel> findByName(String npcName) {
        return findByName(npcName, false);
    }

    /**
     * Finds the closest NPC by name.
     * 
     * @param npcName The NPC name
     * @param exact Whether to match exactly or contain
     * @return Optional containing the closest matching NPC
     */
    public static Optional<Rs2NpcModel> findClosestByName(String npcName, boolean exact) {
        return findClosest(nameMatches(npcName, exact));
    }

    /**
     * Finds the closest NPC by name (partial match).
     * 
     * @param npcName The NPC name
     * @return Optional containing the closest matching NPC
     */
    public static Optional<Rs2NpcModel> findClosestByName(String npcName) {
        return findClosestByName(npcName, false);
    }

    /**
     * Finds NPCs by name within distance from player.
     * 
     * @param npcName The NPC name
     * @param exact Whether to match exactly or contain
     * @param distance Maximum distance in tiles
     * @return Stream of matching NPCs within distance
     */
    public static Stream<Rs2NpcModel> findByNameWithinDistance(String npcName, boolean exact, int distance) {
        return findAll(npc -> nameMatches(npcName, exact).test(npc) && npc.isWithinDistanceFromPlayer(distance));
    }

    /**
     * Finds NPCs by name within distance from player (partial match).
     * 
     * @param npcName The NPC name
     * @param distance Maximum distance in tiles
     * @return Stream of matching NPCs within distance
     */
    public static Stream<Rs2NpcModel> findByNameWithinDistance(String npcName, int distance) {
        return findByNameWithinDistance(npcName, false, distance);
    }

    /**
     * Finds the closest NPC by name within distance from player.
     * 
     * @param npcName The NPC name
     * @param exact Whether to match exactly or contain
     * @param distance Maximum distance in tiles
     * @return Optional containing the closest matching NPC within distance
     */
    public static Optional<Rs2NpcModel> findClosestByNameWithinDistance(String npcName, boolean exact, int distance) {
        return findClosest(npc -> nameMatches(npcName, exact).test(npc) && npc.isWithinDistanceFromPlayer(distance));
    }

    /**
     * Finds the closest NPC by name within distance from player (partial match).
     * 
     * @param npcName The NPC name
     * @param distance Maximum distance in tiles
     * @return Optional containing the closest matching NPC within distance
     */
    public static Optional<Rs2NpcModel> findClosestByNameWithinDistance(String npcName, int distance) {
        return findClosestByNameWithinDistance(npcName, false, distance);
    }

    // Array-based ID utilities

    /**
     * Finds the first NPC matching any of the given IDs.
     * 
     * @param npcIds Array of NPC IDs
     * @return Optional containing the first matching NPC
     */
    public static Optional<Rs2NpcModel> findByIds(Integer[] npcIds) {
        Set<Integer> idSet = Set.of(npcIds);
        return find(npc -> idSet.contains(npc.getId()));
    }

    /**
     * Finds the closest NPC matching any of the given IDs.
     * 
     * @param npcIds Array of NPC IDs
     * @return Optional containing the closest matching NPC
     */
    public static Optional<Rs2NpcModel> findClosestByIds(Integer[] npcIds) {
        Set<Integer> idSet = Set.of(npcIds);
        return findClosest(npc -> idSet.contains(npc.getId()));
    }

    /**
     * Finds NPCs matching any of the given IDs within distance.
     * 
     * @param npcIds Array of NPC IDs
     * @param distance Maximum distance in tiles
     * @return Stream of matching NPCs within distance
     */
    public static Stream<Rs2NpcModel> findByIdsWithinDistance(Integer[] npcIds, int distance) {
        Set<Integer> idSet = Set.of(npcIds);
        return findAll(npc -> idSet.contains(npc.getId()) && npc.isWithinDistanceFromPlayer(distance));
    }

    /**
     * Finds the closest NPC matching any of the given IDs within distance.
     * 
     * @param npcIds Array of NPC IDs
     * @param distance Maximum distance in tiles
     * @return Optional containing the closest matching NPC within distance
     */
    public static Optional<Rs2NpcModel> findClosestByIdsWithinDistance(Integer[] npcIds, int distance) {
        Set<Integer> idSet = Set.of(npcIds);
        return findClosest(npc -> idSet.contains(npc.getId()) && npc.isWithinDistanceFromPlayer(distance));
    }

    // Combat-specific utilities

    /**
     * Finds attackable NPCs (combat level > 0, not dead).
     * 
     * @return Stream of attackable NPCs
     */
    public static Stream<Rs2NpcModel> findAttackable() {
        return findAll(npc -> npc.getCombatLevel() > 0 && !npc.isDead());
    }

    /**
     * Finds the closest attackable NPC.
     * 
     * @return Optional containing the closest attackable NPC
     */
    public static Optional<Rs2NpcModel> findClosestAttackable() {
        return findClosest(npc -> npc.getCombatLevel() > 0 && !npc.isDead());
    }

    /**
     * Finds attackable NPCs by name.
     * 
     * @param npcName The NPC name
     * @param exact Whether to match exactly or contain
     * @return Stream of attackable NPCs matching the name
     */
    public static Stream<Rs2NpcModel> findAttackableByName(String npcName, boolean exact) {
        return findAll(npc -> 
            npc.getCombatLevel() > 0 && 
            !npc.isDead() && 
            nameMatches(npcName, exact).test(npc));
    }

    /**
     * Finds attackable NPCs by name (partial match).
     * 
     * @param npcName The NPC name
     * @return Stream of attackable NPCs matching the name
     */
    public static Stream<Rs2NpcModel> findAttackableByName(String npcName) {
        return findAttackableByName(npcName, false);
    }

    /**
     * Finds the closest attackable NPC by name.
     * 
     * @param npcName The NPC name
     * @param exact Whether to match exactly or contain
     * @return Optional containing the closest attackable NPC matching the name
     */
    public static Optional<Rs2NpcModel> findClosestAttackableByName(String npcName, boolean exact) {
        return findClosest(npc -> 
            npc.getCombatLevel() > 0 && 
            !npc.isDead() && 
            nameMatches(npcName, exact).test(npc));
    }

    /**
     * Finds the closest attackable NPC by name (partial match).
     * 
     * @param npcName The NPC name
     * @return Optional containing the closest attackable NPC matching the name
     */
    public static Optional<Rs2NpcModel> findClosestAttackableByName(String npcName) {
        return findClosestAttackableByName(npcName, false);
    }

    /**
     * Finds attackable NPCs within distance.
     * 
     * @param distance Maximum distance in tiles
     * @return Stream of attackable NPCs within distance
     */
    public static Stream<Rs2NpcModel> findAttackableWithinDistance(int distance) {
        return findAll(npc -> 
            npc.getCombatLevel() > 0 && 
            !npc.isDead() && 
            npc.isWithinDistanceFromPlayer(distance));
    }

    /**
     * Finds the closest attackable NPC within distance.
     * 
     * @param distance Maximum distance in tiles
     * @return Optional containing the closest attackable NPC within distance
     */
    public static Optional<Rs2NpcModel> findClosestAttackableWithinDistance(int distance) {
        return findClosest(npc -> 
            npc.getCombatLevel() > 0 && 
            !npc.isDead() && 
            npc.isWithinDistanceFromPlayer(distance));
    }

    // Player interaction utilities

    /**
     * Finds NPCs that are interacting with the player.
     * 
     * @return Stream of NPCs interacting with the player
     */
    public static Stream<Rs2NpcModel> findInteractingWithPlayer() {
        return findAll(npc -> npc.isInteractingWithPlayer());
    }

    /**
     * Finds the closest NPC that is interacting with the player.
     * 
     * @return Optional containing the closest NPC interacting with the player
     */
    public static Optional<Rs2NpcModel> findClosestInteractingWithPlayer() {
        return findClosest(npc -> npc.isInteractingWithPlayer());
    }

    /**
     * Finds NPCs that are not interacting with anyone.
     * 
     * @return Stream of NPCs not interacting
     */
    public static Stream<Rs2NpcModel> findNotInteracting() {
        return findAll(npc -> !npc.isInteracting());
    }

    /**
     * Finds the closest NPC that is not interacting with anyone.
     * 
     * @return Optional containing the closest non-interacting NPC
     */
    public static Optional<Rs2NpcModel> findClosestNotInteracting() {
        return findClosest(npc -> !npc.isInteracting());
    }

    // Health and status utilities

    /**
     * Finds NPCs with health below a certain percentage.
     * 
     * @param healthPercentage Maximum health percentage (0-100)
     * @return Stream of NPCs with health below the threshold
     */
    public static Stream<Rs2NpcModel> findWithHealthBelow(double healthPercentage) {
        return findAll(npc -> npc.getHealthPercentage() < healthPercentage);
    }

    /**
     * Finds the closest NPC with health below a certain percentage.
     * 
     * @param healthPercentage Maximum health percentage (0-100)
     * @return Optional containing the closest NPC with health below the threshold
     */
    public static Optional<Rs2NpcModel> findClosestWithHealthBelow(double healthPercentage) {
        return findClosest(npc -> npc.getHealthPercentage() < healthPercentage);
    }

    /**
     * Finds NPCs that are moving.
     * 
     * @return Stream of moving NPCs
     */
    public static Stream<Rs2NpcModel> findMoving() {
        return findAll(npc -> npc.isMoving());
    }

    /**
     * Finds NPCs that are not moving (idle).
     * 
     * @return Stream of idle NPCs
     */
    public static Stream<Rs2NpcModel> findIdle() {
        return findAll(npc -> !npc.isMoving());
    }

    /**
     * Finds the closest moving NPC.
     * 
     * @return Optional containing the closest moving NPC
     */
    public static Optional<Rs2NpcModel> findClosestMoving() {
        return findClosest(npc -> npc.isMoving());
    }

    /**
     * Finds the closest idle NPC.
     * 
     * @return Optional containing the closest idle NPC
     */
    public static Optional<Rs2NpcModel> findClosestIdle() {
        return findClosest(npc -> !npc.isMoving());
    }

    // ============================================
    // Scene and Viewport Extraction Methods  
    // ============================================

    /**
     * Gets all NPCs currently in the scene (all cached NPCs).
     * This includes NPCs that may not be visible in the current viewport.
     * 
     * @return Stream of all NPCs in the scene
     */
    public static Stream<Rs2NpcModel> getAllInScene() {
        return getAll();
    }

    /**
     * Gets all NPCs currently visible in the viewport (on screen).
     * Only includes NPCs that have a convex hull and are rendered.
     * 
     * @return Stream of NPCs visible in viewport
     */
    public static Stream<Rs2NpcModel> getAllInViewport() {
        return filterVisibleInViewport(getAll());
    }

    /**
     * Gets all NPCs by ID that are currently visible in the viewport.
     * 
     * @param npcId The NPC ID to filter by
     * @return Stream of NPCs with the specified ID that are visible in viewport
     */
    public static Stream<Rs2NpcModel> getAllInViewport(int npcId) {
        return filterVisibleInViewport(getById(npcId));
    }

    /**
     * Gets the closest NPC in the viewport by ID.
     * 
     * @param npcId The NPC ID
     * @return Optional containing the closest NPC in viewport
     */
    public static Optional<Rs2NpcModel> getClosestInViewport(int npcId) {
        return getAllInViewport(npcId)
                .min(Comparator.comparingInt(Rs2NpcModel::getDistanceFromPlayer));
    }

    /**
     * Gets all NPCs in the viewport that are interactable (within reasonable distance).
     * 
     * @param maxDistance Maximum distance for interaction
     * @return Stream of interactable NPCs in viewport
     */
    public static Stream<Rs2NpcModel> getAllInteractable(int maxDistance) {
        return getAllInViewport()
                .filter(npc -> isInteractable(npc, maxDistance));
    }

    /**
     * Gets all NPCs by ID in the viewport that are interactable.
     * 
     * @param npcId The NPC ID
     * @param maxDistance Maximum distance for interaction
     * @return Stream of interactable NPCs with the specified ID
     */
    public static Stream<Rs2NpcModel> getAllInteractable(int npcId, int maxDistance) {
        return getAllInViewport(npcId)
                .filter(npc -> isInteractable(npc, maxDistance));
    }

    /**
     * Gets the closest interactable NPC by ID.
     * 
     * @param npcId The NPC ID
     * @param maxDistance Maximum distance for interaction
     * @return Optional containing the closest interactable NPC
     */
    public static Optional<Rs2NpcModel> getClosestInteractable(int npcId, int maxDistance) {
        return getAllInteractable(npcId, maxDistance)
                .min(Comparator.comparingInt(Rs2NpcModel::getDistanceFromPlayer));
    }

    // ============================================
    // Line of Sight Methods
    // ============================================

   
    
    /**
     * Finds NPCs that are in line of sight from a specific point.
     * This is useful for finding NPCs that the player can see.
     * 
     * @param from Starting world point
     * @param maxDistance Maximum distance to search (in tiles)
     * @return Stream of NPCs that are in line of sight
     */
    public static Stream<Rs2NpcModel> getNpcsInLineOfSight(WorldPoint from, int maxDistance) {
        if (from == null) {
            return Stream.empty();
        }
        
        int plane = from.getPlane();
        
        return getAll()
            .filter(npc -> {
                WorldPoint npcLocation = npc.getWorldLocation();
                if (npcLocation == null || npcLocation.getPlane() != plane) {
                    return false;
                }
                
                // Check distance first as it's a cheaper operation
                int distance = from.distanceTo(npcLocation);
                if (distance > maxDistance) {
                    return false;
                }
                
                // Then check line of sight
                return hasLineOfSight(from, npc);
            });
    }
    
    /**
     * Finds the nearest NPC in line of sight from a specific point.
     * 
     * @param from Starting world point
     * @param maxDistance Maximum distance to search (in tiles)
     * @return Optional containing the nearest NPC in line of sight
     */
    public static Optional<Rs2NpcModel> getNearestNpcInLineOfSight(WorldPoint from, int maxDistance) {
        return getNpcsInLineOfSight(from, maxDistance)
            .min(Comparator.comparingInt(npc -> from.distanceTo(npc.getWorldLocation())));
    }
    
    /**
     * Finds NPCs that match a predicate and are in line of sight from a specific point.
     * 
     * @param from Starting world point
     * @param maxDistance Maximum distance to search (in tiles)
     * @param predicate Filter to apply to NPCs
     * @return Stream of NPCs that match the predicate and are in line of sight
     */
    public static Stream<Rs2NpcModel> getNpcsInLineOfSight(WorldPoint from, int maxDistance, Predicate<Rs2NpcModel> predicate) {
        return getNpcsInLineOfSight(from, maxDistance).filter(predicate);
    }
    
    // ============================================
    // Line of Sight Utilities
    // ============================================
    
    /**
     * Checks if there is a line of sight between the player and an NPC.
     * Uses RuneLite's WorldArea.hasLineOfSightTo for accurate scene collision detection.
     * 
     * @param npc The NPC to check
     * @return True if line of sight exists, false otherwise
     */
    public static boolean hasLineOfSight(Rs2NpcModel npc) {
        if (npc == null) return false;
        
        try {
            // Get player's current world location and create a small area (1x1)
            WorldPoint playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
            return hasLineOfSight(playerLocation, npc);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Checks if there is a line of sight between a specific point and an NPC.
     * 
     * @param point The world point to check from
     * @param npc The NPC to check against
     * @return True if line of sight exists, false otherwise
     */
    public static boolean hasLineOfSight(WorldPoint point, Rs2NpcModel npc) {
        if (npc == null || point == null) return false;
        
        try {
            WorldPoint npcLocation = npc.getWorldLocation();
            
            // Check same plane
            if (point.getPlane() != npcLocation.getPlane()) {
                return false;
            }
            
            // Create WorldAreas for the point and NPC
            int npcSize = npc.getComposition() != null ? npc.getComposition().getSize() : 1;
            
            return new WorldArea(npcLocation, npcSize, npcSize)
                    .hasLineOfSightTo(
                            Microbot.getClient().getTopLevelWorldView(),
                            new WorldArea(point, 1, 1));
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Gets all NPCs that have line of sight to the player.
     * Useful for identifying potential threats or interactive NPCs.
     * 
     * @return Stream of NPCs with line of sight to player
     */
    public static Stream<Rs2NpcModel> getNpcsWithLineOfSightToPlayer() {
        return getAll().filter(Rs2NpcCacheUtils::hasLineOfSight);
    }
    
    /**
     * Gets all NPCs that have line of sight to a specific world point.
     * 
     * @param point The world point to check from
     * @return Stream of NPCs with line of sight to the point
     */
    public static Stream<Rs2NpcModel> getNpcsWithLineOfSightTo(WorldPoint point) {
        return getAll().filter(npc -> hasLineOfSight(point, npc));
    }
    
    /**
     * Gets all NPCs at a location that have line of sight to the player.
     * 
     * @param worldPoint The world point to check at
     * @param maxDistance Maximum distance from the world point
     * @return Stream of NPCs at the location with line of sight
     */
    public static Stream<Rs2NpcModel> getNpcsAtLocationWithLineOfSight(WorldPoint worldPoint, int maxDistance) {
        return getAll()
                .filter(npc -> npc.getWorldLocation().distanceTo(worldPoint) <= maxDistance)
                .filter(Rs2NpcCacheUtils::hasLineOfSight);
    }

    // ============================================
    // Viewport Visibility and Interactability Utilities
    // ============================================

    /**
     * Checks if an NPC is visible in the current viewport using convex hull detection.
     * Uses client thread for safe access to NPC state.
     * 
     * @param npc The NPC to check
     * @return true if the NPC is visible on screen
     */
    public static boolean isVisibleInViewport(Rs2NpcModel npc) {
        try {
            if (npc == null) {
                return false;
            }
            
            // Use client thread for safe access to convex hull
            return Microbot.getClientThread().runOnClientThreadOptional(() -> 
                npc.getConvexHull() != null
            ).orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if any entity with a location is within the viewport by checking canvas conversion.
     * This is a generic method that can work with any entity that has a world location.
     * Uses client thread for safe access to client state.
     * 
     * @param worldPoint The world point to check
     * @return true if the location is visible on screen
     */
    public static boolean isLocationVisibleInViewport(net.runelite.api.coords.WorldPoint worldPoint) {
        try {
            if (worldPoint == null) {
                return false;
            }

            // Use client thread for safe access to client state
            return Microbot.getClientThread().runOnClientThreadOptional(() -> {
                Client client = Microbot.getClient();
                if (client == null) {
                    return false;
                }

                LocalPoint localPoint = LocalPoint.fromWorld(client.getTopLevelWorldView(), worldPoint);
                if (localPoint == null) {
                    return false;
                }

                net.runelite.api.Point canvasPoint = Perspective.localToCanvas(client, localPoint, client.getTopLevelWorldView().getPlane());
                return canvasPoint != null;
            }).orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Filters a stream of NPCs to only include those visible in viewport.
     * 
     * @param npcStream Stream of NPCs to filter
     * @return Stream of NPCs visible in viewport
     */
    public static Stream<Rs2NpcModel> filterVisibleInViewport(Stream<Rs2NpcModel> npcStream) {
        return npcStream.filter(Rs2NpcCacheUtils::isVisibleInViewport);
    }

    /**
     * Checks if an NPC is interactable (visible and within reasonable distance).
     * 
     * @param npc The NPC to check
     * @param maxDistance Maximum distance in tiles for interaction
     * @return true if the NPC is interactable
     */
    public static boolean isInteractable(Rs2NpcModel npc, int maxDistance) {
        try {
            if (npc == null) {
                return false;
            }

            // Check if visible in viewport first
            if (!isVisibleInViewport(npc)) {
                return false;
            }

            // Check distance from player
            return npc.getDistanceFromPlayer() <= maxDistance;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if an entity at a world point is interactable (within reasonable distance and visible).
     * Uses client thread for safe access to player location.
     * 
     * @param worldPoint The world point to check
     * @param maxDistance Maximum distance in tiles for interaction
     * @return true if the location is potentially interactable
     */
    public static boolean isInteractable(net.runelite.api.coords.WorldPoint worldPoint, int maxDistance) {
        try {
            if (worldPoint == null) {
                return false;
            }

            // Use client thread for safe access to player location
            return Microbot.getClientThread().runOnClientThreadOptional(() -> {
                net.runelite.api.coords.WorldPoint playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
                if (playerLocation.distanceTo(worldPoint) > maxDistance) {
                    return false;
                }
                
                // Check if visible in viewport (already uses client thread internally)
                return isLocationVisibleInViewport(worldPoint);
            }).orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    // ============================================
    // Updated Existing Methods to Use Local Functions
    // ============================================

    /**
     * Gets all NPCs visible in the viewport.
     * 
     * @return Stream of NPCs visible in viewport
     */
    public static Stream<Rs2NpcModel> getVisibleInViewport() {
        return filterVisibleInViewport(getAll());
    }

    /**
     * Gets NPCs by ID that are visible in the viewport.
     * 
     * @param npcId The NPC ID
     * @return Stream of NPCs with the specified ID visible in viewport
     */
    public static Stream<Rs2NpcModel> getVisibleInViewportById(int npcId) {
        return filterVisibleInViewport(getById(npcId));
    }

    /**
     * Finds interactable NPCs by ID within distance from player.
     * 
     * @param npcId The NPC ID
     * @param maxDistance Maximum distance in tiles
     * @return Stream of interactable NPCs with the specified ID
     */
    public static Stream<Rs2NpcModel> findInteractableById(int npcId, int maxDistance) {
        return getById(npcId)
                .filter(npc -> isInteractable(npc, maxDistance));
    }

    /**
     * Finds interactable NPCs by name within distance from player.
     * 
     * @param name The NPC name
     * @param maxDistance Maximum distance in tiles
     * @return Stream of interactable NPCs with the specified name
     */
    public static Stream<Rs2NpcModel> findInteractableByName(String name, int maxDistance) {
        return findAll(nameMatches(name, false))
                .filter(npc -> isInteractable(npc, maxDistance));
    }
}