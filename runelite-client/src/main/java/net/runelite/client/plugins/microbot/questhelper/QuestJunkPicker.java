package net.runelite.client.plugins.microbot.questhelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Which carried items a full inventory may drop to make room for a step's pickup: anything the quest does not need, the
 * player did not bring (the caller protects those), that is cheap but priced, tradeable, not stacked and has a Drop
 * action. Pure, so it can be tested.
 */
public final class QuestJunkPicker
{
	/** Items worth this much each or more are gear or valuables, not junk. */
	public static final long VALUABLE_VALUE = 1_000;

	/** Free slots to aim for after a pickup was refused, so the next one does not bring "no space" straight back. */
	public static final int TARGET_FREE_SLOTS = 3;

	/** How long a "not enough inventory space" message counts as proof that a pickup is blocked. */
	public static final long BLOCKED_WINDOW_MS = 8_000;

	/** Pause after a full inventory with nothing to drop, so the check is not repeated every tick. */
	public static final long IDLE_COOLDOWN_MS = 30_000;

	/** Whether there is evidence that an item gain is blocked: the game said so lately, or a kill step waits for a drop. */
	public static boolean pickupBlocked(long nowMs, long fullMessageAtMs, boolean killStepWantsDrop)
	{
		return killStepWantsDrop || (fullMessageAtMs > 0 && nowMs - fullMessageAtMs < BLOCKED_WINDOW_MS);
	}

	/** Whether the game itself refused a pickup lately (as opposed to a kill step that only expects a drop). */
	public static boolean pickupRefused(long nowMs, long fullMessageAtMs)
	{
		return pickupBlocked(nowMs, fullMessageAtMs, false);
	}

	/** One inventory slot as the decision sees it; {@code value} is the price of a single item. */
	public static final class Item
	{
		final int id;
		final String name;
		final long value;
		final boolean stackable;
		final boolean noted;
		final boolean tradeable;
		final boolean hasDrop;

		public Item(int id, String name, long value, boolean stackable, boolean noted, boolean tradeable, boolean hasDrop)
		{
			this.id = id;
			this.name = name;
			this.value = value;
			this.stackable = stackable;
			this.noted = noted;
			this.tradeable = tradeable;
			this.hasDrop = hasDrop;
		}

		public int id()
		{
			return id;
		}

		public String name()
		{
			return name;
		}

		/** Not needed by the quest or the player: the only items that may leave the inventory at all. */
		private boolean unclaimed(Set<Integer> protectedIds)
		{
			return !protectedIds.contains(id) && !stackable && !noted;
		}

		private boolean droppable(Set<Integer> protectedIds)
		{
			// untradeable items may be quest items the quest never lists; an unpriced item may just have no price yet
			return unclaimed(protectedIds) && hasDrop && tradeable && value > 0 && value < VALUABLE_VALUE;
		}
	}

	private QuestJunkPicker()
	{
	}

	/**
	 * @param protectedIds items the quest still needs and everything the player or the preflight brought
	 * @param targetFree   free slots to reach: 1 for a kill step's drop, {@link #TARGET_FREE_SLOTS} after a refused pickup
	 * @return the slots to drop, cheapest first, as many as it takes to reach the target
	 */
	public static List<Item> pickJunk(List<Item> items, Set<Integer> protectedIds, int freeSlots, int targetFree)
	{
		List<Item> junk = new ArrayList<>();
		for (Item item : items)
		{
			if (item.droppable(protectedIds))
			{
				junk.add(item);
			}
		}
		junk.sort(Comparator.comparingLong((Item i) -> i.value).thenComparing(i -> i.name == null ? "" : i.name));
		int wanted = Math.max(0, targetFree - freeSlots);
		return junk.size() > wanted ? new ArrayList<>(junk.subList(0, wanted)) : junk;
	}

	/** Valuable tradeable slots nothing claims: the only things worth a bank trip (untradeables may be quest items). */
	public static List<Item> bankable(List<Item> items, Set<Integer> protectedIds)
	{
		List<Item> out = new ArrayList<>();
		for (Item item : items)
		{
			if (item.unclaimed(protectedIds) && item.tradeable && item.value >= VALUABLE_VALUE)
			{
				out.add(item);
			}
		}
		return out;
	}
}
