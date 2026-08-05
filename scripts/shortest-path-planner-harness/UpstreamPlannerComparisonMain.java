package shortestpath.pathfinder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import shortestpath.Destination;
import shortestpath.DestinationRequirements;
import shortestpath.ShortestPathConfig;
import shortestpath.TeleportationItem;
import shortestpath.WorldPointUtil;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;
import shortestpath.transport.requirement.ItemRequirement;
import shortestpath.transport.requirement.TransportItems;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Emits reviewed-upstream planner results for Microbot's opt-in comparison harness. */
public final class UpstreamPlannerComparisonMain
{
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private UpstreamPlannerComparisonMain()
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
			"shortest-path-upstream",
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

	private static PlannerCaseResult run(PlannerCase plannerCase)
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

		Client client = mock(Client.class);
		ShortestPathConfig shortestPathConfig = mock(ShortestPathConfig.class);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
		when(client.getTotalLevel()).thenReturn(2277);
		when(shortestPathConfig.calculationCutoff()).thenReturn(
			(int) (plannerCase.policy.cutoffMillis / 600L));
		when(shortestPathConfig.avoidWilderness()).thenReturn(plannerCase.policy.avoidWilderness);
		when(shortestPathConfig.useTeleportationItems()).thenReturn(TeleportationItem.NONE);
		when(shortestPathConfig.useFairyRings()).thenReturn(true);
		when(shortestPathConfig.useGnomeGliders()).thenReturn(true);
		when(shortestPathConfig.useSpiritTrees()).thenReturn(true);

