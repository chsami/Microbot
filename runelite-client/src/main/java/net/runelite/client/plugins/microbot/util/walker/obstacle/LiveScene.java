package net.runelite.client.plugins.microbot.util.walker.obstacle;

import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;

/**
 * Read-only snapshot of the live world an {@link ObstacleResolver} needs to classify a {@link PlannedEdge},
 * with <b>no {@code Rs2*} statics inside the resolver</b>: the walker builds a {@code LiveScene} adapter
 * over {@code Rs2Player}/{@code Rs2Tile}/{@code Rs2GameObject}/{@code Rs2PathApi}, but tests supply an
 * in-memory fake. This is what makes obstacle-resolution decisions verifiable headlessly (P2;
 * docs/walker-p2-unification.md). Kept intentionally small; grows only as a resolver genuinely needs it.
 */
public interface LiveScene {

    /** The player's current tile. */
    WorldPoint playerLocation();

    /** Whether {@code tile} is walk-reachable from the player right now (live-scene collision BFS). */
    boolean isReachable(WorldPoint tile);

	/** Whether a transport starts at {@code tile} (stairs/ladders/shortcuts/teleports). */
	boolean hasTransportAt(WorldPoint tile);

    /** The top interactable object on {@code tile} (door/gate/rockfall/…), or {@code null} if none. */
    TileObject objectAt(WorldPoint tile);
}
