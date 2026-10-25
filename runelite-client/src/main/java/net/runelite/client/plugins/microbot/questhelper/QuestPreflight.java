package net.runelite.client.plugins.microbot.questhelper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The quest preflight: before a quest starts, check that every tradable item the rest of the quest needs is on the
 * player or in the bank, and buy the shortfall. This class holds the pure merge/shortfall computation and the status
 * the Agent Server reports; the game interaction lives in {@link QuestScript}.
 */
public final class QuestPreflight
{
	public static final String PENDING = "pending";
	public static final String CHECKING = "checking";
	public static final String BUYING = "buying";
	public static final String DONE = "done";
	public static final String FAILED_PREFIX = "failed: ";

	private static volatile String status = PENDING;
	private static final AtomicBoolean SKIP_NEXT = new AtomicBoolean(false);

	private QuestPreflight()
	{
	}

	/** One requirement fed to the computation: the ids that satisfy it (alternates included) and how many. */
	public static final class Need
	{
		public final String name;
		public final List<Integer> ids;
		public final int quantity;
		/** False for items the quest does not use up ({@code isNotConsumed()}). */
		public final boolean consumed;

		public Need(String name, List<Integer> ids, int quantity, boolean consumed)
		{
			this.name = name;
			this.ids = ids;
			this.quantity = quantity;
			this.consumed = consumed;
		}
	}

	/** A merged requirement with how much of it is owned and how much is still missing. */
	public static final class Shortfall
	{
		public final String name;
		public final List<Integer> ids;
		public final int needed;
		public final int owned;
		public final int missing;

		Shortfall(String name, List<Integer> ids, int needed, int owned)
		{
			this.name = name;
			this.ids = ids;
			this.needed = needed;
			this.owned = owned;
			this.missing = Math.max(0, needed - owned);
		}
	}

	/**
	 * Merges requirements that share an id (an alternate counts) and works out what is missing.
	 * <p>
	 * Duplicates take the largest quantity rather than the sum: a requirement repeated across steps is almost always
	 * the same item carried through, and whether it is used up in several separate steps cannot be judged from the
	 * requirement alone, so the maximum is used for consumed and unconsumed items alike.
	 *
	 * @param needs requirements, order kept
	 * @param owned count held (inventory + equipment + bank) per item id
	 * @return one entry per merged requirement, including the satisfied ones (missing == 0)
	 */
	public static List<Shortfall> compute(List<Need> needs, Map<Integer, Integer> owned)
	{
		List<String> names = new ArrayList<>();
		List<Set<Integer>> idSets = new ArrayList<>();
		List<Integer> quantities = new ArrayList<>();

		for (Need need : needs)
		{
			if (need == null || need.quantity <= 0 || need.ids == null)
			{
				continue;
			}
			Set<Integer> ids = new LinkedHashSet<>();
			for (Integer id : need.ids)
			{
				if (id != null && id > 0)
				{
					ids.add(id);
				}
			}
			if (ids.isEmpty())
			{
				continue;
			}

			String name = need.name;
			int quantity = need.quantity;
			// Absorb every earlier group that shares an id, which also joins groups an alternate bridges
			for (int i = idSets.size() - 1; i >= 0; i--)
			{
				if (!java.util.Collections.disjoint(idSets.get(i), ids))
				{
					ids.addAll(idSets.get(i));
					quantity = Math.max(quantity, quantities.get(i));
					name = names.get(i) != null ? names.get(i) : name;
					idSets.remove(i);
					quantities.remove(i);
					names.remove(i);
				}
			}
			idSets.add(ids);
			quantities.add(quantity);
			names.add(name);
		}

		List<Shortfall> out = new ArrayList<>();
		for (int i = 0; i < idSets.size(); i++)
		{
			out.add(new Shortfall(names.get(i), new ArrayList<>(idSets.get(i)), quantities.get(i),
				countOwned(idSets.get(i), owned)));
		}
		return out;
	}

	/** Total held of the given ids (each id counted once). */
	public static int countOwned(Iterable<Integer> ids, Map<Integer, Integer> owned)
	{
		long total = 0;
		Set<Integer> seen = new LinkedHashSet<>();
		for (Integer id : ids)
		{
			if (id != null && seen.add(id))
			{
				total += owned.getOrDefault(id, 0);
			}
		}
		return (int) Math.min(Integer.MAX_VALUE, total);
	}

	/** Status reported by {@code GET /quest-helper/status}. */
	public static String getStatus()
	{
		return status;
	}

	static void setStatus(String value)
	{
		status = value;
	}

	static boolean isInProgress()
	{
		return CHECKING.equals(status) || BUYING.equals(status);
	}

	/** Called by the Agent Server before a start that asked to skip the preflight. */
	public static void skipNextStart(boolean skip)
	{
		SKIP_NEXT.set(skip);
	}

	static boolean consumeSkip()
	{
		return SKIP_NEXT.getAndSet(false);
	}
}
