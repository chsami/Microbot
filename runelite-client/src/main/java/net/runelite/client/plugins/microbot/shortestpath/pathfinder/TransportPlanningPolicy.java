package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.client.plugins.microbot.shortestpath.Transport;

/**
 * Engine-side admission seam for an already parsed transport catalog.
 *
 * <p>The pathfinder owns graph search, not knowledge of which interactions Microbot can execute.
 * Production therefore supplies a Microbot-owned policy, while headless planner tests may admit an
 * explicitly constructed catalog without depending on runtime executor classes.</p>
 */
public interface TransportPlanningPolicy
{
	TransportPlanningPolicy ALLOW_ALL = transport -> true;

	/** Whether this catalog row may enter the planner graph. */
	boolean isAdmitted(Transport transport);

	/** Whether a spell row is a registered zero-rune widget action. */
	default boolean isZeroRuneSpell(Transport transport)
	{
		return false;
	}
}
