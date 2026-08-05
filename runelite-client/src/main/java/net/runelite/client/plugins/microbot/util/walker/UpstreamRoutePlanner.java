package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import shortestpath.DestinationRequirements;
import shortestpath.ShortestPathConfig;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.SplitFlagMap;
import shortestpath.pathfinder.TransportAvailability;
import shortestpath.pathfinder.WildernessChecker;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Production-packaged adapter for the pinned reviewed upstream planner core. */
final class UpstreamRoutePlanner implements Rs2RoutePlanner
{
	static final String REVISION = "ff8e961b32120175709df9630ece9468cc11347f";
	private static final ShortestPathConfig EMPTY_CONFIG = new ShortestPathConfig()
	{
		@Override
		public void setBuiltTeleportationBoxes(String content)
		{
		}

		@Override
		public void setBuiltTeleportationPortalsPoh(String content)
		{
		}
	};

	private static final class StaticMapHolder
	{
		private static final SplitFlagMap INSTANCE = SplitFlagMap.fromResources();
	}

	@Override
	public String getEngineId()
	{
		return "shortest-path-upstream@" + REVISION;
	}

	@Override
	public Rs2RouteResult plan(Rs2RouteRequest request, Rs2PlanningSnapshot snapshot)
	{
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(snapshot, "snapshot");
		Rs2RoutePolicy policy = request.getPolicy().orElseThrow(
			() -> new IllegalArgumentException("upstream planner requires a resolved policy"));
		if (snapshot.getPolicy() != policy)
		{
			throw new IllegalArgumentException("route request and planning snapshot policy differ");
		}

		IdentityHashMap<Transport, Rs2TransportEdge> exactEdges = new IdentityHashMap<>();
		TransportAvailability.Builder catalog = new TransportAvailability.Builder(
			Math.max(1, snapshot.getAdmittedTransports().size()));
		for (Rs2TransportEdge edge : snapshot.getAdmittedTransports())
		{
			if ((policy.isIgnoreTeleportAndItems() || policy.isTeleportsDisabled())
				&& edge.getOrigin() == null && edge.isTeleport())
			{
				continue;
			}
			Transport converted = convert(edge);
			catalog.add(converted);
			exactEdges.put(converted, edge);
		}

		Set<Integer> packedTargets = new LinkedHashSet<>();
		for (WorldPoint target : request.getTargets())
		{
			packedTargets.add(WorldPointUtil.packWorldPoint(target));
		}
		UpstreamConfig config = new UpstreamConfig(
			request, snapshot, catalog.build());
		Pathfinder pathfinder = new Pathfinder(
			config, WorldPointUtil.packWorldPoint(request.getStart()), packedTargets);
		pathfinder.run();
		PathfinderResult result = pathfinder.getResult();
		if (result == null)
		{
			throw new IllegalStateException("pinned upstream planner did not publish a result");
		}
		return toResult(request, snapshot, result, exactEdges);
	}

	private static Transport convert(Rs2TransportEdge edge)
	{
		int origin = edge.getOrigin() == null
			? WorldPointUtil.UNDEFINED : WorldPointUtil.packWorldPoint(edge.getOrigin());
		return new Transport.TransportBuilder()
			.origin(origin)
			.destination(WorldPointUtil.packWorldPoint(edge.getDestination()))
			.type(mapType(edge))
			.duration(edge.getDuration())
			.displayInfo(edge.getDisplayInfo())
			.isConsumable(edge.isConsumable())
			.maxWildernessLevel(edge.getMaxWildernessLevel())
			.build();
	}

