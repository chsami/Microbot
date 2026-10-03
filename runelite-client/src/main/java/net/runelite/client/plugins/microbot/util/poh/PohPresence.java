package net.runelite.client.plugins.microbot.util.poh;

import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/** Lightweight house-presence check for pathfinder refreshes. */
public final class PohPresence {
    private PohPresence() {
    }

    public static boolean isInHouse() {
        if (!Rs2Player.IsInInstance()) {
            return false;
        }
        // The player's mapped world tile is unreliable inside a house instance.
        return Microbot.getRs2TileObjectCache()
                .query()
                .withId(ObjectID.POH_EXIT_PORTAL)
                .nearest() != null;
    }
}
