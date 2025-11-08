package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model;

import lombok.Builder;
import lombok.Value;
import net.runelite.api.Constants;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;

/**
 * immutable data class for hazard tracking (tornadoes, floor tiles, etc.)
 * thread-safe by design (all fields final)
 */
@Value
@Builder(toBuilder = true)
public class HazardData {
    int id;                     // unique tracking id
    int gameId;                 // object/graphic/npc id from game
    WorldPoint location;
    Tile tile;
    HazardSource source;        // where this hazard came from
    long timestamp;
    int gameTick;               // game tick when hazard spawned
    int radius;                 // tiles affected around center point
    long customTtlMs;           // custom ttl in milliseconds (0 = use source default)
    int npcIndex;               // npc index for moving hazards (0 if not npc)
    MovingHazard movingHazard;  // trajectory prediction for moving hazards (null if static)

    /**
     * get age in milliseconds
     */
    public long getAgeMs() {
        return System.currentTimeMillis() - timestamp;
    }

    /**
     * get ticks since this hazard spawned
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
     * check if hazard is still active (not expired based on ttl)
     * uses custom ttl if set, otherwise source default
     */
    public boolean isActive() {
        long effectiveTtl = customTtlMs > 0 ? customTtlMs : source.getDefaultTtlMs();
        // 0 ttl = permanent until despawn event
        return effectiveTtl == 0 || getAgeMs() < effectiveTtl;
    }

    /**
     * check if location is within hazard radius
     */
    public boolean isLocationDangerous(WorldPoint point) {
        if (point == null) return false;
        return location.distanceTo(point) <= radius;
    }

    /**
     * check if this is a graphics object hazard
     */
    public boolean isGraphic() {
        return source == HazardSource.GRAPHICS_OBJECT;
    }

    /**
     * check if this is a game object hazard
     */
    public boolean isGameObject() {
        return source == HazardSource.GAME_OBJECT;
    }

    /**
     * check if this is an npc hazard
     */
    public boolean isNpc() {
        return source == HazardSource.NPC;
    }

    /**
     * check if this is a moving hazard (has trajectory prediction)
     */
    public boolean isMoving() {
        return movingHazard != null;
    }

    /**
     * get predicted position at specific tick
     * returns current location if not a moving hazard
     */
    public WorldPoint getPredictedLocation(int tick) {
        if (movingHazard == null) {
            return location;
        }
        return movingHazard.getPredictedPosition(tick);
    }

    /**
     * check if location will be dangerous at specific tick
     * considers predicted position for moving hazards
     */
    public boolean willBeDangerousAt(WorldPoint point, int tick) {
        if (point == null) return false;

        WorldPoint predictedLoc = getPredictedLocation(tick);
        return predictedLoc.distanceTo(point) <= radius;
    }

    /**
     * hazard source types with default ttl values
     * graphics objects should use custom ttl set by plugin
     */
    public enum HazardSource {
        GAME_OBJECT(0),        // permanent until despawn event
        GRAPHICS_OBJECT(0),    // must be set by plugin when registering!
        NPC(0);                // permanent until despawn event

        private final long defaultTtlMs;

        HazardSource(long defaultTtlMs) {
            this.defaultTtlMs = defaultTtlMs;
        }

        public long getDefaultTtlMs() {
            return defaultTtlMs;
        }
    }
}
