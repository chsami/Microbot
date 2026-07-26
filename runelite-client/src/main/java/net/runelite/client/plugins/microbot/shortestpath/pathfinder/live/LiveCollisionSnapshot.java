package net.runelite.client.plugins.microbot.shortestpath.pathfinder.live;

import java.util.BitSet;

import static net.runelite.api.Constants.SCENE_SIZE;

/**
 * Immutable snapshot of the live collision of a single loaded scene, addressed in <b>world</b>
 * coordinates and expressed in the same edge model as
 * {@link net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap}: two flags per tile,
 * flag {@code 0} = can move north, flag {@code 1} = can move east. South and west are read from the
 * neighbour's north/east, exactly as {@code CollisionMap} does, so an overlay built from this stays
 * consistent with the static map it sits on top of.
 * <p>
 * Each edge carries a validity bit as well as a value: an edge is only <em>known</em> when the capture
 * could see both tiles it connects. Edges on the north/east rim of the scene, whose neighbour lies
 * outside the captured 104×104, are left <em>unknown</em> so the caller falls back to the static map
 * there rather than fabricating a wall at the scene boundary.
 * <p>
 * Built once on the client thread by {@link LiveCollisionCapture} and never mutated, so the off-thread
 * pathfinder can read it lock-free.
 */
public final class LiveCollisionSnapshot implements LiveEdgeSource {
    /** North edge, flag 0 in the shared edge model. */
    public static final int FLAG_NORTH = 0;
    /** East edge, flag 1 in the shared edge model. */
    public static final int FLAG_EAST = 1;

    private static final int PLANE_STRIDE = SCENE_SIZE * SCENE_SIZE;

    private final int baseX;
    private final int baseY;
    private final int planeCount;
    private final BitSet northKnown;
    private final BitSet northValue;
    private final BitSet eastKnown;
    private final BitSet eastValue;

    LiveCollisionSnapshot(int baseX, int baseY, int planeCount,
                          BitSet northKnown, BitSet northValue,
                          BitSet eastKnown, BitSet eastValue) {
        this.baseX = baseX;
        this.baseY = baseY;
        this.planeCount = planeCount;
        this.northKnown = northKnown;
        this.northValue = northValue;
        this.eastKnown = eastKnown;
        this.eastValue = eastValue;
    }

    /**
     * @return {@link Boolean#TRUE}/{@link Boolean#FALSE} when {@code (x, y, z)} is inside this snapshot
     * and the edge is known, or {@code null} when the tile is outside the scene or the edge sits on the
     * unknown rim — the caller falls back to the static map. Returns the cached {@code Boolean}
     * constants, so this allocates nothing on the pathfinder hot path.
     */
    @Override
    public Boolean edge(int x, int y, int z, int flag) {
        final int lx = x - baseX;
        final int ly = y - baseY;
        if (lx < 0 || lx >= SCENE_SIZE || ly < 0 || ly >= SCENE_SIZE || z < 0 || z >= planeCount) {
            return null;
        }
        final int index = z * PLANE_STRIDE + ly * SCENE_SIZE + lx;
        final BitSet known = flag == FLAG_EAST ? eastKnown : northKnown;
        if (!known.get(index)) {
            return null;
        }
        final BitSet value = flag == FLAG_EAST ? eastValue : northValue;
        return value.get(index) ? Boolean.TRUE : Boolean.FALSE;
    }

    public int getBaseX() {
        return baseX;
    }

    public int getBaseY() {
        return baseY;
    }

    public int getPlaneCount() {
        return planeCount;
    }
}
