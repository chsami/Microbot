package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.registry;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * thread-safe singleton registry for trackable NPC attack animation IDs
 * allows runtime registration of boss-specific attack animations
 */
@Slf4j
@Singleton
public class AttackAnimationRegistry {

    @Getter
    private final Set<Integer> trackableAnimationIds = ConcurrentHashMap.newKeySet();

    @Getter
    private final ConcurrentHashMap<Integer, String> animationIdToDescription = new ConcurrentHashMap<>();

    /**
     * register an animation ID to track as attack animation
     */
    public void registerAnimationId(int animationId, String description) {
        trackableAnimationIds.add(animationId);
        animationIdToDescription.put(animationId, description);
        log.debug("Registered trackable animation: {} ({})", description, animationId);
    }

    /**
     * register animation ID without description
     */
    public void registerAnimationId(int animationId) {
        trackableAnimationIds.add(animationId);
        log.debug("Registered trackable animation: {}", animationId);
    }

    /**
     * check if animation ID should be tracked
     */
    public boolean isTrackable(int animationId) {
        return trackableAnimationIds.contains(animationId);
    }

    /**
     * get description for animation ID (if registered)
     */
    public Optional<String> getDescriptionForAnimationId(int animationId) {
        return Optional.ofNullable(animationIdToDescription.get(animationId));
    }

    /**
     * unregister animation ID
     */
    public void unregisterAnimationId(int animationId) {
        trackableAnimationIds.remove(animationId);
        String description = animationIdToDescription.remove(animationId);
        log.debug("Unregistered animation: {} ({})", description, animationId);
    }

    /**
     * clear all registrations
     */
    public void clearAll() {
        int count = trackableAnimationIds.size();
        trackableAnimationIds.clear();
        animationIdToDescription.clear();
        log.debug("Cleared {} trackable animation IDs", count);
    }

    /**
     * get count of registered animations
     */
    public int getTrackableCount() {
        return trackableAnimationIds.size();
    }
}
