package net.runelite.client.plugins.microbot.util.walker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportItemRequirement;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Emits local planner results for the opt-in dual-engine comparison harness. */
public final class LocalPlannerComparisonMain
{
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final boolean EMBEDDED_UPSTREAM = Boolean.getBoolean(
		"microbot.planner.embedded-upstream");

	private LocalPlannerComparisonMain()
	{
	}

	public static void main(String[] args) throws Exception
	{
		if (args.length != 2)
		{
			throw new IllegalArgumentException("expected <corpus.json> <output.json>");
		}
		Path corpusPath = Path.of(args[0]).toAbsolutePath().normalize();
		Path outputPath = Path.of(args[1]).toAbsolutePath().normalize();
		PlannerCorpus corpus = readCorpus(corpusPath);
		List<PlannerCaseResult> results = new ArrayList<>();
		for (PlannerCase plannerCase : corpus.cases)
		{
			results.add(run(plannerCase));
		}
		PlannerRun run = new PlannerRun(
			corpus.schemaVersion,
			EMBEDDED_UPSTREAM ? "shortest-path-upstream-embedded" : "microbot-local",
			System.getProperty("microbot.planner.revision", "unknown"),
			results);
		Files.createDirectories(outputPath.getParent());
		Files.writeString(outputPath, GSON.toJson(run) + System.lineSeparator(),
			StandardCharsets.UTF_8);
	}

