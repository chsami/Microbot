package net.runelite.client.plugins.microbot.questhelper.combat;

import java.util.Random;

/** Chooses the health percentage at which food is eaten: the setup's own value wins over the global setting, with a small variance. */
final class EatPolicy
{
	static final int VARIANCE = 5;

	private EatPolicy()
	{
	}

	/** Base percentage: the setup's (per quest/fight) value when a non-skipped setup exists, else the global one; clamped 10..90. */
	static int base(int globalPercent, QuestCombatLoadout loadout)
	{
		int pct = loadout != null && !loadout.isSkip() ? loadout.getEatAtPercent() : globalPercent;
		return Math.max(10, Math.min(90, pct));
	}

	/** Random offset in -VARIANCE..+VARIANCE, re-rolled after each meal so the threshold does not flicker per tick. */
	static int rollOffset(Random rng)
	{
		return rng.nextInt(2 * VARIANCE + 1) - VARIANCE;
	}

	static int threshold(int globalPercent, QuestCombatLoadout loadout, int offset)
	{
		return Math.max(5, Math.min(95, base(globalPercent, loadout) + offset));
	}
}
