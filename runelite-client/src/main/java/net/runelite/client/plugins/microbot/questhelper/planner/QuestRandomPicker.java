package net.runelite.client.plugins.microbot.questhelper.planner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Chooses the next quest of the random/auto queue mode, and how long to rest between quests. Pure. */
public final class QuestRandomPicker
{
	/** A quest the picker may choose. */
	public static final class Candidate
	{
		public final String name;
		/** Requirements met right now (skills, quests, unlocks) and not completed. */
		public final boolean doable;
		public final boolean needsOtherPlayer;
		/** Needs items the player did not allow buying and does not have. */
		public final boolean needsBlockedItems;

		public Candidate(String name, boolean doable, boolean needsOtherPlayer, boolean needsBlockedItems)
		{
			this.name = name;
			this.doable = doable;
			this.needsOtherPlayer = needsOtherPlayer;
			this.needsBlockedItems = needsBlockedItems;
		}
	}

	private QuestRandomPicker()
	{
	}

	/** The quests the picker may choose from, name order. {@code pool} empty means every doable quest. */
	public static List<Candidate> eligible(List<Candidate> all, Collection<String> pool, Collection<String> skipped)
	{
		List<Candidate> out = new ArrayList<>();
		for (Candidate candidate : all)
		{
			if (!candidate.doable || candidate.needsOtherPlayer || candidate.needsBlockedItems
				|| (skipped != null && skipped.contains(candidate.name))
				|| (pool != null && !pool.isEmpty() && !pool.contains(candidate.name)))
			{
				continue;
			}
			out.add(candidate);
		}
		out.sort(Comparator.comparing(c -> c.name));
		return out;
	}

	/** One eligible quest chosen at random, or null when nothing is doable. Seeded {@code random} is repeatable. */
	public static String pick(List<Candidate> all, Collection<String> pool, Collection<String> skipped, Random random)
	{
		List<Candidate> options = eligible(all, pool, skipped);
		return options.isEmpty() ? null : options.get(random.nextInt(options.size())).name;
	}

	/**
	 * The rest between two quests: uniform between the min and max minutes, pushed towards the max by fatigue
	 * (0 to 1) so a tired session rests longer. Returns milliseconds.
	 */
	public static long pauseMs(int minMinutes, int maxMinutes, double fatigue, Random random)
	{
		int lo = Math.max(0, Math.min(minMinutes, maxMinutes));
		int hi = Math.max(lo, Math.max(minMinutes, maxMinutes));
		double f = Math.max(0, Math.min(1, fatigue));
		double span = (hi - lo) * 60_000.0;
		double draw = random.nextDouble();
		double skewed = draw + (1 - draw) * f * 0.5;
		return (long) (lo * 60_000.0 + span * skewed);
	}
}
