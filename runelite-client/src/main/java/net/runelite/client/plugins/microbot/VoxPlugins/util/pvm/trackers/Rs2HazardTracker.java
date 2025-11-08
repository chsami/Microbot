package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.trackers;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GraphicsObject;
import net.runelite.api.NPC;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model.HazardData;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model.MovingHazard;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.registry.HazardRegistry;
import net.runelite.client.plugins.microbot.util.cache.CacheMode;
import net.runelite.client.plugins.microbot.util.cache.Rs2Cache;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * thread-safe tracker for hazardous objects and graphics
 * extends Rs2Cache for consistent caching behavior
 * key: unique hazard ID (auto-incrementing)
 * 
 * COMPLETE FLOW:
 * 1. Plugin registers hazards in HazardRegistry with TTL and radius
 *    hazardRegistry.registerHazardousGraphic(graphicId, 5000L, 1, "Floor tile");
 * 
 * 2. Game event fires (GameObjectSpawned, GraphicsObjectCreated)
 *    Rs2PvMEventManager forwards to this tracker
 * 
 * 3. Tracker retrieves HazardConfig from registry
 *    Optional<HazardConfig> config = hazardRegistry.getConfigForGraphic(graphicId);
 * 
 * 4. HazardData created using config values
 *    - customTtlMs: from config.getTtlMs() (time until auto-despawn)
 *    - radius: from config.getDefaultRadius() (danger zone size)
 * 
 * 5. Cleanup:
 *    - Game objects: removed via GameObjectDespawned event
 *    - Graphics objects: removed via TTL expiration in onGameTick()
 *    - NPCs: removed via NpcDespawned event
 */
@Slf4j
@Singleton
public class Rs2HazardTracker extends Rs2Cache<Integer, HazardData> {

    private static Rs2HazardTracker instance;
    private final AtomicInteger hazardIdCounter = new AtomicInteger(0);

    @Inject
    private HazardRegistry hazardRegistry;

    private Rs2HazardTracker() {
        super("HazardTracker", CacheMode.EVENT_DRIVEN_ONLY);
    }

    public static synchronized Rs2HazardTracker getInstance() {
        if (instance == null) {
            instance = new Rs2HazardTracker();
        }
        return instance;
    }

    /**
     * update method for Rs2Cache interface
     * not used for event-driven hazard tracking
     */
    @Override
    public void update() {
        // event-driven tracking only, no polling update needed
    }

    /**
     * handle game object spawned event (from Rs2PvMEventManager)
     * retrieves radius from registry config
     */
    public void onGameObjectSpawned(GameObjectSpawned event) {
        GameObject obj = event.getGameObject();
        int objectId = obj.getId();

        // retrieve pre-registered config (ttl, radius, description)
        Optional<HazardRegistry.HazardConfig> configOpt = hazardRegistry != null
            ? hazardRegistry.getConfigForObject(objectId)
            : Optional.empty();

        if (configOpt.isEmpty()) {
            log.warn("Object {} not registered in hazard registry", objectId);
            return;
        }

        HazardRegistry.HazardConfig config = configOpt.get();
        Tile tile = getHazardsTile(obj.getWorldLocation());

        // create immutable hazard data using config values
        int uniqueId = hazardIdCounter.incrementAndGet();
        HazardData data = HazardData.builder()
            .id(uniqueId)
            .gameId(objectId)
            .location(obj.getWorldLocation())
            .tile(tile)
            .source(HazardData.HazardSource.GAME_OBJECT)
            .timestamp(System.currentTimeMillis())
            .gameTick(Microbot.getClient().getTickCount())
            .radius(config.getDefaultRadius())      // ← radius from registry config
            .customTtlMs(config.getTtlMs())         // ← ttl from registry config (0 = permanent)
            .build();

        put(uniqueId, data);

        log.debug("Tracked hazardous object: game_id={}, location={}, radius={}, tick={}",
            objectId, data.getLocation(), data.getRadius(), data.getGameTick());
    }

    /**
     * handle game object despawned event (from Rs2PvMEventManager)
     * removes only matching object id at location for precise cleanup
     */
    public void onGameObjectDespawned(GameObjectDespawned event) {
        GameObject obj = event.getGameObject();
        int objectId = obj.getId();
        WorldPoint location = obj.getWorldLocation();

        // find and remove hazards matching both objectId AND location
        List<Integer> toRemove = new ArrayList<>();
        entryStream().forEach(entry -> {
            HazardData data = entry.getValue();
            if (data.isGameObject() &&
                data.getGameId() == objectId &&
                data.getLocation().equals(location)) {
                toRemove.add(entry.getKey());
            }
        });

        toRemove.forEach(this::remove);

        if (!toRemove.isEmpty()) {
            log.debug("Removed {} hazard(s) for object {} at location {}", toRemove.size(), objectId, location);
        }
    }

