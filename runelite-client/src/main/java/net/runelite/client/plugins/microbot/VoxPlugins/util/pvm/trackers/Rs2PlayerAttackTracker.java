package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.trackers;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.client.plugins.microbot.Microbot;

import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * thread-safe tracker for player attack hits on bosses
 * tracks successful player hits for weapon swap timing and offensive prayer rotation
 *
 * USAGE:
 * - Hunllef: Every 6 player hits, Hunllef changes defensive prayer
 * - Zulrah: Track hits for phase transitions
 * - General PvM: Track DPS and hit patterns
 *
 * Author: Voxslyvae
 */
@Slf4j
@Singleton
public class Rs2PlayerAttackTracker {

    private static Rs2PlayerAttackTracker instance;

    // track player attack patterns by target NPC index
    private final ConcurrentHashMap<Integer, PlayerAttackPattern> activeTracking = new ConcurrentHashMap<>();

    private Rs2PlayerAttackTracker() {}

    public static synchronized Rs2PlayerAttackTracker getInstance() {
        if (instance == null) {
            instance = new Rs2PlayerAttackTracker();
        }
        return instance;
    }

    /**
     * immutable model for player attack tracking
     */
    @Data
    public static class PlayerAttackPattern {
        private final int targetNpcId;
        private final int targetNpcIndex;
        private final int hitCount; // successful hits landed
        private final int splashCount; // attacks that splashed (0 damage)
        private final int totalAttacks; // hits + splashes
        private final int cycleLength; // hits before cycle repeats (e.g., 6 for Hunllef)
        private final long lastHitTick;
        private final long lastHitTimestamp;
        private final boolean active;

        /**
         * record successful hit
         */
        public PlayerAttackPattern withHit(long currentTick) {
            int newHitCount = hitCount + 1;
            int newTotal = totalAttacks + 1;

            log.debug("Player hit registered: target_npc_index={}, hits={}/{}, total_attacks={}",
                targetNpcIndex, newHitCount, cycleLength, newTotal);

            return new PlayerAttackPattern(
                targetNpcId,
                targetNpcIndex,
                newHitCount,
                splashCount,
                newTotal,
                cycleLength,
                currentTick,
                System.currentTimeMillis(),
                active
            );
        }

        /**
         * record splash (0 damage)
         */
        public PlayerAttackPattern withSplash(long currentTick) {
            int newSplashCount = splashCount + 1;
            int newTotal = totalAttacks + 1;

            log.debug("Player splash registered: target_npc_index={}, splashes={}, total_attacks={}",
                targetNpcIndex, newSplashCount, newTotal);

            return new PlayerAttackPattern(
                targetNpcId,
                targetNpcIndex,
                hitCount,
                newSplashCount,
                newTotal,
                cycleLength,
                currentTick,
                System.currentTimeMillis(),
                active
            );
        }

        /**
         * get hits remaining until cycle completes
         */
        public int getHitsRemaining() {
            if (cycleLength == 0) return 0;
            int currentPosition = hitCount % cycleLength;
            return cycleLength - currentPosition;
        }

        /**
         * check if cycle just completed
         */
        public boolean isCycleComplete() {
            return cycleLength > 0 && hitCount % cycleLength == 0 && hitCount > 0;
        }

        /**
         * get current position in cycle (1-based)
         */
        public int getCyclePosition() {
            if (cycleLength == 0) return 0;
            int position = hitCount % cycleLength;
            return position == 0 ? cycleLength : position; // 1-indexed
        }

        /**
         * get accuracy percentage
         */
        public double getAccuracy() {
            if (totalAttacks == 0) return 0.0;
            return (double) hitCount / totalAttacks * 100.0;
        }

        /**
         * reset pattern to initial state
         */
        public PlayerAttackPattern withReset() {
            log.debug("Resetting player attack pattern for target_npc_index={}", targetNpcIndex);
            return new PlayerAttackPattern(
                targetNpcId,
                targetNpcIndex,
                0,
                0,
                0,
                cycleLength,
                Microbot.getClient().getTickCount(),
                System.currentTimeMillis(),
                true
            );
        }

        /**
         * deactivate tracking
         */
        public PlayerAttackPattern withDeactivated() {
            return new PlayerAttackPattern(
                targetNpcId,
                targetNpcIndex,
                hitCount,
                splashCount,
                totalAttacks,
                cycleLength,
                lastHitTick,
                lastHitTimestamp,
                false
            );
        }
    }

    /**
     * start tracking player attacks on specific NPC
     *
     * @param targetNpcId NPC ID to track
     * @param targetNpcIndex NPC instance index
     * @param cycleLength hits before cycle repeats (e.g., 6 for Hunllef weapon swap)
     */
    public void startTracking(int targetNpcId, int targetNpcIndex, int cycleLength) {
        PlayerAttackPattern pattern = new PlayerAttackPattern(
            targetNpcId,
            targetNpcIndex,
            0, // no hits yet
            0, // no splashes yet
            0, // no total attacks yet
            cycleLength,
            Microbot.getClient().getTickCount(),
            System.currentTimeMillis(),
            true
        );

        activeTracking.put(targetNpcIndex, pattern);

        log.info("Started tracking player attacks: target_npc_id={}, target_npc_index={}, cycle_length={}",
            targetNpcId, targetNpcIndex, cycleLength);
    }

    /**
     * start tracking Hunllef attacks (6-hit weapon swap cycle)
     */
    public void startTrackingHunllef(NPC hunllef) {
        startTracking(hunllef.getId(), hunllef.getIndex(), 6);
        log.info("Started tracking Hunllef attacks: weapon swap every 6 hits");
    }

