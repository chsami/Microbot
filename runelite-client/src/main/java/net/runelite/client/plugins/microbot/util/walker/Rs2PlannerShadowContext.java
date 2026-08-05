package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Coordinate-free route classification attached to one production shadow comparison. */
public final class Rs2PlannerShadowContext
{
	public enum Invocation
	{
		SYNCHRONOUS_QUERY,
		ACTIVE_ROUTE,
		ACTIVE_REPLAN,
		RECOVERY_REPLAN
	}

	public enum Coverage
	{
		SYNCHRONOUS_QUERY,
		ACTIVE_ROUTE,
		ACTIVE_REPLAN,
		RECOVERY_REPLAN,
		MEMBERS_WORLD_POLICY,
		SURFACE_COORDINATES_ONLY,
		UNDERGROUND_COORDINATES,
		WALKING_ONLY_SELECTED,
		USES_TRANSPORT,
		SELECTS_MEMBERS_TRANSPORT,
		SELECTS_ITEM_GATED_TRANSPORT,
		SELECTS_SKILL_GATED_TRANSPORT,
		SELECTS_QUEST_GATED_TRANSPORT,
		SELECTS_STATE_GATED_TRANSPORT,
		SELECTS_NON_ITEM_REQUIREMENT_GATED_TRANSPORT,
		BANK_ITEMS_ENABLED,
		BANK_ROUTE_DIRECT,
		BANK_ROUTE_TO_BANK,
		BANK_ROUTE_FROM_BANK,
		BANK_ROUTE_FROM_BANK_SELECTS_ITEM_GATED_TRANSPORT,
		LIVE_COLLISION_ENABLED,
		LIVE_COLLISION_CONSULTED
	}

	private static final int UNDERGROUND_Y = 6400;

	private final Invocation invocation;
	private final Set<Coverage> coverage;
	private final Set<Rs2TransportExecutor> transportExecutors;
	private final Set<Rs2TransportType> transportTypes;

	private Rs2PlannerShadowContext(
		Invocation invocation,
		Set<Coverage> coverage,
		Set<Rs2TransportExecutor> transportExecutors,
		Set<Rs2TransportType> transportTypes)
	{
		this.invocation = Objects.requireNonNull(invocation, "invocation");
		this.coverage = Collections.unmodifiableSet(EnumSet.copyOf(coverage));
		this.transportExecutors = Collections.unmodifiableSet(
			EnumSet.copyOf(transportExecutors));
		this.transportTypes = Collections.unmodifiableSet(EnumSet.copyOf(transportTypes));
	}

