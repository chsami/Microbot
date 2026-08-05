package shortestpath.pathfinder;

/** Immutable per-search override for the pinned static collision edge model. */
@FunctionalInterface
public interface EdgeOverride
{
	/**
	 * @return {@code TRUE}/{@code FALSE} for a known override, or {@code null} to use static collision.
	 */
	Boolean edge(int x, int y, int plane, int flag);
}
