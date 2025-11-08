package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.handlers;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Prayer;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model.AttackAnimationData;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model.ProjectileData;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.trackers.Rs2AnimationTracker;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.trackers.Rs2ProjectileTracker;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import javax.inject.Inject;
import javax.inject.Singleton;

import static net.runelite.client.plugins.microbot.util.Global.sleep;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * thread-safe handler for prayer switching automation
 * supports multiple detection methods:
 * - projectile-based (tick-perfect timing) - ONLY for player-targeted projectiles
 * - animation-based (NPC attack animations)
 * - NPC priority system (multiple threats)
 * 
 * CRITICAL: Only switches prayers for player-targeted projectiles (not AOE/ground-targeted)
 *           AOE projectiles should be handled by Rs2DodgeHandler instead
 */
@Slf4j
@Singleton
public class Rs2PrayerHandler {

    private static Rs2PrayerHandler instance;

    @Inject
    private Rs2ProjectileTracker projectileTracker;

    @Inject
    private Rs2AnimationTracker animationTracker;

    // projectile ID -> prayer mapping (only for player-targeted projectiles)
    private final Map<Integer, Rs2PrayerEnum> projectilePrayerMap = new ConcurrentHashMap<>();

    // NPC ID + animation ID -> prayer mapping
    private final Map<AnimationKey, Rs2PrayerEnum> animationPrayerMap = new ConcurrentHashMap<>();

    // NPC ID priority (higher = more important to pray against)
    private final Map<Integer, Integer> npcPriorityMap = new ConcurrentHashMap<>();

    // default tick buffer for projectile switching (1-2 ticks before impact)
    private int defaultTickBuffer = 2;

    // flicking configuration
    private boolean lazyFlickingEnabled = false;
    private boolean oneTickFlickingEnabled = false;

    // track flicked prayers (prayer -> tick when activated for flicking)
    private final Map<Rs2PrayerEnum, Integer> activeFlicks = new ConcurrentHashMap<>();

    // prayer disable tracking (projectile ID -> duration in milliseconds)
    private final Map<Integer, Long> prayerDisableProjectiles = new ConcurrentHashMap<>();

    // current prayer disable state (expiration timestamp)
    private long prayerDisableExpirationMs = 0;

    private Rs2PrayerHandler() {
    }

    public static synchronized Rs2PrayerHandler getInstance() {
        if (instance == null) {
            instance = new Rs2PrayerHandler();
        }
        return instance;
    }

    /**
     * register projectile -> prayer mapping
     * used for tick-perfect prayer switching based on incoming player-targeted projectiles
     * NOTE: only register player-targeted projectiles here (AOE projectiles should be dodged, not prayed against)
     */
    public void registerProjectilePrayer(int projectileId, Rs2PrayerEnum prayer) {
        projectilePrayerMap.put(projectileId, prayer);
        log.debug("Registered projectile prayer: projectile_id={}, prayer={}",
            projectileId, prayer);
    }

    /**
     * register NPC animation -> prayer mapping
     * used for prayer switching based on NPC attack animations
     */
    public void registerAnimationPrayer(int npcId, int animationId, Rs2PrayerEnum prayer) {
        AnimationKey key = new AnimationKey(npcId, animationId);
        animationPrayerMap.put(key, prayer);
        log.debug("Registered animation prayer: npc_id={}, animation_id={}, prayer={}",
            npcId, animationId, prayer);
    }

    /**
     * register NPC priority for multi-threat scenarios
     * higher priority = more important to pray against
     */
    public void registerNpcPriority(int npcId, int priority) {
        npcPriorityMap.put(npcId, priority);
        log.debug("Registered NPC priority: npc_id={}, priority={}", npcId, priority);
    }

    /**
     * auto-switch prayer based on incoming projectiles
     * uses tick buffer to switch 1-2 ticks before impact
     */
    public boolean handleIncomingProjectiles() {
        return handleIncomingProjectiles(defaultTickBuffer);
    }

