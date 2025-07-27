package net.runelite.client.plugins.microbot.util.cache;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.util.cache.strategy.entity.NpcUpdateStrategy;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Thread-safe cache for tracking NPCs using the unified cache architecture.
 * Returns Rs2NpcModel objects for enhanced NPC handling.
 * Uses EVENT_DRIVEN_ONLY mode to persist NPCs until despawn or game state changes.
 * 
 * This class extends Rs2UnifiedCache and provides specific NPC caching functionality
 * with proper EventBus integration for @Subscribe methods.
 */
@Slf4j
public class Rs2NpcCache extends Rs2Cache<Integer, Rs2NpcModel> {
    
    private static Rs2NpcCache instance;
    
    /**
     * Private constructor for singleton pattern.
     */
    private Rs2NpcCache() {
        super("NpcCache", CacheMode.EVENT_DRIVEN_ONLY);
        this.withUpdateStrategy(new NpcUpdateStrategy());
    }
    
    /**
     * Gets the singleton instance of Rs2NpcCache.
     * 
     * @return The singleton NPC cache instance
     */
    public static synchronized Rs2NpcCache getInstance() {
        if (instance == null) {
            instance = new Rs2NpcCache();
        }
        return instance;
    }
    
    // ============================================
    // Legacy API Compatibility Methods
    // ============================================
    
    /**
     * Gets an NPC by its index.
     * 
     * @param index The NPC index
     * @return Optional containing the NPC model if found
     */
    public static Optional<Rs2NpcModel> getNpcByIndex(int index) {
        return Optional.ofNullable(getInstance().get(index));
    }
    
    /**
     * Gets NPCs by ID - Legacy compatibility method.
     * 
     * @param npcId The NPC ID
     * @return Stream of matching NPCs
     */
    public static Stream<Rs2NpcModel> getNpcsById(int npcId) {
        return getInstance().stream()
                .filter(npc -> npc.getId() == npcId);
    }
    
    /**
     * Gets first NPC by ID - Legacy compatibility method.
     * 
     * @param npcId The NPC ID
     * @return Optional containing the first matching NPC
     */
    public static Optional<Rs2NpcModel> getFirstNpcById(int npcId) {
        return getNpcsById(npcId).findFirst();
    }
    
    /**
     * Gets all NPCs - Legacy compatibility method.
     * 
     * @return Stream of all NPCs
     */
    public static Stream<Rs2NpcModel> getAllNpcs() {
        return getInstance().stream();
    }
    
    /**
     * Gets all NPCs matching a specific name (case-insensitive).
     * 
     * @param name The NPC name to search for
     * @return Stream of matching Rs2NpcModel objects
     */
    public static Stream<Rs2NpcModel> getNpcsByName(String name) {
        return getInstance().stream()
                .filter(npc -> npc.getName() != null && 
                              npc.getName().toLowerCase().contains(name.toLowerCase()));
    }
    
    /**
     * Gets all NPCs within a certain distance from a location.
     * 
     * @param location The center location
     * @param maxDistance The maximum distance in tiles
     * @return Stream of NPCs within the specified distance
     */
    public static Stream<Rs2NpcModel> getNpcsWithinDistance(net.runelite.api.coords.WorldPoint location, int maxDistance) {
        return getInstance().stream()
                .filter(npc -> npc.getWorldLocation() != null &&
                              npc.getWorldLocation().distanceTo(location) <= maxDistance);
    }
    
    /**
     * Gets the first NPC matching the specified name.
     * 
     * @param name The NPC name
     * @return Optional containing the first matching NPC model
     */
    public static Optional<Rs2NpcModel> getFirstNpcByName(String name) {
        return getNpcsByName(name).findFirst();
    }
    
    /**
     * Gets NPCs matching a specific combat level.
     * 
     * @param combatLevel The combat level to search for
     * @return Stream of matching NPCs
     */
    public static Stream<Rs2NpcModel> getNpcsByCombatLevel(int combatLevel) {
        return getAllNpcs()
                .filter(npc -> npc.getCombatLevel() == combatLevel);
    }
    
