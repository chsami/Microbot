package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportExecutionRegistry;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.TransportPlanningPolicy;

/** Microbot executor capabilities projected into the local planner's catalog-admission seam. */
public final class Rs2TransportPlanningPolicy implements TransportPlanningPolicy
{
	public static final Rs2TransportPlanningPolicy INSTANCE = new Rs2TransportPlanningPolicy();

	private Rs2TransportPlanningPolicy()
	{
	}

	@Override
	public boolean isAdmitted(Transport transport)
	{
		return TransportExecutionRegistry.canExecute(transport);
	}

	@Override
	public boolean isZeroRuneSpell(Transport transport)
	{
		return transport != null
			&& TransportExecutionRegistry.homeTeleportFor(transport.getDisplayInfo()).isPresent();
	}
}
