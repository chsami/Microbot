package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.shortestpath.TeleportationItem;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Microbot-owned facade over the shortest-path plugin's mutable static state.
 *
 * <p><b>Why this exists (Stage 2 of the facade migration — see
 * {@code shortestpath/WEBWALKER_IMPROVEMENT_PLAN.md} "Facade migration").</b>
 * Automation code ({@code Rs2Walker} and ~25 other consumers) currently reaches directly into
 * {@link ShortestPathPlugin}'s public static fields and accessors. Every time an upstream
 * (Skretzo/shortest-path) fix touches that internal wiring, the walker is at risk. Routing all
 * plugin-state access through this single class freezes the surface the walker sees, so future
 * upstream backports can change the plugin internals while only this facade (and not every
 * consumer) has to move with them.</p>
 *
 * <p><b>Contract.</b> This is a <i>thin, 1:1 delegation</i>. Every method here forwards verbatim to
 * the corresponding {@link ShortestPathPlugin} static member catalogued in the Stage 1 sweep. It
 * intentionally introduces <b>no behaviour change</b> and holds <b>no state</b> of its own. The
 * value types it returns ({@link Pathfinder}, {@link PathfinderConfig}, {@link Transport},
 * {@code TransportType}, {@code WorldPointUtil}) are treated as the stable Microbot-facing path API
 * and are deliberately <i>not</i> re-wrapped — they are pure data / pure functions.</p>
 *
 * <p><b>Migration status.</b> Stage 3 is complete: every consumer under {@code microbot/util/} now
 * routes through this facade, so the only remaining references to {@link ShortestPathPlugin}'s
 * static members outside the {@code shortestpath} package are the delegations below. That invariant
 * is greppable, and is what keeps the blast radius of an upstream backport confined to this class:
 * <pre>grep -rn "ShortestPathPlugin\." microbot/util/   # expect hits in Rs2PathApi only</pre>
 * {@link ShortestPathPlugin}'s members remain public and binary-compatible for out-of-tree callers.
 * Do not add logic here — if a call needs new behaviour, put it behind the plugin and expose it
 * through a matching delegate.</p>
 */
public final class Rs2PathApi
{
	private Rs2PathApi()
	{
	}

	/** Config group key for the shortest-path plugin ({@link ShortestPathPlugin#CONFIG_GROUP}). */
	public static final String CONFIG_GROUP = ShortestPathPlugin.CONFIG_GROUP;

	/** Shared world-map marker sprite ({@link ShortestPathPlugin#MARKER_IMAGE}). */
	public static final BufferedImage MARKER_IMAGE = ShortestPathPlugin.MARKER_IMAGE;

	// ------------------------------------------------------------------
	// Pathfinder lifecycle
	// ------------------------------------------------------------------

	/** @return the current pathfinder instance, or {@code null} if none is running. */
	public static Pathfinder getPathfinder()
	{
		return ShortestPathPlugin.getPathfinder();
	}

	public static void setPathfinder(Pathfinder pathfinder)
	{
		ShortestPathPlugin.setPathfinder(pathfinder);
	}

	/** @return the {@link Future} tracking the in-flight pathfinding task, or {@code null}. */
	public static Future<?> getPathfinderFuture()
	{
		return ShortestPathPlugin.getPathfinderFuture();
	}

	public static void setPathfinderFuture(Future<?> future)
	{
		ShortestPathPlugin.setPathfinderFuture(future);
	}

	/** @return the single-threaded executor pathfinding runs on. */
	public static ExecutorService getPathfindingExecutor()
	{
		return ShortestPathPlugin.getPathfindingExecutor();
	}

	public static void setPathfindingExecutor(ExecutorService executor)
	{
		ShortestPathPlugin.setPathfindingExecutor(executor);
	}

	/** @return the monitor guarding pathfinder start/cancel transitions. */
	public static Object getPathfinderMutex()
	{
		return ShortestPathPlugin.getPathfinderMutex();
	}

	// ------------------------------------------------------------------
	// Config
	// ------------------------------------------------------------------

	/** @return the shared pathfinder configuration (transports, restrictions, toggles). */
	public static PathfinderConfig getPathfinderConfig()
	{
		return ShortestPathPlugin.getPathfinderConfig();
	}

	/** Distance from the target at which the path is considered reached. */
	public static void setReachedDistance(int reachedDistance)
	{
		ShortestPathPlugin.setReachedDistance(reachedDistance);
	}

	public static boolean override(String configOverrideKey, boolean defaultValue)
	{
		return ShortestPathPlugin.override(configOverrideKey, defaultValue);
	}

	public static int override(String configOverrideKey, int defaultValue)
	{
		return ShortestPathPlugin.override(configOverrideKey, defaultValue);
	}

	public static TeleportationItem override(String configOverrideKey, TeleportationItem defaultValue)
	{
		return ShortestPathPlugin.override(configOverrideKey, defaultValue);
	}

	// ------------------------------------------------------------------
	// Target / walker state
	// ------------------------------------------------------------------

	/** Clears the active target and tears down the current path (see {@link ShortestPathPlugin#exit()}). */
	public static void exit()
	{
		ShortestPathPlugin.exit();
	}

	public static boolean isStartPointSet()
	{
		return ShortestPathPlugin.isStartPointSet();
	}

	public static void setStartPointSet(boolean startPointSet)
	{
		ShortestPathPlugin.setStartPointSet(startPointSet);
	}

	/** Records the player's last known world location (write-only on the plugin). */
	public static void setLastLocation(WorldPoint lastLocation)
	{
		ShortestPathPlugin.setLastLocation(lastLocation);
	}

	// ------------------------------------------------------------------
	// Marker (world-map overlay)
	// ------------------------------------------------------------------

	public static WorldMapPoint getMarker()
	{
		return ShortestPathPlugin.getMarker();
	}

	public static void setMarker(WorldMapPoint marker)
	{
		ShortestPathPlugin.setMarker(marker);
	}

	// ------------------------------------------------------------------
	// Transport data
	// ------------------------------------------------------------------

	/** @return the transport graph keyed by origin tile. */
	public static Map<WorldPoint, Set<Transport>> getTransports()
	{
		return ShortestPathPlugin.getTransports();
	}
}
