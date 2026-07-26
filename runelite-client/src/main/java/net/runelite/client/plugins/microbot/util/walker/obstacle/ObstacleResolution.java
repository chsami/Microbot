package net.runelite.client.plugins.microbot.util.walker.obstacle;

import net.runelite.api.coords.WorldPoint;

/**
 * Outcome of an {@link ObstacleResolver} inspecting/handling a {@link PlannedEdge}. One result type for
 * every obstacle kind (door / rockfall / shortcut / transport), so the walker's recovery has a single
 * {@code switch} instead of forty special-cased branches (P2; docs/walker-p2-unification.md).
 */
public final class ObstacleResolution {

    public enum Kind {
        /** This resolver does not apply to the edge — try the next resolver / fall back. */
        NOT_APPLICABLE,
        /** The edge is now traversable; the walker may continue the route. */
        CROSSED,
        /** An interaction was issued (door opening, rock mining) — yield a tick and re-check. */
        INTERACTED,
        /** Still settling from a prior interaction — yield without re-issuing. */
        WAITING,
        /** The player must first step onto {@link #walkTarget} (e.g. a stepping-stone origin) to proceed. */
        WALK_TO_ORIGIN,
        /** Cannot resolve; the caller should record the real blocked edge and recalculate. */
        ABORT
    }

    private static final ObstacleResolution NOT_APPLICABLE = new ObstacleResolution(Kind.NOT_APPLICABLE, null, null);
    private static final ObstacleResolution CROSSED = new ObstacleResolution(Kind.CROSSED, null, null);
    private static final ObstacleResolution INTERACTED = new ObstacleResolution(Kind.INTERACTED, null, null);
    private static final ObstacleResolution WAITING = new ObstacleResolution(Kind.WAITING, null, null);

    private final Kind kind;
    private final WorldPoint walkTarget;
    private final String reason;

    private ObstacleResolution(Kind kind, WorldPoint walkTarget, String reason) {
        this.kind = kind;
        this.walkTarget = walkTarget;
        this.reason = reason;
    }

    public static ObstacleResolution notApplicable() {
        return NOT_APPLICABLE;
    }

    public static ObstacleResolution crossed() {
        return CROSSED;
    }

    public static ObstacleResolution interacted() {
        return INTERACTED;
    }

    public static ObstacleResolution waiting() {
        return WAITING;
    }

    public static ObstacleResolution walkToOrigin(WorldPoint target) {
        return new ObstacleResolution(Kind.WALK_TO_ORIGIN, target, null);
    }

    public static ObstacleResolution abort(String reason) {
        return new ObstacleResolution(Kind.ABORT, null, reason);
    }

    public Kind kind() {
        return kind;
    }

    /** The tile to step onto, when {@link #kind()} is {@link Kind#WALK_TO_ORIGIN}; otherwise {@code null}. */
    public WorldPoint walkTarget() {
        return walkTarget;
    }

    /** The abort reason, when {@link #kind()} is {@link Kind#ABORT}; otherwise {@code null}. */
    public String reason() {
        return reason;
    }

    @Override
    public String toString() {
        return "ObstacleResolution{" + kind
                + (walkTarget != null ? " walkTarget=" + walkTarget : "")
                + (reason != null ? " reason=" + reason : "") + "}";
    }
}
