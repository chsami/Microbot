package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.registry;

import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * registry for projectiles that should be tracked
 * plugins register projectile IDs they want to monitor
 * thread-safe singleton for runtime registration
 */
@Slf4j
@Singleton
public class ProjectileRegistry {

    @Value
    public static class ProjectileConfig {
        int projectileId;     // game projectile id
        String description;   // human-readable description for logging
    }

    @Getter
    private final ConcurrentHashMap<Integer, ProjectileConfig> trackedProjectiles = new ConcurrentHashMap<>();

    /**
     * register a projectile ID to track
     * 
     * @param projectileId game projectile id
     * @param description human-readable description
     */
    public void registerProjectile(int projectileId, String description) {
        ProjectileConfig config = new ProjectileConfig(projectileId, description);
        trackedProjectiles.put(projectileId, config);
        log.debug("Registered projectile: id={}, desc={}", projectileId, description);
    }

    /**
     * check if projectile ID should be tracked
     */
    public boolean isTrackedProjectile(int projectileId) {
        return trackedProjectiles.containsKey(projectileId);
    }

    /**
     * get config for projectile ID
     */
    public Optional<ProjectileConfig> getConfigForProjectile(int projectileId) {
        return Optional.ofNullable(trackedProjectiles.get(projectileId));
    }

    /**
     * unregister projectile (for cleanup)
     */
    public void unregisterProjectile(int projectileId) {
        trackedProjectiles.remove(projectileId);
        log.debug("Unregistered projectile: {}", projectileId);
    }

    /**
     * clear all registrations
     */
    public void clearAll() {
        int count = trackedProjectiles.size();
        trackedProjectiles.clear();
        log.debug("Cleared {} tracked projectiles", count);
    }

    /**
     * get count of registered projectiles
     */
    public int getTrackedProjectileCount() {
        return trackedProjectiles.size();
    }
}