		Catalog catalog = Catalog.from(plannerCase);
		PathfinderConfig config = new ComparisonPathfinderConfig(
			client, shortestPathConfig, catalog, plannerCase);
		config.refresh();
		int start = plannerCase.start.pack();
		int target = plannerCase.target.pack();
		resetHeapPeaks();
		long heapBefore = usedHeap();
		Pathfinder pathfinder = new Pathfinder(config, start, Set.of(target));
		pathfinder.run();
		long peakHeapDelta = Math.max(0L, peakHeap() - heapBefore);
		PathfinderResult result = pathfinder.getResult();
		if (result == null)
		{
			throw new IllegalStateException("upstream result unavailable for " + plannerCase.id);
		}
		List<PathStep> path = result.getPathSteps();
		PathStep last = path == null || path.isEmpty() ? null : path.get(path.size() - 1);
		int endpoint = last == null ? WorldPointUtil.UNDEFINED : last.getPackedPosition();
		long pathCost = pathCost(path);
		List<SelectedTransport> selected = selectedTransports(path, catalog);
		boolean bankVisited = path != null && path.stream().anyMatch(PathStep::isBankVisited);
		return PlannerCaseResult.supported(
			plannerCase.id,
			result.getTerminationReason().name(),
			result.isReached(),
			Point.fromPacked(endpoint),
			path == null ? 0 : path.size(),
			pathCost,
			result.getNodesChecked(),
			result.getTransportsChecked(),
			result.getElapsedNanos(),
			peakHeapDelta,
			selected,
			bankVisited);
	}

	private static long pathCost(List<PathStep> path)
	{
		if (path == null)
		{
			return -1L;
		}
		long cost = 0L;
		for (int i = 1; i < path.size(); i++)
		{
			Transport transport = path.get(i).getTransport();
			cost += transport == null
				? WorldPointUtil.distanceBetween(
					path.get(i - 1).getPackedPosition(), path.get(i).getPackedPosition())
				: transport.getDuration();
		}
		return cost;
	}

	private static List<SelectedTransport> selectedTransports(List<PathStep> path, Catalog catalog)
	{
		if (path == null)
		{
			return Collections.emptyList();
		}
		List<SelectedTransport> selected = new ArrayList<>();
		for (int i = 1; i < path.size(); i++)
		{
			Transport transport = path.get(i).getTransport();
			if (transport == null)
			{
				continue;
			}
			String id = catalog.ids.get(transport);
			if (id == null)
			{
				throw new IllegalStateException("selected transport is not from the explicit corpus catalog: "
					+ transport);
			}
			selected.add(new SelectedTransport(id,
				Point.fromPacked(path.get(i - 1).getPackedPosition()),
				Point.fromPacked(path.get(i).getPackedPosition()),
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

	private static final class ComparisonPathfinderConfig extends PathfinderConfig
	{
		private final TransportAvailability withoutBank;
		private final TransportAvailability withBank;
		private final boolean bankPathEnabled;
		private final Set<Integer> bankLocations;

		private ComparisonPathfinderConfig(Client client, ShortestPathConfig config,
			Catalog catalog, PlannerCase plannerCase)
		{
			this(client, config, catalog, plannerCase, Destination.loadAllFromResources());
		}

		private ComparisonPathfinderConfig(Client client, ShortestPathConfig config,
			Catalog catalog, PlannerCase plannerCase, Map<String, Set<Integer>> destinations)
		{
			super(client, config,
				SplitFlagMap.fromResources(),
				catalog.withBankByOrigin,
				destinations,
				PathfinderConfig.filterDestinations(destinations),
				Collections.<Integer, DestinationRequirements>emptyMap());
			withoutBank = availability(catalog.withoutBankByOrigin);
			withBank = availability(catalog.withBankByOrigin);
			bankPathEnabled = "BANK_AWARE_EXPLICIT_CATALOG".equals(
				plannerCase.policy.transportMode);
			Set<Integer> packedBanks = new java.util.HashSet<>();
			if (plannerCase.bankLocations != null)
			{
				for (Point bank : plannerCase.bankLocations)
				{
					packedBanks.add(bank.pack());
				}
			}
			bankLocations = Collections.unmodifiableSet(packedBanks);
		}

		private static TransportAvailability availability(
			Map<Integer, Set<Transport>> transports)
		{
			TransportAvailability.Builder builder = new TransportAvailability.Builder(
				Math.max(1, transports.size()));
			for (Set<Transport> values : transports.values())
			{
				for (Transport transport : values)
				{
					builder.add(transport);
				}
			}
			return builder.build();
		}

		@Override
		public TransportAvailability getTransportAvailability(boolean bankVisited)
		{
			return bankVisited ? withBank : withoutBank;
		}

		@Override
		public boolean isBankPathEnabled()
		{
			return bankPathEnabled;
		}

		@Override
		public boolean bankAccessible(int packedPosition)
		{
			return bankLocations.contains(packedPosition);
		}

		@Override
		public QuestState getQuestState(Quest quest)
		{
			return QuestState.FINISHED;
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
		private final Map<Integer, Set<Transport>> withoutBankByOrigin = new HashMap<>();
		private final Map<Integer, Set<Transport>> withBankByOrigin = new HashMap<>();
		private final IdentityHashMap<Transport, String> ids = new IdentityHashMap<>();

		private static Catalog from(PlannerCase plannerCase)
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
				int origin = definition.origin.pack();
				Transport.TransportBuilder builder = new Transport.TransportBuilder()
					.origin(origin)
					.destination(definition.destination.pack())
					.type(TransportType.valueOf(definition.type))
					.duration(definition.duration)
					.displayInfo(definition.displayInfo);
				if (definition.items != null && !definition.items.isBlank())
				{
					builder.itemRequirements(definition.items);
				}
				Transport transport = builder.build();
				if (!"ALWAYS".equals(definition.availability)
					&& !"AFTER_BANK".equals(definition.availability))
				{
					throw new IllegalArgumentException("unsupported transport availability for "
						+ definition.id + ": " + definition.availability);
				}
				if (hasRequiredItems(transport, plannerCase, true))
				{
					catalog.withBankByOrigin
						.computeIfAbsent(origin, ignored -> new LinkedHashSet<>())
						.add(transport);
				}
				if ("ALWAYS".equals(definition.availability)
					&& hasRequiredItems(transport, plannerCase, false))
				{
					catalog.withoutBankByOrigin
						.computeIfAbsent(origin, ignored -> new LinkedHashSet<>())
						.add(transport);
				}
				catalog.ids.put(transport, definition.id);
			}
			return catalog;
		}

		/** Mirrors the reviewed upstream provider-consumption contract over explicit headless state. */
		private static boolean hasRequiredItems(
			Transport transport, PlannerCase plannerCase, boolean includeBank)
		{
			TransportItems requirements = transport.getItemRequirements();
			if (requirements == null)
			{
				return true;
			}
			Map<Integer, Integer> available = availableItems(plannerCase, includeBank);
			boolean usingStaff = false;
			boolean usingOffhand = false;
			for (ItemRequirement requirement : requirements.getRequirements())
			{
				boolean missing = !hasQuantity(
					requirement.getItemIds(), requirement.getQuantity(), available);
				if (missing && !usingStaff && hasProvider(
					requirement.getStaffIds(), requirement.getQuantity(), available))
				{
					usingStaff = true;
					missing = false;
				}
				if (missing && !usingOffhand && hasProvider(
					requirement.getOffhandIds(), requirement.getQuantity(), available))
				{
					usingOffhand = true;
					missing = false;
				}
				if (missing)
				{
					return false;
				}
			}
			return true;
		}

		private static boolean hasQuantity(
			int[] itemIds, int required, Map<Integer, Integer> available)
		{
			if (itemIds == null)
			{
				return false;
			}
			for (int itemId : itemIds)
			{
				int quantity = available.getOrDefault(itemId, 0);
				if (required > 0 && quantity >= required || required == 0 && quantity == 0)
				{
					return true;
				}
			}
			return false;
		}

		private static boolean hasProvider(
			int[] itemIds, int required, Map<Integer, Integer> available)
		{
			if (itemIds == null)
			{
				return false;
			}
			for (int itemId : itemIds)
			{
				int quantity = available.getOrDefault(itemId, 0);
				if (required > 0 && quantity >= 1 || required == 0 && quantity == 0)
				{
					return true;
				}
			}
			return false;
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

		private int pack()
		{
			return WorldPointUtil.packWorldPoint(x, y, plane);
		}

		private static Point fromPacked(int packed)
		{
			if (packed == WorldPointUtil.UNDEFINED)
			{
				return null;
			}
			WorldPoint point = WorldPointUtil.unpackWorldPoint(packed);
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
