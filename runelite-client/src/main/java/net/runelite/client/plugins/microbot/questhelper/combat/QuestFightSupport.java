package net.runelite.client.plugins.microbot.questhelper.combat;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.runelite.api.coords.WorldPoint;

/** Pure helpers for fight preparation: dragon detection, antifire timing and safespot choice. */
public final class QuestFightSupport
{
	/** Antifire potion families, best protection first. */
	public static final List<String> ANTIFIRE_NAMES = Arrays.asList(
		"Extended super antifire", "Super antifire potion", "Extended antifire", "Antifire potion");

	private static final Pattern DRAGON_NAME = Pattern.compile("dragon|elvarg|\bwyrm\b|drake", Pattern.CASE_INSENSITIVE);
	private static final Pattern FIRE_TEXT = Pattern.compile("dragonfire|dragon fire|antifire|anti-fire|fire breath",
		Pattern.CASE_INSENSITIVE);

	/** The grace after a sip before the varbit is trusted, so a slow varbit update does not cause a second drink. */
	static final long VARBIT_GRACE_MS = 4_000L;
	private static final long EARLY_REDRINK_MS = 10_000L;

	private QuestFightSupport()
	{
	}

	/** Whether the fight is against a dragon: target name has dragon/Elvarg, or the step text mentions dragonfire. */
	public static boolean isDragonFight(String targetName, List<String> stepText)
	{
		if (targetName != null && DRAGON_NAME.matcher(targetName).find()
			&& !targetName.toLowerCase(Locale.ROOT).contains("dragon slayer"))
		{
			return true;
		}
		if (stepText != null)
		{
			for (String line : stepText)
			{
				if (line != null && FIRE_TEXT.matcher(line.replaceAll("<[^>]*>", " ")).find())
				{
					return true;
				}
			}
		}
		return false;
	}

	/** Whether an item name is an antifire potion of any dose. */
	public static boolean isAntifire(String itemName)
	{
		String base = ChargedItemNames.baseName(itemName);
		for (String name : ANTIFIRE_NAMES)
		{
			if (base.equals(name.toLowerCase(Locale.ROOT)))
			{
				return true;
			}
		}
		return false;
	}

	/** How long one sip lasts, in ms (the shortest a potion of that type is documented to give). */
	public static long antifireDurationMs(String itemName)
	{
		String base = ChargedItemNames.baseName(itemName);
		switch (base)
		{
			case "extended super antifire":
				return 6 * 60_000L;
			case "extended antifire":
				return 12 * 60_000L;
			case "super antifire potion":
				return 3 * 60_000L;
			default:
				return 6 * 60_000L;
		}
	}

	/**
	 * Whether to drink antifire now. With the game varbit reporting active nothing is needed; once it has been seen
	 * active and has since lapsed the effect ended; if it never reported, fall back to the potion's duration.
	 */
	public static boolean antifireDue(long lastDrinkMs, long nowMs, long durationMs, boolean varbitSeenActive,
		boolean varbitActive)
	{
		if (lastDrinkMs <= 0)
		{
			return !varbitActive;
		}
		if (varbitActive)
		{
			return false;
		}
		long since = nowMs - lastDrinkMs;
		if (since < VARBIT_GRACE_MS)
		{
			return false;
		}
		return varbitSeenActive || since >= durationMs - EARLY_REDRINK_MS;
	}

	/** The safespot nearest to {@code from} on the same plane, or null when there is none. */
	public static WorldPoint nearestSafeSpot(WorldPoint from, List<WorldPoint> spots)
	{
		if (from == null || spots == null)
		{
			return null;
		}
		WorldPoint best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (WorldPoint spot : spots)
		{
			if (spot == null || spot.getPlane() != from.getPlane())
			{
				continue;
			}
			int distance = spot.distanceTo(from);
			if (distance < bestDistance)
			{
				best = spot;
				bestDistance = distance;
			}
		}
		return best;
	}

	/** Safespots only help against ranged and magic; melee must stand next to the target. */
	public static boolean usesSafeSpot(QuestCombatLoadout.Style style)
	{
		return style == QuestCombatLoadout.Style.RANGED || style == QuestCombatLoadout.Style.MAGIC;
	}

	/** Whether saved combat gear for {@code gearSlot} must be left alone because the quest needs that slot worn. */
	public static boolean slotConflicts(String gearSlot, java.util.Set<String> questSlots)
	{
		return gearSlot != null && !gearSlot.isEmpty() && questSlots != null
			&& questSlots.contains(gearSlot.toLowerCase(Locale.ROOT));
	}
}
