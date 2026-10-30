package net.runelite.client.plugins.microbot.questhelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;
import net.runelite.client.plugins.microbot.questhelper.combat.ChargedItemNames;

/** Pure rules for the preflight's Grand Exchange buying: what may stop a quest, and which variant gets bought. */
public final class QuestBuyRules
{
	/** Charged jewellery families: every charge is a separate item id, but only the fullest is traded. */
	private static final Set<String> JEWELLERY = new HashSet<>(Arrays.asList(
		"games necklace", "necklace of minigames", "ring of dueling", "amulet of glory", "skills necklace",
		"combat bracelet", "ring of wealth", "necklace of passage", "digsite pendant", "burning amulet",
		"slayer ring", "ring of the elements"));

	private static final java.util.regex.Pattern TRIMMED = java.util.regex.Pattern.compile("\\(t\\d+\\)$",java.util.regex.Pattern.CASE_INSENSITIVE);

	private QuestBuyRules()
	{
	}

	/** One buyable id of a requirement with its in-game name. */
	public static final class Candidate
	{
		public final int id;
		public final String name;

		public Candidate(int id, String name)
		{
			this.id = id;
			this.name = name == null ? "" : name;
		}
	}

	/** True for a (non-trimmed) charged jewellery item such as "Games necklace(3)". */
	public static boolean isChargedJewellery(String name)
	{
		if (name == null || ChargedItemNames.charge(name) <= 0 || TRIMMED.matcher(name.trim()).find())
		{
			return false;
		}
		return JEWELLERY.contains(ChargedItemNames.baseName(name));
	}

	/** Whether a failed purchase stops the quest: only when nothing was placed and a required item is among them. */
	public static boolean mustStop(int placedOffers, boolean anyRequiredWanted)
	{
		return placedOffers == 0 && anyRequiredWanted;
	}

	/**
	 * The id to buy. A jewellery family buys the fullest charge that has price data (the low charges are rarely traded
	 * and unpriced); anything else buys the cheapest priced id, which skips league and cosmetic copies. With no price
	 * data at all the first id (fullest charge for jewellery) is returned. -1 when there are no candidates.
	 *
	 * @param priceOf price of an id, or <= 0 when unknown; asked lazily
	 */
	public static int pickBuyId(List<Candidate> candidates, ToIntFunction<Integer> priceOf)
	{
		if (candidates == null || candidates.isEmpty())
		{
			return -1;
		}
		boolean jewellery = candidates.stream().allMatch(c -> isChargedJewellery(c.name));
		List<Candidate> ordered = new ArrayList<>(candidates);
		if (jewellery)
		{
			ordered.sort(Comparator.comparingInt((Candidate c) -> ChargedItemNames.charge(c.name)).reversed());
			for (Candidate c : ordered)
			{
				int price = priceOf.applyAsInt(c.id);
				if (price > 0 && price < Integer.MAX_VALUE)
				{
					return c.id;
				}
			}
			return ordered.get(0).id;
		}
		int bestId = ordered.get(0).id;
		int bestPrice = Integer.MAX_VALUE;
		for (Candidate c : ordered)
		{
			int price = priceOf.applyAsInt(c.id);
			if (price > 0 && price < bestPrice)
			{
				bestPrice = price;
				bestId = c.id;
			}
		}
		return bestId;
	}

	/** Key that identifies a requirement across preflight rounds (its alternates), for the skipped-optional set. */
	public static String key(List<Integer> ids)
	{
		return ids == null ? "" : new ArrayList<>(ids).toString();
	}
}
