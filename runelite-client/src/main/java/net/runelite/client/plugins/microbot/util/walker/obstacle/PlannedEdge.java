package net.runelite.client.plugins.microbot.util.walker.obstacle;

import net.runelite.api.coords.WorldPoint;

/**
 * A single step of the planned route the walker is currently unable to make: moving from {@link #from} to
 * {@link #to}. This is the unit an {@link ObstacleResolver} inspects — a door on this edge, a rockfall on
 * it, a stepping stone / transport whose origin is {@code from}, etc. (P2 obstacle unification;
 * docs/walker-p2-unification.md).
 */
public final class PlannedEdge {
    private final WorldPoint from;
    private final WorldPoint to;

    public PlannedEdge(WorldPoint from, WorldPoint to) {
        this.from = from;
        this.to = to;
    }

    public WorldPoint from() {
        return from;
    }

    public WorldPoint to() {
        return to;
    }

    /** A plain same-plane walking step (Chebyshev distance 1) — a door/rockfall/agility-shortcut candidate. */
    public boolean adjacent() {
        return from != null && to != null
                && from.getPlane() == to.getPlane()
                && from.distanceTo2D(to) == 1;
    }

    /** A cross-plane step (stairs/ladder) — always a transport, never a plain walk. */
    public boolean crossPlane() {
        return from != null && to != null && from.getPlane() != to.getPlane();
    }

    @Override
    public String toString() {
        return "PlannedEdge{" + from + " -> " + to + "}";
    }
}
