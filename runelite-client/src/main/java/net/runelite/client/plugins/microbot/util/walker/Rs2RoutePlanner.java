package net.runelite.client.plugins.microbot.util.walker;

/** Interchangeable route-engine boundary used after Microbot resolves an immutable request policy. */
public interface Rs2RoutePlanner
{
	/** Stable diagnostic id such as {@code microbot-local} or a pinned upstream revision. */
	String getEngineId();

	/**
	 * Calculate one route. Implementations must reject requests without a resolved policy rather than
	 * consulting mutable Microbot or plugin globals.
	 */
	Rs2RouteResult plan(Rs2RouteRequest request, Rs2PlanningSnapshot snapshot);
}