    /**
     * handle graphics object created event (from Rs2PvMEventManager)
     * retrieves ttl and radius from registry config
     * CRITICAL: graphics have NO despawn event, so ttl MUST be set in registry!
     */
    public void onGraphicsObjectCreated(GraphicsObjectCreated event) {
        GraphicsObject graphic = event.getGraphicsObject();
        int graphicId = graphic.getId();

        // retrieve pre-registered config (ttl, radius, description)
        Optional<HazardRegistry.HazardConfig> configOpt = hazardRegistry != null
            ? hazardRegistry.getConfigForGraphic(graphicId)
            : Optional.empty();

        if (configOpt.isEmpty()) {
            log.warn("Graphic {} not registered in hazard registry", graphicId);
            return;
        }

        HazardRegistry.HazardConfig config = configOpt.get();

        // create immutable hazard data using config values
        int uniqueId = hazardIdCounter.incrementAndGet();
        LocalPoint localPoint = graphic.getLocation();
        WorldPoint graphicsWorldPoint = WorldPoint.fromLocalInstance(Microbot.getClient(), localPoint);
        Tile tile = getHazardsTile(graphicsWorldPoint);

        HazardData data = HazardData.builder()
            .id(uniqueId)
            .gameId(graphicId)
            .location(graphicsWorldPoint)
            .tile(tile)
            .source(HazardData.HazardSource.GRAPHICS_OBJECT)
            .timestamp(System.currentTimeMillis())
            .gameTick(Microbot.getClient().getTickCount())
            .radius(config.getDefaultRadius())      // ← radius from registry config
            .customTtlMs(config.getTtlMs())         // ← ttl from registry config (REQUIRED!)
            .build();

        put(uniqueId, data);

        log.debug("Tracked hazardous graphic: game_id={}, location={}, ttl={}ms, radius={}, tick={}",
            graphicId, data.getLocation(), config.getTtlMs(), data.getRadius(), data.getGameTick());
    }

    /**
     * handle NPC spawned event (from Rs2PvMEventManager)
     * only tracks NPCs registered as hazardous (tornadoes, minions, etc.)
     */
    public void onNpcSpawned(NpcSpawned event) {
        NPC npc = event.getNpc();
        int npcId = npc.getId();

        // check if this NPC is registered as hazardous
        Optional<HazardRegistry.HazardConfig> configOpt = hazardRegistry != null
            ? hazardRegistry.getConfigForNpc(npcId)
            : Optional.empty();

        if (configOpt.isEmpty()) {
            // not a registered hazard, skip tracking
            return;
        }

        HazardRegistry.HazardConfig config = configOpt.get();
        WorldPoint location = npc.getWorldLocation();
        Tile tile = getHazardsTile(location);

        // create immutable hazard data for NPC
        int uniqueId = hazardIdCounter.incrementAndGet();
        HazardData data = HazardData.builder()
            .id(uniqueId)
            .gameId(npcId)
            .location(location)
            .tile(tile)
            .source(HazardData.HazardSource.NPC)
            .timestamp(System.currentTimeMillis())
            .gameTick(Microbot.getClient().getTickCount())
            .radius(config.getDefaultRadius())
            .customTtlMs(0)  // NPCs use despawn events, not TTL
            .npcIndex(npc.getIndex())  // store NPC index for location updates
            .build();

        put(uniqueId, data);

        log.debug("Tracked hazardous NPC: game_id={}, npc_index={}, location={}, radius={}, tick={}",
            npcId, npc.getIndex(), location, data.getRadius(), data.getGameTick());
    }

    /**
     * handle NPC despawned event (from Rs2PvMEventManager)
     * removes NPC hazards by NPC index
     */
    public void onNpcDespawned(NpcDespawned event) {
        NPC npc = event.getNpc();
        int npcIndex = npc.getIndex();

        // find and remove hazards matching NPC index
        List<Integer> toRemove = new ArrayList<>();
        entryStream().forEach(entry -> {
            HazardData data = entry.getValue();
            if (data.isNpc() && data.getNpcIndex() == npcIndex) {
                toRemove.add(entry.getKey());
            }
        });

        toRemove.forEach(this::remove);

        if (!toRemove.isEmpty()) {
            log.debug("Removed {} hazard(s) for NPC index {}", toRemove.size(), npcIndex);
        }
    }

    public Tile getHazardsTile(WorldPoint worldPoint) {
        if (worldPoint == null) {
            return null;
        }
        Client client = Microbot.getClient();
        if (client == null) {
            return null;
        }
        if (client.getLocalPlayer() == null) {
            return null;
        }

        // get scene from player's world view
    	var scene = client.getWorldView(client.getLocalPlayer().getLocalLocation().getWorldView()).getScene();
        if (scene == null) {
            return null;
        }

        var tiles = scene.getTiles();

        // convert worldPoint to scene coordinates (use parameter, not player location!)
		int tileX = worldPoint.getX() - scene.getBaseX();
		int tileY = worldPoint.getY() - scene.getBaseY();

		// bounds check
		if (worldPoint.getPlane() < 0 || worldPoint.getPlane() >= tiles.length ||
		    tileX < 0 || tileX >= tiles[worldPoint.getPlane()].length ||
		    tileY < 0 || tileY >= tiles[worldPoint.getPlane()][tileX].length) {
		    return null;
		}

		return tiles[worldPoint.getPlane()][tileX][tileY];
			
    }