	static Rs2PlannerShadowContext from(
		Invocation invocation,
		boolean walkingOnlySelected,
		Rs2RouteRequest request,
		Rs2RouteResult local)
	{
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(local, "local");
		Rs2RoutePolicy policy = request.getPolicy().orElseThrow(
			() -> new IllegalArgumentException("shadow context requires resolved policy"));
		EnumSet<Coverage> coverage = EnumSet.noneOf(Coverage.class);
		switch (invocation)
		{
			case SYNCHRONOUS_QUERY: coverage.add(Coverage.SYNCHRONOUS_QUERY); break;
			case ACTIVE_ROUTE: coverage.add(Coverage.ACTIVE_ROUTE); break;
			case ACTIVE_REPLAN: coverage.add(Coverage.ACTIVE_REPLAN); break;
			case RECOVERY_REPLAN: coverage.add(Coverage.RECOVERY_REPLAN); break;
			default: throw new IllegalStateException("unhandled invocation " + invocation);
		}
		boolean underground = isUnderground(request.getStart())
			|| request.getTargets().stream().anyMatch(Rs2PlannerShadowContext::isUnderground)
			|| local.getPath().stream().anyMatch(Rs2PlannerShadowContext::isUnderground);
		coverage.add(underground
			? Coverage.UNDERGROUND_COORDINATES : Coverage.SURFACE_COORDINATES_ONLY);
		if (walkingOnlySelected)
		{
			coverage.add(Coverage.WALKING_ONLY_SELECTED);
		}
		EnumSet<Rs2TransportExecutor> transportExecutors =
			EnumSet.noneOf(Rs2TransportExecutor.class);
		EnumSet<Rs2TransportType> transportTypes = EnumSet.noneOf(Rs2TransportType.class);
		boolean itemGatedTransport = false;
		boolean membersTransport = false;
		boolean skillGatedTransport = false;
		boolean questGatedTransport = false;
		boolean stateGatedTransport = false;
		for (Rs2RouteStep step : local.getTransportSteps())
		{
			Rs2TransportEdge edge = step.getTransport().orElseThrow(IllegalStateException::new);
			transportExecutors.add(edge.getExecutor());
			transportTypes.add(edge.getType());
			itemGatedTransport |= !edge.getItemRequirements().isEmpty()
				|| edge.getCurrencyAmount() > 0;
			membersTransport |= edge.isMembers();
			skillGatedTransport |= edge.isSkillGated();
			questGatedTransport |= edge.isQuestGated();
			stateGatedTransport |= edge.isStateGated();
		}
		if (policy.isMembersWorld())
		{
			coverage.add(Coverage.MEMBERS_WORLD_POLICY);
		}
		if (!transportExecutors.isEmpty())
		{
			coverage.add(Coverage.USES_TRANSPORT);
		}
		if (itemGatedTransport)
		{
			coverage.add(Coverage.SELECTS_ITEM_GATED_TRANSPORT);
		}
		if (membersTransport)
		{
			coverage.add(Coverage.SELECTS_MEMBERS_TRANSPORT);
		}
		if (skillGatedTransport)
		{
			coverage.add(Coverage.SELECTS_SKILL_GATED_TRANSPORT);
		}
		if (questGatedTransport)
		{
			coverage.add(Coverage.SELECTS_QUEST_GATED_TRANSPORT);
		}
		if (stateGatedTransport)
		{
			coverage.add(Coverage.SELECTS_STATE_GATED_TRANSPORT);
		}
		if (skillGatedTransport || questGatedTransport || stateGatedTransport)
		{
			coverage.add(Coverage.SELECTS_NON_ITEM_REQUIREMENT_GATED_TRANSPORT);
		}
		if (policy.isUseBankItems())
		{
			coverage.add(Coverage.BANK_ITEMS_ENABLED);
		}
		switch (request.getPurpose())
		{
			case GENERAL: break;
			case BANK_ROUTE_DIRECT: coverage.add(Coverage.BANK_ROUTE_DIRECT); break;
			case BANK_ROUTE_TO_BANK: coverage.add(Coverage.BANK_ROUTE_TO_BANK); break;
			case BANK_ROUTE_FROM_BANK:
				coverage.add(Coverage.BANK_ROUTE_FROM_BANK);
				if (itemGatedTransport)
				{
					coverage.add(Coverage.BANK_ROUTE_FROM_BANK_SELECTS_ITEM_GATED_TRANSPORT);
				}
				break;
			default: throw new IllegalStateException(
				"unhandled route request purpose " + request.getPurpose());
		}
		if (policy.isLiveCollisionEnabled())
		{
			coverage.add(Coverage.LIVE_COLLISION_ENABLED);
		}
		if (local.getMetrics().hasLiveCollisionEdgesChecked()
			&& local.getMetrics().getLiveCollisionEdgesChecked() > 0L)
		{
			coverage.add(Coverage.LIVE_COLLISION_CONSULTED);
		}
		return new Rs2PlannerShadowContext(
			invocation, coverage, transportExecutors, transportTypes);
	}

	private static boolean isUnderground(WorldPoint point)
	{
		return point != null && point.getY() >= UNDERGROUND_Y;
	}

	public Invocation getInvocation()
	{
		return invocation;
	}

	public Set<Coverage> getCoverage()
	{
		return coverage;
	}

	public Set<Rs2TransportExecutor> getTransportExecutors()
	{
		return transportExecutors;
	}

	public Set<Rs2TransportType> getTransportTypes()
	{
		return transportTypes;
	}
}
