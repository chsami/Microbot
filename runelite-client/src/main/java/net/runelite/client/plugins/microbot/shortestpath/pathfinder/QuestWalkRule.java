package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;

/**
 * Quest automation walks: ships, charters, quetzals, spirit trees, fairy rings, gliders, carpets, balloons,
 * minecarts, canoes, mushtrees and teleports are off while a quest is automated. Only when the destination cannot
 * be reached on foot (another landmass, or the search fails) does {@link Pathfinder#run()} search again with them
 * allowed, and it logs which ones it used.
 */
public final class QuestWalkRule
{
	private static volatile boolean active;
	private static final ThreadLocal<Boolean> FALLBACK = ThreadLocal.withInitial(() -> Boolean.FALSE);

	private static final Set<TransportType> TRAVEL = EnumSet.of(
		TransportType.BOAT, TransportType.CANOE, TransportType.CHARTER_SHIP, TransportType.SHIP,
		TransportType.FAIRY_RING, TransportType.QUETZAL, TransportType.GNOME_GLIDER, TransportType.MINECART,
		TransportType.POH, TransportType.SPIRIT_TREE, TransportType.MAGIC_CARPET, TransportType.HOT_AIR_BALLOON,
		TransportType.MAGIC_MUSHTREE);

	private QuestWalkRule()
	{
	}

	public static void setActive(boolean on)
	{
		active = on;
	}

	public static boolean isActive()
	{
		return active;
	}

	static boolean inFallback()
	{
		return FALLBACK.get();
	}

	/** Runs {@code body} with the restriction lifted on this thread. */
	static void allowAll(Runnable body)
	{
		FALLBACK.set(Boolean.TRUE);
		try
		{
			body.run();
		}
		finally
		{
			FALLBACK.set(Boolean.FALSE);
		}
	}

	public static boolean isRestrictedType(TransportType type, WorldPoint origin)
	{
		return TRAVEL.contains(type) || TransportType.isTeleport(type, origin);
	}

	/** Whether the search must skip this transport right now. */
	public static boolean blocks(TransportType type, WorldPoint origin)
	{
		return active && !FALLBACK.get() && isRestrictedType(type, origin);
	}
}