    /**
     * auto-switch prayer based on incoming projectiles with custom tick buffer
     * ONLY switches for player-targeted projectiles (not AOE/ground-targeted)
     */
    public boolean handleIncomingProjectiles(int tickBuffer) {
        if (projectileTracker == null) {
            return false;
        }

        // get projectiles about to impact that are TARGETING THE PLAYER (not AOE)
        List<ProjectileData> incomingProjectiles =
            projectileTracker.getProjectilesAboutToImpact(tickBuffer).stream()
                .filter(ProjectileData::isTargetingPlayer)  // only player-targeted
                .filter(data -> !data.isAoe())              // exclude AOE (ground-targeted)
                .collect(Collectors.toList());

        if (incomingProjectiles.isEmpty()) {
            return false;
        }

        // get nearest projectile (lowest ticks until impact)
        ProjectileData nearest = incomingProjectiles.stream()
            .min(Comparator.comparingInt(ProjectileData::getTicksUntilImpact))
            .orElse(null);

        if (nearest == null) {
            return false;
        }

        // check if we have prayer mapping for this projectile
        Rs2PrayerEnum prayer = projectilePrayerMap.get(nearest.getId());
        
        if (prayer == null) {
            log.debug("No prayer mapping for projectile: {}", nearest.getId());
            return false;
        }
        
        // check if already active
        if (Rs2Prayer.isPrayerActive(prayer)) {
            return true;
        }

        // switch prayer
        boolean success = Rs2Prayer.toggle(prayer,true);
        if (success) {
            log.debug("Switched to {} for player-targeted projectile {} (impact in {} ticks)",
                prayer, nearest.getId(), nearest.getTicksUntilImpact());
        }

        return success;
    }

    /**
     * auto-switch prayer based on NPC animations
     * useful when projectiles are not reliable indicators
     */
    public boolean handleNpcAnimations() {
        if (animationTracker == null) {
            return false;
        }

        // get all recent animations (within 2 ticks)
        List<AttackAnimationData> recentAnimations =
            animationTracker.getRecentAnimations(2);

        if (recentAnimations.isEmpty()) {
            return false;
        }

        // if multiple NPCs, prioritize by priority map
        AttackAnimationData priorityAnimation = recentAnimations.stream()
            .max(Comparator.comparingInt(data ->
                npcPriorityMap.getOrDefault(data.getNpcId(), 0)))
            .orElse(null);

        if (priorityAnimation == null) {
            return false;
        }

        // check if we have prayer mapping for this animation
        AnimationKey key = new AnimationKey(
            priorityAnimation.getNpcId(),
            priorityAnimation.getAnimationId()
        );

        Rs2PrayerEnum prayer = animationPrayerMap.get(key);
        if (prayer == null) {
            log.debug("No prayer mapping for animation: npc_id={}, animation_id={}",
                priorityAnimation.getNpcId(), priorityAnimation.getAnimationId());
            return false;
        }

        // check if already active
        if (Rs2Prayer.isPrayerActive(prayer)) {
            return true;
        }

        // switch prayer
        boolean success = Rs2Prayer.toggle(prayer, true);
        if (success) {
            log.debug("Switched to {} for NPC {} animation {}",
                prayer, priorityAnimation.getNpcId(), priorityAnimation.getAnimationId());
        }

        return success;
    }

