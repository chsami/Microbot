package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.trackers;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Animation;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.PostAnimation;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model.AttackAnimationData;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.registry.HazardRegistry;
import net.runelite.client.plugins.microbot.util.cache.CacheMode;
import net.runelite.client.plugins.microbot.util.cache.Rs2Cache;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * thread-safe tracker for NPC attack animations
 * extends Rs2Cache for consistent caching behavior
 * key: npcIndex (unique per NPC instance)
 * 
 * FILTERING: only tracks player + registered hazardous NPCs
 */
@Slf4j
@Singleton
public class Rs2AnimationTracker extends Rs2Cache<Integer, AttackAnimationData> {

    private static Rs2AnimationTracker instance;

    @Inject
    private HazardRegistry hazardRegistry;

    // track current stance for NPCs (for Hunllef stance tracking)
    private final ConcurrentHashMap<Integer, Integer> npcStanceAnimations = new ConcurrentHashMap<>();

    private Rs2AnimationTracker() {
        super("AnimationTracker", CacheMode.EVENT_DRIVEN_ONLY);
    }

    public static synchronized Rs2AnimationTracker getInstance() {
        if (instance == null) {
            instance = new Rs2AnimationTracker();
        }
        return instance;
    }

    /**
     * handle animation changed event (from Rs2PvMEventManager)
     * ONLY tracks player + registered hazardous NPCs
     */
    public void onAnimationChanged(AnimationChanged event) {
        Actor actor = event.getActor();
        boolean isPlayer = false;
        // always track player animations
        if (actor instanceof Player){
            Player playerActor = (Player) actor;
            isPlayer= playerActor.equals(Microbot.getClient().getLocalPlayer());
        }

        // check if NPC is registered as hazardous
        boolean isHazardousNpc = false;
        int npcId = -1;
        int npcIndex = -1;

        if (actor instanceof NPC && !isPlayer) {
            NPC npc = (NPC) actor;
            npcId = npc.getId();
            npcIndex = npc.getIndex();

            // filter: only track if registered in hazard registry
            isHazardousNpc = hazardRegistry != null && hazardRegistry.isHazardousNpc(npcId);
        }

        // skip if not player and not registered hazardous NPC
        if (!isPlayer && !isHazardousNpc) {
            return;
        }

        int animationId = actor.getAnimation();

        // ignore idle animations (-1)
        if (animationId == -1) return;

        // create immutable data snapshot
        AttackAnimationData data = AttackAnimationData.builder()
            .npcIndex(isPlayer ? -1 : npcIndex)
            .npcId(isPlayer ? -1 : npcId)
            .animationId(animationId)
            .timestamp(System.currentTimeMillis())
            .gameTick(Microbot.getClient().getTickCount())
            .build();

        // store by npc index (use -1 for player)
        int key = isPlayer ? -1 : npcIndex;
        put(key, data);

        log.debug("Tracked animation: target={}, npc_index={}, npc_id={}, animation_id={}, tick={}",
            isPlayer ? "PLAYER" : "NPC", npcIndex, npcId, animationId, data.getGameTick());
    }

    /**
     * handle post animation event (fires after animation fully loaded)
     * more reliable than AnimationChanged for attack detection
     * PostAnimation provides the Animation object with full details
     */
    public void onPostAnimation(PostAnimation event) {
        if (event == null || event.getAnimation() == null) return;

        Animation animation = event.getAnimation();
        int animationId = animation.getId();

        // PostAnimation doesn't provide actor context directly
        // this event is mainly useful for verifying animation IDs are fully loaded
        // actual tracking happens in onAnimationChanged

        log.trace("PostAnimation event: animation_id={}", animationId);
    }

    /**
     * register stance change animation (for Hunllef mage/range switching)
     */
    public void registerStanceChange(int npcIndex, int stanceAnimationId) {
        npcStanceAnimations.put(npcIndex, stanceAnimationId);
        log.debug("Registered stance change for npc_index={}: stance_animation={}", npcIndex, stanceAnimationId);
    }

    /**
     * get current stance animation for NPC
     */
    public Optional<Integer> getCurrentStance(int npcIndex) {
        return Optional.ofNullable(npcStanceAnimations.get(npcIndex));
    }

    /**
     * cleanup stale animations (called every game tick)
     */
    public void onGameTick() {
        // collect stale animations for removal
        List<Integer> toRemove = new ArrayList<>();
        entryStream().forEach(entry -> {
            AttackAnimationData data = entry.getValue();
            if (data.isStaleTicks(10)) {
                toRemove.add(entry.getKey());
                log.debug("Removing stale animation: npc_index={}, animation_id={}, age_ticks={}",
                    entry.getKey(), data.getAnimationId(), data.getTicksAgo());
                // also remove stance tracking
                npcStanceAnimations.remove(entry.getKey());
            }
        });

        // remove stale entries
        toRemove.forEach(this::remove);

        if (!toRemove.isEmpty()) {
            log.debug("Cleaned up {} stale animations", toRemove.size());
        }
    }

    /**
     * get last attack animation for NPC by index
     */
    public Optional<AttackAnimationData> getLastAttackAnimation(int npcIndex) {
        return Optional.ofNullable(get(npcIndex));
    }

    /**
     * get last attack animation for NPC by NPC object
     */
    public Optional<AttackAnimationData> getLastAttackAnimation(NPC npc) {
        return getLastAttackAnimation(npc.getIndex());
    }

    /**
     * get all animations for specific NPC ID
     */
    public List<AttackAnimationData> getAnimationsByNpcId(int npcId) {
        return stream()
            .filter(data -> data.getNpcId() == npcId)
            .collect(Collectors.toList());
    }

    /**
     * get all recent animations (within tickThreshold)
     */
    public List<AttackAnimationData> getRecentAnimations(int tickThreshold) {
        return stream()
            .filter(data -> data.getTicksAgo() <= tickThreshold)
            .collect(Collectors.toList());
    }

    /**
     * check if NPC has performed specific animation recently
     */
    public boolean hasPerformedAnimation(int npcIndex, int animationId, int tickThreshold) {
        return getLastAttackAnimation(npcIndex)
            .map(data -> data.getAnimationId() == animationId && data.getTicksAgo() <= tickThreshold)
            .orElse(false);
    }
    /**
     * clear all tracked animations and stance data
     */

    public void clear() {
        invalidateAll();
        npcStanceAnimations.clear();
        log.debug("Cleared all animations and stance tracking");
    }
    public void update() {
        
    }

}
