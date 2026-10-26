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


	/** Quests that cannot be finished solo: enum names of {@code QuestHelperQuest} mapped to what the other player does. */
	private static final Map<String, String> NEEDS_ANOTHER_PLAYER = new java.util.LinkedHashMap<>();

	static
	{
		NEEDS_ANOTHER_PLAYER.put("SHIELD_OF_ARRAV_PHOENIX_GANG", "trade the certificate half / key");
		NEEDS_ANOTHER_PLAYER.put("SHIELD_OF_ARRAV_BLACK_ARM_GANG", "trade the certificate half / key");
		NEEDS_ANOTHER_PLAYER.put("HEROES_QUEST", "trade the other gang's key");
	}

	/** Whether the quest (a {@code QuestHelperQuest} enum name) needs a second player. */
	public static boolean needsAnotherPlayer(String questEnumName)
	{
		return questEnumName != null && NEEDS_ANOTHER_PLAYER.containsKey(questEnumName);
	}

	/** The failed status for such a quest, or null when it can be done solo. */
	public static String anotherPlayerFailure(String questEnumName, String questDisplayName)
	{
		String task = questEnumName == null ? null : NEEDS_ANOTHER_PLAYER.get(questEnumName);
		return task == null ? null
			: FAILED_PREFIX + questDisplayName + " needs another player to " + task + " - can't be done solo";
	}

	/** Items a quest hands over itself, by quest enum name; never bought up front. */
	private static final Map<String, Set<String>> QUEST_GIVEN = new java.util.HashMap<>();

	static
	{
		QUEST_GIVEN.put("RUNE_MYSTERIES", new LinkedHashSet<>(java.util.Arrays.asList(
			"air talisman", "research package", "research notes")));
	}

	/**
	 * Whether the quest itself supplies this item, so the preflight must not buy it: it is flagged "can be obtained
	 * during the quest", the quest is known to hand it out, or the quest has not started and the item only appears
	 * on later steps without being in the quest's upfront item list.
	 */
	public static boolean obtainedDuringQuest(String questEnumName, String itemName, String tooltip,
		boolean notStarted, boolean inUpfrontList)
	{
		String lowerName = itemName == null ? "" : itemName.toLowerCase(java.util.Locale.ROOT);
		Set<String> given = questEnumName == null ? null : QUEST_GIVEN.get(questEnumName);
		if (given != null && given.contains(lowerName))
		{
			return true;
		}
		String lowerTip = tooltip == null ? "" : tooltip.toLowerCase(java.util.Locale.ROOT);
		if (lowerTip.contains("obtained during the quest") || lowerTip.contains("you can get another from"))
		{
			return true;
		}
		return notStarted && !inUpfrontList;
	}

	/** Any dose of any antifire potion, for quests with a dragon fight; the caller adds it to the needs list. */
	public static Need antifireNeed()
	{
		return new Need("Antifire potion", java.util.Arrays.asList(
			net.runelite.api.ItemID.ANTIFIRE_POTION4, net.runelite.api.ItemID.ANTIFIRE_POTION3,
			net.runelite.api.ItemID.ANTIFIRE_POTION2, net.runelite.api.ItemID.ANTIFIRE_POTION1,
			net.runelite.api.ItemID.EXTENDED_ANTIFIRE4, net.runelite.api.ItemID.EXTENDED_ANTIFIRE3,
			net.runelite.api.ItemID.EXTENDED_ANTIFIRE2, net.runelite.api.ItemID.EXTENDED_ANTIFIRE1,
			net.runelite.api.ItemID.SUPER_ANTIFIRE_POTION4, net.runelite.api.ItemID.SUPER_ANTIFIRE_POTION3,
			net.runelite.api.ItemID.SUPER_ANTIFIRE_POTION2, net.runelite.api.ItemID.SUPER_ANTIFIRE_POTION1,
			net.runelite.api.ItemID.EXTENDED_SUPER_ANTIFIRE4, net.runelite.api.ItemID.EXTENDED_SUPER_ANTIFIRE3,
			net.runelite.api.ItemID.EXTENDED_SUPER_ANTIFIRE2, net.runelite.api.ItemID.EXTENDED_SUPER_ANTIFIRE1), 1, true);
	}

	private QuestPreflight()
	{
	}

	/** A requirement for a supply the user selected in the setup window. */
	public static Need supplyNeed(String name, List<Integer> ids, int quantity, boolean consumed)
	{
		return new Need(name, ids, quantity, consumed, true);
	}

	/** Label for the purchase list: says plainly that a selected supply was not found and will be bought. */
	public static String purchaseLabel(Shortfall s)
	{
		if (!s.supply || s.missing <= 0)
		{
			return s.name;
		}
		return s.name + (s.owned == 0 ? " (missing - will buy)" : " (short " + s.missing + " - will buy)");
	}

	/** One requirement fed to the computation: the ids that satisfy it (alternates included) and how many. */
	public static final class Need
	{
		public final String name;
		public final List<Integer> ids;
		public final int quantity;
		/** False for items the quest does not use up ({@code isNotConsumed()}). */
		public final boolean consumed;
		/** True for a supply the user ticked in the quest setup (stamina, food, potions, custom items). */
		public final boolean supply;

		/** A requirement satisfied by any of {@code ids}, needing {@code quantity} in total. */
		public Need(String name, List<Integer> ids, int quantity, boolean consumed)
		{
			this(name, ids, quantity, consumed, false);
		}

		Need(String name, List<Integer> ids, int quantity, boolean consumed, boolean supply)
		{
			this.supply = supply;
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
		public final boolean supply;

		Shortfall(String name, List<Integer> ids, int needed, int owned, boolean supply)
		{
			this.supply = supply;
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
		List<Boolean> supplies = new ArrayList<>();

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
			boolean supply = need.supply;
			// Absorb every earlier group that shares an id, which also joins groups an alternate bridges
			for (int i = idSets.size() - 1; i >= 0; i--)
			{
				if (!java.util.Collections.disjoint(idSets.get(i), ids))
				{
					ids.addAll(idSets.get(i));
					quantity = Math.max(quantity, quantities.get(i));
					supply = supply || supplies.get(i);
					supplies.remove(i);
					name = names.get(i) != null ? names.get(i) : name;
					idSets.remove(i);
					quantities.remove(i);
					names.remove(i);
				}
			}
			idSets.add(ids);
			quantities.add(quantity);
			names.add(name);
			supplies.add(supply);
		}

		List<Shortfall> out = new ArrayList<>();
		for (int i = 0; i < idSets.size(); i++)
		{
			out.add(new Shortfall(names.get(i), new ArrayList<>(idSets.get(i)), quantities.get(i),
				countOwned(idSets.get(i), owned), supplies.get(i)));
		}
		return out;
	}

	/**
	 * Adds the noted stock the player carries to its unnoted id, once per item id however many needs list that id.
	 *
	 * @param notedVariant noted id for an unnoted id, or a value <= 0 when there is none
	 * @param onPlayer inventory + equipment counts, updated in place
	 * @param owned inventory + equipment + bank counts, updated in place
	 */
	static void foldNoted(List<Need> needs, java.util.function.IntUnaryOperator notedVariant,
		Map<Integer, Integer> onPlayer, Map<Integer, Integer> owned)
	{
		Set<Integer> folded = new java.util.HashSet<>();
		for (Need need : needs)
		{
			if (need == null || need.ids == null)
			{
				continue;
			}
			for (Integer id : need.ids)
			{
				if (id == null || !folded.add(id))
				{
					continue;
				}
				int notedId = notedVariant.applyAsInt(id);
				if (notedId > 0)
				{
					int noted = onPlayer.getOrDefault(notedId, 0);
					if (noted > 0)
					{
						onPlayer.merge(id, noted, Integer::sum);
						owned.merge(id, noted, Integer::sum);
					}
				}
			}
		}
	}

	/**
	 * Whether the bank was actually opened and read this session. The bank item list can be restored from a previous
	 * session, so a non-empty list alone proves nothing; only a container update tick (>= 0) does.
	 */
	public static boolean bankScanned(int bankLastUpdatedTick)
	{
		return bankLastUpdatedTick >= 0;
	}

	/** Whether the bank holds enough of a needed item (or, if the item is carry-limited, any of it) to withdraw it. */
	public static boolean bankCovers(int inBank, int needed, boolean carryLimited)
	{
		return needed > 0 && (inBank >= needed || (inBank > 0 && carryLimited));
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

	/** Sets the status reported to the Agent Server. */
	static void setStatus(String value)
	{
		status = value;
	}

	/** True while the preflight is counting or buying. */
	static boolean isInProgress()
	{
		return CHECKING.equals(status) || BUYING.equals(status);
	}

	/** Stop: back to idle, so the status no longer says checking or buying. */
	static void resetToIdle()
	{
		status = PENDING;
	}

	/** Called by the Agent Server before a start that asked to skip the preflight. */
	public static void skipNextStart(boolean skip)
	{
		SKIP_NEXT.set(skip);
	}

	/** Reads and clears the skip flag set by {@link #skipNextStart(boolean)}. */
	static boolean consumeSkip()
	{
		return SKIP_NEXT.getAndSet(false);
	}
}