    /**
     * get recommended prayer for current threats
     * combines projectile and animation detection
     * ONLY considers player-targeted projectiles (not AOE)
     */
    public Optional<Rs2PrayerEnum> getRecommendedPrayer() {
        // check projectiles first (highest priority)
        // ONLY player-targeted projectiles require prayer (AOE = dodge instead)
        if (projectileTracker != null) {
            List<ProjectileData> incoming = projectileTracker.getProjectilesAboutToImpact(defaultTickBuffer).stream()
                .filter(ProjectileData::isTargetingPlayer)  // only player-targeted
                .filter(data -> !data.isAoe())              // exclude AOE (ground-targeted)
                .collect(Collectors.toList());
            if (!incoming.isEmpty()) {
                ProjectileData nearest = incoming.get(0);
                Rs2PrayerEnum prayer = projectilePrayerMap.get(nearest.getId());
                if (prayer != null) {
                    return Optional.of(prayer);
                }
            }
        }        // fallback to animations
        if (animationTracker != null) {
            List<AttackAnimationData> recentAnimations = animationTracker.getRecentAnimations(2);
            if (!recentAnimations.isEmpty()) {
                AttackAnimationData priorityAnimation = recentAnimations.stream()
                    .max(Comparator.comparingInt(data ->
                        npcPriorityMap.getOrDefault(data.getNpcId(), 0)))
                    .orElse(null);

                if (priorityAnimation != null) {
                    AnimationKey key = new AnimationKey(
                        priorityAnimation.getNpcId(),
                        priorityAnimation.getAnimationId()
                    );
                    Rs2PrayerEnum prayer = animationPrayerMap.get(key);
                    if (prayer != null) {
                        return Optional.of(prayer);
                    }
                }
            }
        }

        return Optional.empty();
    }

    /**
     * set default tick buffer for projectile switching
     */
    public void setDefaultTickBuffer(int tickBuffer) {
        this.defaultTickBuffer = tickBuffer;
        log.debug("Set default tick buffer to {}", tickBuffer);
    }

    /**
     * get default tick buffer
     */
    public int getDefaultTickBuffer() {
        return defaultTickBuffer;
    }

    /**
     * clear all prayer mappings
     */
    public void clearMappings() {
        projectilePrayerMap.clear();
        animationPrayerMap.clear();
        npcPriorityMap.clear();
        prayerDisableProjectiles.clear();
        log.debug("Cleared all prayer mappings");
    }

    // ============================================
    // Prayer Disable Tracking
    // ============================================

    /**
     * register projectile that disables prayers
     * used for bosses with prayer-disabling attacks (e.g., Hunllef)
     *
     * @param projectileId projectile ID that disables prayers
     * @param durationMs duration in milliseconds that prayers are disabled
     */
    public void registerPrayerDisableProjectile(int projectileId, long durationMs) {
        prayerDisableProjectiles.put(projectileId, durationMs);
        log.debug("Registered prayer disable projectile: projectile_id={}, duration_ms={}",
            projectileId, durationMs);
    }

    /**
     * handle prayer disable projectile (called from projectile tracker)
     * activates prayer disable state for specified duration
     *
     * @param projectileId projectile ID that hit
     */
    public void handlePrayerDisableProjectile(int projectileId) {
        Long durationMs = prayerDisableProjectiles.get(projectileId);
        if (durationMs == null) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        prayerDisableExpirationMs = currentTime + durationMs;

        log.warn("Prayer disabled by projectile {}: duration={}ms, expires_at={}",
            projectileId, durationMs, prayerDisableExpirationMs);
    }

    /**
     * check if prayers are currently disabled
     */
    public boolean isPrayerDisabled() {
        long currentTime = System.currentTimeMillis();
        boolean disabled = currentTime < prayerDisableExpirationMs;

        if (disabled) {
            log.trace("Prayers disabled: {}ms remaining", prayerDisableExpirationMs - currentTime);
        }

        return disabled;
    }

    /**
     * get time remaining on prayer disable (milliseconds)
     */
    public long getPrayerDisableTimeRemaining() {
        if (!isPrayerDisabled()) {
            return 0;
        }

        long currentTime = System.currentTimeMillis();
        return Math.max(0, prayerDisableExpirationMs - currentTime);
    }

    /**
     * get time remaining on prayer disable (game ticks)
     */
    public int getPrayerDisableTicksRemaining() {
        long msRemaining = getPrayerDisableTimeRemaining();
        return (int) Math.ceil(msRemaining / 600.0); // 600ms per tick
    }

    /**
     * clear prayer disable state (used for testing or manual override)
     */
    public void clearPrayerDisable() {
        prayerDisableExpirationMs = 0;
        log.info("Cleared prayer disable state");
    }

    /**
     * check if projectile is registered as prayer-disabling
     */
    public boolean isPrayerDisableProjectile(int projectileId) {
        return prayerDisableProjectiles.containsKey(projectileId);
    }

