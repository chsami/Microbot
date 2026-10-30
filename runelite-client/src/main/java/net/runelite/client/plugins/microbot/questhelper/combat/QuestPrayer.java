package net.runelite.client.plugins.microbot.questhelper.combat;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

/** Pure helpers for the "Best offensive" prayer option and the pre-fight altar recharge. */
public final class QuestPrayer
{
	/** Known safe altars, used when no altar is loaded in the scene. */
	public static final List<WorldPoint> KNOWN_ALTARS = List.of(
		new WorldPoint(3253, 3484, 0), // Varrock church
		new WorldPoint(3243, 3206, 0)); // Lumbridge church

	/** Which unlockable prayers the player has; all false for a fresh account. */
	public static final class Unlocks
	{
		public boolean chivalry;
		public boolean piety;
		public boolean rigour;
		public boolean deadeye;
		public boolean augury;
		public boolean mysticVigour;
	}

	private QuestPrayer()
	{
	}

	/** Prayer level below which the quest helper will not use the Best offensive default. */
	public static final int MIN_DEFAULT_LEVEL = 4;

	/** The default prayer setting: Best offensive when the quest has fights and the player has the level. */
	public static QuestCombatLoadout.Prayer defaultPrayer(boolean hasFights, int prayerLevel)
	{
		return hasFights && prayerLevel >= MIN_DEFAULT_LEVEL
			? QuestCombatLoadout.Prayer.BEST_OFFENSIVE : QuestCombatLoadout.Prayer.OFF;
	}

	/** True when prayer points are low enough to go to an altar before a fight. */
	public static boolean needsRecharge(int points, int prayerLevel)
	{
		return points < Math.max(10, prayerLevel / 2);
	}

	/**
	 * The offensive prayers to run for the style: melee gives strength, attack and defence prayers (or Chivalry/
	 * Piety), ranged and magic give the one boost prayer. Empty when nothing is available at the level.
	 */
	public static List<Rs2PrayerEnum> bestOffensive(QuestCombatLoadout.Style style, int prayerLevel, Unlocks unlocks)
	{
		List<Rs2PrayerEnum> out = new ArrayList<>();
		if (style == null || unlocks == null)
		{
			return out;
		}
		switch (style)
		{
			case RANGED:
				addFirst(out, prayerLevel, unlocks.rigour ? Rs2PrayerEnum.RIGOUR : null,
					unlocks.deadeye ? Rs2PrayerEnum.DEAD_EYE : null, Rs2PrayerEnum.EAGLE_EYE, Rs2PrayerEnum.HAWK_EYE,
					Rs2PrayerEnum.SHARP_EYE);
				break;
			case MAGIC:
				addFirst(out, prayerLevel, unlocks.augury ? Rs2PrayerEnum.AUGURY : null,
					unlocks.mysticVigour ? Rs2PrayerEnum.MYSTIC_VIGOUR : null, Rs2PrayerEnum.MYSTIC_MIGHT,
					Rs2PrayerEnum.MYSTIC_LORE, Rs2PrayerEnum.MYSTIC_WILL);
				break;
			case MELEE:
			default:
				addFirst(out, prayerLevel, unlocks.piety ? Rs2PrayerEnum.PIETY : null,
					unlocks.chivalry ? Rs2PrayerEnum.CHIVALRY : null);
				if (!out.isEmpty())
				{
					break;
				}
				addFirst(out, prayerLevel, Rs2PrayerEnum.ULTIMATE_STRENGTH, Rs2PrayerEnum.SUPERHUMAN_STRENGTH,
					Rs2PrayerEnum.BURST_STRENGTH);
				addFirst(out, prayerLevel, Rs2PrayerEnum.INCREDIBLE_REFLEXES, Rs2PrayerEnum.IMPROVED_REFLEXES,
					Rs2PrayerEnum.CLARITY_THOUGHT);
				addFirst(out, prayerLevel, Rs2PrayerEnum.STEEL_SKIN, Rs2PrayerEnum.ROCK_SKIN,
					Rs2PrayerEnum.THICK_SKIN);
				break;
		}
		return out;
	}

	private static void addFirst(List<Rs2PrayerEnum> out, int prayerLevel, Rs2PrayerEnum... candidates)
	{
		for (Rs2PrayerEnum p : candidates)
		{
			if (p != null && prayerLevel >= p.getLevel())
			{
				out.add(p);
				return;
			}
		}
	}

	/** The known altar closest to {@code here} on the same plane, or null. */
	public static WorldPoint nearestKnownAltar(WorldPoint here)
	{
		WorldPoint best = null;
		int bestDist = Integer.MAX_VALUE;
		if (here == null)
		{
			return null;
		}
		for (WorldPoint altar : KNOWN_ALTARS)
		{
			int d = altar.distanceTo2D(here);
			if (d < bestDist)
			{
				bestDist = d;
				best = altar;
			}
		}
		return best;
	}

	/** Wilderness altars (Chaos altar) are skipped: the walk would be dangerous. */
	public static boolean inWilderness(WorldPoint p)
	{
		return p != null && p.getY() >= 3523 && p.getY() < 3968 && p.getX() >= 2944 && p.getX() < 3392;
	}

	/** Prayer points at or below which a held potion is drunk: the setup's value when set, else 15 or 30% of level. */
	public static int drinkThreshold(int loadoutDrinkAt, int prayerLevel)
	{
		return loadoutDrinkAt > 0 ? loadoutDrinkAt : Math.max(15, prayerLevel * 30 / 100);
	}

	/** Drink a held prayer potion: some prayer is active (ours or the player's), points low, potion held, rate ok. */
	public static boolean shouldDrinkHeldPotion(boolean anyPrayerActive, int points, int threshold, boolean potionHeld,
		long sinceLastConsumeMs, long minGapMs)
	{
		return anyPrayerActive && potionHeld && points <= threshold && sinceLastConsumeMs >= minGapMs;
	}

	/** Prayers are to be switched off: no points left, no potion to restore with, something still on. */
	public static boolean shouldStopPraying(boolean anyPrayerActive, int points, boolean potionHeld)
	{
		return anyPrayerActive && points <= 0 && !potionHeld;
	}
}