	/**
	 * Project the planner-independent type into the pinned upstream schema.
	 *
	 * <p>Keep this switch exhaustive. A newly introduced Microbot transport category must be reviewed
	 * before it can enter the upstream catalog; silently flattening it to {@code TRANSPORT} can alter
	 * teleport admission, wilderness limits, delayed visits, or transport cost.</p>
	 */
	static TransportType mapType(Rs2TransportEdge edge)
	{
		if (edge.getOrigin() == null)
		{
			switch (edge.getType())
			{
				case QUETZAL_WHISTLE:
					return TransportType.QUETZAL_WHISTLE;
				case SEASONAL_TRANSPORT:
					// Upstream's seasonal category is anchored; originless seasonal rows are
					// Microbot teleports and therefore use upstream teleport admission/costing.
					return TransportType.TELEPORTATION_ITEM;
				case TELEPORTATION_ITEM:
					return TransportType.TELEPORTATION_ITEM;
				case TELEPORTATION_MINIGAME:
					return TransportType.TELEPORTATION_MINIGAME;
				case TELEPORTATION_SPELL:
					return TransportType.TELEPORTATION_SPELL;
				case TELEPORTATION_SPELL_HOME:
					return TransportType.TELEPORTATION_SPELL_HOME;
				default:
					throw unsupportedType(edge, "originless transport category is not an upstream teleport");
			}
		}

		switch (edge.getType())
		{
			case TRANSPORT:
				return TransportType.TRANSPORT;
			case AGILITY_SHORTCUT:
				return TransportType.AGILITY_SHORTCUT;
			case GRAPPLE_SHORTCUT:
				return TransportType.GRAPPLE_SHORTCUT;
			case BOAT:
				return TransportType.BOAT;
			case CANOE:
				return TransportType.CANOE;
			case CHARTER_SHIP:
				return TransportType.CHARTER_SHIP;
			case SHIP:
				return TransportType.SHIP;
			case FAIRY_RING:
				return TransportType.FAIRY_RING;
			case QUETZAL:
				return TransportType.QUETZAL;
			case QUETZAL_WHISTLE:
				return TransportType.QUETZAL_WHISTLE;
			case GNOME_GLIDER:
				return TransportType.GNOME_GLIDER;
			case MINECART:
				return TransportType.MINECART;
			case POH:
			case NPC:
				// These Microbot-owned execution families have no upstream type. They remain
				// distinct on the exact Rs2TransportEdge retained by the adapter.
				return TransportType.TRANSPORT;
			case SPIRIT_TREE:
				return TransportType.SPIRIT_TREE;
			case TELEPORTATION_BOX:
				return TransportType.TELEPORTATION_BOX;
			case TELEPORTATION_LEVER:
				return TransportType.TELEPORTATION_LEVER;
			case TELEPORTATION_PORTAL:
				return TransportType.TELEPORTATION_PORTAL;
			case TELEPORTATION_PORTAL_POH:
				return TransportType.TELEPORTATION_PORTAL_POH;
			case TELEPORTATION_MINIGAME:
				return TransportType.TELEPORTATION_MINIGAME;
			case TELEPORTATION_ITEM:
				return TransportType.TELEPORTATION_ITEM;
			case TELEPORTATION_SPELL:
				return TransportType.TELEPORTATION_SPELL;
			case TELEPORTATION_SPELL_HOME:
				return TransportType.TELEPORTATION_SPELL_HOME;
			case WILDERNESS_OBELISK:
				return TransportType.WILDERNESS_OBELISK;
			case MAGIC_CARPET:
				return TransportType.MAGIC_CARPET;
			case HOT_AIR_BALLOON:
				return TransportType.HOT_AIR_BALLOON;
			case MAGIC_MUSHTREE:
				return TransportType.MAGIC_MUSHTREE;
			case SEASONAL_TRANSPORT:
				return TransportType.SEASONAL_TRANSPORTS;
			case UNKNOWN:
			default:
				throw unsupportedType(edge, "transport category has no reviewed upstream projection");
		}
	}

	private static IllegalArgumentException unsupportedType(Rs2TransportEdge edge, String reason)
	{
		return new IllegalArgumentException(reason + ": " + edge.getType());
	}

