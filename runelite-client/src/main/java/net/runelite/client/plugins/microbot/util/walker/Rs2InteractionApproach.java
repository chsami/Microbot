package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;

/** Read-only handoff check; the caller interacts only after walking has returned. */
public final class Rs2InteractionApproach {
    private Rs2InteractionApproach() { }

    public static boolean isReady(GameObject object) {
        if (object == null) return false;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            WorldPoint player = Rs2Player.getWorldLocation();
            WorldPoint target = object.getWorldLocation();
            if (!withinRange(player, target)
                    || !Rs2Camera.isTileOnScreen(object.getLocalLocation())) return false;
            Rs2WorldPoint approach = Rs2Tile.getNearestWalkableTile(object);
            // Collision-aware local distance excludes closed doors and walls.
            return approach != null && approach.distanceToPath(player) <= 16;
        }).orElse(false);
    }

    static boolean withinRange(WorldPoint player, WorldPoint target) {
        return player != null && target != null && player.getPlane() == target.getPlane()
                && player.distanceTo2D(target) <= 12;
    }
}
