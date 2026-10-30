package net.runelite.client.plugins.microbot.questhelper.planner;

/**
 * How many stamina potions may sit in the inventory. Staminas never displace quest items or reserved slots, and are
 * dropped altogether while a step needs an empty or restricted inventory. Pure.
 */
public final class QuestStaminaRules
{
	private QuestStaminaRules()
	{
	}

	/**
	 * @param wanted         the stamina potions the profile wants kept (0-4)
	 * @param held           stamina potions already in the inventory
	 * @param freeSlots      empty inventory slots now
	 * @param reservedSlots  slots reserved for quest items still to be picked up or withdrawn
	 * @param restricted     the current or upcoming step forbids carrying items
	 * @return how many stamina potions to have in the inventory in total
	 */
	public static int allowed(int wanted, int held, int freeSlots, int reservedSlots, boolean restricted)
	{
		return allowed(wanted, held, freeSlots, reservedSlots, restricted, QuestProfile.MAX_MIN_STAMINA);
	}

	/** As {@link #allowed(int, int, int, int, boolean)} with a larger cap, for an explicit stamina count. */
	public static int allowed(int wanted, int held, int freeSlots, int reservedSlots, boolean restricted, int cap)
	{
		if (restricted)
		{
			return 0;
		}
		int target = Math.max(0, Math.min(cap, wanted));
		// slots staminas can use: the free ones, plus the ones they already hold, minus what the quest needs
		int room = Math.max(0, freeSlots + Math.max(0, held) - Math.max(0, reservedSlots));
		return Math.min(target, room);
	}

	/** How many more to withdraw, 0 when enough are held. */
	public static int toWithdraw(int wanted, int held, int freeSlots, int reservedSlots, boolean restricted)
	{
		return Math.max(0, allowed(wanted, held, freeSlots, reservedSlots, restricted) - Math.max(0, held));
	}

	/** True when the staminas must be banked: restricted, or more are held than the slot rules allow. */
	public static boolean mustDeposit(int wanted, int held, int freeSlots, int reservedSlots, boolean restricted)
	{
		return held > allowed(wanted, held, freeSlots, reservedSlots, restricted);
	}

	/** As {@link #mustDeposit(int, int, int, int, boolean)} with a larger cap. */
	public static boolean mustDeposit(int wanted, int held, int freeSlots, int reservedSlots, boolean restricted,
		int cap)
	{
		return held > allowed(wanted, held, freeSlots, reservedSlots, restricted, cap);
	}

	/**
	 * Whether to put the staminas back now: only when a bank is open already or reached on the way, never an extra
	 * trip for them.
	 */
	public static boolean withdrawNow(boolean bankOpen, boolean bankOnTheWay)
	{
		return bankOpen || bankOnTheWay;
	}
}
