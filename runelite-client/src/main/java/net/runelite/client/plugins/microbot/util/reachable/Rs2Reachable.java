package net.runelite.client.plugins.microbot.util.reachable;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import java.util.Objects;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;

public class Rs2Reachable {
    private static int lastUpdateTick = -1;
    private static volatile IntSet reachableTiles = IntSets.EMPTY_SET;
    private static WorldPoint lastOrigin;
    private static WorldView lastView;
    private static Scene lastScene;
    private static int lastWorld, lastBaseX, lastBaseY;

    /** Last published, read-only flood result. */
    public static IntSet getReachableTiles() { return reachableTiles; }

    public static boolean isReachable(WorldPoint target) {
        return isReachable(target, null);
    }

    /** A null view means the local player's current view. */
    public static boolean isReachable(WorldPoint target, WorldView targetView) {
        if (target == null) return false;
        return Microbot.getClientThread().invoke((java.util.function.Supplier<Boolean>) () -> {
            Player player = Microbot.getClient().getLocalPlayer();
            if (player == null) return false;
            WorldView view = player.getWorldView();
            if (view == null || (targetView != null && targetView != view)) return false;
            return collect(player.getWorldLocation(), view).contains(WorldPointUtil.packWorldPoint(
                    target.getX(), target.getY(), target.getPlane()));
        });
    }

    /** Flood from an explicit origin in the local player's current world view. */
    public static IntSet getReachableTiles(WorldPoint start) {
        return Microbot.getClientThread().invoke(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            return collect(start, player == null ? null : player.getWorldView());
        });
    }

    private static IntSet collect(WorldPoint start, WorldView worldView) {
        assert Microbot.getClient().isClientThread() : "Client-thread-only helper";
            Client c = Microbot.getClient();
            int tick = c.getTickCount();
            if (c.getGameState() != net.runelite.api.GameState.LOGGED_IN
                    || worldView == null || start == null || start.getPlane() != worldView.getPlane()) {
                return clearReachableTiles();
            }
            if (lastUpdateTick == tick && Objects.equals(lastOrigin, start)
                    && lastView == worldView && lastScene == worldView.getScene()
                    && lastWorld == c.getWorld() && lastBaseX == worldView.getBaseX()
                    && lastBaseY == worldView.getBaseY()) return reachableTiles;
            CollisionData[] collisionMaps = worldView.getCollisionMaps();
            if (collisionMaps == null || worldView.getPlane() >= collisionMaps.length
                    || collisionMaps[worldView.getPlane()] == null) {
                return clearReachableTiles();
            }

            int plane = worldView.getPlane();
            int[][] collisionFlags = collisionMaps[plane].getFlags();

            if (collisionFlags == null || collisionFlags.length == 0 || collisionFlags[0] == null) return clearReachableTiles();
            int width = collisionFlags.length, height = collisionFlags[0].length;
            boolean[][] visited = new boolean[width][height];
            IntArrayFIFOQueue openQueue = new IntArrayFIFOQueue();

            int worldBaseX = worldView.getBaseX();
            int worldBaseY = worldView.getBaseY();

            if (start == null || start.getPlane() != plane) {
                return clearReachableTiles();
            }

            int localStartX = start.getX() - worldBaseX;
            int localStartY = start.getY() - worldBaseY;

            if (localStartX < 0 || localStartY < 0
                    || localStartX >= width || localStartY >= height) {
                return clearReachableTiles();
            }

            int startKey = (localStartX << 16) | localStartY;
            openQueue.enqueue(startKey);
            visited[localStartX][localStartY] = true;

            while (!openQueue.isEmpty()) {
                int tileKey = openQueue.dequeueInt();
                int localX = tileKey >> 16;
                int localY = tileKey & 0xFFFF;

                int tileFlags = collisionFlags[localX][localY];

                // South
                int southY = localY - 1;
                if (southY >= 0
                        && (tileFlags & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) == 0
                        && (collisionFlags[localX][southY] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0
                        && !visited[localX][southY]) {
                    openQueue.enqueue((localX << 16) | southY);
                    visited[localX][southY] = true;
                }

                // North
                int northY = localY + 1;
                if (northY < height
                        && (tileFlags & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) == 0
                        && (collisionFlags[localX][northY] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0
                        && !visited[localX][northY]) {
                    openQueue.enqueue((localX << 16) | northY);
                    visited[localX][northY] = true;
                }

                // West
                int westX = localX - 1;
                if (westX >= 0
                        && (tileFlags & CollisionDataFlag.BLOCK_MOVEMENT_WEST) == 0
                        && (collisionFlags[westX][localY] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0
                        && !visited[westX][localY]) {
                    openQueue.enqueue((westX << 16) | localY);
                    visited[westX][localY] = true;
                }

                // East
                int eastX = localX + 1;
                if (eastX < width
                        && (tileFlags & CollisionDataFlag.BLOCK_MOVEMENT_EAST) == 0
                        && (collisionFlags[eastX][localY] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0
                        && !visited[eastX][localY]) {
                    openQueue.enqueue((eastX << 16) | localY);
                    visited[eastX][localY] = true;
                }
            }

            IntSet reachablePackedPoints = new IntOpenHashSet();

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (visited[x][y]) {
                        int worldX = worldBaseX + x;
                        int worldY = worldBaseY + y;
                        reachablePackedPoints.add(
                                WorldPointUtil.packWorldPoint(worldX, worldY, plane)
                        );
                    }
                }
            }

            reachableTiles = IntSets.unmodifiable(reachablePackedPoints);
            lastOrigin = start;
            lastView = worldView;
            lastScene = worldView.getScene();
            lastWorld = c.getWorld();
            lastBaseX = worldBaseX;
            lastBaseY = worldBaseY;
            lastUpdateTick = tick;
            return reachableTiles;
    }
    private static IntSet clearReachableTiles() {
        lastUpdateTick = -1;
        lastOrigin = null;
        lastView = null;
        lastScene = null;
        lastWorld = lastBaseX = lastBaseY = 0;
        reachableTiles = IntSets.EMPTY_SET;
        return reachableTiles;
    }
}
