package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model;

import lombok.Builder;
import lombok.Value;
import net.runelite.api.Constants;
import net.runelite.client.plugins.microbot.Microbot;

/**
 * immutable data class for attack animation tracking
 * thread-safe by design (all fields final)
 */
@Value
@Builder
public class AttackAnimationData {
    int npcIndex;
    int npcId;
    int animationId;
    long timestamp;
    int gameTick;  // game tick when animation started

    /**
     * get age in milliseconds
     */
    public long getAgeMs() {
        return System.currentTimeMillis() - timestamp;
    }

    /**
     * get ticks since this animation
     */
    public int getTicksAgo() {
        int currentTick = Microbot.getClient().getTickCount();
        return currentTick - gameTick;
    }

    /**
     * get age in game ticks
     */
    public int getAgeTicks() {
        return getTicksAgo();
    }

    /**
     * get age in milliseconds from ticks (more accurate than timestamp)
     */
    public long getAgeFromTicks() {
        return getTicksAgo() * Constants.GAME_TICK_LENGTH;
    }

    /**
     * check if animation data is stale (older than threshold)
     */
    public boolean isStale(long maxAgeMs) {
        return getAgeMs() > maxAgeMs;
    }

    /**
     * check if animation data is stale (based on ticks)
     */
    public boolean isStaleTicks(int maxAgeTicks) {
        return getTicksAgo() > maxAgeTicks;
    }
}
