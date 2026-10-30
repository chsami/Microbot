package net.runelite.client.plugins.microbot.questhelper;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What to do when a full inventory stands between the player and an item to pick up ("You don't have enough inventory
 * space to hold that item"): eat if hurt and carrying food, otherwise drop a junk item or, at full health, food. Never a
 * quest item, an untradeable or unpriced item, a noted item or a stack. Pure, so the decision can be tested.
 */
public final class QuestMakeRoom
{
	/** Things that are junk in almost every inventory; a quest that uses one lists it in its requirements. */
	private static final Set<String> JUNK = Set.of(
		"bones", "big bones", "burnt bones", "ashes", "vial", "empty vial", "jug", "empty jug", "bucket", "empty bucket",
		"burnt fish", "burnt meat", "burnt shrimp", "seaweed", "oyster", "casket", "beer glass");

	/** One inventory item as the decision sees it. */
	public static final class Slot
	{
		final int id;
		final String name;
		final long value;
		final boolean food;
		final boolean needed;
		final boolean tradeable;
		final boolean noted;
		final boolean stackable;

		public Slot(int id, String name, long value, boolean food, boolean needed, boolean tradeable, boolean noted,
			boolean stackable)
		{
			this.id = id;
			this.name = name;
			this.value = value;
			this.food = food;
			this.needed = needed;
			this.tradeable = tradeable;
			this.noted = noted;
			this.stackable = stackable;
		}

		/** A slot the quest needs and nothing else about it matters (for tests). */
		public Slot(int id, String name, long value, boolean food, boolean needed)
		{
			this(id, name, value, food, needed, true, false, false);
		}

		/** An item that is never dropped: needed, untradeable, unpriced, noted or stacked. */
		boolean protectedItem()
		{
			return needed || !tradeable || value <= 0 || noted || stackable;
		}

		boolean junk()
		{
			return name != null && JUNK.contains(name.toLowerCase(Locale.ROOT));
		}
	}

	public enum Action
	{
		/** A slot is free already. */
		NONE,
		/** Eat food: it heals and frees the slot. */
		EAT,
		/** Drop the item in {@link Decision#slot}. */
		DROP,
		/** Nothing carried may be dropped: nothing can be freed. */
		STUCK
	}

	public static final class Decision
	{
		public final Action action;
		public final Slot slot;

		Decision(Action action, Slot slot)
		{
			this.action = action;
			this.slot = slot;
		}
	}

	private QuestMakeRoom()
	{
	}

	/**
	 * @param freeSlots   free inventory slots right now
	 * @param hurt        hitpoints are below the maximum, so eating is not wasted
	 * @param items       one entry per distinct item stack carried, without the ones that already failed to drop
	 */
	public static Decision decide(int freeSlots, boolean hurt, List<Slot> items)
	{
		if (freeSlots > 0)
		{
			return new Decision(Action.NONE, null);
		}
		if (hurt)
		{
			for (Slot slot : items)
			{
				if (slot.food && !slot.needed)
				{
					return new Decision(Action.EAT, slot);
				}
			}
		}
		Slot cheapestJunk = null;
		Slot cheapestFood = null;
		for (Slot slot : items)
		{
			if (slot.protectedItem())
			{
				continue;
			}
			if (slot.junk() && (cheapestJunk == null || slot.value < cheapestJunk.value))
			{
				cheapestJunk = slot;
			}
			else if (slot.food && !hurt && (cheapestFood == null || slot.value < cheapestFood.value))
			{
				cheapestFood = slot;
			}
		}
		if (cheapestJunk != null)
		{
			return new Decision(Action.DROP, cheapestJunk);
		}
		return cheapestFood != null ? new Decision(Action.DROP, cheapestFood) : new Decision(Action.STUCK, null);
	}
}
