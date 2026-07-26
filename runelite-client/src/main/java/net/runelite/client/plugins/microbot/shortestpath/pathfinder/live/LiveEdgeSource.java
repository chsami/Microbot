package net.runelite.client.plugins.microbot.shortestpath.pathfinder.live;

/**
 * A read-only source of live-collision edges in the shared can-north / can-east model, addressed in
 * <b>world</b> coordinates. Both a single-scene {@link LiveCollisionSnapshot} and a multi-region
 * {@link LiveCollisionView} implement it, so {@code CollisionMap} can pin either behind one reference and
 * read it lock-free for the duration of a search.
 */
public interface LiveEdgeSource {
    /**
     * @return {@link Boolean#TRUE}/{@link Boolean#FALSE} when the edge from {@code (x, y, z)} in the given
     * direction is known, or {@code null} when it is unknown here (outside coverage, on an untrusted rim,
     * or owned by the runtime door handler) — the caller then falls back to the static map. Must allocate
     * nothing on the hot path; return the cached {@code Boolean} constants.
     */
    Boolean edge(int x, int y, int z, int flag);
}