    /**
     * cleanup stale hazards (called every game tick)
     * removes expired graphics, updates NPC locations
     */
    public void onGameTick() {
        // collect inactive graphic hazards for removal
        List<Integer> toRemove = new ArrayList<>();
        entryStream().forEach(entry -> {
            HazardData data = entry.getValue();

            // check graphics for active time expiration
            // game objects and NPCs are managed by despawn events
            if (data.isGraphic() && !data.isActive()) {
                toRemove.add(entry.getKey());
                log.debug("Removing inactive graphic hazard: id={}, game_id={}, age_ms={}",
                    entry.getKey(), data.getGameId(), data.getAgeMs());
            }

            // update NPC hazard locations and trajectory predictions (NPCs can move)
            if (data.isNpc() && data.isActive() && data.getNpcIndex() > 0) {
                Client client = Microbot.getClient();
                if (client != null && client.getTopLevelWorldView() != null) {
                    // iterate npcs to find matching index
                    for (NPC npc : client.getTopLevelWorldView().npcs()) {
                        if (npc != null && npc.getIndex() == data.getNpcIndex()) {
                            WorldPoint currentLocation = npc.getWorldLocation();

                            // update if location changed OR if moving hazard needs prediction update
                            if (!currentLocation.equals(data.getLocation()) || data.isMoving()) {
                                Tile updatedTile = getHazardsTile(currentLocation);

                                // update trajectory prediction if this is a moving hazard
                                MovingHazard updatedMovingHazard = null;
                                if (data.getMovingHazard() != null) {
                                    WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
                                    int currentTick = client.getTickCount();

                                    // update moving hazard with new position and predicted path
                                    updatedMovingHazard = data.getMovingHazard()
                                        .withUpdatedPosition(currentLocation, playerPos, currentTick);
                                }

                                HazardData updatedData = data.toBuilder()
                                    .location(currentLocation)
                                    .tile(updatedTile)
                                    .movingHazard(updatedMovingHazard)
                                    .build();

                                put(entry.getKey(), updatedData);

                                if (updatedMovingHazard != null) {
                                    log.trace("Updated moving NPC hazard: id={}, game_id={}, location={}, predicted_path_size={}",
                                        entry.getKey(), data.getGameId(), currentLocation,
                                        updatedMovingHazard.getPredictedPath().size());
                                } else {
                                    log.trace("Updated NPC hazard location: id={}, game_id={}, location={}",
                                        entry.getKey(), data.getGameId(), currentLocation);
                                }
                            }
                            break;
                        }
                    }
                }
            }

            // update tile for all active hazards
            if (data.isActive()) {
                Tile updatedTile = getHazardsTile(data.getLocation());
                if (updatedTile != null && !updatedTile.equals(data.getTile())) {
                    // create updated hazard data with new tile
                    HazardData updatedData = data.toBuilder()
                        .tile(updatedTile)
                        .build();
                    put(entry.getKey(), updatedData);
                }
            }
        });

        // remove inactive graphic entries
        toRemove.forEach(this::remove);

        if (!toRemove.isEmpty()) {
            log.debug("Cleaned up {} inactive graphic hazards", toRemove.size());
        }
    }

    /**
     * get all active hazards
     */
    public List<HazardData> getActiveHazards() {
        return stream()
            .filter(HazardData::isActive)
            .collect(Collectors.toList());
    }

    /**
     * get hazards near location (within distance)
     */
    public List<HazardData> getHazardsNear(WorldPoint location, int distance) {
        return stream()
            .filter(HazardData::isActive)
            .filter(data -> data.getLocation().distanceTo(location) <= distance)
            .collect(Collectors.toList());
    }

    /**
     * check if location is dangerous (within any hazard radius)
     */
    public boolean isLocationDangerous(WorldPoint location) {
        return stream()
            .filter(HazardData::isActive)
            .anyMatch(data -> data.isLocationDangerous(location));
    }

    /**
     * get hazards by source type
     */
    public List<HazardData> getHazardsBySource(HazardData.HazardSource source) {
        return stream()
            .filter(data -> data.getSource() == source && data.isActive())
            .collect(Collectors.toList());
    }

    /**
     * get hazards by game id (object/graphic/npc id from game)
     */
    public List<HazardData> getHazardsByGameId(int gameId) {
        return stream()
            .filter(data -> data.getGameId() == gameId && data.isActive())
            .collect(Collectors.toList());
    }

    /**
     * get closest hazard to location
     */
    public Optional<HazardData> getClosestHazard(WorldPoint location) {
        return stream()
            .filter(HazardData::isActive)
            .min((a, b) -> Integer.compare(
                a.getLocation().distanceTo(location),
                b.getLocation().distanceTo(location)
            ));
    }
}
