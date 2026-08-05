package net.runelite.client.plugins.microbot.util.walker;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable bank preparation required by the exact transport edges selected for a route.
 *
 * <p>Withdrawals and equipment changes are one contract: a route that depends on a banked staff or
 * tome is not prepared until the item is both withdrawn and equipped. An unsatisfiable loadout is
 * distinct from an empty loadout so callers cannot mistake a missing bank item for "nothing to do".</p>
 */
public final class Rs2TransportLoadout
{
	private static final Rs2TransportLoadout EMPTY =
		new Rs2TransportLoadout(Collections.emptyMap(), Collections.emptyList(), true);
	private static final Rs2TransportLoadout UNAVAILABLE =
		new Rs2TransportLoadout(Collections.emptyMap(), Collections.emptyList(), false);

	private final Map<Integer, Integer> withdrawals;
	private final List<Integer> equipmentItemIds;
	private final boolean satisfiable;

	public Rs2TransportLoadout(
		Map<Integer, Integer> withdrawals,
		List<Integer> equipmentItemIds,
		boolean satisfiable)
	{
		LinkedHashMap<Integer, Integer> withdrawalCopy = new LinkedHashMap<>();
		if (withdrawals != null)
		{
			for (Map.Entry<Integer, Integer> withdrawal : withdrawals.entrySet())
			{
				Integer itemId = withdrawal.getKey();
				Integer quantity = withdrawal.getValue();
				if (itemId == null || itemId <= 0 || quantity == null || quantity <= 0)
				{
					throw new IllegalArgumentException("invalid transport withdrawal: " + withdrawal);
				}
				withdrawalCopy.merge(itemId, quantity, Integer::sum);
			}
		}
		this.withdrawals = Collections.unmodifiableMap(withdrawalCopy);
		if (equipmentItemIds == null)
		{
			this.equipmentItemIds = Collections.emptyList();
		}
		else
		{
			for (Integer itemId : equipmentItemIds)
			{
				if (itemId == null || itemId <= 0)
				{
					throw new IllegalArgumentException("invalid equipment item id: " + itemId);
				}
			}
			this.equipmentItemIds = List.copyOf(equipmentItemIds);
		}
		this.satisfiable = satisfiable;
	}

	public static Rs2TransportLoadout empty()
	{
		return EMPTY;
	}

	public static Rs2TransportLoadout unavailable()
	{
		return UNAVAILABLE;
	}

	public Map<Integer, Integer> getWithdrawals() { return withdrawals; }
	public List<Integer> getEquipmentItemIds() { return equipmentItemIds; }
	public boolean isSatisfiable() { return satisfiable; }
	public boolean isEmpty() { return withdrawals.isEmpty() && equipmentItemIds.isEmpty(); }
}
