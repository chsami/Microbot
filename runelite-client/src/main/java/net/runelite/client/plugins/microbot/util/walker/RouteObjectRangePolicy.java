package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;

import java.util.regex.Pattern;

/** Identifies object route steps whose menu action may safely start before reaching the origin. */
public final class RouteObjectRangePolicy
{
	private static final int MAX_FENCE_SHORTCUT_HOP = 3;
	private static final Pattern FENCE_OR_RAILING = Pattern.compile("\\b(?:fence|railing)\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern BROKEN_FENCE = Pattern.compile("\\bbroken fence\\b", Pattern.CASE_INSENSITIVE);

	private RouteObjectRangePolicy()
	{
	}

	/**
	 * Eligibility of the exact selected edge only. The caller must still enforce route order,
	 * player-to-object range, instance, settle, and failed-attempt guards before dispatch.
	 */
	public static boolean isRangedObjectDispatchEligible(Rs2TransportEdge edge)
	{
		if (edge == null || edge.getExecutor() != Rs2TransportExecutor.OBJECT)
		{
			return false;
		}
		if (edge.getType() == Rs2TransportType.TRANSPORT)
		{
			return true;
		}
		if (edge.getType() != Rs2TransportType.AGILITY_SHORTCUT)
		{
			return false;
		}
		return isEligibleFenceShortcut(edge.getOrigin(), edge.getDestination(), edge.getAction(), edge.getTarget());
	}

	static boolean isEligibleFenceShortcut(WorldPoint origin, WorldPoint destination, String action, String target)
	{
		if (origin == null || destination == null || origin.getPlane() != destination.getPlane())
		{
			return false;
		}
		int hop = origin.distanceTo2D(destination);
		if (hop < 1 || hop > MAX_FENCE_SHORTCUT_HOP)
		{
			return false;
		}

		return target != null && FENCE_OR_RAILING.matcher(target).find()
			&& action != null && ("Squeeze-through".equalsIgnoreCase(action.trim())
				|| "Climb-over".equalsIgnoreCase(action.trim())
				|| "Jump-over".equalsIgnoreCase(action.trim())
				|| "Jump".equalsIgnoreCase(action.trim()) && BROKEN_FENCE.matcher(target).find());
	}
}
