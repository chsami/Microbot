package net.runelite.client.plugins.microbot.shortestpath.pathfinder.live;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.client.plugins.microbot.Microbot;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static net.runelite.api.Constants.SCENE_SIZE;

/**
 * Builds a {@link LiveCollisionSnapshot} from the live RuneLite scene.
 * <p>
 * {@link #capture()} must run on the client thread — it reads {@code WorldView.getCollisionMaps()} — and
 * hands the result to an immutable snapshot the off-thread pathfinder can then read lock-free. The
 * translation from RuneLite's per-tile {@link CollisionDataFlag} bitmask to the can-north / can-east edge
 * model mirrors the canonical logic in {@code Rs2Tile.getReachableTilesFromTileInternal}: a tile is
 * standable unless {@link CollisionDataFlag#BLOCK_MOVEMENT_FULL} is set, and a directional wall bit blocks
 * the corresponding edge. An edge is only recorded when both tiles it connects were captured, so the
 * north/east rim of the scene is left unknown and falls back to the static map.
 * <p>
 * Instances are deliberately not captured in this stage: the scene→world mapping for rotated and repeated
 * template chunks needs its own handling, and an approximate mapping would fabricate phantom walls. Inside
 * an instance {@link #capture()} returns {@code null}, so the overlay covers nothing and the pathfinder
 * uses the static map — the same behaviour as today.
 */
@Slf4j
public final class LiveCollisionCapture {

    /**
     * Actions that mark a wall object as a door the walker opens at runtime. Mirrors the door-action set
     * in {@code Rs2Walker.handleDoors}. Any edge touching such a door is left <em>unknown</em> in the
     * snapshot so it falls back to the static map (which treats the doorway as passable) and the existing
     * runtime door subsystem keeps owning it — otherwise a closed door's live-blocked edge would make the
     * pathfinder route around an openable door.
     */
    private static final Set<String> DOOR_ACTIONS = new HashSet<>(Arrays.asList(
            "open", "go-through", "walk-through", "pass", "pick-lock", "pay-toll"));

    private LiveCollisionCapture() {
    }

    /**
     * Reads the live scene on the client thread and returns an immutable snapshot, or {@code null} when
     * there is nothing to capture (no world view, no collision data, or an instanced scene).
     */
    public static LiveCollisionSnapshot capture() {
        return Microbot.getClientThread().runOnClientThreadOptional(LiveCollisionCapture::captureOnClientThread)
                .orElse(null);
    }

    /**
     * Same as {@link #capture()} but for callers that are <b>already on the client thread</b> (e.g. a
     * {@code GameTick} subscriber), avoiding a redundant thread hop.
     */
    public static LiveCollisionSnapshot captureOnClientThread() {
        final WorldView wv = Microbot.getClient().getTopLevelWorldView();
        if (wv == null) {
            return null;
        }
        // See class javadoc: instances need dedicated scene->world chunk mapping; skip for now.
        if (wv.isInstance()) {
            return null;
        }

        final CollisionData[] collisionMaps = wv.getCollisionMaps();
        if (collisionMaps == null || collisionMaps.length == 0) {
            return null;
        }

        final int planeCount = collisionMaps.length;
        final int[][][] flagsByPlane = new int[planeCount][][];
        for (int z = 0; z < planeCount; z++) {
            final CollisionData plane = collisionMaps[z];
            flagsByPlane[z] = plane != null ? plane.getFlags() : null;
        }

        return build(wv.getBaseX(), wv.getBaseY(), planeCount, flagsByPlane, findDoorTiles(wv, planeCount));
    }

    /**
     * Scans the loaded scene for openable-door wall objects. Runs on the client thread as part of
     * {@link #captureOnClientThread()}. {@code doorTile[z][sx][sy]} is set where a wall object with a
     * door action sits, so {@link #build} can exempt every edge touching it.
     */
    private static boolean[][][] findDoorTiles(WorldView wv, int planeCount) {
        final Tile[][][] tiles = wv.getScene().getTiles();
        if (tiles == null) {
            return null;
        }
        final boolean[][][] doorTile = new boolean[planeCount][][];
        final int planes = Math.min(planeCount, tiles.length);
        for (int z = 0; z < planes; z++) {
            final Tile[][] plane = tiles[z];
            if (plane == null) {
                continue;
            }
            boolean[][] doors = null;
            for (int sx = 0; sx < SCENE_SIZE && sx < plane.length; sx++) {
                final Tile[] col = plane[sx];
                if (col == null) {
                    continue;
                }
                for (int sy = 0; sy < SCENE_SIZE && sy < col.length; sy++) {
                    final Tile tile = col[sy];
                    if (tile == null) {
                        continue;
                    }
                    final WallObject wall = tile.getWallObject();
                    if (wall != null && isOpenableDoor(wall.getId())) {
                        if (doors == null) {
                            doors = new boolean[SCENE_SIZE][SCENE_SIZE];
                        }
                        doors[sx][sy] = true;
                    }
                }
            }
            doorTile[z] = doors;
        }
        return doorTile;
    }

