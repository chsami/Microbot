package net.runelite.client.plugins.microbot.util.walker.recovery;

import net.runelite.api.coords.WorldPoint;

import java.util.Objects;

/**
 * Counts consecutive {@code recovery_target_walled} replans issued from the same player tile toward
 * the same walled target. A replan that returns the same walled target from the same tile is not
 * making progress (live bug #45: a decorative ladder platform at 2665,3334,p1 replanned for minutes),
 * so once the streak reaches the limit the caller blacklists the edge for the session instead of
 * replanning again.
 *
 * <p>Pure: no client state, safe to unit-test. Not thread-safe; owned by the walker thread.
 */
public final class WalledReplanLedger {

    public static final int DEFAULT_STRIKE_LIMIT = 5;

    private WorldPoint lastPlayer;
    private WorldPoint lastTarget;
    private int streak;

    /**
     * Records one walled replan.
     *
     * @return {@code true} when this record reaches {@code strikeLimit} consecutive replans for the
     * same (player tile, target) pair; the streak is then reset so the caller blacklists once.
     */
    public boolean record(WorldPoint player, WorldPoint target, int strikeLimit) {
        if (player == null || target == null) {
            reset();
            return false;
        }
        if (Objects.equals(player, lastPlayer) && Objects.equals(target, lastTarget)) {
            streak++;
        } else {
            lastPlayer = player;
            lastTarget = target;
            streak = 1;
        }
        if (streak >= Math.max(1, strikeLimit)) {
            reset();
            return true;
        }
        return false;
    }

    public int streak() {
        return streak;
    }

    public void reset() {
        lastPlayer = null;
        lastTarget = null;
        streak = 0;
    }
}
