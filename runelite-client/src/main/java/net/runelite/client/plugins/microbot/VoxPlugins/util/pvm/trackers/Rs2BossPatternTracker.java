package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.trackers;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.Prayer;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * thread-safe tracker for boss attack cycle patterns
 * tracks cyclical attack patterns (e.g., every N attacks, prayer/mechanic changes)
 *
 * USAGE:
 * - Hunllef: Every 4 attacks, offensive prayer rotates (Range → Mage → Range → Mage)
 * - Zulrah: Phase-based attack patterns
 * - Olm: Hand attack cycles
 *
 * Author: Voxslyvae
 */
@Slf4j
@Singleton
public class Rs2BossPatternTracker {

    private static Rs2BossPatternTracker instance;

    // track active boss patterns by NPC index
    private final ConcurrentHashMap<Integer, BossCyclePattern> activePatterns = new ConcurrentHashMap<>();

    private Rs2BossPatternTracker() {}

    public static synchronized Rs2BossPatternTracker getInstance() {
        if (instance == null) {
            instance = new Rs2BossPatternTracker();
        }
        return instance;
    }

    /**
     * immutable model for boss attack cycle patterns
     */
    @Data
    public static class BossCyclePattern {
        private final int npcId;
        private final int npcIndex;
        private final int cycleLength; // attacks before pattern repeats
        private final int currentAttack; // current position in cycle (0-based)
        private final int totalAttacks; // total attacks tracked
        private final List<Integer> excludedAnimations; // animations that don't count (stomp, etc)
        private final long lastAttackTick;
        private final Rs2PrayerEnum[] prayerRotation; // prayer sequence for cycle
        private final boolean active;

        /**
         * increment attack count, respecting cycle length
         */
        public BossCyclePattern withAttack(int animationId, long currentTick) {
            // check if this animation should be excluded
            if (excludedAnimations != null && excludedAnimations.contains(animationId)) {
                log.debug("Excluded animation {} from boss pattern cycle", animationId);
                return this;
            }

            int newAttack = (currentAttack + 1) % cycleLength;
            int newTotal = totalAttacks + 1;

            log.debug("Boss attack registered: cycle_position={}/{}, total_attacks={}, animation={}",
                newAttack + 1, cycleLength, newTotal, animationId);

            return new BossCyclePattern(
                npcId,
                npcIndex,
                cycleLength,
                newAttack,
                newTotal,
                excludedAnimations,
                currentTick,
                prayerRotation,
                active
            );
        }

        /**
         * get current prayer based on cycle position
         */
        public Rs2PrayerEnum getCurrentPrayer() {
            if (prayerRotation == null || prayerRotation.length == 0) {
                return null;
            }

            int index = currentAttack % prayerRotation.length;
            return prayerRotation[index];
        }

        /**
         * get next prayer in rotation
         */
        public Rs2PrayerEnum getNextPrayer() {
            if (prayerRotation == null || prayerRotation.length == 0) {
                return null;
            }

            int nextIndex = (currentAttack + 1) % prayerRotation.length;
            return prayerRotation[nextIndex];
        }

        /**
         * get attacks remaining until next prayer switch
         */
        public int getAttacksUntilSwitch() {
            if (prayerRotation == null || prayerRotation.length == 0) {
                return 0;
            }

            // attacks until next prayer change
            // for 2-prayer rotation (Range/Mage), switch every attack
            // for 4-prayer rotation, depends on pattern
            int attacksPerPrayer = cycleLength / prayerRotation.length;
            int positionInCurrentPrayer = currentAttack % attacksPerPrayer;
            return attacksPerPrayer - positionInCurrentPrayer;
        }

        /**
         * check if cycle just completed
         */
        public boolean isCycleComplete() {
            return currentAttack == 0 && totalAttacks > 0;
        }

        /**
         * reset pattern to initial state
         */
        public BossCyclePattern withReset() {
            log.debug("Resetting boss pattern for npc_index={}", npcIndex);
            return new BossCyclePattern(
                npcId,
                npcIndex,
                cycleLength,
                0,
                0,
                excludedAnimations,
                Microbot.getClient().getTickCount(),
                prayerRotation,
                true
            );
        }

        /**
         * deactivate pattern tracking
         */
        public BossCyclePattern withDeactivated() {
            return new BossCyclePattern(
                npcId,
                npcIndex,
                cycleLength,
                currentAttack,
                totalAttacks,
                excludedAnimations,
                lastAttackTick,
                prayerRotation,
                false
            );
        }
    }

    /**
     * register a new boss pattern
     *
     * @param npcId NPC ID to track
     * @param npcIndex NPC instance index
     * @param cycleLength number of attacks before pattern repeats
     * @param prayerRotation prayer sequence (e.g., [PROTECT_FROM_MISSILES, PROTECT_FROM_MAGIC])
     * @param excludedAnimations animations to exclude from count (stomp, phase change, etc)
     */
    public void registerPattern(int npcId, int npcIndex, int cycleLength,
                               Rs2PrayerEnum[] prayerRotation, List<Integer> excludedAnimations) {

        BossCyclePattern pattern = new BossCyclePattern(
            npcId,
            npcIndex,
            cycleLength,
            0, // start at attack 0
            0, // no attacks yet
            excludedAnimations,
            Microbot.getClient().getTickCount(),
            prayerRotation,
            true
        );

        activePatterns.put(npcIndex, pattern);

        log.info("Registered boss pattern: npc_id={}, npc_index={}, cycle_length={}, prayer_rotation={}",
            npcId, npcIndex, cycleLength,
            prayerRotation != null ? prayerRotation.length + " prayers" : "none");
    }