    private static boolean isOpenableDoor(int objectId) {
        final ObjectComposition comp = Microbot.getClient().getObjectDefinition(objectId);
        if (comp == null) {
            return false;
        }
        if (hasDoorAction(comp)) {
            return true;
        }
        // A closed door is often a multiloc whose openable form is an impostor.
        if (comp.getImpostorIds() != null) {
            final ObjectComposition impostor = comp.getImpostor();
            return impostor != null && hasDoorAction(impostor);
        }
        return false;
    }

    private static boolean hasDoorAction(ObjectComposition comp) {
        final String[] actions = comp.getActions();
        if (actions == null) {
            return false;
        }
        for (String action : actions) {
            if (action != null && DOOR_ACTIONS.contains(action.toLowerCase(Locale.ENGLISH))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pure translation, split out so it can be unit-tested without a client. {@code flagsByPlane[z]} is
     * the {@code int[SCENE_SIZE][SCENE_SIZE]} returned by {@code CollisionData.getFlags()}, indexed
     * {@code [sceneX][sceneY]}, or {@code null} for a plane with no data.
     */
    public static LiveCollisionSnapshot build(int baseX, int baseY, int planeCount, int[][][] flagsByPlane) {
        return build(baseX, baseY, planeCount, flagsByPlane, null);
    }

    /**
     * As {@link #build(int, int, int, int[][][])} but exempting openable-door edges: any edge touching a
     * tile flagged in {@code doorTile} is left unknown so the pathfinder falls back to the static map and
     * the runtime door handler opens it. {@code doorTile} may be {@code null} (no doors), as may any plane
     * within it.
     */
    public static LiveCollisionSnapshot build(int baseX, int baseY, int planeCount, int[][][] flagsByPlane,
                                              boolean[][][] doorTile) {
        final int cells = planeCount * SCENE_SIZE * SCENE_SIZE;
        final BitSet northKnown = new BitSet(cells);
        final BitSet northValue = new BitSet(cells);
        final BitSet eastKnown = new BitSet(cells);
        final BitSet eastValue = new BitSet(cells);

        for (int z = 0; z < planeCount; z++) {
            final int[][] flags = flagsByPlane[z];
            if (flags == null) {
                continue;
            }
            final boolean[][] doors = doorTile != null ? doorTile[z] : null;
            for (int sx = 0; sx < SCENE_SIZE; sx++) {
                for (int sy = 0; sy < SCENE_SIZE; sy++) {
                    final int index = (z * SCENE_SIZE + sy) * SCENE_SIZE + sx;
                    final int data = flags[sx][sy];
                    final boolean walkableHere = standable(data);

                    // North edge: known only when the north neighbour is inside the scene and neither
                    // endpoint is a door tile (doors defer to the static map + runtime handler).
                    if (sy + 1 < SCENE_SIZE && !isDoor(doors, sx, sy) && !isDoor(doors, sx, sy + 1)) {
                        northKnown.set(index);
                        final int north = flags[sx][sy + 1];
                        final boolean open = walkableHere
                                && standable(north)
                                && (data & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) == 0
                                && (north & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) == 0;
                        if (open) {
                            northValue.set(index);
                        }
                    }

                    // East edge: known only when the east neighbour is inside the scene and neither
                    // endpoint is a door tile.
                    if (sx + 1 < SCENE_SIZE && !isDoor(doors, sx, sy) && !isDoor(doors, sx + 1, sy)) {
                        eastKnown.set(index);
                        final int east = flags[sx + 1][sy];
                        final boolean open = walkableHere
                                && standable(east)
                                && (data & CollisionDataFlag.BLOCK_MOVEMENT_EAST) == 0
                                && (east & CollisionDataFlag.BLOCK_MOVEMENT_WEST) == 0;
                        if (open) {
                            eastValue.set(index);
                        }
                    }
                }
            }
        }

        return new LiveCollisionSnapshot(baseX, baseY, planeCount, northKnown, northValue, eastKnown, eastValue);
    }

    private static boolean isDoor(boolean[][] doors, int sx, int sy) {
        return doors != null && doors[sx][sy];
    }

    private static boolean standable(int data) {
        return (data & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0;
    }
}
