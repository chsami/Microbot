package net.runelite.client.plugins.microbot.shortestpath.pathfinder.live;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.client.plugins.microbot.Microbot;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
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
    /**
     * Version of the capture <em>semantics</em> — the flag→edge translation, the border margin, and the
     * exempted-obstacle set. Bump this whenever a change here would make previously persisted regions
     * disagree with what a fresh capture would now produce, so {@link LiveCollisionPersistence} rejects the
     * stale data on load instead of trusting it. This is what removes the manual "Reset learned collision"
     * step: e.g. adding the rockfall exemption changed what a rockfall tile records, so that data must not
     * survive the change. History: v1 = original translation; v2 = rockfall (26679/26680) exemption.
     */
    public static final int CAPTURE_VERSION = 2;

    private static final Set<String> DOOR_ACTIONS = new HashSet<>(Arrays.asList(
            "open", "go-through", "walk-through", "pass", "pick-lock", "pay-toll"));

    /**
     * Game objects a runtime walker handler clears in place (mines, etc.) rather than routing around —
     * currently the Motherlode Mine rockfalls, cleared by {@code Rs2Walker.handleRockfall}. Their tiles
     * read as fully blocked in the live scene, but the static map deliberately marks them <em>passable</em>
     * (dumper {@code Exclusion} list) so the pathfinder plans a route through them and the runtime handler
     * opens them. Left un-exempted, the live overlay would wall each rockfall off, the pathfinder would
     * avoid it, and {@code handleRockfall} would never receive a through-path to mine — so we exempt every
     * edge touching their footprint, deferring to the static-passable value exactly like an openable door.
     */
    private static final Set<Integer> RUNTIME_HANDLED_OBSTACLE_IDS = new HashSet<>(Arrays.asList(
            26679, 26680)); // MOTHERLODE_ROCKFALL_1, MOTHERLODE_ROCKFALL_2

    /**
     * Tiles this close to the edge of the loaded scene are left unknown (deferred to the static map),
     * because RuneLite's collision for the scene's outer ring is incomplete while adjacent chunks are
     * only partially loaded — verified live, where the border produced a uniform band of false walls out
     * to about five tiles while the deep interior matched reality. The scene always loads centred on the
     * player (~tile 52 of 104), so the trusted interior still covers everything near the player, and by
     * the time the player nears the border the scene has recentred. Eight gives headroom over the ~5
     * tiles of artifact observed.
     */
    private static final int SCENE_BORDER_MARGIN = 8;

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

        return build(wv.getBaseX(), wv.getBaseY(), planeCount, flagsByPlane, findDoorEdges(wv, planeCount));
    }

    /**
     * Scans the loaded scene for objects owned by the runtime door handler. Wall objects contribute
     * only their oriented door edge; game-object doors contribute the edges touching their footprint
     * because they do not expose a wall orientation.
     */
    private static LiveCollisionDoorMask findDoorEdges(WorldView wv, int planeCount) {
        final Tile[][][] tiles = wv.getScene().getTiles();
        if (tiles == null) {
            return null;
        }
        final LiveCollisionDoorMask doorEdges = new LiveCollisionDoorMask(planeCount);
        // A scene can reference the same object id many times (and multi-tile game objects can appear
        // on more than one Tile). Resolve each definition at most once per capture.
        final Map<Integer, Boolean> wallDoorIds = new HashMap<>();
        final Map<Integer, Boolean> gameObjectDoorIds = new HashMap<>();
        final int planes = Math.min(planeCount, tiles.length);
        for (int z = 0; z < planes; z++) {
            final Tile[][] plane = tiles[z];
            if (plane == null) {
                continue;
            }
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
                    if (wall != null && wallDoorIds.computeIfAbsent(
                            wall.getId(), LiveCollisionCapture::isOpenableDoor)) {
                        doorEdges.markWall(z, sx, sy, wall.getOrientationA(), wall.getOrientationB());
                    }
                    final GameObject[] gameObjects = tile.getGameObjects();
                    if (gameObjects == null) {
                        continue;
                    }
                    for (GameObject gameObject : gameObjects) {
                        if (gameObject == null) {
                            continue;
                        }
                        final Point min = gameObject.getSceneMinLocation();
                        final Point max = gameObject.getSceneMaxLocation();
                        if (min == null || max == null || min.getX() != sx || min.getY() != sy) {
                            continue; // process a multi-tile object once, at its scene-min tile
                        }
                        final boolean exempt = RUNTIME_HANDLED_OBSTACLE_IDS.contains(gameObject.getId())
                                || gameObjectDoorIds.computeIfAbsent(
                                        gameObject.getId(), LiveCollisionCapture::isOpenableGameObjectDoor);
                        if (!exempt) {
                            continue;
                        }
                        doorEdges.markGameObject(z, min.getX(), min.getY(), max.getX(), max.getY());
                    }
                }
            }
        }
        return doorEdges;
    }

    private static boolean isOpenableDoor(int objectId) {
        final ObjectComposition comp = resolvedComposition(objectId);
        if (comp == null) {
            return false;
        }
        return hasDoorAction(comp);
    }

    private static boolean isOpenableGameObjectDoor(int objectId) {
        final ObjectComposition comp = resolvedComposition(objectId);
        return comp != null
                && comp.getName() != null
                && comp.getName().toLowerCase(Locale.ENGLISH).contains("door")
                && hasDoorAction(comp);
    }

    private static ObjectComposition resolvedComposition(int objectId) {
        final ObjectComposition comp = Microbot.getClient().getObjectDefinition(objectId);
        return comp != null && comp.getImpostorIds() != null ? comp.getImpostor() : comp;
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
     * As {@link #build(int, int, int, int[][][])} but exempting edges owned by the runtime door handler.
     */
    public static LiveCollisionSnapshot build(int baseX, int baseY, int planeCount, int[][][] flagsByPlane,
                                              LiveCollisionDoorMask doorEdges) {
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
            for (int sx = 0; sx < SCENE_SIZE; sx++) {
                for (int sy = 0; sy < SCENE_SIZE; sy++) {
                    final int index = (z * SCENE_SIZE + sy) * SCENE_SIZE + sx;
                    final int data = flags[sx][sy];
                    final boolean walkableHere = standable(data);

                    // North edge: known only when both endpoints are trusted interior tiles (away from
                    // the unreliable scene border) and the runtime door handler does not own this edge.
                    if (interior(sx, sy) && interior(sx, sy + 1)
                            && (doorEdges == null || !doorEdges.exemptsNorth(z, sx, sy))) {
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

                    // East edge: same, for the east neighbour.
                    if (interior(sx, sy) && interior(sx + 1, sy)
                            && (doorEdges == null || !doorEdges.exemptsEast(z, sx, sy))) {
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

    /** A scene tile is trusted only when it is at least {@link #SCENE_BORDER_MARGIN} tiles from the edge. */
    private static boolean interior(int sx, int sy) {
        return sx >= SCENE_BORDER_MARGIN && sx < SCENE_SIZE - SCENE_BORDER_MARGIN
                && sy >= SCENE_BORDER_MARGIN && sy < SCENE_SIZE - SCENE_BORDER_MARGIN;
    }

    private static boolean standable(int data) {
        return (data & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0;
    }
}
