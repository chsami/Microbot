package net.runelite.client.plugins.microbot.util.walker;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.shortestpath.PlannerSelectionMode;
import net.runelite.client.plugins.microbot.shortestpath.TeleportationItem;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportExecutionRegistry;
import net.runelite.client.plugins.microbot.shortestpath.TransportItemRequirement;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathEdge;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathTerminationReason;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.live.LiveCollisionView;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Microbot-owned facade over the shortest-path plugin's mutable static state.
 *
 * <p><b>Why this exists (Stage 2 of the facade migration — see
 * {@code shortestpath/WEBWALKER_IMPROVEMENT_PLAN.md} "Facade migration").</b>
 * Automation code ({@code Rs2Walker} and ~25 other consumers) currently reaches directly into
 * {@link ShortestPathPlugin}'s public static fields and accessors. Every time an upstream
 * (Skretzo/shortest-path) fix touches that internal wiring, the walker is at risk. Routing all
 * plugin-state access through this single class confines direct static coupling, so future upstream
 * backports have one compatibility seam instead of many consumers to update.</p>
 *
 * <p><b>Contract.</b> Legacy methods here retain thin delegation to the corresponding
 * {@link ShortestPathPlugin} static member. New planning operations accept Microbot-owned immutable
 * request/result values and keep refresh, construction, cancellation, executor ownership and temporary
 * policy changes behind this seam. The
	 * legacy concrete accessors for {@link Pathfinder}, {@link PathfinderConfig} and {@link Transport}
 * remain for binary compatibility, but new operations expose immutable route values or narrow named
 * queries. That makes this a compatibility seam, not yet the final stable planning contract: active route
	 * state, lifecycle, synchronous queries and migrated catalog consumers no longer require concrete planner
	 * types outside this class.</p>
 *
 * <p><b>Migration status.</b> Stage 3 is complete: every consumer under {@code microbot/util/} now
 * routes through this facade, so the only remaining references to {@link ShortestPathPlugin}'s
 * static members outside the {@code shortestpath} package are the delegations below, plus the plugin
 * class literal used by {@code MicrobotPluginChoice}. That invariant is enforced by
 * {@code scripts/check-shortest-path-boundary.py}.
 * {@link ShortestPathPlugin}'s members remain public and binary-compatible for out-of-tree callers.</p>
 */
public final class Rs2PathApi
{
	private static volatile Pathfinder activeRouteSnapshotSource;
	private static volatile Rs2RouteResult activeRouteSnapshot;
	private static final AtomicLong activeRouteGeneration = new AtomicLong();
	private static volatile boolean activeRouteComparisonEligible;
	private static final Object shadowEvidenceMutex = new Object();
	private static final long shadowEvidenceStartedAtEpochMillis = System.currentTimeMillis();
	private static final long[][] shadowCoverageOutcomes = new long
		[Rs2PlannerShadowContext.Coverage.values().length]
		[Rs2PlannerShadowComparison.Status.values().length];
	private static final long[][] shadowTransportExecutorOutcomes = new long
		[Rs2TransportExecutor.values().length]
		[Rs2PlannerShadowComparison.Status.values().length];
	private static final long[][] shadowTransportTypeOutcomes = new long
		[Rs2TransportType.values().length]
		[Rs2PlannerShadowComparison.Status.values().length];
	private static long shadowGeneration;
	private static long shadowSubmitted;
	private static long shadowCompleted;
	private static long shadowMatches;
	private static long shadowDivergences;
	private static long shadowFailures;
	private static long shadowStaleResults;
	private static long shadowDiscarded;
	private static long shadowRouteShapeDifferences;
	private static long upstreamCanarySelections;
	private static long localFallbackDivergences;
	private static long localFallbackFailures;
	private static long shadowWalkerArrivals;
	private static long shadowWalkerUnreachable;
	private static long shadowWalkerExits;
	private static long shadowRecoveryArrivals;
	private static long shadowRecoveryUnreachable;
	private static long shadowRecoveryExits;
	private static long canaryPlanningSamples;
	private static long canaryPlanningNanosTotal;
	private static long canaryPlanningNanosMax;
	private static long canaryLocalSearchNanosTotal;
	private static long canaryLocalSearchNanosMax;
	private static long canaryUpstreamSearchSamples;
	private static long canaryUpstreamSearchNanosTotal;
	private static long canaryUpstreamSearchNanosMax;
	private static volatile Rs2PlannerShadowComparison lastShadowComparison;
	private static volatile Rs2PlannerShadowComparison lastRouteShapeDifference;
	private static volatile Rs2PlannerShadowComparison lastDivergence;
	private static volatile Rs2PlannerShadowComparison lastPlannerFailure;
	private static final ThreadPoolExecutor SHADOW_EXECUTOR = new ThreadPoolExecutor(
		1, 1, 0L, TimeUnit.MILLISECONDS,
		new ArrayBlockingQueue<>(1),
		new ThreadFactoryBuilder().setDaemon(true)
			.setNameFormat("microbot-upstream-planner-shadow-%d").build(),
		(command, executor) ->
		{
			if (executor.isShutdown())
			{
				recordShadowDiscarded();
				return;
			}
			Runnable discarded = executor.getQueue().poll();
			if (discarded != null)
			{
				recordShadowDiscarded();
			}
			if (!executor.getQueue().offer(command))
			{
				recordShadowDiscarded();
			}
		});
	private static final Map<WorldPoint, CatalogEdgeSnapshot> catalogEdgeSnapshots =
		new ConcurrentHashMap<>();

	private static final class CatalogEdgeSnapshot
	{
		private final Set<Transport> source;
		private final List<Rs2TransportEdge> edges;

		private CatalogEdgeSnapshot(Set<Transport> source, List<Rs2TransportEdge> edges)
		{
			this.source = source;
			this.edges = edges;
		}
	}

	private static final class PlannerComparisonTicket
	{
		private final long generation;
		private final Rs2PlannerShadowContext context;

		private PlannerComparisonTicket(long generation, Rs2PlannerShadowContext context)
		{
			this.generation = generation;
			this.context = context;
		}
	}

	private static final class PlannerEvaluation
	{
		private final PlannerComparisonTicket ticket;
		private final Rs2RouteResult local;
		private final Rs2RouteResult candidate;
		private final Rs2PlannerShadowComparison comparison;

		private PlannerEvaluation(
			PlannerComparisonTicket ticket,
			Rs2RouteResult local,
			Rs2RouteResult candidate,
			Rs2PlannerShadowComparison comparison)
		{
			this.ticket = ticket;
			this.local = local;
			this.candidate = candidate;
			this.comparison = comparison;
		}

