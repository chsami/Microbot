package net.runelite.client.plugins.microbot.shortestpath.pathfinder.live;

import java.util.BitSet;

import static net.runelite.api.Constants.SCENE_SIZE;

/**
 * Edges owned by the walker's runtime door handler. Marked edges are left unknown in the live snapshot,
 * so they fall back to the static map and remain available to the door interaction pipeline.
 */
public final class LiveCollisionDoorMask {
    private static final int PLANE_STRIDE = SCENE_SIZE * SCENE_SIZE;

    private final int planeCount;
    private final BitSet north;
    private final BitSet east;

    public LiveCollisionDoorMask(int planeCount) {
        this.planeCount = Math.max(0, planeCount);
        final int cells = this.planeCount * PLANE_STRIDE;
        this.north = new BitSet(cells);
        this.east = new BitSet(cells);
    }

    /**
     * Marks the precise wall edge(s) represented by RuneLite wall orientations. Diagonal wall
     * orientations map to the two cardinal edges which bound that corner, matching the pathfinder's
     * cardinal-edge representation.
     */
    public void markWall(int plane, int sceneX, int sceneY, int orientationA, int orientationB) {
        markOrientation(plane, sceneX, sceneY, orientationA);
        markOrientation(plane, sceneX, sceneY, orientationB);
    }

    /**
     * Game-object doors have no wall-edge orientation. Conservatively exempt every edge touching their
     * footprint, while keeping that broader fallback limited to the game object itself.
     */
    public void markGameObject(int plane, int minSceneX, int minSceneY, int maxSceneX, int maxSceneY) {
        for (int x = minSceneX; x <= maxSceneX; x++) {
            for (int y = minSceneY; y <= maxSceneY; y++) {
                markCardinal(plane, x, y, -1, 0);
                markCardinal(plane, x, y, 1, 0);
                markCardinal(plane, x, y, 0, -1);
                markCardinal(plane, x, y, 0, 1);
            }
        }
    }

    public boolean exemptsNorth(int plane, int sceneX, int sceneY) {
        return valid(plane, sceneX, sceneY) && north.get(index(plane, sceneX, sceneY));
    }

    public boolean exemptsEast(int plane, int sceneX, int sceneY) {
        return valid(plane, sceneX, sceneY) && east.get(index(plane, sceneX, sceneY));
    }

    private void markOrientation(int plane, int sceneX, int sceneY, int orientation) {
        switch (orientation) {
            case 1: // west
                markCardinal(plane, sceneX, sceneY, -1, 0);
                break;
            case 2: // north
                markCardinal(plane, sceneX, sceneY, 0, 1);
                break;
            case 4: // east
                markCardinal(plane, sceneX, sceneY, 1, 0);
                break;
            case 8: // south
                markCardinal(plane, sceneX, sceneY, 0, -1);
                break;
            case 16: // north-west
                markCardinal(plane, sceneX, sceneY, -1, 0);
                markCardinal(plane, sceneX, sceneY, 0, 1);
                break;
            case 32: // north-east
                markCardinal(plane, sceneX, sceneY, 0, 1);
                markCardinal(plane, sceneX, sceneY, 1, 0);
                break;
            case 64: // south-east
                markCardinal(plane, sceneX, sceneY, 1, 0);
                markCardinal(plane, sceneX, sceneY, 0, -1);
                break;
            case 128: // south-west
                markCardinal(plane, sceneX, sceneY, 0, -1);
                markCardinal(plane, sceneX, sceneY, -1, 0);
                break;
            default:
                break;
        }
    }

    private void markCardinal(int plane, int sceneX, int sceneY, int dx, int dy) {
        int edgeX = sceneX;
        int edgeY = sceneY;
        final BitSet edges;
        if (dx != 0) {
            if (dx < 0) {
                edgeX--;
            }
            edges = east;
        } else {
            if (dy < 0) {
                edgeY--;
            }
            edges = north;
        }
        if (valid(plane, edgeX, edgeY)) {
            edges.set(index(plane, edgeX, edgeY));
        }
    }

    private boolean valid(int plane, int sceneX, int sceneY) {
        return plane >= 0 && plane < planeCount
                && sceneX >= 0 && sceneX < SCENE_SIZE
                && sceneY >= 0 && sceneY < SCENE_SIZE;
    }

    private static int index(int plane, int sceneX, int sceneY) {
        return plane * PLANE_STRIDE + sceneY * SCENE_SIZE + sceneX;
    }
}