    /**
     * check if projectile prayer mapping exists
     */
    public boolean hasProjectilePrayer(int projectileId) {
        return projectilePrayerMap.containsKey(projectileId);
    }

    /**
     * check if animation prayer mapping exists
     */
    public boolean hasAnimationPrayer(int npcId, int animationId) {
        AnimationKey key = new AnimationKey(npcId, animationId);
        return animationPrayerMap.containsKey(key);
    }

    // ============================================
    // Prayer Flicking (Zero Drain)
    // ============================================

    /**
     * enable lazy flicking mode
     * activates prayers on LAST tick before damage, deactivates on FIRST tick after
     * reduces prayer drain significantly (1 prayer point every 2-3 attacks vs constant drain)
     */
    public void enableLazyFlicking(boolean enabled) {
        this.lazyFlickingEnabled = enabled;
        if (!enabled) {
            activeFlicks.clear();
        }
        log.info("Lazy flicking: {}", enabled ? "enabled" : "disabled");
    }

    /**
     * enable 1-tick flicking mode (zero drain)
     * activates prayer for 1 tick, deactivates same tick
     * requires manual onGameTick() call for processing
     * most advanced flicking - zero prayer drain if done perfectly
     */
    public void enable1TickFlicking(boolean enabled) {
        this.oneTickFlickingEnabled = enabled;
        if (!enabled) {
            activeFlicks.clear();
        }
        log.info("1-tick flicking: {}", enabled ? "enabled" : "disabled");
    }

    /**
     * lazy flick prayer - activate on specified tick (typically tick before damage)
     * will automatically deactivate next tick
     *
     * @param prayer prayer to flick
     * @param activationTick game tick to activate prayer (absolute tick count)
     * @return true if flick scheduled successfully
     */
    public boolean scheduleLazyFlick(Rs2PrayerEnum prayer, int activationTick) {
        if (!lazyFlickingEnabled) {
            log.warn("Lazy flicking not enabled, use enableLazyFlicking(true) first");
            return false;
        }

        // track this prayer for lazy flicking
        activeFlicks.put(prayer, activationTick);
        log.debug("Scheduled lazy flick: prayer={}, activation_tick={}", prayer, activationTick);
        return true;
    }

    /**
     * schedule 1-tick flick - activate and deactivate prayer on same tick
     * requires perfect tick timing for zero drain
     *
     * @param prayer prayer to flick
     * @param flickTick game tick to perform flick (absolute tick count)
     * @return true if flick scheduled successfully
     */
    public boolean schedule1TickFlick(Rs2PrayerEnum prayer, int flickTick) {
        if (!oneTickFlickingEnabled) {
            log.warn("1-tick flicking not enabled, use enable1TickFlicking(true) first");
            return false;
        }

        // track this prayer for 1-tick flicking
        activeFlicks.put(prayer, flickTick);
        log.debug("Scheduled 1-tick flick: prayer={}, flick_tick={}", prayer, flickTick);
        return true;
    }

    /**
     * process prayer flicks for current game tick
     * MUST be called every game tick from Rs2PvMEventManager.onGameTick()
     * handles both lazy flicking and 1-tick flicking
     */
    public void processFlicks() {
        int currentTick = Microbot.getClient().getTickCount();

        if (lazyFlickingEnabled) {
            processLazyFlicks(currentTick);
        }

        if (oneTickFlickingEnabled) {
            process1TickFlicks(currentTick);
        }
    }

    /**
     * process lazy flicks for current tick
     * - activates prayers scheduled for this tick
     * - deactivates prayers that were activated last tick
     */
    private void processLazyFlicks(int currentTick) {
        // activate prayers scheduled for this tick
        activeFlicks.entrySet().stream()
            .filter(entry -> entry.getValue() == currentTick)
            .forEach(entry -> {
                Rs2PrayerEnum prayer = entry.getKey();
                if (!Rs2Prayer.isPrayerActive(prayer)) {
                    Rs2Prayer.toggle(prayer, true);
                    log.debug("Lazy flick activated: prayer={}, tick={}", prayer, currentTick);
                }
            });

        // deactivate prayers that were activated last tick
        activeFlicks.entrySet().stream()
            .filter(entry -> entry.getValue() == currentTick - 1)
            .forEach(entry -> {
                Rs2PrayerEnum prayer = entry.getKey();
                if (Rs2Prayer.isPrayerActive(prayer)) {
                    Rs2Prayer.toggle(prayer, false);
                    log.debug("Lazy flick deactivated: prayer={}, tick={}", prayer, currentTick);
                }
            });

        // cleanup old flicks (older than 2 ticks)
        activeFlicks.entrySet().removeIf(entry -> entry.getValue() < currentTick - 2);
    }