	private static Rs2RouteResult toResult(
		Rs2RouteRequest request,
		Rs2PlanningSnapshot snapshot,
		PathfinderResult result,
		IdentityHashMap<Transport, Rs2TransportEdge> exactEdges)
	{
		List<PathStep> sourcePath = result.getPathSteps() == null
			? Collections.emptyList() : result.getPathSteps();
		List<WorldPoint> path = new ArrayList<>(sourcePath.size());
		for (PathStep step : sourcePath)
		{
			path.add(WorldPointUtil.unpackWorldPoint(step.getPackedPosition()));
		}
		List<Rs2RouteStep> steps = new ArrayList<>(Math.max(0, path.size() - 1));
		long cost = 0L;
		for (int i = 1; i < sourcePath.size(); i++)
		{
			WorldPoint from = path.get(i - 1);
			WorldPoint to = path.get(i);
			Transport selected = sourcePath.get(i).getTransport();
			if (selected == null)
			{
				steps.add(Rs2RouteStep.walk(from, to));
				cost += WorldPointUtil.distanceBetween(
					sourcePath.get(i - 1).getPackedPosition(), sourcePath.get(i).getPackedPosition());
				cost += snapshot.getAdditionalWalkingCost(
					sourcePath.get(i).getPackedPosition(), request.getTargets());
			}
			else
			{
				Rs2TransportEdge exact = exactEdges.get(selected);
				if (exact == null)
				{
					throw new IllegalStateException(
						"upstream selected a transport outside the projected catalog");
				}
				steps.add(Rs2RouteStep.transport(from, to, exact));
				cost += selected.getDuration();
				if (selected.getOrigin() == WorldPointUtil.UNDEFINED)
				{
					cost += snapshot.getPolicy().getDistanceBeforeUsingTeleport();
				}
			}
		}
		return new Rs2RouteResult(
			request.getStart(),
			request.getTargets(),
			path,
			steps,
			Rs2RouteTermination.valueOf(result.getTerminationReason().name()),
			new Rs2RouteMetrics(
				result.getElapsedNanos(),
				cost,
				result.getNodesChecked(),
				result.getTransportsChecked()));
	}

	private static final class UpstreamConfig extends PathfinderConfig
	{
		private final Rs2RouteRequest request;
		private final Rs2PlanningSnapshot snapshot;
		private final TransportAvailability availability;
		private final CollisionMap collisionMap;
		private final Set<Integer> restricted;

		private UpstreamConfig(
			Rs2RouteRequest request,
			Rs2PlanningSnapshot snapshot,
			TransportAvailability availability)
		{
			super(null, EMPTY_CONFIG, StaticMapHolder.INSTANCE,
				Collections.emptyMap(),
				Collections.<String, Set<Integer>>emptyMap(),
				Collections.<String, Set<Integer>>emptyMap(),
				Collections.<Integer, DestinationRequirements>emptyMap());
			this.request = request;
			this.snapshot = snapshot;
			this.availability = availability;
			this.collisionMap = new CollisionMap(
				StaticMapHolder.INSTANCE, snapshot::collisionOverride);
			Set<Integer> packedRestricted = new LinkedHashSet<>();
			for (WorldPoint point : snapshot.getPolicy().getRestrictedPoints())
			{
				packedRestricted.add(WorldPointUtil.packWorldPoint(point));
			}
			this.restricted = Collections.unmodifiableSet(packedRestricted);
		}

		@Override
		public CollisionMap getMap()
		{
			return collisionMap;
		}

		@Override
		public TransportAvailability getTransportAvailability(boolean bankVisited)
		{
			return availability;
		}

		@Override
		public boolean isBankPathEnabled()
		{
			return false;
		}

		@Override
		public boolean bankAccessible(int packedPosition)
		{
			return false;
		}

		@Override
		public long getCalculationCutoffMillis()
		{
			return snapshot.getPolicy().getCalculationCutoffMillis();
		}

		@Override
		public boolean avoidWilderness(
			int packedPosition, int packedNeighborPosition, boolean targetInWilderness)
		{
			return snapshot.getPolicy().isAvoidWilderness()
				&& !targetInWilderness
				&& !WildernessChecker.isInWilderness(packedPosition)
				&& WildernessChecker.isInWilderness(packedNeighborPosition);
		}

		@Override
		public boolean avoidBlockedRegion(
			int packedPosition, int packedNeighborPosition, boolean targetInBlockedRegion)
		{
			return restricted.contains(packedNeighborPosition)
				|| snapshot.isWalkingEdgeBlocked(packedPosition, packedNeighborPosition);
		}

		@Override
		public int getAdditionalWalkingCost(int packedDestination)
		{
			return snapshot.getAdditionalWalkingCost(
				packedDestination, request.getTargets());
		}

		@Override
		public int getAdditionalTransportCost(Transport transport)
		{
			return transport.getOrigin() == WorldPointUtil.UNDEFINED
				? snapshot.getPolicy().getDistanceBeforeUsingTeleport() : 0;
		}

		@Override
		public int getDifferentialCost(Transport transport)
		{
			return 0;
		}
	}
}
