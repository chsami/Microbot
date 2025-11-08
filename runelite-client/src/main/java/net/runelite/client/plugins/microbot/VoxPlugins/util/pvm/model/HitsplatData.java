
    
package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model;

import lombok.Builder;
import lombok.Value;
import net.runelite.api.Actor;
import net.runelite.api.Constants;
import net.runelite.client.plugins.microbot.Microbot;

/**
 * immutable data class for hitsplat tracking
 * includes correlation fields for animations and projectiles
 *
 * CORRELATION FLOW:
 * 1. Animation starts (tracked by Rs2AnimationTracker)
 * 2. Projectile spawns (tracked by Rs2ProjectileTracker)
 * 3. Hitsplat applies (tracked by Rs2HitsplatTracker with correlation)
 *
 * Author: Voxslyvae
 */
@Value
@Builder
public class HitsplatData {
    int id;
    Actor targetActor;
    boolean isPlayer;
    int amount;
    int hitsplatType;
    boolean isMine;
    boolean isOthers;
    long timestamp;
    int gameTick;

    // correlation fields (for animation/projectile synchronization)
    Integer sourceActorIndex;      // index of actor who caused hitsplat (null if unknown)
    Integer correlatedAnimationId; // animation ID that caused this hitsplat (null if unknown)
    Integer correlatedProjectileId; // projectile ID that caused this hitsplat (null if unknown)
    Integer attackStartTick;        // tick when attack animation started (null if unknown)

    /**
     * get ticks since hitsplat applied
     */
    public int getTicksAgo() {
        return Microbot.getClient().getTickCount() - gameTick;
    }

    /**
     * get age in milliseconds
     */
    public long getAgeMs() {
        return System.currentTimeMillis() - timestamp;
    }

    /**
     * get age in milliseconds from ticks
     */
    public long getAgeFromTicks() {
        return getTicksAgo() * Constants.GAME_TICK_LENGTH;
    }

    /**
     * check if hitsplat has animation correlation
     */
    public boolean hasAnimationCorrelation() {
        return correlatedAnimationId != null && correlatedAnimationId > 0;
    }

    /**
     * check if hitsplat has projectile correlation
     */
    public boolean hasProjectileCorrelation() {
        return correlatedProjectileId != null && correlatedProjectileId > 0;
    }

    /**
     * check if hitsplat has source actor
     */
    public boolean hasSourceActor() {
        return sourceActorIndex != null && sourceActorIndex >= 0;
    }

    /**
     * get ticks between attack animation and hitsplat
     * returns -1 if no animation correlation
     */
    public int getTicksBetweenAnimationAndHitsplat() {
        if (attackStartTick == null) {
            return -1;
        }
        return gameTick - attackStartTick;
    }

    /**
     * check if this hitsplat can be correlated with given animation
     * uses timing window (animations typically take 1-10 ticks to apply hitsplat)
     */
    public boolean canCorrelateWithAnimation(int animationTick, int maxTickWindow) {
        int tickDifference = gameTick - animationTick;
        return tickDifference >= 0 && tickDifference <= maxTickWindow;
    }

    /**
     * check if this hitsplat can be correlated with given projectile
     * projectiles apply hitsplat on same tick they hit
     */
    public boolean canCorrelateWithProjectile(int projectileTick) {
        return gameTick == projectileTick || gameTick == projectileTick + 1;
    }
}