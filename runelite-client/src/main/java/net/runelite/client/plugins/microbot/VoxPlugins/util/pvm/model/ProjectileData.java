package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model;

import lombok.Builder;
import lombok.Value;
import net.runelite.api.Actor;
import net.runelite.api.Constants;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;

/**
 * immutable data class for projectile tracking with real-time position updates
 * thread-safe by design (all fields final)
 * 
 * position tracking: currentX/Y/Z updated via ProjectileMoved events for precise dodging
 */
@Value
@Builder
public class ProjectileData {
    int instanceId;          // unique instance ID (assigned by tracker)
    int id;                  // projectile graphics ID (not unique!)
    int spotanimId;
    LocalPoint startPoint;
    LocalPoint targetPoint;
    int startCycle;
    int endCycle;
    int startHeight;
    int endHeight;
    int slope;
    long timestamp;
    int startGameTick;           // game tick when projectile spawned
    boolean targetingPlayer;
    boolean isAoe;               // true if no specific target (null actor)
    Actor targetActor;           // the target actor, null if AOE
    
    // dynamic position tracking (updated via ProjectileMoved events)
    double currentX;             // current x scene coordinate
    double currentY;             // current y scene coordinate
    double currentZ;             // current z scene coordinate (height)
    int lastPositionUpdateTick;  // game tick when position was last updated

    /**
     * create from RuneLite projectile with unique instance ID
     */
    public static ProjectileData fromProjectile(Projectile projectile, int instanceId) {
        Actor target = projectile.getTargetActor();

        // convert source WorldPoint to LocalPoint for consistency
        WorldPoint sourceWorld = projectile.getSourcePoint();
        LocalPoint sourceLocal = LocalPoint.fromWorld(
            Microbot.getClient().getTopLevelWorldView(),
            sourceWorld.getX(),
            sourceWorld.getY()
        );

        // convert target WorldPoint to LocalPoint
        WorldPoint targetWorld = projectile.getTargetPoint();
        LocalPoint targetLocal = LocalPoint.fromWorld(
            Microbot.getClient().getTopLevelWorldView(),
            targetWorld.getX(),
            targetWorld.getY()
        );
        boolean targetingPlayer = (target != null && target instanceof Player) ? ((Player)target) ==  Microbot.getClient().getLocalPlayer(): false;

        return ProjectileData.builder()
            .instanceId(instanceId)
            .id(projectile.getId())
            .spotanimId(projectile.getId())
            .startPoint(sourceLocal != null ? sourceLocal : 
                LocalPoint.fromScene(0, 0, Microbot.getClient().getTopLevelWorldView().getScene()))
            .targetPoint(targetLocal != null ? targetLocal : 
                LocalPoint.fromScene(0, 0, Microbot.getClient().getTopLevelWorldView().getScene()))
            .startCycle(projectile.getStartCycle())
            .endCycle(projectile.getEndCycle())
            .startHeight(projectile.getStartHeight())
            .endHeight(projectile.getEndHeight())
            .slope(projectile.getSlope())
            .timestamp(System.currentTimeMillis())
            .startGameTick(Microbot.getClient().getTickCount())
            .targetingPlayer(targetingPlayer)
            .isAoe(target == null)
            .targetActor(target)
            // initialize current position from projectile
            .currentX(projectile.getX())
            .currentY(projectile.getY())
            .currentZ(projectile.getZ())
            .lastPositionUpdateTick(Microbot.getClient().getTickCount())
            .build();
    }

    /**
     * get remaining cycles until impact
     */
    public int getRemainingCycles() {
        int currentCycle = Microbot.getClient().getGameCycle();
        return Math.max(0, endCycle - currentCycle);
    }

    /**
     * get remaining ticks until impact (approximation from cycles)
     */
    public int getTicksUntilImpact() {
        int currentTick = Microbot.getClient().getTickCount();
        int elapsedTicks = currentTick - startGameTick;
        int cyclesPerTick = 30; // approximate game cycles per tick
        int estimatedTotalTicks = (endCycle - startCycle) / cyclesPerTick;
        return Math.max(0, estimatedTotalTicks - elapsedTicks);
    }

    /**
     * get age in milliseconds
     */
    public long getAgeMs() {
        return System.currentTimeMillis() - timestamp;
    }

    /**
     * get ticks since projectile spawned
     */
    public int getTicksAgo() {
        int currentTick = Microbot.getClient().getTickCount();
        return currentTick - startGameTick;
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
     * check if projectile has expired (past impact time)
     */
    public boolean isExpired() {
        return getRemainingCycles() <= 0;
    }

    /**
     * check if projectile is about to impact (within buffer ticks)
     */
    public boolean isAboutToImpact(int tickBuffer) {
        return getTicksUntilImpact() <= tickBuffer;
    }

    /**
     * get current position as LocalPoint (from scene coordinates)
     */
    public LocalPoint getCurrentPosition() {
        // convert scene coordinates to local point
        return LocalPoint.fromScene((int) currentX, (int) currentY, 
            Microbot.getClient().getTopLevelWorldView().getScene());
    }

    /**
     * get distance from specific WorldPoint using current position
     */
    public int getDistanceFrom(WorldPoint point) {
        if (point == null) {
            return Integer.MAX_VALUE;
        }
        
        WorldPoint current = WorldPoint.fromLocal(
            Microbot.getClient(),
            getCurrentPosition()
        );
        
        return current != null ? current.distanceTo(point) : Integer.MAX_VALUE;
    }

    /**
     * get distance from player using current position
     */
    public int getDistanceFromPlayer() {
        WorldPoint playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
        return getDistanceFrom(playerLocation);
    }

    /**
     * check if position has been updated recently (within 1 tick)
     */
    public boolean hasRecentPositionUpdate() {
        int currentTick = Microbot.getClient().getTickCount();
        return (currentTick - lastPositionUpdateTick) <= 1;
    }

    /**
     * get target location as WorldPoint
     */
    public WorldPoint getTargetWorldPoint() {
        return WorldPoint.fromLocal(
            Microbot.getClient(),
            targetPoint
        );
    }
}
