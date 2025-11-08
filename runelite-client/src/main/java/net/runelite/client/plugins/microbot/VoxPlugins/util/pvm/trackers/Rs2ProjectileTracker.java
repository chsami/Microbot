package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.trackers;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Projectile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model.ProjectileData;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.registry.ProjectileRegistry;
import net.runelite.client.plugins.microbot.util.cache.CacheMode;
import net.runelite.client.plugins.microbot.util.cache.Rs2Cache;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * thread-safe tracker for projectiles
 * extends Rs2Cache for consistent caching behavior
 * 
 * FILTERING: only tracks registered projectiles
 */
@Slf4j
@Singleton
public class Rs2ProjectileTracker extends Rs2Cache<Integer, ProjectileData> {

    private static Rs2ProjectileTracker instance;

    // auto-incrementing counter for unique projectile instance IDs
    private final java.util.concurrent.atomic.AtomicInteger instanceIdCounter = new java.util.concurrent.atomic.AtomicInteger(0);

    @Inject
    private ProjectileRegistry projectileRegistry;

    private Rs2ProjectileTracker() {
        super("ProjectileTracker", CacheMode.EVENT_DRIVEN_ONLY);
    }

    public static synchronized Rs2ProjectileTracker getInstance() {
        if (instance == null) {
            instance = new Rs2ProjectileTracker();
        }
        return instance;
    }

    /**
     * update method for Rs2Cache interface
     * not used for event-driven tracking
     */
    @Override
    public void update() {
        // event-driven tracking only, no polling update needed
    }

    /**
     * handle projectile moved event (from Rs2PvMEventManager)
     * ONLY tracks registered projectiles
     * updates position on every event for real-time tracking
     */
    public void onProjectileMoved(ProjectileMoved event, boolean targetingPlayer) {
        Projectile projectile = event.getProjectile();
        int projectileGraphicsId = projectile.getId();

        // filter: only track if registered in projectile registry
        if (projectileRegistry != null && !projectileRegistry.isTrackedProjectile(projectileGraphicsId)) {
            return;
        }

        // find existing projectile instance by matching startCycle
        // multiple projectiles can have same graphics ID, so we match by startCycle
        ProjectileData existing = stream()
            .filter(data -> data.getId() == projectileGraphicsId &&
                          data.getStartCycle() == projectile.getStartCycle())
            .findFirst()
            .orElse(null);

        if (existing == null) {
            // first time seeing this projectile instance - create full snapshot
            int instanceId = instanceIdCounter.incrementAndGet();
            ProjectileData data = ProjectileData.fromProjectile(projectile, instanceId);
            put(instanceId, data);  // use instanceId as key

            log.debug("New projectile tracked: instance_id={}, graphics_id={}, targeting_player={}, aoe={}, ticks_until_impact={}",
                instanceId, projectileGraphicsId, targetingPlayer, data.isAoe(), data.getTicksUntilImpact());
        } else {
            // update existing projectile with current position
            int currentTick = Microbot.getClient().getTickCount();

            ProjectileData updated = ProjectileData.builder()
                // preserve original data
                .instanceId(existing.getInstanceId())
                .id(existing.getId())
                .spotanimId(existing.getSpotanimId())
                .startPoint(existing.getStartPoint())
                .targetPoint(event.getPosition())  // may change for actor-targeted projectiles
                .startCycle(existing.getStartCycle())
                .endCycle(existing.getEndCycle())
                .startHeight(existing.getStartHeight())
                .endHeight(existing.getEndHeight())
                .slope(existing.getSlope())
                .timestamp(existing.getTimestamp())
                .startGameTick(existing.getStartGameTick())
                .targetingPlayer(existing.isTargetingPlayer())
                .isAoe(existing.isAoe())
                .targetActor(existing.getTargetActor())
                // UPDATE: real-time position from projectile
                .currentX(projectile.getX())
                .currentY(projectile.getY())
                .currentZ(projectile.getZ())
                .lastPositionUpdateTick(currentTick)
                .build();

            put(existing.getInstanceId(), updated);  // use instanceId as key

            log.trace("Updated projectile position: instance_id={}, graphics_id={}, x={}, y={}, z={}, distance_from_player={}",
                existing.getInstanceId(), projectileGraphicsId, projectile.getX(), projectile.getY(), projectile.getZ(),
                updated.getDistanceFromPlayer());
        }
    }