	private static PlannerCorpus readCorpus(Path path) throws IOException
	{
		PlannerCorpus corpus = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8),
			PlannerCorpus.class);
		if (corpus == null || corpus.schemaVersion != 3 || corpus.cases == null)
		{
			throw new IllegalArgumentException("unsupported or incomplete planner corpus");
		}
		return corpus;
	}

	private static PlannerCaseResult run(PlannerCase plannerCase) throws Exception
	{
		if (!"STATIC_COLLISION_ONLY".equals(plannerCase.policy.transportMode)
			&& !"EXPLICIT_CATALOG".equals(plannerCase.policy.transportMode)
			&& !"BANK_AWARE_EXPLICIT_CATALOG".equals(plannerCase.policy.transportMode))
		{
			return PlannerCaseResult.unsupported(plannerCase.id,
				"unsupported transport policy: " + plannerCase.policy.transportMode);
		}
		if (plannerCase.policy.cutoffMillis <= 0 || plannerCase.policy.cutoffMillis % 600L != 0)
		{
			throw new IllegalArgumentException(
				"comparison cutoff must be a positive whole number of game ticks");
		}

		Catalog catalog = Catalog.from(plannerCase);
		WorldPoint start = plannerCase.start.toWorldPoint();
		WorldPoint target = plannerCase.target.toWorldPoint();
		resetHeapPeaks();
		long heapBefore = usedHeap();
		SearchOutcome outcome = "BANK_AWARE_EXPLICIT_CATALOG".equals(
			plannerCase.policy.transportMode)
			? searchWithBankDetours(plannerCase, catalog, start, target)
			: search(newConfig(plannerCase, catalog.withoutBankByOrigin, false), start, target, false);
		long peakHeapDelta = Math.max(0L, peakHeap() - heapBefore);
		WorldPoint endpoint = outcome.path.isEmpty()
			? null : outcome.path.get(outcome.path.size() - 1);
		return PlannerCaseResult.supported(
			plannerCase.id,
			outcome.termination,
			outcome.reached,
			Point.from(endpoint),
			outcome.path.size(),
			outcome.cost,
			outcome.nodesChecked,
			outcome.transportsChecked,
			outcome.elapsedNanos,
			peakHeapDelta,
			selectedTransports(outcome.edges, catalog),
			outcome.bankVisited);
	}

	private static PathfinderConfig newConfig(
		PlannerCase plannerCase, Map<WorldPoint, Set<Transport>> activeCatalog,
		boolean useBankItems) throws Exception
	{
		PathfinderConfig config = new PathfinderConfig(
			SplitFlagMap.fromResources(), activeCatalog, Collections.emptyList(), null, null);
		config.getTransports().putAll(activeCatalog);
		for (Map.Entry<WorldPoint, Set<Transport>> entry : activeCatalog.entrySet())
		{
			config.getTransportsPacked().put(
				net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil.packWorldPoint(entry.getKey()),
				entry.getValue());
		}
		setField(config, "calculationCutoffMillis", plannerCase.policy.cutoffMillis);
		setField(config, "avoidWilderness", plannerCase.policy.avoidWilderness);
		config.setUseBankItems(useBankItems);
		return config;
	}

	private static SearchOutcome search(
		PathfinderConfig config, WorldPoint start, WorldPoint target, boolean bankVisited)
	{
		Rs2RouteRequest request = Rs2PathApi.resolvePolicy(
			Rs2RouteRequest.to(start, target)
				.withRefreshPolicy(Rs2RouteRequest.RefreshPolicy.NEVER),
			config);
		Rs2RoutePlanner planner = EMBEDDED_UPSTREAM
			? Rs2PathApi.upstreamPlanner() : Rs2PathApi.localPlanner(config);
		Rs2RouteResult result = planner.plan(
			request, Rs2PathApi.resolvePlanningSnapshot(request, config));
		List<WorldPoint> path = result.getPath();
		List<Rs2RouteStep> pathEdges = result.getSteps();
		Rs2RouteMetrics metrics = result.getMetrics();
		WorldPoint endpoint = path.isEmpty() ? null : path.get(path.size() - 1);
		boolean reached = endpoint != null && endpoint.equals(target);
		long reconstructedCost = pathCost(pathEdges, request.getPolicy().orElseThrow());
		if (metrics.getPathCost() != reconstructedCost)
		{
			throw new IllegalStateException(
				"local selected cost differs from reconstructed edge cost: " + start + " -> " + target);
		}
		return new SearchOutcome(
			path,
			pathEdges,
			result.getTerminationReason().name(),
			reached,
			reconstructedCost,
			metrics.getNodesChecked(),
			metrics.getTransportsChecked(),
			metrics.getSearchNanos(),
			bankVisited);
	}

	private static SearchOutcome searchWithBankDetours(
		PlannerCase plannerCase, Catalog catalog, WorldPoint start, WorldPoint target) throws Exception
	{
		List<Point> definitions = plannerCase.bankLocations == null
			? Collections.emptyList() : plannerCase.bankLocations;
		if (definitions.isEmpty())
		{
			throw new IllegalArgumentException(
				"bank-aware comparison requires at least one bank: " + plannerCase.id);
		}

		SearchOutcome direct = search(
			newConfig(plannerCase, catalog.withoutBankByOrigin, false), start, target, false);
		SearchOutcome chosen = direct;
		long totalNodes = availableMetric(direct.nodesChecked);
		long totalTransports = availableMetric(direct.transportsChecked);
		long totalElapsed = availableMetric(direct.elapsedNanos);
		for (Point definition : definitions)
		{
			WorldPoint bank = definition.toWorldPoint();
			SearchOutcome toBank = search(
				newConfig(plannerCase, catalog.withoutBankByOrigin, false), start, bank, false);
			totalNodes += availableMetric(toBank.nodesChecked);
			totalTransports += availableMetric(toBank.transportsChecked);
			totalElapsed += availableMetric(toBank.elapsedNanos);
			if (!toBank.reached)
			{
				continue;
			}
			SearchOutcome fromBank = search(
				newConfig(plannerCase, catalog.withBankByOrigin, true), bank, target, true);
			totalNodes += availableMetric(fromBank.nodesChecked);
			totalTransports += availableMetric(fromBank.transportsChecked);
			totalElapsed += availableMetric(fromBank.elapsedNanos);
			if (!fromBank.reached)
			{
				continue;
			}
			SearchOutcome bankRoute = SearchOutcome.combine(toBank, fromBank);
			if (!chosen.reached || bankRoute.cost < chosen.cost)
			{
				chosen = bankRoute;
			}
		}
		return chosen.withMetrics(totalNodes, totalTransports, totalElapsed);
	}

	private static long availableMetric(long value)
	{
		return value < 0L ? 0L : value;
	}

	private static void setField(PathfinderConfig config, String name, Object value) throws Exception
	{
		Field field = PathfinderConfig.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(config, value);
	}

	private static long pathCost(List<Rs2RouteStep> path, Rs2RoutePolicy policy)
	{
		if (path == null)
		{
			return -1L;
		}
		long cost = 0L;
		for (Rs2RouteStep edge : path)
		{
			if (edge.isTransport())
			{
				Rs2TransportEdge transport = edge.getTransport().orElseThrow();
				cost += transport.getDuration();
				if (transport.isTeleport())
				{
					cost += policy.getDistanceBeforeUsingTeleport();
				}
			}
			else
			{
				cost += net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil.distanceBetween(
					edge.getFrom(), edge.getTo());
			}
		}
		return cost;
	}

	private static List<SelectedTransport> selectedTransports(
		List<Rs2RouteStep> path, Catalog catalog)
	{
		List<SelectedTransport> selected = new ArrayList<>();
		for (Rs2RouteStep edge : path)
		{
			if (!edge.isTransport())
			{
				continue;
			}
			Rs2TransportEdge transport = edge.getTransport().orElseThrow();
			Object sourceIdentity = transport.getSourceIdentity();
			String id = sourceIdentity instanceof Transport
				? catalog.ids.get((Transport) sourceIdentity)
				: null;
			if (id == null)
			{
				throw new IllegalStateException("selected transport is not from the explicit corpus catalog: "
					+ transport);
			}
			selected.add(new SelectedTransport(id, Point.from(edge.getFrom()), Point.from(edge.getTo()),
				transport.getType().name(), transport.getDuration()));
		}
		return Collections.unmodifiableList(selected);
	}

	private static void resetHeapPeaks()
	{
		for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans())
		{
			if (pool.getType() == MemoryType.HEAP)
			{
				pool.resetPeakUsage();
			}
		}
	}

	private static long usedHeap()
	{
		return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
	}

	private static long peakHeap()
	{
		long peak = 0L;
		for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans())
		{
			if (pool.getType() == MemoryType.HEAP && pool.getPeakUsage() != null)
			{
				peak += Math.max(0L, pool.getPeakUsage().getUsed());
			}
		}
		return peak;
	}

	private static final class SearchOutcome
	{
		private final List<WorldPoint> path;
		private final List<Rs2RouteStep> edges;
		private final String termination;
		private final boolean reached;
		private final long cost;
		private final long nodesChecked;
		private final long transportsChecked;
		private final long elapsedNanos;
		private final boolean bankVisited;

		private SearchOutcome(List<WorldPoint> path, List<Rs2RouteStep> edges, String termination,
			boolean reached, long cost, long nodesChecked, long transportsChecked,
			long elapsedNanos, boolean bankVisited)
		{
			this.path = List.copyOf(path);
			this.edges = List.copyOf(edges);
			this.termination = termination;
			this.reached = reached;
			this.cost = cost;
			this.nodesChecked = nodesChecked;
			this.transportsChecked = transportsChecked;
			this.elapsedNanos = elapsedNanos;
			this.bankVisited = bankVisited;
		}

		private static SearchOutcome combine(SearchOutcome toBank, SearchOutcome fromBank)
		{
			if (toBank.path.isEmpty() || fromBank.path.isEmpty()
				|| !toBank.path.get(toBank.path.size() - 1).equals(fromBank.path.get(0)))
			{
				throw new IllegalArgumentException("bank route legs are not contiguous");
			}
			List<WorldPoint> path = new ArrayList<>(toBank.path);
			path.addAll(fromBank.path.subList(1, fromBank.path.size()));
			List<Rs2RouteStep> edges = new ArrayList<>(toBank.edges);
			edges.addAll(fromBank.edges);
			return new SearchOutcome(
				path,
				edges,
				fromBank.termination,
				fromBank.reached,
				Math.addExact(toBank.cost, fromBank.cost),
				availableMetric(toBank.nodesChecked) + availableMetric(fromBank.nodesChecked),
				availableMetric(toBank.transportsChecked)
					+ availableMetric(fromBank.transportsChecked),
				availableMetric(toBank.elapsedNanos) + availableMetric(fromBank.elapsedNanos),
				true);
		}

		private SearchOutcome withMetrics(long nodes, long transports, long elapsed)
		{
			return new SearchOutcome(path, edges, termination, reached, cost,
				nodes, transports, elapsed, bankVisited);
		}
	}

	private static final class PlannerCorpus
	{
		private int schemaVersion;
		private List<PlannerCase> cases;
	}

	private static final class PlannerCase
	{
		private String id;
		private Point start;
		private Point target;
		private PlannerPolicy policy;
		private List<PlannerTransport> transports = Collections.emptyList();
		private List<Point> bankLocations = Collections.emptyList();
		private List<PlannerItem> inventoryItems = Collections.emptyList();
		private List<PlannerItem> equipmentItems = Collections.emptyList();
		private List<PlannerItem> bankItems = Collections.emptyList();
	}

	private static final class PlannerPolicy
	{
		private String transportMode;
		private boolean avoidWilderness;
		private long cutoffMillis;
	}

	private static final class PlannerTransport
	{
		private String id;
		private Point origin;
		private Point destination;
		private String type;
		private int duration;
		private String displayInfo;
		private String availability = "ALWAYS";
		private String items;
	}

	private static final class PlannerItem
	{
		private int id;
		private int quantity;
	}

	private static final class Catalog
	{
		private final Map<WorldPoint, Set<Transport>> withoutBankByOrigin = new HashMap<>();
		private final Map<WorldPoint, Set<Transport>> withBankByOrigin = new HashMap<>();
		private final IdentityHashMap<Transport, String> ids = new IdentityHashMap<>();

		private static Catalog from(PlannerCase plannerCase) throws Exception
		{
			Catalog catalog = new Catalog();
			List<PlannerTransport> definitions = plannerCase.transports == null
				? Collections.emptyList() : plannerCase.transports;
			if ("STATIC_COLLISION_ONLY".equals(plannerCase.policy.transportMode)
				&& !definitions.isEmpty())
			{
				throw new IllegalArgumentException("static-only case has a transport catalog: "
					+ plannerCase.id);
			}
			Set<String> seenIds = new java.util.HashSet<>();
			for (PlannerTransport definition : definitions)
			{
				if (definition.id == null || !seenIds.add(definition.id))
				{
					throw new IllegalArgumentException("missing or duplicate transport id in "
						+ plannerCase.id + ": " + definition.id);
				}
				if (definition.origin == null || definition.destination == null
					|| definition.type == null || definition.duration < 0)
				{
					throw new IllegalArgumentException("incomplete transport " + definition.id
						+ " in " + plannerCase.id);
				}
				WorldPoint origin = definition.origin.toWorldPoint();
				Transport transport = new Transport(origin, definition.destination.toWorldPoint(),
					definition.displayInfo, TransportType.valueOf(definition.type), false,
					definition.duration);
				applyItemRequirements(transport, definition.items);
				if (!"ALWAYS".equals(definition.availability)
					&& !"AFTER_BANK".equals(definition.availability))
				{
					throw new IllegalArgumentException("unsupported transport availability for "
						+ definition.id + ": " + definition.availability);
				}
				if (hasRequiredItems(transport, plannerCase, true))
				{
					catalog.withBankByOrigin
						.computeIfAbsent(origin, ignored -> new java.util.LinkedHashSet<>())
						.add(transport);
				}
				if ("ALWAYS".equals(definition.availability)
					&& hasRequiredItems(transport, plannerCase, false))
				{
					catalog.withoutBankByOrigin
						.computeIfAbsent(origin, ignored -> new java.util.LinkedHashSet<>())
						.add(transport);
				}
				catalog.ids.put(transport, definition.id);
			}
			return catalog;
		}

		private static void applyItemRequirements(Transport transport, String items)
			throws Exception
		{
			if (items == null || items.isBlank())
			{
				return;
			}
			Method setter = Transport.class.getDeclaredMethod(
				"setItemRequirements", List.class);
			setter.setAccessible(true);
			setter.invoke(transport, TransportItemRequirement.parseRequirements(items));
		}

		private static boolean hasRequiredItems(
			Transport transport, PlannerCase plannerCase, boolean includeBank)
		{
			Map<Integer, Integer> available = availableItems(plannerCase, includeBank);
			return TransportItemRequirement.selectProviders(
				transport.getItemRequirements(),
				itemId -> available.getOrDefault(itemId, 0),
				itemId -> available.getOrDefault(itemId, 0) > 0,
				itemId -> available.getOrDefault(itemId, 0) > 0).isPresent();
		}

		private static Map<Integer, Integer> availableItems(
			PlannerCase plannerCase, boolean includeBank)
		{
			Map<Integer, Integer> available = new HashMap<>();
			addItems(available, plannerCase.inventoryItems, "inventory");
			addItems(available, plannerCase.equipmentItems, "equipment");
			if (includeBank)
			{
				addItems(available, plannerCase.bankItems, "bank");
			}
			return available;
		}

		private static void addItems(
			Map<Integer, Integer> available, List<PlannerItem> items, String source)
		{
			if (items == null)
			{
				return;
			}
			for (PlannerItem item : items)
			{
				if (item == null || item.id <= 0 || item.quantity <= 0)
				{
					throw new IllegalArgumentException("invalid " + source + " item state");
				}
				available.merge(item.id, item.quantity, Math::addExact);
			}
		}

	}

	private static final class Point
	{
		private int x;
		private int y;
		private int plane;

		private WorldPoint toWorldPoint()
		{
			return new WorldPoint(x, y, plane);
		}

		private static Point from(WorldPoint point)
		{
			if (point == null)
			{
				return null;
			}
			Point value = new Point();
			value.x = point.getX();
			value.y = point.getY();
			value.plane = point.getPlane();
			return value;
		}
	}

	private static final class PlannerRun
	{
		private final int schemaVersion;
		private final String engine;
		private final String revision;
		private final List<PlannerCaseResult> cases;

		private PlannerRun(int schemaVersion, String engine, String revision,
			List<PlannerCaseResult> cases)
		{
			this.schemaVersion = schemaVersion;
			this.engine = engine;
			this.revision = revision;
			this.cases = cases;
		}
	}

	private static final class PlannerCaseResult
	{
		private final String id;
		private final boolean supported;
		private final String unsupportedReason;
		private final String termination;
		private final boolean reached;
		private final Point endpoint;
		private final int pathLength;
		private final long pathCost;
		private final long nodesChecked;
		private final long transportsChecked;
		private final long elapsedNanos;
		private final long peakHeapDeltaBytes;
		private final List<SelectedTransport> selectedTransports;
		private final boolean bankVisited;

		private PlannerCaseResult(String id, boolean supported, String unsupportedReason,
			String termination, boolean reached, Point endpoint, int pathLength, long pathCost,
			long nodesChecked, long transportsChecked, long elapsedNanos, long peakHeapDeltaBytes,
			List<SelectedTransport> selectedTransports, boolean bankVisited)
		{
			this.id = id;
			this.supported = supported;
			this.unsupportedReason = unsupportedReason;
			this.termination = termination;
			this.reached = reached;
			this.endpoint = endpoint;
			this.pathLength = pathLength;
			this.pathCost = pathCost;
			this.nodesChecked = nodesChecked;
			this.transportsChecked = transportsChecked;
			this.elapsedNanos = elapsedNanos;
			this.peakHeapDeltaBytes = peakHeapDeltaBytes;
			this.selectedTransports = selectedTransports;
			this.bankVisited = bankVisited;
		}

		private static PlannerCaseResult unsupported(String id, String reason)
		{
			return new PlannerCaseResult(id, false, reason, null, false, null,
				0, -1L, -1L, -1L, -1L, -1L, Collections.emptyList(), false);
		}

		private static PlannerCaseResult supported(String id, String termination,
			boolean reached, Point endpoint, int pathLength, long pathCost, long nodesChecked,
			long transportsChecked, long elapsedNanos, long peakHeapDeltaBytes,
			List<SelectedTransport> selectedTransports, boolean bankVisited)
		{
			return new PlannerCaseResult(id, true, null, termination, reached, endpoint,
				pathLength, pathCost, nodesChecked, transportsChecked, elapsedNanos,
				peakHeapDeltaBytes, selectedTransports, bankVisited);
		}
	}

	private static final class SelectedTransport
	{
		private final String id;
		private final Point from;
		private final Point to;
		private final String type;
		private final int duration;

		private SelectedTransport(String id, Point from, Point to, String type, int duration)
		{
			this.id = id;
			this.from = from;
			this.to = to;
			this.type = type;
			this.duration = duration;
		}
	}
}