    /**
     * register Hunllef-specific pattern (4 attacks, Range → Mage rotation, starts with Range)
     */
    public void registerHunllefPattern(NPC hunllef, List<Integer> excludedAnimations) {
        Rs2PrayerEnum[] hunllefRotation = {
            Rs2PrayerEnum.PROTECT_RANGE, // attack 1-2
            Rs2PrayerEnum.PROTECT_RANGE,
            Rs2PrayerEnum.PROTECT_MAGIC,     // attack 3-4
            Rs2PrayerEnum.PROTECT_MAGIC
        };

        registerPattern(
            hunllef.getId(),
            hunllef.getIndex(),
            4, // 4 attacks per cycle
            hunllefRotation,
            excludedAnimations
        );

        log.info("Registered Hunllef pattern: starts with PROTECT_FROM_MISSILES");
    }

    /**
     * record boss attack (increments cycle position)
     */
    public void recordAttack(int npcIndex, int animationId) {
        BossCyclePattern pattern = activePatterns.get(npcIndex);
        if (pattern == null || !pattern.isActive()) {
            log.trace("No active pattern for npc_index={}", npcIndex);
            return;
        }

        long currentTick = Microbot.getClient().getTickCount();
        BossCyclePattern updated = pattern.withAttack(animationId, currentTick);
        activePatterns.put(npcIndex, updated);

        log.debug("Boss attack recorded: npc_index={}, cycle_position={}/{}, next_prayer={}",
            npcIndex, updated.getCurrentAttack() + 1, updated.getCycleLength(), updated.getNextPrayer());
    }

    /**
     * get current prayer for boss
     */
    public Optional<Rs2PrayerEnum> getCurrentPrayer(int npcIndex) {
        return Optional.ofNullable(activePatterns.get(npcIndex))
            .filter(BossCyclePattern::isActive)
            .map(BossCyclePattern::getCurrentPrayer);
    }

    /**
     * get next prayer in rotation
     */
    public Optional<Rs2PrayerEnum> getNextPrayer(int npcIndex) {
        return Optional.ofNullable(activePatterns.get(npcIndex))
            .filter(BossCyclePattern::isActive)
            .map(BossCyclePattern::getNextPrayer);
    }

    /**
     * get attacks until next prayer switch
     */
    public int getAttacksUntilSwitch(int npcIndex) {
        return Optional.ofNullable(activePatterns.get(npcIndex))
            .filter(BossCyclePattern::isActive)
            .map(BossCyclePattern::getAttacksUntilSwitch)
            .orElse(0);
    }

    /**
     * get current attack position in cycle
     */
    public int getCurrentAttackPosition(int npcIndex) {
        return Optional.ofNullable(activePatterns.get(npcIndex))
            .filter(BossCyclePattern::isActive)
            .map(pattern -> pattern.getCurrentAttack() + 1) // 1-based for display
            .orElse(0);
    }

    /**
     * get total boss attacks tracked
     */
    public int getTotalAttacks(int npcIndex) {
        return Optional.ofNullable(activePatterns.get(npcIndex))
            .filter(BossCyclePattern::isActive)
            .map(BossCyclePattern::getTotalAttacks)
            .orElse(0);
    }

    /**
     * check if boss has active pattern
     */
    public boolean isTrackingBoss(int npcIndex) {
        return Optional.ofNullable(activePatterns.get(npcIndex))
            .map(BossCyclePattern::isActive)
            .orElse(false);
    }

    /**
     * reset pattern for boss (start from attack 0)
     */
    public void resetPattern(int npcIndex) {
        BossCyclePattern pattern = activePatterns.get(npcIndex);
        if (pattern != null) {
            activePatterns.put(npcIndex, pattern.withReset());
            log.info("Reset boss pattern for npc_index={}", npcIndex);
        }
    }

    /**
     * stop tracking boss pattern
     */
    public void stopTracking(int npcIndex) {
        BossCyclePattern pattern = activePatterns.get(npcIndex);
        if (pattern != null) {
            activePatterns.put(npcIndex, pattern.withDeactivated());
            log.info("Stopped tracking boss pattern for npc_index={}", npcIndex);
        }
    }

    /**
     * remove boss pattern entirely
     */
    public void removePattern(int npcIndex) {
        activePatterns.remove(npcIndex);
        log.info("Removed boss pattern for npc_index={}", npcIndex);
    }

    /**
     * cleanup stale patterns (called on game tick)
     */
    public void onGameTick() {
        long currentTick = Microbot.getClient().getTickCount();

        activePatterns.entrySet().removeIf(entry -> {
            BossCyclePattern pattern = entry.getValue();
            long ticksSinceLastAttack = currentTick - pattern.getLastAttackTick();

            // remove patterns with no activity for 100 ticks (~60 seconds)
            if (ticksSinceLastAttack > 100) {
                log.debug("Removing stale boss pattern: npc_index={}, ticks_inactive={}",
                    entry.getKey(), ticksSinceLastAttack);
                return true;
            }

            return false;
        });
    }

    /**
     * clear all tracked patterns
     */
    public void clear() {
        activePatterns.clear();
        log.info("Cleared all boss attack patterns");
    }

    /**
     * get pattern details for debugging/overlay
     */
    public Optional<BossCyclePattern> getPattern(int npcIndex) {
        return Optional.ofNullable(activePatterns.get(npcIndex));
    }
}