		private PlannerEvaluation materializationFailed(RuntimeException failure)
		{
			return new PlannerEvaluation(
				ticket,
				local,
				null,
				Rs2PlannerShadowComparison.failed(
					comparison.getShadowEngineId(), ticket.context,
					local, failure));
		}
	}

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
		if (ShortestPathPlugin.getPathfinder() != pathfinder)
		{
			clearActiveRouteSnapshot();
			activeRouteComparisonEligible = false;
			activeRouteGeneration.incrementAndGet();
			// A route replacement makes any in-flight comparison evidence stale, even when
			// the replacement has shadow mode disabled and therefore submits no newer task.
			synchronized (shadowEvidenceMutex)
			{
				shadowGeneration++;
				lastShadowComparison = null;
			}
		}
		ShortestPathPlugin.setPathfinder(pathfinder);
	}

	/** Replace the concrete compatibility view without starting a new logical route generation. */
	private static boolean replaceActivePathfinderLocked(
		Pathfinder expected, Pathfinder replacement)
	{
		if (getPathfinder() != expected)
		{
			return false;
		}
		clearActiveRouteSnapshot();
		ShortestPathPlugin.setPathfinder(replacement);
		return true;
	}

	private static void clearActiveRouteSnapshot()
	{
		activeRouteSnapshotSource = null;
		activeRouteSnapshot = null;
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

	/** Immutable start point of the currently published route, if one exists. */
	public static Optional<WorldPoint> getActiveRouteStart()
	{
		return getActiveRouteStatus().getStart();
	}

	/** Immutable target snapshot of the currently published route. */
	public static Set<WorldPoint> getActiveRouteTargets()
	{
		return getActiveRouteStatus().getTargets();
	}

	/**
	 * Capture a coherent immutable view of the currently published route.
	 *
	 * <p>The lifecycle mutex prevents a route replacement halfway through the copy. The pathfinder may
	 * continue improving its partial path while calculating, but the returned lists cannot change under
	 * the caller.</p>
	 */
	public static Rs2ActiveRouteStatus getActiveRouteStatus()
	{
		synchronized (getPathfinderMutex())
		{
			long generation = activeRouteGeneration.get();
			Pathfinder source = getPathfinder();
			if (source == null)
			{
				return Rs2ActiveRouteStatus.absent(generation);
			}

			Future<?> activeFuture = getPathfinderFuture();
			boolean selectionComplete = activeFuture == null || activeFuture.isDone();
			boolean ready = source.isDone() && selectionComplete;
			List<WorldPoint> rawPath = immutablePath(source.getPath());
			List<WorldPoint> walkablePath = ready
				? immutablePath(source.getWalkablePath())
				: rawPath;
			if (!ready)
			{
				return new Rs2ActiveRouteStatus(
					generation,
					Rs2ActiveRouteStatus.Phase.CALCULATING,
					source.getStart(),
					source.getTargets(),
					rawPath,
					walkablePath,
					null,
					null);
			}

			Pathfinder.PathfinderStats stats = source.getStats();
			Rs2RouteMetrics metrics = new Rs2RouteMetrics(
				stats == null ? Rs2RouteMetrics.UNAVAILABLE : stats.getElapsedTimeNanos(),
				source.getSelectedPathCost(),
				stats == null ? Rs2RouteMetrics.UNAVAILABLE : stats.getNodesChecked(),
				stats == null ? Rs2RouteMetrics.UNAVAILABLE : stats.getTransportsChecked());
			return new Rs2ActiveRouteStatus(
				generation,
				Rs2ActiveRouteStatus.Phase.READY,
				source.getStart(),
				source.getTargets(),
				rawPath,
				walkablePath,
				mapTermination(source.getTerminationReason()),
				metrics);
		}
	}

	private static List<WorldPoint> immutablePath(List<WorldPoint> path)
	{
		return path == null || path.isEmpty() ? Collections.emptyList() : List.copyOf(path);
	}

	/**
	 * Cancel and unpublish the active local planner without exposing its concrete lifecycle to callers.
	 */
	public static void cancelAndClearActiveRoute()
	{
		synchronized (getPathfinderMutex())
		{
			cancelAndClearActiveRouteLocked();
		}
	}

	private static void cancelAndClearActiveRouteLocked()
	{
		Pathfinder active = getPathfinder();
		if (active != null)
		{
			active.cancel();
		}
		Future<?> activeFuture = getPathfinderFuture();
		if (activeFuture != null && !activeFuture.isDone())
		{
			activeFuture.cancel(true);
		}
		setPathfinderFuture(null);
		setPathfinder(null);
	}

	/**
	 * Refresh, create and publish the active route using only Microbot-owned request policy.
	 *
	 * <p>The cave preference preserves the existing walker policy: calculate both the normal route and
	 * a walking-only route, then prefer walking when it reaches any requested target and is not longer.
	 * Non-cave planning remains asynchronous on the owned single-thread executor.</p>
	 */
	public static boolean restartActiveRoute(
		Rs2RouteRequest request, boolean preferWalkingOnly, int reachedDistance)
	{
		return restartActiveRoute(
			request, preferWalkingOnly, reachedDistance,
			Rs2PlannerShadowContext.Invocation.ACTIVE_ROUTE);
	}

	/**
	 * Restart an active route with coordinate-free invocation metadata for shadow evidence.
	 */
	public static boolean restartActiveRoute(
		Rs2RouteRequest request,
		boolean preferWalkingOnly,
		int reachedDistance,
		Rs2PlannerShadowContext.Invocation invocation)
	{
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(invocation, "invocation");
		if (invocation == Rs2PlannerShadowContext.Invocation.SYNCHRONOUS_QUERY)
		{
			throw new IllegalArgumentException(
				"an active route cannot use the synchronous-query shadow invocation");
		}
		if (reachedDistance < 0)
		{
			throw new IllegalArgumentException("reachedDistance must be non-negative");
		}
		if (request.getUseBankItems() != null)
		{
			throw new IllegalArgumentException(
				"active asynchronous routes cannot use a temporary bank-item policy");
		}
		PathfinderConfig config = getPathfinderConfig();
		if (config == null)
		{
			return false;
		}

		synchronized (getPathfinderMutex())
		{
			cancelAndClearActiveRouteLocked();
			if (shouldRefresh(request, config))
			{
				config.refresh(request.getRefreshTarget());
			}

			if (preferWalkingOnly)
			{
				PlannerSelectionMode plannerMode = config.getPlannerSelectionMode();
				boolean comparisonEnabled = plannerMode.comparisonEnabled();
				Rs2RouteRequest normalRequest = comparisonEnabled
					? resolvePolicy(request, config) : null;
				Rs2PlanningSnapshot normalSnapshot = comparisonEnabled
					? resolvePlanningSnapshot(normalRequest, config) : null;
				long canaryPlanningStarted = System.nanoTime();
				Pathfinder normal = runLocalPlanner(config, request);
				Pathfinder walkingOnly;
				Rs2RouteRequest walkingRequest = null;
				Rs2PlanningSnapshot walkingSnapshot = null;
				try
				{
					config.setIgnoreTeleportAndItems(true);
					if (comparisonEnabled)
					{
						walkingRequest = resolvePolicy(request, config);
						walkingSnapshot = resolvePlanningSnapshot(walkingRequest, config);
					}
					walkingOnly = runLocalPlanner(config, request);
				}
				finally
				{
					config.setIgnoreTeleportAndItems(false);
				}
				Pathfinder selected = selectCaveRoute(
					normal, walkingOnly, request.getTargets(), reachedDistance);
				setPathfinder(selected);
				boolean walkingSelected = selected == walkingOnly;
				Rs2RouteRequest selectedRequest = walkingSelected ? walkingRequest : normalRequest;
				Rs2PlanningSnapshot selectedSnapshot = walkingSelected ? walkingSnapshot : normalSnapshot;
				activeRouteComparisonEligible = plannerMode == PlannerSelectionMode.SHADOW
					|| isF2pCanary(plannerMode, selectedRequest);
				if (isF2pCanary(plannerMode, selectedRequest))
				{
					Rs2RouteResult local = snapshot(selected, activeSearchNanos(selected));
					PlannerEvaluation evaluation = evaluateUpstream(
						selectedRequest, selectedSnapshot, local, invocation, walkingSelected);
					Pathfinder materialized = null;
					if (shouldSelectUpstream(evaluation.comparison))
					{
						try
						{
							materialized = materializeUpstreamRoute(
								evaluation.candidate, config);
						}
						catch (RuntimeException failure)
						{
							evaluation = evaluation.materializationFailed(failure);
						}
					}
					if (materialized != null)
					{
						replaceActivePathfinderLocked(selected, materialized);
					}
					recordPlannerComparison(evaluation);
					recordCanaryOutcome(
						evaluation.comparison,
						elapsedNanos(canaryPlanningStarted),
						combinedSearchNanos(normal, walkingOnly));
				}
				else if (plannerMode == PlannerSelectionMode.SHADOW)
				{
					submitUpstreamShadow(
						selectedRequest,
						selectedSnapshot,
						snapshot(selected, activeSearchNanos(selected)),
						invocation,
						walkingSelected);
				}
				return true;
			}

			ExecutorService executor = getPathfindingExecutor();
			if (executor == null || executor.isShutdown())
			{
				ThreadFactory threadFactory = new ThreadFactoryBuilder()
					.setNameFormat("shortest-path-%d")
					.build();
				executor = Executors.newSingleThreadExecutor(threadFactory);
				setPathfindingExecutor(executor);
			}
			PlannerSelectionMode plannerMode = config.getPlannerSelectionMode();
			boolean comparisonEnabled = plannerMode.comparisonEnabled();
			Rs2RouteRequest comparisonRequest = comparisonEnabled
				? resolvePolicy(request, config) : null;
			Rs2PlanningSnapshot comparisonSnapshot = comparisonEnabled
				? resolvePlanningSnapshot(comparisonRequest, config) : null;
			Pathfinder active = new Pathfinder(config, request.getStart(), request.getTargets());
			setPathfinder(active);
			activeRouteComparisonEligible = plannerMode == PlannerSelectionMode.SHADOW
				|| isF2pCanary(plannerMode, comparisonRequest);
			long routeGeneration = activeRouteGeneration.get();
			long canaryPlanningStarted = System.nanoTime();
			setPathfinderFuture(executor.submit(() ->
			{
				active.run();
				if (!comparisonEnabled)
				{
					return;
				}
				if (isF2pCanary(plannerMode, comparisonRequest))
				{
					runActiveCanarySelection(
						active, routeGeneration, comparisonRequest, comparisonSnapshot,
						config, invocation, canaryPlanningStarted);
					return;
				}
				if (plannerMode != PlannerSelectionMode.SHADOW)
				{
					return;
				}
				synchronized (getPathfinderMutex())
				{
					if (getPathfinder() != active
						|| activeRouteGeneration.get() != routeGeneration)
					{
						return;
					}
					submitUpstreamShadow(
						comparisonRequest,
						comparisonSnapshot,
						snapshot(active, activeSearchNanos(active)),
						invocation,
						false);
				}
			}));
			return true;
		}
	}

	private static Pathfinder runLocalPlanner(PathfinderConfig config, Rs2RouteRequest request)
	{
		Pathfinder pathfinder = new Pathfinder(config, request.getStart(), request.getTargets());
		pathfinder.run();
		return pathfinder;
	}

	static boolean isF2pCanary(
		PlannerSelectionMode plannerMode, Rs2RouteRequest request)
	{
		return plannerMode != null
			&& plannerMode.f2pCanaryEnabled()
			&& request != null
			&& request.getPolicy().map(policy -> !policy.isMembersWorld()).orElse(false);
	}

	private static void runActiveCanarySelection(
		Pathfinder active,
		long routeGeneration,
		Rs2RouteRequest request,
		Rs2PlanningSnapshot planningSnapshot,
		PathfinderConfig config,
		Rs2PlannerShadowContext.Invocation invocation,
		long canaryPlanningStarted)
	{
		synchronized (getPathfinderMutex())
		{
			if (getPathfinder() != active || activeRouteGeneration.get() != routeGeneration)
			{
				return;
			}
		}

		Rs2RouteResult local = snapshot(active, activeSearchNanos(active));
		PlannerEvaluation evaluation = evaluateUpstream(
			request, planningSnapshot, local, invocation, false);
		Pathfinder materialized = null;
		if (shouldSelectUpstream(evaluation.comparison))
		{
			try
			{
				materialized = materializeUpstreamRoute(evaluation.candidate, config);
			}
			catch (RuntimeException failure)
			{
				evaluation = evaluation.materializationFailed(failure);
			}
		}

		synchronized (getPathfinderMutex())
		{
			if (getPathfinder() != active || activeRouteGeneration.get() != routeGeneration)
			{
				recordPlannerComparison(evaluation);
				return;
			}
			if (materialized != null)
			{
				replaceActivePathfinderLocked(active, materialized);
			}
			recordPlannerComparison(evaluation);
			recordCanaryOutcome(
				evaluation.comparison,
				elapsedNanos(canaryPlanningStarted),
				evaluation.comparison.getLocalSearchNanos());
		}
	}

	/** Package-private selection seam for cave lifecycle policy regressions. */
	static Pathfinder selectCaveRoute(
		Pathfinder normal,
		Pathfinder walkingOnly,
		Set<WorldPoint> targets,
		int reachedDistance)
	{
		boolean normalAvailable = normal != null && !normal.getPath().isEmpty();
		boolean walkingAvailable = walkingOnly != null && !walkingOnly.getPath().isEmpty();
		if (!walkingAvailable)
		{
			return normalAvailable ? normal : walkingOnly;
		}
		WorldPoint endpoint = walkingOnly.getPath().get(walkingOnly.getPath().size() - 1);
		boolean walkingReachesTarget = targets.stream()
			.anyMatch(target -> target.getPlane() == endpoint.getPlane()
				&& target.distanceTo2D(endpoint) <= reachedDistance);
		if (walkingReachesTarget
			&& normalAvailable
			&& normal.getPath().size() >= walkingOnly.getPath().size())
		{
			return walkingOnly;
		}
		return normalAvailable ? normal : walkingOnly;
	}

	// ------------------------------------------------------------------
	// Microbot-owned synchronous planning contract
	// ------------------------------------------------------------------

	/**
	 * Calculate a route without publishing it as the active walker pathfinder.
	 *
	 * <p>The shared configuration and its transport snapshots are mutable, so synchronous searches are
	 * serialized with walker start/cancel transitions. A request-level bank-item policy is temporary and
	 * always restored, including when refresh or pathfinding fails. This operation must run on a script or
	 * worker thread because refresh and search are blocking.</p>
	 */
	public static Rs2RouteResult plan(Rs2RouteRequest request)
	{
		return calculate(request);
	}

	private static Rs2RouteResult calculate(Rs2RouteRequest request)
	{
		Objects.requireNonNull(request, "request");
		if (Microbot.getClientThread() != null && Microbot.getClientThread().isClientThread())
		{
			throw new IllegalStateException("synchronous route planning must not run on the client thread");
		}
		PathfinderConfig config = getPathfinderConfig();
		if (config == null)
		{
			throw new IllegalStateException("shortest-path configuration is not initialized");
		}

		synchronized (getPathfinderMutex())
		{
			Boolean requestedBankItems = request.getUseBankItems();
			boolean originalUseBankItems = config.isUseBankItems();
			boolean bankPolicyChanged = requestedBankItems != null
				&& requestedBankItems != originalUseBankItems;
			try
			{
				if (bankPolicyChanged)
				{
					config.setUseBankItems(requestedBankItems);
				}
				if (shouldRefresh(request, config))
				{
					config.refresh(request.getRefreshTarget());
				}
				Rs2RouteRequest resolved = resolvePolicy(request, config);
				Rs2PlanningSnapshot snapshot = resolvePlanningSnapshot(resolved, config);
				long canaryPlanningStarted = System.nanoTime();
				Rs2RouteResult local = localPlanner(config).plan(resolved, snapshot);
				PlannerSelectionMode plannerMode = config.getPlannerSelectionMode();
				if (isF2pCanary(plannerMode, resolved))
				{
					PlannerEvaluation evaluation = evaluateUpstream(
						resolved,
						snapshot,
						local,
						Rs2PlannerShadowContext.Invocation.SYNCHRONOUS_QUERY,
						false);
					recordPlannerComparison(evaluation);
					recordCanaryOutcome(
						evaluation.comparison,
						elapsedNanos(canaryPlanningStarted),
						evaluation.comparison.getLocalSearchNanos());
					return shouldSelectUpstream(evaluation.comparison)
						? evaluation.candidate : local;
				}
				if (plannerMode == PlannerSelectionMode.SHADOW)
				{
					submitUpstreamShadow(
						resolved,
						snapshot,
						local,
						Rs2PlannerShadowContext.Invocation.SYNCHRONOUS_QUERY,
						false);
				}
				return local;
			}
			finally
			{
				if (bankPolicyChanged)
				{
					config.setUseBankItems(originalUseBankItems);
					config.refresh(request.getRefreshTarget());
				}
			}
		}
	}

	private static boolean shouldRefresh(Rs2RouteRequest request, PathfinderConfig config)
	{
		if (request.getUseBankItems() != null
			|| request.getRefreshPolicy() == Rs2RouteRequest.RefreshPolicy.ALWAYS)
		{
			return true;
		}
		return request.getRefreshPolicy() == Rs2RouteRequest.RefreshPolicy.IF_TRANSPORTS_EMPTY
			&& config.getTransports().isEmpty();
	}

	/** Package-private calculation seam for headless tests with an isolated configuration. */
	static Rs2RouteResult planWithConfig(Rs2RouteRequest request, PathfinderConfig config)
	{
		Rs2RouteRequest resolved = resolvePolicy(request, config);
		return localPlanner(config).plan(resolved, resolvePlanningSnapshot(resolved, config));
	}

	static Rs2RoutePlanner localPlanner(PathfinderConfig config)
	{
		return new LocalRoutePlanner(config);
	}

	static Rs2RoutePlanner upstreamPlanner()
	{
		UpstreamRoutePlanner delegate = new UpstreamRoutePlanner();
		if (Boolean.getBoolean("microbot.test.mode")
			&& Boolean.getBoolean("microbot.test.walker.forceUpstreamPlannerFailure"))
		{
			return new Rs2RoutePlanner()
			{
				@Override
				public String getEngineId()
				{
					return delegate.getEngineId();
				}

				@Override
				public Rs2RouteResult plan(
					Rs2RouteRequest request, Rs2PlanningSnapshot snapshot)
				{
					throw new IllegalStateException("synthetic upstream rollout failure");
				}
			};
		}
		return delegate;
	}

	public static Optional<Rs2PlannerShadowComparison> getLastShadowComparison()
	{
		return Optional.ofNullable(lastShadowComparison);
	}

	/**
	 * Most recently completed semantic match whose exact walking shape differed.
	 *
	 * <p>Unlike {@link #getLastShadowComparison()}, this process-lifetime diagnostic survives active-route
	 * teardown so a settled harness snapshot can still classify a non-zero aggregate shape-difference count.</p>
	 */
	public static Optional<Rs2PlannerShadowComparison> getLastRouteShapeDifference()
	{
		return Optional.ofNullable(lastRouteShapeDifference);
	}

	/**
	 * Most recently completed semantic divergence.
	 *
	 * <p>This process-lifetime diagnostic deliberately survives active-route teardown. It contains only the
	 * coordinate-free comparison summary exposed by the shadow endpoint, so a harness can explain a non-zero
	 * divergence counter without retaining the player's route.</p>
	 */
	public static Optional<Rs2PlannerShadowComparison> getLastDivergence()
	{
		return Optional.ofNullable(lastDivergence);
	}

	/**
	 * Most recently completed upstream planner failure, retained for the process lifetime.
	 *
	 * <p>The serialized comparison exposes only the exception class name, never its message or route data.</p>
	 */
	public static Optional<Rs2PlannerShadowComparison> getLastPlannerFailure()
	{
		return Optional.ofNullable(lastPlannerFailure);
	}

	public static boolean isUpstreamPlannerShadowEnabled()
	{
		PathfinderConfig config = getPathfinderConfig();
		return config != null && config.getPlannerSelectionMode().comparisonEnabled();
	}

	public static PlannerSelectionMode getPlannerSelectionMode()
	{
		PathfinderConfig config = getPathfinderConfig();
		return config == null ? PlannerSelectionMode.LOCAL : config.getPlannerSelectionMode();
	}

	public static String getUpstreamPlannerEngineId()
	{
		return upstreamPlanner().getEngineId();
	}

	/** Process-lifetime counters for bounded, non-authoritative shadow evidence. */
	public static Rs2PlannerShadowStats getShadowStats()
	{
		synchronized (shadowEvidenceMutex)
		{
			EnumMap<Rs2PlannerShadowContext.Coverage, Rs2PlannerShadowCoverageStats>
				coverage = new EnumMap<>(Rs2PlannerShadowContext.Coverage.class);
			for (Rs2PlannerShadowContext.Coverage value
				: Rs2PlannerShadowContext.Coverage.values())
			{
				long[] outcomes = shadowCoverageOutcomes[value.ordinal()];
				coverage.put(value, new Rs2PlannerShadowCoverageStats(
					outcomes[Rs2PlannerShadowComparison.Status.MATCH.ordinal()],
					outcomes[Rs2PlannerShadowComparison.Status.DIVERGENCE.ordinal()],
					outcomes[Rs2PlannerShadowComparison.Status.FAILED.ordinal()]));
			}
			EnumMap<Rs2TransportExecutor, Rs2PlannerShadowCoverageStats>
				transportExecutors = new EnumMap<>(Rs2TransportExecutor.class);
			for (Rs2TransportExecutor value : Rs2TransportExecutor.values())
			{
				long[] outcomes = shadowTransportExecutorOutcomes[value.ordinal()];
				transportExecutors.put(value, new Rs2PlannerShadowCoverageStats(
					outcomes[Rs2PlannerShadowComparison.Status.MATCH.ordinal()],
					outcomes[Rs2PlannerShadowComparison.Status.DIVERGENCE.ordinal()],
					outcomes[Rs2PlannerShadowComparison.Status.FAILED.ordinal()]));
			}
			EnumMap<Rs2TransportType, Rs2PlannerShadowCoverageStats>
				transportTypes = new EnumMap<>(Rs2TransportType.class);
			for (Rs2TransportType value : Rs2TransportType.values())
			{
				long[] outcomes = shadowTransportTypeOutcomes[value.ordinal()];
				transportTypes.put(value, new Rs2PlannerShadowCoverageStats(
					outcomes[Rs2PlannerShadowComparison.Status.MATCH.ordinal()],
					outcomes[Rs2PlannerShadowComparison.Status.DIVERGENCE.ordinal()],
					outcomes[Rs2PlannerShadowComparison.Status.FAILED.ordinal()]));
			}
			return new Rs2PlannerShadowStats(
				shadowSubmitted,
				shadowCompleted,
				shadowMatches,
				shadowDivergences,
				shadowFailures,
				shadowStaleResults,
				shadowDiscarded,
				shadowRouteShapeDifferences,
				upstreamCanarySelections,
				localFallbackDivergences,
				localFallbackFailures,
				shadowEvidenceStartedAtEpochMillis,
				coverage,
				transportExecutors,
				transportTypes,
				new Rs2WalkerShadowExecutionStats(
					shadowWalkerArrivals,
					shadowWalkerUnreachable,
					shadowWalkerExits,
					shadowRecoveryArrivals,
					shadowRecoveryUnreachable,
					shadowRecoveryExits),
				new Rs2PlannerCanaryPerformanceStats(
					canaryPlanningSamples,
					canaryPlanningNanosTotal,
					canaryPlanningNanosMax,
					canaryLocalSearchNanosTotal,
					canaryLocalSearchNanosMax,
					canaryUpstreamSearchSamples,
					canaryUpstreamSearchNanosTotal,
					canaryUpstreamSearchNanosMax));
		}
	}

	/**
	 * Whether the current logical active route was admitted to planner comparison.
	 *
	 * <p>This is captured when a blocking walk first consumes a generation-matched ready route because arrival
	 * normally clears the active route before the terminal outcome is recorded. In F2P-canary mode it is false
	 * for members-policy routes, even though the process-wide planner mode still has comparison capability
	 * enabled.</p>
	 */
	static boolean isActiveRouteComparisonEligible()
	{
		return activeRouteComparisonEligible;
	}

	/** Generation-matched form used when a walker consumes a previously captured active-route snapshot. */
	static boolean isActiveRouteComparisonEligible(long routeGeneration)
	{
		synchronized (getPathfinderMutex())
		{
			return activeRouteGeneration.get() == routeGeneration
				&& activeRouteComparisonEligible;
		}
	}

	/** Record one blocking walk's terminal result for a route that actually entered planner comparison. */
	static void recordShadowWalkerOutcome(
		WalkerState state, boolean recoveryTriggered, boolean comparisonEligible)
	{
		Objects.requireNonNull(state, "state");
		if (!comparisonEligible || state == WalkerState.MOVING)
		{
			return;
		}
		synchronized (shadowEvidenceMutex)
		{
			switch (state)
			{
				case ARRIVED: shadowWalkerArrivals++; break;
				case UNREACHABLE: shadowWalkerUnreachable++; break;
				case EXIT: shadowWalkerExits++; break;
				default: throw new IllegalStateException("unhandled walker state " + state);
			}
			if (recoveryTriggered)
			{
				switch (state)
				{
					case ARRIVED: shadowRecoveryArrivals++; break;
					case UNREACHABLE: shadowRecoveryUnreachable++; break;
					case EXIT: shadowRecoveryExits++; break;
					default: throw new IllegalStateException("unhandled walker state " + state);
				}
			}
		}
	}

	private static void recordShadowDiscarded()
	{
		synchronized (shadowEvidenceMutex)
		{
			shadowDiscarded++;
		}
	}

	private static void submitUpstreamShadow(
		Rs2RouteRequest request,
		Rs2PlanningSnapshot snapshot,
		Rs2RouteResult local,
		Rs2PlannerShadowContext.Invocation invocation,
		boolean walkingOnlySelected)
	{
		Rs2PlannerShadowContext context = Rs2PlannerShadowContext.from(
			invocation, walkingOnlySelected, request, local);
		PlannerComparisonTicket ticket = beginPlannerComparison(context);
		SHADOW_EXECUTOR.execute(() -> recordPlannerComparison(
			evaluateUpstream(ticket, request, snapshot, local)));
	}

	private static PlannerEvaluation evaluateUpstream(
		Rs2RouteRequest request,
		Rs2PlanningSnapshot snapshot,
		Rs2RouteResult local,
		Rs2PlannerShadowContext.Invocation invocation,
		boolean walkingOnlySelected)
	{
		Rs2PlannerShadowContext context = Rs2PlannerShadowContext.from(
			invocation, walkingOnlySelected, request, local);
		return evaluateUpstream(beginPlannerComparison(context), request, snapshot, local);
	}

	private static PlannerComparisonTicket beginPlannerComparison(
		Rs2PlannerShadowContext context)
	{
		synchronized (shadowEvidenceMutex)
		{
			long generation = ++shadowGeneration;
			shadowSubmitted++;
			lastShadowComparison = null;
			return new PlannerComparisonTicket(generation, context);
		}
	}

	private static PlannerEvaluation evaluateUpstream(
		PlannerComparisonTicket ticket,
		Rs2RouteRequest request,
		Rs2PlanningSnapshot snapshot,
		Rs2RouteResult local)
	{
		Rs2RoutePlanner planner = upstreamPlanner();
		try
		{
			Rs2RouteResult candidate = planner.plan(request, snapshot);
			return new PlannerEvaluation(
				ticket,
				local,
				candidate,
				Rs2PlannerShadowComparison.compare(
					planner.getEngineId(), ticket.context, local, candidate));
		}
		catch (RuntimeException failure)
		{
			return new PlannerEvaluation(
				ticket,
				local,
				null,
				Rs2PlannerShadowComparison.failed(
					planner.getEngineId(), ticket.context, local, failure));
		}
	}

	private static void recordPlannerComparison(PlannerEvaluation evaluation)
	{
		Rs2PlannerShadowComparison comparison = evaluation.comparison;
		synchronized (shadowEvidenceMutex)
		{
			switch (comparison.getStatus())
			{
				case MATCH: shadowMatches++; break;
				case DIVERGENCE:
					shadowDivergences++;
					lastDivergence = comparison;
					break;
				case FAILED:
					shadowFailures++;
					lastPlannerFailure = comparison;
					break;
				default: throw new IllegalStateException(
					"unhandled shadow comparison status " + comparison.getStatus());
			}
			for (Rs2PlannerShadowContext.Coverage value
				: comparison.getContext().getCoverage())
			{
				shadowCoverageOutcomes[value.ordinal()][comparison.getStatus().ordinal()]++;
			}
			for (Rs2TransportExecutor value
				: comparison.getContext().getTransportExecutors())
			{
				shadowTransportExecutorOutcomes[value.ordinal()]
					[comparison.getStatus().ordinal()]++;
			}
			for (Rs2TransportType value : comparison.getContext().getTransportTypes())
			{
				shadowTransportTypeOutcomes[value.ordinal()]
					[comparison.getStatus().ordinal()]++;
			}
			if (comparison.getStatus() != Rs2PlannerShadowComparison.Status.FAILED
				&& !comparison.isPathMatches())
			{
				shadowRouteShapeDifferences++;
				lastRouteShapeDifference = comparison;
			}
			shadowCompleted++;
			if (shadowGeneration == evaluation.ticket.generation)
			{
				lastShadowComparison = comparison;
			}
			else
			{
				shadowStaleResults++;
			}
		}
	}

	private static void recordCanaryOutcome(
		Rs2PlannerShadowComparison comparison,
		long planningNanos,
		long localPlanningNanos)
	{
		synchronized (shadowEvidenceMutex)
		{
			switch (comparison.getStatus())
			{
				case MATCH: upstreamCanarySelections++; break;
				case DIVERGENCE: localFallbackDivergences++; break;
				case FAILED: localFallbackFailures++; break;
				default: throw new IllegalStateException(
					"unhandled canary comparison status " + comparison.getStatus());
			}
			if (planningNanos < 0L || localPlanningNanos < 0L)
			{
				throw new IllegalArgumentException(
					"canary planning and local search durations must be available");
			}
			canaryPlanningSamples++;
			canaryPlanningNanosTotal = saturatedAdd(
				canaryPlanningNanosTotal, planningNanos);
			canaryPlanningNanosMax = Math.max(canaryPlanningNanosMax, planningNanos);
			canaryLocalSearchNanosTotal = saturatedAdd(
				canaryLocalSearchNanosTotal, localPlanningNanos);
			canaryLocalSearchNanosMax = Math.max(
				canaryLocalSearchNanosMax, localPlanningNanos);
			long upstreamSearchNanos = comparison.getShadowSearchNanos();
			if (upstreamSearchNanos >= 0L)
			{
				canaryUpstreamSearchSamples++;
				canaryUpstreamSearchNanosTotal = saturatedAdd(
					canaryUpstreamSearchNanosTotal, upstreamSearchNanos);
				canaryUpstreamSearchNanosMax = Math.max(
					canaryUpstreamSearchNanosMax, upstreamSearchNanos);
			}
		}
	}

	private static long elapsedNanos(long started)
	{
		return Math.max(0L, System.nanoTime() - started);
	}

	private static long combinedSearchNanos(Pathfinder... pathfinders)
	{
		long total = 0L;
		for (Pathfinder pathfinder : pathfinders)
		{
			long searchNanos = activeSearchNanos(pathfinder);
			if (searchNanos < 0L)
			{
				return Rs2RouteMetrics.UNAVAILABLE;
			}
			total = saturatedAdd(total, searchNanos);
		}
		return total;
	}

	private static long saturatedAdd(long left, long right)
	{
		if (right > Long.MAX_VALUE - left)
		{
			return Long.MAX_VALUE;
		}
		return left + right;
	}

	/** A route-shape-only difference is a semantic match and remains eligible for the canary. */
	static boolean shouldSelectUpstream(Rs2PlannerShadowComparison comparison)
	{
		return comparison != null
			&& comparison.getStatus() == Rs2PlannerShadowComparison.Status.MATCH;
	}

	static Rs2RouteRequest resolvePolicy(
		Rs2RouteRequest request, PathfinderConfig config)
	{
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(config, "config");
		EnumSet<Rs2TransportType> enabledTypes = EnumSet.noneOf(Rs2TransportType.class);
		for (TransportType type : config.getEnabledTransportTypes())
		{
			enabledTypes.add(mapTransportType(type));
		}
		Set<WorldPoint> restrictedPoints = new LinkedHashSet<>();
		for (int packed : config.getRestrictedPointsPacked())
		{
			restrictedPoints.add(WorldPointUtil.unpackWorldPoint(packed));
		}
		Rs2RoutePolicy policy = new Rs2RoutePolicy(
			config.isUseBankItems(),
			config.isAvoidWilderness(),
			config.isAvoidDangerousNpcs(),
			config.isIgnoreTeleportAndItems(),
			Rs2Walker.disableTeleports,
			config.isMembersWorld(),
			config.getLiveCollisionOverlay().isEnabled(),
			config.getCalculationCutoffMillis(),
			config.getDistanceBeforeUsingTeleport(),
			Rs2RoutePolicy.TeleportationItemMode.valueOf(
				config.getTeleportationItemPolicy().name()),
			enabledTypes,
			restrictedPoints);
		return request.withPolicy(policy);
	}

	static Rs2PlanningSnapshot resolvePlanningSnapshot(
		Rs2RouteRequest request, PathfinderConfig config)
	{
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(config, "config");
		Rs2RoutePolicy policy = request.getPolicy().orElseThrow(
			() -> new IllegalArgumentException("planning snapshot requires a resolved policy"));
		Set<Transport> admitted = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		Map<WorldPoint, Set<Transport>> activeTransports = config.getTransports();
		if (activeTransports != null)
		{
			for (Set<Transport> values : activeTransports.values())
			{
				admitted.addAll(values);
			}
		}
		Set<Transport> usableTeleports = config.getUsableTeleportsSnapshot();
		if (usableTeleports != null)
		{
			admitted.addAll(usableTeleports);
		}
		List<Rs2TransportEdge> edges = new ArrayList<>(admitted.size());
		for (Transport transport : admitted)
		{
			edges.add(toTransportEdge(transport));
		}
		net.runelite.client.plugins.microbot.shortestpath.pathfinder.live.LiveCollisionOverlay overlay =
			config.getLiveCollisionOverlay();
		LiveCollisionView live = overlay == null ? null : overlay.current();
		Rs2PlanningSnapshot.CollisionOverride collision = live == null ? null : live::edge;
		Set<Long> blocked = config.getBlockedTransportEdgesPacked();
		return new Rs2PlanningSnapshot(
			policy,
			edges,
			collision,
			blocked == null ? Collections.emptySet() : new LinkedHashSet<>(blocked),
			config::isDangerousAdjacentTile);
	}

	private static final class LocalRoutePlanner implements Rs2RoutePlanner
	{
		private final PathfinderConfig config;

		private LocalRoutePlanner(PathfinderConfig config)
		{
			this.config = Objects.requireNonNull(config, "config");
		}

		@Override
		public String getEngineId()
		{
			return "microbot-local";
		}

		@Override
		public Rs2RouteResult plan(Rs2RouteRequest request, Rs2PlanningSnapshot snapshot)
		{
			Objects.requireNonNull(request, "request");
			Objects.requireNonNull(snapshot, "snapshot");
			Rs2RoutePolicy policy = request.getPolicy().orElseThrow(
				() -> new IllegalArgumentException("route planner requires a resolved policy"));
			if (snapshot.getPolicy() != policy)
			{
				throw new IllegalArgumentException("route request and planning snapshot policy differ");
			}
			long started = System.nanoTime();
			Pathfinder pathfinder = new Pathfinder(
				config, request.getStart(), request.getTargets());
			pathfinder.run();
			long elapsed = System.nanoTime() - started;
			return snapshot(pathfinder, elapsed);
		}
	}

	/** Transitional concrete view for legacy overlays; the immutable route remains authoritative. */
	static Pathfinder materializeUpstreamRoute(
		Rs2RouteResult result, PathfinderConfig config)
	{
		Objects.requireNonNull(result, "result");
		Objects.requireNonNull(config, "config");
		List<WorldPoint> path = result.getPath();
		List<Rs2RouteStep> steps = result.getSteps();
		if (steps.size() != Math.max(0, path.size() - 1))
		{
			throw new IllegalArgumentException(
				"upstream route steps must align with the materialized path");
		}
		List<Transport> transportsByStep = new ArrayList<>(steps.size());
		for (int i = 0; i < steps.size(); i++)
		{
			Rs2RouteStep step = steps.get(i);
			if (!path.get(i).equals(step.getFrom())
				|| !path.get(i + 1).equals(step.getTo()))
			{
				throw new IllegalArgumentException(
					"upstream route step endpoints must align with the materialized path");
			}
			if (!step.isTransport())
			{
				transportsByStep.add(null);
				continue;
			}
			Object sourceIdentity = step.getTransport().orElseThrow(
				IllegalStateException::new).getSourceIdentity();
			if (!(sourceIdentity instanceof Transport))
			{
				throw new IllegalArgumentException(
					"upstream transport step is missing exact local execution identity");
			}
			transportsByStep.add((Transport) sourceIdentity);
		}

		Rs2RouteMetrics metrics = result.getMetrics();
		return Pathfinder.completedRoute(
			config,
			result.getStart(),
			result.getTargets(),
			path,
			transportsByStep,
			mapTermination(result.getTerminationReason()),
			metrics.getPathCost(),
			metrics.getSearchNanos(),
			metrics.getNodesChecked(),
			metrics.getTransportsChecked(),
			metrics.getLiveCollisionEdgesChecked());
	}

	/**
	 * Immutable view of the completed path currently published to the walker.
	 *
	 * <p>The source pathfinder identity is checked on every call. Replanning therefore invalidates the
	 * cached value even if the new route happens to contain the same points. In-flight pathfinders do not
	 * publish partial executor contracts.</p>
	 */
	public static Optional<Rs2RouteResult> getActiveRoute()
	{
		Pathfinder source = getPathfinder();
		Future<?> activeFuture = getPathfinderFuture();
		if (source == null || !source.isDone()
			|| (activeFuture != null && !activeFuture.isDone()))
		{
			return Optional.empty();
		}
		Rs2RouteResult snapshot = activeRouteSnapshot;
		if (activeRouteSnapshotSource == source && snapshot != null)
		{
			return Optional.of(snapshot);
		}
		synchronized (getPathfinderMutex())
		{
			activeFuture = getPathfinderFuture();
			if (getPathfinder() != source || !source.isDone()
				|| (activeFuture != null && !activeFuture.isDone()))
			{
				return Optional.empty();
			}
			snapshot = snapshot(source, Rs2RouteMetrics.UNAVAILABLE);
			activeRouteSnapshotSource = source;
			activeRouteSnapshot = snapshot;
			return Optional.of(snapshot);
		}
	}

	private static Rs2RouteResult snapshot(Pathfinder pathfinder, long elapsed)
	{
		Pathfinder.PathfinderStats stats = pathfinder.getStats();
		List<WorldPoint> path = pathfinder.getPath() == null
			? Collections.emptyList()
			: pathfinder.getPath();
		List<Rs2RouteStep> steps = new ArrayList<>();
		for (PathEdge edge : pathfinder.getPathEdges())
		{
			if (edge.isTransport())
			{
				steps.add(Rs2RouteStep.transport(
					edge.getFrom(), edge.getTo(), toTransportEdge(edge.getTransport())));
			}
			else
			{
				steps.add(Rs2RouteStep.walk(edge.getFrom(), edge.getTo()));
			}
		}
		return new Rs2RouteResult(
			pathfinder.getStart(),
			pathfinder.getTargets(),
			path,
			steps,
			mapTermination(pathfinder.getTerminationReason()),
			new Rs2RouteMetrics(
				elapsed,
				pathfinder.getSelectedPathCost(),
				stats == null ? Rs2RouteMetrics.UNAVAILABLE : stats.getNodesChecked(),
				stats == null ? Rs2RouteMetrics.UNAVAILABLE : stats.getTransportsChecked(),
				stats == null ? Rs2RouteMetrics.UNAVAILABLE
					: stats.getLiveCollisionEdgesChecked()));
	}

	private static long activeSearchNanos(Pathfinder pathfinder)
	{
		Pathfinder.PathfinderStats stats = pathfinder.getStats();
		return stats == null
			? Rs2RouteMetrics.UNAVAILABLE : stats.getElapsedTimeNanos();
	}

	/**
	 * Exact local execution selection for the runtime walker.
	 *
	 * <p>The immutable edge and its executor are the engine-independent contract. The local transport is
	 * an opaque implementation payload for the existing handlers (not a public planning value); POH in
	 * particular carries executable subtype behavior. It is always the exact object selected by search,
	 * never a catalog lookup or origin/destination rematch.</p>
	 */
	static final class ActiveTransportSelection
	{
		private final int pathIndex;
		private final Rs2TransportEdge edge;
		private final Transport localExecutionTransport;

		private ActiveTransportSelection(int pathIndex, Rs2TransportEdge edge, Transport localExecutionTransport)
		{
			this.pathIndex = pathIndex;
			this.edge = edge;
			this.localExecutionTransport = localExecutionTransport;
		}

		int getPathIndex() { return pathIndex; }
		Rs2TransportEdge getEdge() { return edge; }
		Rs2TransportExecutor getExecutor() { return edge.getExecutor(); }
		Transport getLocalExecutionTransport() { return localExecutionTransport; }
		boolean isExecutable() { return getExecutor() != Rs2TransportExecutor.UNSUPPORTED; }
	}

	static Optional<ActiveTransportSelection> getActiveTransportSelection(
		List<WorldPoint> expectedPath, int pathIndex)
	{
		List<ActiveTransportSelection> selections = getActiveTransportSelections(expectedPath);
		return selections.stream()
			.filter(selection -> selection.getPathIndex() == pathIndex)
			.findFirst();
	}

	static List<ActiveTransportSelection> getActiveTransportSelections(List<WorldPoint> expectedPath)
	{
		Pathfinder source = getPathfinder();
		List<ActiveTransportSelection> selections = getTransportSelections(source, expectedPath);
		return getPathfinder() == source ? selections : Collections.emptyList();
	}

	static Optional<Rs2TransportEdge> getActiveTransportEdge(WorldPoint from, WorldPoint to)
	{
		if (from == null || to == null)
		{
			return Optional.empty();
		}
		return getActiveRoute().flatMap(route -> route.getTransportEdge(from, to));
	}

	/** Package-private pure seam for route-selection regressions. */
	static List<ActiveTransportSelection> getTransportSelections(
		Pathfinder source, List<WorldPoint> expectedPath)
	{
		if (source == null || !source.isDone() || expectedPath == null)
		{
			return Collections.emptyList();
		}
		List<WorldPoint> actualPath = source.getPath();
		if (actualPath == null || !actualPath.equals(expectedPath))
		{
			return Collections.emptyList();
		}
		List<PathEdge> pathEdges = source.getPathEdges();
		if (pathEdges.size() != Math.max(0, actualPath.size() - 1))
		{
			return Collections.emptyList();
		}
		List<ActiveTransportSelection> selections = new ArrayList<>();
		for (int i = 0; i < pathEdges.size(); i++)
		{
			PathEdge pathEdge = pathEdges.get(i);
			Transport selected = pathEdge.getTransport();
			if (selected == null)
			{
				continue;
			}
			if (!actualPath.get(i).equals(pathEdge.getFrom())
				|| !actualPath.get(i + 1).equals(pathEdge.getTo()))
			{
				return Collections.emptyList();
			}
			selections.add(new ActiveTransportSelection(i, toTransportEdge(selected), selected));
		}
		return List.copyOf(selections);
	}

	private static Rs2TransportEdge toTransportEdge(Transport transport)
	{
		List<Rs2TransportItemRequirement> itemRequirements = new ArrayList<>();
		for (TransportItemRequirement requirement : transport.getItemRequirements())
		{
			itemRequirements.add(new Rs2TransportItemRequirement(
				requirement.getAlternatives(),
				requirement.getStaffAlternatives(),
				requirement.getOffhandAlternatives(),
				requirement.isRuneOnly()));
		}
		return new Rs2TransportEdge(
			transport.getOrigin(),
			transport.getDestination(),
			mapTransportType(transport.getType()),
			mapExecutor(TransportExecutionRegistry.executorFor(transport).orElse(null)),
			mapTerminalTravelMode(transport),
			transport.getDisplayInfo(),
			transport.getAction(),
			transport.getName(),
			transport.getObjectId(),
			transport.getDuration(),
			TransportType.isTeleport(transport.getType(), transport.getOrigin()),
			transport.isConsumable(),
			transport.isMembers(),
			transport.getMaxWildernessLevel(),
			transport.getCurrencyName(),
			transport.getCurrencyAmount(),
			itemRequirements,
			Arrays.stream(transport.getSkillLevels()).anyMatch(level -> level > 0),
			transport.isQuestLocked(),
			!transport.getVarbits().isEmpty() || !transport.getVarplayers().isEmpty(),
			transport);
	}

	private static Rs2TerminalTravelMode mapTerminalTravelMode(Transport transport)
	{
		return TransportExecutionRegistry.terminalTravelModeFor(transport)
			.map(mode -> Rs2TerminalTravelMode.valueOf(mode.name()))
			.orElse(Rs2TerminalTravelMode.UNSUPPORTED);
	}

	private static Rs2TransportExecutor mapExecutor(TransportExecutionRegistry.Executor executor)
	{
		if (executor == null)
		{
			return Rs2TransportExecutor.UNSUPPORTED;
		}
		try
		{
			return Rs2TransportExecutor.valueOf(executor.name());
		}
		catch (IllegalArgumentException ignored)
		{
			return Rs2TransportExecutor.UNSUPPORTED;
		}
	}

	private static Rs2TransportType mapTransportType(TransportType type)
	{
		if (type == null)
		{
			return Rs2TransportType.UNKNOWN;
		}
		try
		{
			return Rs2TransportType.valueOf(type.name());
		}
		catch (IllegalArgumentException ignored)
		{
			return Rs2TransportType.UNKNOWN;
		}
	}

	private static Rs2RouteTermination mapTermination(PathTerminationReason reason)
	{
		if (reason == null)
		{
			return Rs2RouteTermination.FAILED;
		}
		switch (reason)
		{
			case TARGET_REACHED:
				return Rs2RouteTermination.TARGET_REACHED;
			case SEARCH_EXHAUSTED:
				return Rs2RouteTermination.SEARCH_EXHAUSTED;
			case CUTOFF_REACHED:
				return Rs2RouteTermination.CUTOFF_REACHED;
			case CANCELLED:
				return Rs2RouteTermination.CANCELLED;
			case FAILED:
			default:
				return Rs2RouteTermination.FAILED;
		}
	}

	private static PathTerminationReason mapTermination(Rs2RouteTermination reason)
	{
		if (reason == null)
		{
			return PathTerminationReason.FAILED;
		}
		switch (reason)
		{
			case TARGET_REACHED:
				return PathTerminationReason.TARGET_REACHED;
			case SEARCH_EXHAUSTED:
				return PathTerminationReason.SEARCH_EXHAUSTED;
			case CUTOFF_REACHED:
				return PathTerminationReason.CUTOFF_REACHED;
			case CANCELLED:
				return PathTerminationReason.CANCELLED;
			case FAILED:
			default:
				return PathTerminationReason.FAILED;
		}
	}

	// ------------------------------------------------------------------
	// Config
	// ------------------------------------------------------------------

	/**
	 * Whether at least one tile within {@code distance} of {@code target} is standable in the
	 * configured static collision map.
	 *
	 * <p>This check deliberately abstains when the configuration, target or map region is absent.
	 * Instances and newly added regions must be left to the planner rather than rejected as blocked.</p>
	 */
	public static boolean hasWalkableTileWithin(WorldPoint target, int distance)
	{
		PathfinderConfig config = getPathfinderConfig();
		return hasWalkableTileWithin(config == null ? null : config.getMap(), target, distance);
	}

	/** Package-private collision seam for deterministic headless tests. */
	static boolean hasWalkableTileWithin(CollisionMap map, WorldPoint target, int distance)
	{
		if (map == null || target == null)
		{
			return true;
		}
		if (!map.hasRegion(target.getX(), target.getY()))
		{
			return true;
		}
		int radius = Math.max(0, distance);
		for (int dx = -radius; dx <= radius; dx++)
		{
			for (int dy = -radius; dy <= radius; dy++)
			{
				int x = target.getX() + dx;
				int y = target.getY() + dy;
				if (!map.hasRegion(x, y) || !map.isBlocked(x, y, target.getPlane()))
				{
					return true;
				}
			}
		}
		return false;
	}

	/** Nearest mapped standable tile to {@code target} within {@code maxRadius}, or {@code null}. */
	public static WorldPoint nearestWalkableTile(WorldPoint target, int maxRadius)
	{
		PathfinderConfig config = getPathfinderConfig();
		return nearestWalkableTile(config == null ? null : config.getMap(), target, maxRadius);
	}

	/** Package-private collision seam for deterministic headless tests. */
	static WorldPoint nearestWalkableTile(CollisionMap map, WorldPoint target, int maxRadius)
	{
		if (map == null || target == null)
		{
			return null;
		}
		for (int radius = 1; radius <= Math.max(0, maxRadius); radius++)
		{
			for (int dx = -radius; dx <= radius; dx++)
			{
				for (int dy = -radius; dy <= radius; dy++)
				{
					if (Math.max(Math.abs(dx), Math.abs(dy)) != radius)
					{
						continue;
					}
					int x = target.getX() + dx;
					int y = target.getY() + dy;
					if (map.hasRegion(x, y) && !map.isBlocked(x, y, target.getPlane()))
					{
						return new WorldPoint(x, y, target.getPlane());
					}
				}
			}
		}
		return null;
	}

	/** Refresh transport and restriction policy without exposing the mutable configuration. */
	public static boolean refreshPlanningConfiguration()
	{
		return refreshPlanningConfiguration(null);
	}

	/** Refresh transport and restriction policy for an optional route target. */
	public static boolean refreshPlanningConfiguration(WorldPoint target)
	{
		PathfinderConfig config = getPathfinderConfig();
		if (config == null)
		{
			return false;
		}
		synchronized (getPathfinderMutex())
		{
			config.refresh(target);
		}
		return true;
	}

	/** Invalidate cached transport-policy snapshots so the next refresh observes external state. */
	public static boolean invalidateTransportRefreshCache()
	{
		PathfinderConfig config = getPathfinderConfig();
		if (config == null)
		{
			return false;
		}
		synchronized (getPathfinderMutex())
		{
			config.invalidateTransportRefreshCache();
		}
		return true;
	}

	/** Record a stable walking edge failure in the planner's learned-block store. */
	public static boolean learnBlockedEdge(WorldPoint origin, WorldPoint destination, String reason)
	{
		PathfinderConfig config = getPathfinderConfig();
		if (config == null)
		{
			return false;
		}
		synchronized (getPathfinderMutex())
		{
			return config.learnBlockedEdge(origin, destination, reason);
		}
	}

	/** Whether runtime recovery policy should avoid this dangerous-NPC adjacency tile. */
	public static boolean shouldAvoidDangerousTile(WorldPoint tile)
	{
		PathfinderConfig config = getPathfinderConfig();
		return config != null
			&& config.isAvoidDangerousNpcs()
			&& tile != null
			&& config.isDangerousAdjacentTile(WorldPointUtil.packWorldPoint(tile));
	}

	/** Whether the currently refreshed planning policy permits spirit-tree travel. */
	public static boolean isSpiritTreeTravelEnabled()
	{
		PathfinderConfig config = getPathfinderConfig();
		return config != null && config.isUseSpiritTrees();
	}

	/** Planner-consistent Wilderness classification without exposing its implementation class. */
	public static boolean isInWilderness(WorldPoint point)
	{
		return point != null && PathfinderConfig.isInWilderness(point);
	}

	/**
	 * Whether {@code itemId} belongs to a currently known item-teleport requirement.
	 *
	 * <p>An empty catalog is refreshed under the lifecycle mutex before it is inspected. Additional
	 * compatibility IDs cover items such as fairy-ring staves that are not ordinary teleport rows.</p>
	 */
	public static boolean isTeleportItem(int itemId, int... additionalItemIds)
	{
		if (additionalItemIds != null)
		{
			for (int additionalItemId : additionalItemIds)
			{
				if (itemId == additionalItemId)
				{
					return true;
				}
			}
		}
		PathfinderConfig config = getPathfinderConfig();
		if (config == null)
		{
			return false;
		}
		synchronized (getPathfinderMutex())
		{
			if (config.getAllTransports().isEmpty())
			{
				config.refresh();
			}
			return config.getAllTransports().values().stream()
				.flatMap(Set::stream)
				.filter(transport -> TransportType.isTeleport(
					transport.getType(), transport.getOrigin()))
				.flatMap(transport -> transport.getItemIdRequirements().stream())
				.flatMap(Set::stream)
				.anyMatch(requiredItemId -> requiredItemId == itemId);
		}
	}

	/**
	 * Switch the shared active-route policy from bank visibility to inventory/equipment visibility
	 * after required transport items have been withdrawn, then rebuild the target-specific catalog.
	 */
	public static boolean prepareInventoryOnlyRoute(WorldPoint target)
	{
		PathfinderConfig config = getPathfinderConfig();
		if (config == null)
		{
			return false;
		}
		synchronized (getPathfinderMutex())
		{
			config.setUseBankItems(false);
			config.refresh(target);
		}
		return true;
	}

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

	/**
	 * Whether the static catalog contains at least one transport keyed by {@code origin}.
	 *
	 * <p>This is the planner-independent query used by recovery and obstacle classification. Callers
	 * that only need transport presence must not consume the mutable concrete catalog.</p>
	 */
	public static boolean hasCatalogTransportOrigin(WorldPoint origin)
	{
		return hasCatalogTransportOrigin(ShortestPathPlugin.getTransports(), origin);
	}

	static boolean hasCatalogTransportOrigin(
		Map<WorldPoint, Set<Transport>> transports, WorldPoint origin)
	{
		if (transports == null || origin == null)
		{
			return false;
		}
		Set<Transport> atOrigin = transports.get(origin);
		return atOrigin != null && !atOrigin.isEmpty();
	}

	/** Whether the static catalog contains the exact directed {@code origin -> destination} edge. */
	public static boolean hasCatalogTransportEdge(WorldPoint origin, WorldPoint destination)
	{
		return hasCatalogTransportEdge(ShortestPathPlugin.getTransports(), origin, destination);
	}

	static boolean hasCatalogTransportEdge(
		Map<WorldPoint, Set<Transport>> transports,
		WorldPoint origin,
		WorldPoint destination)
	{
		if (transports == null || origin == null || destination == null)
		{
			return false;
		}
		Set<Transport> atOrigin = transports.get(origin);
		if (atOrigin == null || atOrigin.isEmpty())
		{
			return false;
		}
		return atOrigin.stream()
			.filter(Objects::nonNull)
			.anyMatch(transport -> destination.equals(transport.getDestination()));
	}

	/**
	 * Immutable planner-independent descriptions of every catalog entry keyed by {@code origin}.
	 *
	 * <p>This is intended for catalog classification (for example distinguishing a door row from a
	 * ladder), not route execution. Runtime execution must use the exact edge selected in the active
	 * route so ambiguous same-endpoint transports cannot be rematched incorrectly.</p>
	 */
	public static List<Rs2TransportEdge> getCatalogTransportEdges(WorldPoint origin)
	{
		if (origin == null)
		{
			return Collections.emptyList();
		}
		Map<WorldPoint, Set<Transport>> transports = ShortestPathPlugin.getTransports();
		Set<Transport> source = transports == null ? null : transports.get(origin);
		if (source == null || source.isEmpty())
		{
			catalogEdgeSnapshots.remove(origin);
			return Collections.emptyList();
		}
		CatalogEdgeSnapshot cached = catalogEdgeSnapshots.get(origin);
		if (cached != null && cached.source == source)
		{
			return cached.edges;
		}
		List<Rs2TransportEdge> edges = toCatalogTransportEdges(source);
		catalogEdgeSnapshots.put(origin, new CatalogEdgeSnapshot(source, edges));
		return edges;
	}

	static List<Rs2TransportEdge> getCatalogTransportEdges(
		Map<WorldPoint, Set<Transport>> transports, WorldPoint origin)
	{
		if (transports == null || origin == null)
		{
			return Collections.emptyList();
		}
		return toCatalogTransportEdges(transports.get(origin));
	}

	private static List<Rs2TransportEdge> toCatalogTransportEdges(Set<Transport> atOrigin)
	{
		if (atOrigin == null || atOrigin.isEmpty())
		{
			return Collections.emptyList();
		}
		List<Rs2TransportEdge> result = new ArrayList<>(atOrigin.size());
		for (Transport transport : atOrigin)
		{
			if (transport != null)
			{
				result.add(toTransportEdge(transport));
			}
		}
		return List.copyOf(result);
	}

	/**
	 * Compatibility access to the concrete mutable transport graph.
	 * New code should use the named catalog queries above or exact immutable route steps.
	 */
	@Deprecated
	public static Map<WorldPoint, Set<Transport>> getTransports()
	{
		return ShortestPathPlugin.getTransports();
	}
}