    /**
     * cleanup stale projectiles (called every game tick)
     */
    public void onGameTick() {
        // collect expired projectiles for removal
        List<Integer> toRemove = new ArrayList<>();
        entryStream().forEach(entry -> {
            ProjectileData data = entry.getValue();
            if (data.getTicksAgo() > 10) { // 10 tick buffer after spawn
                toRemove.add(entry.getKey());
                log.debug("Removing expired projectile: id={}, age_ticks={}", entry.getKey(), data.getTicksAgo());
            }
        });

        // remove expired entries
        toRemove.forEach(this::remove);

        if (!toRemove.isEmpty()) {
            log.debug("Cleaned up {} expired projectiles", toRemove.size());
        }
    }

    /**
     * get all projectiles targeting player
     */
    public List<ProjectileData> getPlayerTargetingProjectiles() {
        return stream()
            .filter(ProjectileData::isTargetingPlayer)
            .collect(Collectors.toList());
    }

    /**
     * get all AOE projectiles
     */
    public List<ProjectileData> getAoeProjectiles() {
        return stream()
            .filter(ProjectileData::isAoe)
            .collect(Collectors.toList());
    }

    /**
     * get projectile by instance ID (unique per projectile)
     */
    public Optional<ProjectileData> getProjectileByInstanceId(int instanceId) {
        return Optional.ofNullable(get(instanceId));
    }

    /**
     * get all projectiles with specific graphics ID
     * NOTE: multiple projectile instances can have the same graphics ID
     */
    public List<ProjectileData> getProjectilesByGraphicsId(int graphicsId) {
        return stream()
            .filter(data -> data.getId() == graphicsId)
            .collect(Collectors.toList());
    }

    /**
     * get all active projectiles
     */
    public List<ProjectileData> getAllActiveProjectiles() {
        return stream()
            .filter(data -> !data.isExpired())
            .collect(Collectors.toList());
    }

    /**
     * get projectiles about to impact (within tickBuffer)
     */
    public List<ProjectileData> getProjectilesAboutToImpact(int tickBuffer) {
        return stream()
            .filter(data -> data.isAboutToImpact(tickBuffer))
            .collect(Collectors.toList());
    }

    /**
     * check if any projectile is targeting player and about to impact
     */
    public boolean hasIncomingPlayerProjectile(int tickBuffer) {
        return stream()
            .anyMatch(data -> data.isTargetingPlayer() && data.isAboutToImpact(tickBuffer));
    }

    /**
     * get projectiles within range of a specific WorldPoint using CURRENT position
     * useful for path validation and dodging mechanics
     */
    public List<ProjectileData> getProjectilesNear(WorldPoint point, int maxDistance) {
        return stream()
            .filter(ProjectileData::hasRecentPositionUpdate)  // only projectiles with recent position data
            .filter(data -> data.getDistanceFrom(point) <= maxDistance)
            .collect(Collectors.toList());
    }

    /**
     * get projectiles near player using CURRENT position
     */
    public List<ProjectileData> getProjectilesNearPlayer(int maxDistance) {
        WorldPoint playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
        return getProjectilesNear(playerLocation, maxDistance);
    }

    /**
     * check if any AOE projectile is near a specific location
     * useful for safe tile validation
     */
    public boolean hasAoeProjectileNear(WorldPoint point, int maxDistance) {
        return stream()
            .filter(ProjectileData::isAoe)
            .filter(ProjectileData::hasRecentPositionUpdate)
            .anyMatch(data -> data.getDistanceFrom(point) <= maxDistance);
    }
}
