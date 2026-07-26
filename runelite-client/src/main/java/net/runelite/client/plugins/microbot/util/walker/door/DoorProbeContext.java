package net.runelite.client.plugins.microbot.util.walker.door;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldPoint;

/**
 * The scan-scoped door-probe caches captured once per {@code handleNearbyRawPathSceneObjects} pass,
 * grouped into one object so the door-probe logic can move out of {@code Rs2Walker} while the walker
 * facade retains ownership of the state.
 * <p>
 * All fields are nullable: a {@link #EMPTY} context (used outside a scan) means "no snapshot, fall
 * back to live queries", which every consumer already handles. The map fields are shared references
 * to the walker's live caches, so writes (composition/segment memoisation) persist through them.
 */
public final class DoorProbeContext {

    /** No snapshot — consumers fall back to live scene queries. */
    public static final DoorProbeContext EMPTY = new DoorProbeContext(null, null, null, null, null);

    private final List<WallObject> wallSnapshot;
    private final List<GameObject> gameObjectSnapshot;
    private final Map<TileObject, WorldPoint> locationSnapshot;
    private final Map<TileObject, Optional<ObjectComposition>> compositionCache;
    private final Map<String, Optional<TileObject>> segmentCache;

    public DoorProbeContext(List<WallObject> wallSnapshot,
                            List<GameObject> gameObjectSnapshot,
                            Map<TileObject, WorldPoint> locationSnapshot,
                            Map<TileObject, Optional<ObjectComposition>> compositionCache,
                            Map<String, Optional<TileObject>> segmentCache) {
        this.wallSnapshot = wallSnapshot;
        this.gameObjectSnapshot = gameObjectSnapshot;
        this.locationSnapshot = locationSnapshot;
        this.compositionCache = compositionCache;
        this.segmentCache = segmentCache;
    }

    public List<WallObject> wallSnapshot() {
        return wallSnapshot;
    }

    public List<GameObject> gameObjectSnapshot() {
        return gameObjectSnapshot;
    }

    public Map<TileObject, WorldPoint> locationSnapshot() {
        return locationSnapshot;
    }

    public Map<TileObject, Optional<ObjectComposition>> compositionCache() {
        return compositionCache;
    }

    public Map<String, Optional<TileObject>> segmentCache() {
        return segmentCache;
    }

    /** True when a wall/game-object snapshot was captured (vs. live-query fallback). */
    public boolean hasSnapshot() {
        return wallSnapshot != null || gameObjectSnapshot != null;
    }
}
