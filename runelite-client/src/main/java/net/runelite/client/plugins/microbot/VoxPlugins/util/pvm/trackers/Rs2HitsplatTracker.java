package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.trackers;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model.AttackAnimationData;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model.HitsplatData;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model.ProjectileData;
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
 * thread-safe tracker for hitsplats (damage tracking)
 * extends Rs2Cache for consistent caching behavior
 * key: unique hitsplat ID (auto-incrementing)
 *
 * FILTERING: only tracks player + registered hazardous NPCs
 * CORRELATION: automatically correlates with animations and projectiles
 *
 * Author: Voxslyvae
 */
@Slf4j
@Singleton
public class Rs2HitsplatTracker extends Rs2Cache<Integer, HitsplatData> {

    private static Rs2HitsplatTracker instance;
    private final AtomicInteger hitsplatIdCounter = new AtomicInteger(0);

    @Inject
    private HazardRegistry hazardRegistry;

    @Inject
    private Rs2AnimationTracker animationTracker;

    @Inject
    private Rs2ProjectileTracker projectileTracker;

    private Rs2HitsplatTracker() {
        super("HitsplatTracker", CacheMode.EVENT_DRIVEN_ONLY);
    }

    public static synchronized Rs2HitsplatTracker getInstance() {
        if (instance == null) {
            instance = new Rs2HitsplatTracker();
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
     * handle hitsplat applied event (from Rs2PvMEventManager)
     * ONLY tracks player + registered hazardous NPCs
     * CORRELATES with recent animations and projectiles
     */
    public void onHitsplatApplied(HitsplatApplied event) {
        Actor target = event.getActor();
        Hitsplat hitsplat = event.getHitsplat();

        // determine if player was hit
        boolean isPlayer = target.equals(Microbot.getClient().getLocalPlayer());

        // check if NPC is registered as hazardous
        boolean isHazardousNpc = false;
        if (target instanceof NPC && !isPlayer) {
            NPC npc = (NPC) target;
            isHazardousNpc = hazardRegistry != null && hazardRegistry.isHazardousNpc(npc.getId());
        }

        // filter: only track player or registered hazardous NPCs
        if (!isPlayer && !isHazardousNpc) {
            return;
        }

        int currentTick = Microbot.getClient().getTickCount();

        // CORRELATION: find matching animation (within last 10 ticks)
        Integer correlatedAnimationId = null;
        Integer sourceActorIndex = null;
        Integer attackStartTick = null;

        if (animationTracker != null && target instanceof NPC) {
            NPC targetNpc = (NPC) target;
            Optional<AttackAnimationData> recentAnimation = animationTracker.getLastAttackAnimation(targetNpc.getIndex());

            if (recentAnimation.isPresent()) {
                AttackAnimationData animData = recentAnimation.get();
                int ticksSinceAnim = currentTick - animData.getGameTick();

                // animations typically take 1-10 ticks to apply hitsplat
                if (ticksSinceAnim >= 0 && ticksSinceAnim <= 10) {
                    correlatedAnimationId = animData.getAnimationId();
                    sourceActorIndex = animData.getNpcIndex();
                    attackStartTick = animData.getGameTick();

                    log.debug("Correlated hitsplat with animation: anim_id={}, ticks_ago={}",
                        correlatedAnimationId, ticksSinceAnim);
                }
            }
        }

        // CORRELATION: find matching projectile (same tick or 1 tick ago)
        Integer correlatedProjectileId = null;

        if (projectileTracker != null) {
            Optional<ProjectileData> recentProjectile = projectileTracker.stream()
                .filter(proj -> proj.getTargetActor() == target)
                .filter(proj -> {
                    int projTick = proj.getStartGameTick();
                    return currentTick == projTick || currentTick == projTick + 1;
                })
                .findFirst();

            if (recentProjectile.isPresent()) {
                ProjectileData projData = recentProjectile.get();
                correlatedProjectileId = projData.getId();

                // projectiles don't have source actor in this model
                // correlation is based on target matching

                log.debug("Correlated hitsplat with projectile: proj_id={}, tick_diff={}",
                    correlatedProjectileId, currentTick - projData.getStartGameTick());
            }
        }

        // create immutable hitsplat data with correlation fields
        int uniqueId = hitsplatIdCounter.incrementAndGet();
        HitsplatData data = HitsplatData.builder()
            .id(uniqueId)
            .targetActor(target)
            .isPlayer(isPlayer)
            .amount(hitsplat.getAmount())
            .hitsplatType(hitsplat.getHitsplatType())
            .isMine(hitsplat.isMine())
            .isOthers(hitsplat.isOthers())
            .timestamp(System.currentTimeMillis())
            .gameTick(currentTick)
            // correlation fields
            .sourceActorIndex(sourceActorIndex)
            .correlatedAnimationId(correlatedAnimationId)
            .correlatedProjectileId(correlatedProjectileId)
            .attackStartTick(attackStartTick)
            .build();

        put(uniqueId, data);

        if (data.hasAnimationCorrelation() || data.hasProjectileCorrelation()) {
            log.debug("Tracked hitsplat WITH CORRELATION: id={}, target={}, amount={}, anim={}, proj={}, source_actor={}, tick={}",
                uniqueId, target.getName(), data.getAmount(), correlatedAnimationId, correlatedProjectileId, sourceActorIndex, currentTick);
        } else {
            log.debug("Tracked hitsplat: id={}, target={}, is_player={}, amount={}, type={}, tick={}",
                uniqueId, target.getName(), isPlayer, data.getAmount(), data.getHitsplatType(), currentTick);
        }
    }

    /**
     * cleanup old hitsplat data (called every game tick)
     */
    public void onGameTick() {
        // collect hitsplats older than 10 ticks for removal
        List<Integer> toRemove = new ArrayList<>();
        entryStream().forEach(entry -> {
            HitsplatData data = entry.getValue();
            if (data.getTicksAgo() > 10) {
                toRemove.add(entry.getKey());
                log.debug("Removing old hitsplat: id={}, age_ticks={}", entry.getKey(), data.getTicksAgo());
            }
        });

        // remove old entries
        toRemove.forEach(this::remove);

        if (!toRemove.isEmpty()) {
            log.debug("Cleaned up {} old hitsplats", toRemove.size());
        }
    }

    /**
     * get all hitsplats on player
     */
    public List<HitsplatData> getPlayerHitsplats() {
        return stream()
            .filter(HitsplatData::isPlayer)
            .collect(Collectors.toList());
    }

    /**
     * get recent hitsplats on player (within tickThreshold)
     */
    public List<HitsplatData> getRecentPlayerHitsplats(int tickThreshold) {
        return stream()
            .filter(data -> data.isPlayer() && data.getTicksAgo() <= tickThreshold)
            .collect(Collectors.toList());
    }

    /**
     * get total damage taken by player in last N ticks
     */
    public int getPlayerDamageTaken(int tickThreshold) {
        return getRecentPlayerHitsplats(tickThreshold).stream()
            .mapToInt(HitsplatData::getAmount)
            .sum();
    }

    /**
     * get all hitsplats by actor index
     */
    public List<HitsplatData> getHitsplatsByActor(Actor actor) {
        return stream()
            .filter(data -> data.getTargetActor() == actor)
            .collect(Collectors.toList());
    }

    /**
     * get recent hitsplats by actor (within tickThreshold)
     */
    public List<HitsplatData> getRecentHitsplatsByActor(Actor actor, int tickThreshold) {
        return stream()
            .filter(data -> data.getTargetActor() == actor && data.getTicksAgo() <= tickThreshold)
            .collect(Collectors.toList());
    }

    /**
     * check if player was hit recently (within tickThreshold)
     */
    public boolean wasPlayerHitRecently(int tickThreshold) {
        return stream()
            .anyMatch(data -> data.isPlayer() && data.getTicksAgo() <= tickThreshold);
    }

}