    /**
     * process 1-tick flicks for current tick
     * activates AND deactivates prayer on same tick for zero drain
     */
    private void process1TickFlicks(int currentTick) {
        // find prayers scheduled for this tick
        activeFlicks.entrySet().stream()
            .filter(entry -> entry.getValue() == currentTick)
            .forEach(entry -> {
                Rs2PrayerEnum prayer = entry.getKey();

                // activate prayer
                if (!Rs2Prayer.isPrayerActive(prayer)) {
                    Rs2Prayer.toggle(prayer, true);
                    log.debug("1-tick flick activated: prayer={}, tick={}", prayer, currentTick);
                    // not deactivated yet
                    
                }else{

                // immediately deactivate prayer (same tick)
                // note: actual game client requires specific timing within the tick
                // this implementation approximates by toggling twice rapidly
                    Rs2Prayer.toggle(prayer, false);
                    log.debug("1-tick flick deactivated: prayer={}, tick={}", prayer, currentTick);
                    Global.sleep(100, 200);// brief sleep to simulate rapid toggle
                    Rs2Prayer.toggle(prayer, true);
                }
            });

        // cleanup flicks for this tick
        activeFlicks.entrySet().removeIf(entry -> entry.getValue() <= currentTick);
    }

    /**
     * convenience method: lazy flick based on projectile impact
     * activates prayer 1 tick before projectile impacts
     *
     * @param prayer prayer to flick
     * @param projectileData projectile to flick against
     * @return true if flick scheduled successfully
     */
    public boolean lazyFlickForProjectile(Rs2PrayerEnum prayer, net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model.ProjectileData projectileData) {
        int currentTick = Microbot.getClient().getTickCount();
        int impactTick = currentTick + projectileData.getTicksUntilImpact();
        int activationTick = impactTick - 1; // activate 1 tick before impact

        return scheduleLazyFlick(prayer, activationTick);
    }

    /**
     * convenience method: 1-tick flick based on projectile impact
     * flicks prayer on exact tick of projectile impact
     *
     * @param prayer prayer to flick
     * @param projectileData projectile to flick against
     * @return true if flick scheduled successfully
     */
    public boolean oneTickFlickForProjectile(Rs2PrayerEnum prayer, net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model.ProjectileData projectileData) {
        int currentTick = Microbot.getClient().getTickCount();
        int impactTick = currentTick + projectileData.getTicksUntilImpact();

        return schedule1TickFlick(prayer, impactTick);
    }

    /**
     * check if lazy flicking is enabled
     */
    public boolean isLazyFlickingEnabled() {
        return lazyFlickingEnabled;
    }

    /**
     * check if 1-tick flicking is enabled
     */
    public boolean is1TickFlickingEnabled() {
        return oneTickFlickingEnabled;
    }

    /**
     * get count of active scheduled flicks
     */
    public int getActiveFlickCount() {
        return activeFlicks.size();
    }

    /**
     * clear all scheduled flicks
     */
    public void clearFlicks() {
        activeFlicks.clear();
        log.debug("Cleared all scheduled flicks");
    }

    /**
     * composite key for animation mappings
     */
    private static class AnimationKey {
        private final int npcId;
        private final int animationId;

        AnimationKey(int npcId, int animationId) {
            this.npcId = npcId;
            this.animationId = animationId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AnimationKey that = (AnimationKey) o;
            return npcId == that.npcId && animationId == that.animationId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(npcId, animationId);
        }
    }
}
