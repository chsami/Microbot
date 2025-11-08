package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.registry;

import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * thread-safe singleton registry for hazardous objects and graphics
 * allows runtime registration of boss-specific hazard IDs with custom TTL
 * 
 * USAGE PATTERN:
 * 1. Plugin startup: Register hazards with TTL and radius
 *    registry.registerHazardousGraphic(graphicId, 5000L, 1, "Floor tile");
 * 
 * 2. Event fires: Rs2HazardTracker retrieves config from registry
 *    Optional<HazardConfig> config = registry.getConfigForGraphic(graphicId);
 * 
 * 3. HazardData created using config values (ttl, radius)
 *    HazardData.builder().customTtlMs(config.getTtlMs()).radius(config.getDefaultRadius())...
 */
@Slf4j
@Singleton
public class HazardRegistry {

    @Getter
    private final ConcurrentHashMap<Integer, HazardConfig> hazardousObjects = new ConcurrentHashMap<>();

    @Getter
    private final ConcurrentHashMap<Integer, HazardConfig> hazardousGraphics = new ConcurrentHashMap<>();

    @Getter
    private final ConcurrentHashMap<Integer, HazardConfig> hazardousNpcs = new ConcurrentHashMap<>();

    /**
     * configuration for a hazard type
     * stores ttl (time until despawn) and radius when registered
     */
    @Value
    public static class HazardConfig {
        int id;               // game object/graphic/npc id
        long ttlMs;           // time to live in milliseconds (0 = permanent until despawn event)
        int defaultRadius;    // danger radius in tiles from center
        String description;   // human-readable description for logging
        boolean isMoving;     // true for hazards that chase/move (tornadoes)
        double movementSpeed; // tiles per tick (default 0 for static hazards)
    }

    /**
     * register a hazardous object ID (permanent until despawn event)
     *
     * @param objectId game object id
     * @param radius danger radius in tiles
     * @param description human-readable description
     */
    public void registerHazardousObject(int objectId, int radius, String description) {
        HazardConfig config = new HazardConfig(objectId, 0, radius, description, false, 0.0);
        hazardousObjects.put(objectId, config);
        log.debug("Registered hazardous object: id={}, radius={}, desc={}", objectId, radius, description);
    }

    /**
     * register a hazardous graphic ID with custom TTL
     * IMPORTANT: graphics have no despawn event, so ttl MUST be specified!
     *
     * @param graphicId graphic object id
     * @param ttlMs time to live in milliseconds (how long before despawn)
     * @param radius danger radius in tiles
     * @param description human-readable description
     */
    public void registerHazardousGraphic(int graphicId, long ttlMs, int radius, String description) {
        if (ttlMs <= 0) {
            log.warn("Graphics objects require positive TTL! id={}, defaulting to 10s", graphicId);
            ttlMs = 10000; // 10 second fallback
        }
        HazardConfig config = new HazardConfig(graphicId, ttlMs, radius, description, false, 0.0);
        hazardousGraphics.put(graphicId, config);
        log.debug("Registered hazardous graphic: id={}, ttl={}ms, radius={}, desc={}",
            graphicId, ttlMs, radius, description);
    }

    /**
     * register a hazardous NPC ID (permanent until despawn event)
     * NPCs like tornadoes, minions that players must avoid
     *
     * @param npcId npc id
     * @param radius danger radius in tiles
     * @param description human-readable description
     */
    public void registerHazardousNpc(int npcId, int radius, String description) {
        registerHazardousNpc(npcId, 0, radius, description, false, 0.0);
    }

    /**
     * register a hazardous NPC ID with TTL and movement prediction
     * Use for moving hazards like tornadoes that chase the player
     *
     * @param npcId npc id
     * @param ttlMs time to live in milliseconds (0 = permanent until despawn, 20 ticks = 12000ms for tornadoes)
     * @param radius danger radius in tiles
     * @param description human-readable description
     * @param isMoving true if hazard moves/chases player
     * @param movementSpeed tiles per tick (e.g., 1.0 for tornadoes)
     */
    public void registerHazardousNpc(int npcId, long ttlMs, int radius, String description, boolean isMoving, double movementSpeed) {
        HazardConfig config = new HazardConfig(npcId, ttlMs, radius, description, isMoving, movementSpeed);
        hazardousNpcs.put(npcId, config);
        log.debug("Registered hazardous NPC: id={}, ttl={}ms, radius={}, moving={}, speed={}, desc={}",
            npcId, ttlMs, radius, isMoving, movementSpeed, description);
    }

    /**
     * check if object ID is hazardous
     */
    public boolean isHazardousObject(int objectId) {
        return hazardousObjects.containsKey(objectId);
    }

    /**
     * check if graphic ID is hazardous
     */
    public boolean isHazardousGraphic(int graphicId) {
        return hazardousGraphics.containsKey(graphicId);
    }

    /**
     * check if NPC ID is hazardous
     */
    public boolean isHazardousNpc(int npcId) {
        return hazardousNpcs.containsKey(npcId);
    }

    /**
     * get config for object ID
     */
    public Optional<HazardConfig> getConfigForObject(int objectId) {
        return Optional.ofNullable(hazardousObjects.get(objectId));
    }

    /**
     * get hazard config for graphic ID
     */
    public Optional<HazardConfig> getConfigForGraphic(int graphicId) {
        return Optional.ofNullable(hazardousGraphics.get(graphicId));
    }

    /**
     * get hazard config for NPC ID
     */
    public Optional<HazardConfig> getConfigForNpc(int npcId) {
        return Optional.ofNullable(hazardousNpcs.get(npcId));
    }

    /**
     * unregister object (for cleanup)
     */
    public void unregisterObject(int objectId) {
        hazardousObjects.remove(objectId);
        log.debug("Unregistered hazardous object: {}", objectId);
    }

    /**
     * unregister graphic (for cleanup)
     */
    public void unregisterGraphic(int graphicId) {
        hazardousGraphics.remove(graphicId);
        log.debug("Unregistered hazardous graphic: {}", graphicId);
    }

    /**
     * unregister NPC (for cleanup)
     */
    public void unregisterNpc(int npcId) {
        hazardousNpcs.remove(npcId);
        log.debug("Unregistered hazardous NPC: {}", npcId);
    }

    /**
     * clear all registrations
     */
    public void clearAll() {
        int objectCount = hazardousObjects.size();
        int graphicCount = hazardousGraphics.size();
        int npcCount = hazardousNpcs.size();

        hazardousObjects.clear();
        hazardousGraphics.clear();
        hazardousNpcs.clear();

        log.debug("Cleared {} hazardous objects, {} graphics, {} NPCs", 
            objectCount, graphicCount, npcCount);
    }

    /**
     * get count of registered hazards
     */
    public int getTotalHazardCount() {
        return hazardousObjects.size() + hazardousGraphics.size() + hazardousNpcs.size();
    }
}