    /**
     * process hitsplat event (called from Rs2PvMEventManager)
     */
    public void onHitsplatApplied(HitsplatApplied event) {
        Actor target = event.getActor();

        // only track hits on NPCs
        if (!(target instanceof NPC)) {
            return;
        }

        NPC npc = (NPC) target;
        int npcIndex = npc.getIndex();

        // check if we're tracking this NPC
        PlayerAttackPattern pattern = activeTracking.get(npcIndex);
        if (pattern == null || !pattern.isActive()) {
            return;
        }

        Hitsplat hitsplat = event.getHitsplat();

        // check if hitsplat is from player
        // hitsplat types: DAMAGE (player), BLOCK_ME (blocked), HEAL, etc.
        if (!hitsplat.isMine()) {
            return;
        }

        long currentTick = Microbot.getClient().getTickCount();

        // record hit or splash
        if (hitsplat.getAmount() > 0) {
            // successful hit
            PlayerAttackPattern updated = pattern.withHit(currentTick);
            activeTracking.put(npcIndex, updated);

            // check if cycle completed (e.g., 6th hit for Hunllef)
            if (updated.isCycleComplete()) {
                log.info("Player attack cycle completed: target_npc_index={}, hits={}, cycle={}",
                    npcIndex, updated.getHitCount(), updated.getCyclePosition());
            }
        } else {
            // splash (0 damage)
            PlayerAttackPattern updated = pattern.withSplash(currentTick);
            activeTracking.put(npcIndex, updated);
        }
    }

    /**
     * get current hit count for target
     */
    public int getHitCount(int npcIndex) {
        return Optional.ofNullable(activeTracking.get(npcIndex))
            .filter(PlayerAttackPattern::isActive)
            .map(PlayerAttackPattern::getHitCount)
            .orElse(0);
    }

    /**
     * get total attack count (hits + splashes)
     */
    public int getTotalAttacks(int npcIndex) {
        return Optional.ofNullable(activeTracking.get(npcIndex))
            .filter(PlayerAttackPattern::isActive)
            .map(PlayerAttackPattern::getTotalAttacks)
            .orElse(0);
    }

    /**
     * get hits remaining until cycle completes
     */
    public int getHitsRemaining(int npcIndex) {
        return Optional.ofNullable(activeTracking.get(npcIndex))
            .filter(PlayerAttackPattern::isActive)
            .map(PlayerAttackPattern::getHitsRemaining)
            .orElse(0);
    }

    /**
     * get current position in cycle (1-based)
     */
    public int getCyclePosition(int npcIndex) {
        return Optional.ofNullable(activeTracking.get(npcIndex))
            .filter(PlayerAttackPattern::isActive)
            .map(PlayerAttackPattern::getCyclePosition)
            .orElse(0);
    }

    /**
     * check if cycle just completed
     */
    public boolean isCycleComplete(int npcIndex) {
        return Optional.ofNullable(activeTracking.get(npcIndex))
            .filter(PlayerAttackPattern::isActive)
            .map(PlayerAttackPattern::isCycleComplete)
            .orElse(false);
    }

    /**
     * get accuracy percentage
     */
    public double getAccuracy(int npcIndex) {
        return Optional.ofNullable(activeTracking.get(npcIndex))
            .filter(PlayerAttackPattern::isActive)
            .map(PlayerAttackPattern::getAccuracy)
            .orElse(0.0);
    }

    /**
     * check if tracking NPC
     */
    public boolean isTracking(int npcIndex) {
        return Optional.ofNullable(activeTracking.get(npcIndex))
            .map(PlayerAttackPattern::isActive)
            .orElse(false);
    }

    /**
     * reset pattern for target (start from 0 hits)
     */
    public void resetPattern(int npcIndex) {
        PlayerAttackPattern pattern = activeTracking.get(npcIndex);
        if (pattern != null) {
            activeTracking.put(npcIndex, pattern.withReset());
            log.info("Reset player attack pattern for target_npc_index={}", npcIndex);
        }
    }

    /**
     * stop tracking target
     */
    public void stopTracking(int npcIndex) {
        PlayerAttackPattern pattern = activeTracking.get(npcIndex);
        if (pattern != null) {
            activeTracking.put(npcIndex, pattern.withDeactivated());
            log.info("Stopped tracking player attacks for target_npc_index={}", npcIndex);
        }
    }

    /**
     * remove tracking entirely
     */
    public void removeTracking(int npcIndex) {
        activeTracking.remove(npcIndex);
        log.info("Removed player attack tracking for target_npc_index={}", npcIndex);
    }

    /**
     * cleanup stale patterns (called on game tick)
     */
    public void onGameTick() {
        long currentTime = System.currentTimeMillis();

        activeTracking.entrySet().removeIf(entry -> {
            PlayerAttackPattern pattern = entry.getValue();
            long timeSinceLastHit = currentTime - pattern.getLastHitTimestamp();

            // remove patterns with no activity for 60 seconds
            if (timeSinceLastHit > 60000) {
                log.debug("Removing stale player attack pattern: target_npc_index={}, time_inactive={}ms",
                    entry.getKey(), timeSinceLastHit);
                return true;
            }

            return false;
        });
    }

    /**
     * clear all tracked patterns
     */
    public void clear() {
        activeTracking.clear();
        log.info("Cleared all player attack tracking");
    }

    /**
     * get pattern details for debugging/overlay
     */
    public Optional<PlayerAttackPattern> getPattern(int npcIndex) {
        return Optional.ofNullable(activeTracking.get(npcIndex));
    }
}