    /**
     * Gets NPCs that are currently in combat.
     * 
     * @return Stream of NPCs in combat
     */
    public static Stream<Rs2NpcModel> getNpcsInCombat() {
        return getAllNpcs()
                .filter(npc -> npc.isInteracting());
    }
    
    /**
     * Gets the closest NPC to the player with the specified ID.
     * 
     * @param npcId The NPC ID to search for
     * @return Optional containing the closest NPC
     */
    public static Optional<Rs2NpcModel> getClosestNpcByGameId(int npcId) {
        return getNpcsById(npcId)
                .min((a, b) -> Integer.compare(a.getDistanceFromPlayer(), b.getDistanceFromPlayer()));
    }
    
    /**
     * Gets the closest NPC to the player with the specified name.
     * 
     * @param name The NPC name to search for
     * @return Optional containing the closest NPC
     */
    public static Optional<Rs2NpcModel> getClosestNpcByName(String name) {
        return getNpcsByName(name)
                .min((a, b) -> Integer.compare(a.getDistanceFromPlayer(), b.getDistanceFromPlayer()));
    }
    
    /**
     * Gets the closest NPC to a specific anchor point with the specified ID.
     * 
     * @param npcId The NPC ID to search for
     * @param anchorPoint The anchor point to calculate distance from
     * @return Optional containing the closest NPC
     */
    public static Optional<Rs2NpcModel> getClosestNpcByGameId(int npcId, net.runelite.api.coords.WorldPoint anchorPoint) {
        return getNpcsById(npcId)
                .min((a, b) -> Integer.compare(
                    a.getWorldLocation().distanceTo(anchorPoint), 
                    b.getWorldLocation().distanceTo(anchorPoint)
                ));
    }
    
    /**
     * Gets the closest NPC to a specific anchor point with the specified name.
     * 
     * @param name The NPC name to search for
     * @param anchorPoint The anchor point to calculate distance from
     * @return Optional containing the closest NPC
     */
    public static Optional<Rs2NpcModel> getClosestNpcByName(String name, net.runelite.api.coords.WorldPoint anchorPoint) {
        return getNpcsByName(name)
                .min((a, b) -> Integer.compare(
                    a.getWorldLocation().distanceTo(anchorPoint), 
                    b.getWorldLocation().distanceTo(anchorPoint)
                ));
    }
    
    /**
     * Gets the total number of cached NPCs.
     * 
     * @return The total NPC count
     */
    public static int getNpcCount() {
        return getInstance().size();
    }
    
    /**
     * Manually adds an NPC to the cache.
     * 
     * @param npc The NPC to add
     */
    public static void addNpc(NPC npc) {
        if (npc != null) {
            Rs2NpcModel npcModel = new Rs2NpcModel(npc);
            getInstance().put(npc.getIndex(), npcModel);
            log.debug("Manually added NPC: {} [{}] at {}", npc.getName(), npc.getId(), npc.getWorldLocation());
        }
    }
    
    /**
     * Manually removes an NPC from the cache.
     * 
     * @param index The NPC index to remove
     */
    public static void removeNpc(int index) {
        getInstance().remove(index);
        log.debug("Manually removed NPC with index: {}", index);
    }
    
    /**
     * Invalidates all NPC cache entries.
     */
    public static void invalidateAllNpcs() {
        getInstance().invalidateAll();
        log.debug("Invalidated all NPC cache entries");
    }
    
    /**
     * Event handler registration for the unified cache.
     * The unified cache handles events through its strategy automatically.
     */
    
        
    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {        
        getInstance().handleEvent(event);
    }
    
    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {        
        getInstance().handleEvent(event);
    }

    
    /**
     * Resets the singleton instance. Used for testing.
     */
    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }
    
    /**
     * Gets cache mode - Legacy compatibility method.
     * 
     * @return The cache mode
     */
    public static CacheMode getNpcCacheMode() {
        return getInstance().getCacheMode();
    }
    
    /**
     * Gets cache statistics - Legacy compatibility method.
     * 
     * @return Statistics string for debugging
     */
    public static String getNpcCacheStatistics() {
        return getInstance().getStatisticsString();
    }
    @Override
    public void update() {
        // This method can be used to trigger a manual update if needed
        // no idea for now, how we can implement a static update method for now
        
        
    }
}
