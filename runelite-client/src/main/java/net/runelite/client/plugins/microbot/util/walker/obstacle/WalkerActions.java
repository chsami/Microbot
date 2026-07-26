package net.runelite.client.plugins.microbot.util.walker.obstacle;

import net.runelite.api.coords.WorldPoint;

/**
 * The thin imperative shell an {@link ObstacleResolver} uses to actually act on the world — the only part
 * that touches the live client. Kept behind an interface so resolver decision logic stays pure and the
 * actions are mocked in tests (P2; docs/walker-p2-unification.md). Grows only as a resolver needs it.
 */
public interface WalkerActions {

    /** Interacts with the object on {@code tile} using {@code action} (e.g. "Open", "Mine"). */
    boolean interactAt(WorldPoint tile, String action);

    /** Clicks/walks toward {@code target} (e.g. to step onto a stepping-stone origin). */
    boolean walkToward(WorldPoint target);

    /** Blocks until the interactable object on {@code tile} is gone (a mined rock, an opened door). */
    boolean waitUntilObjectGone(WorldPoint tile);
}
