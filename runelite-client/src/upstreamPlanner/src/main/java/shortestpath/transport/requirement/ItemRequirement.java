package shortestpath.transport.requirement;

import java.util.Arrays;

import lombok.Getter;

/**
 * Represents an item requirement with its variations and quantity.
 * For example: AIR_RUNE=3 where AIR_RUNE can be substituted by DUST_RUNE,
 * SMOKE_RUNE, etc.
 */
@Getter
public class ItemRequirement
{
	/**
	 * The item IDs that satisfy this requirement (variations)
	 */
	private final int[] itemIds;

	/**
	 * Staff IDs that can substitute runes for this requirement
	 */
	private final int[] staffIds;

	/**
	 * Offhand IDs that can substitute for this requirement
	 */
	private final int[] offhandIds;

	/**
	 * The quantity required
	 */
	private final int quantity;

	public ItemRequirement(int[] itemIds, int[] staffIds, int[] offhandIds, int quantity)
	{
		this.itemIds = itemIds;
		this.staffIds = staffIds;
		this.offhandIds = offhandIds;
		this.quantity = quantity;
	}

	@Override
	public int hashCode()
	{
		int result = Arrays.hashCode(itemIds);
		result = 31 * result + Arrays.hashCode(staffIds);
		result = 31 * result + Arrays.hashCode(offhandIds);
		result = 31 * result + quantity;
		return result;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (o == null || getClass() != o.getClass())
		{
			return false;
		}
		ItemRequirement that = (ItemRequirement) o;
		return quantity == that.quantity &&
			Arrays.equals(itemIds, that.itemIds) &&
			Arrays.equals(staffIds, that.staffIds) &&
			Arrays.equals(offhandIds, that.offhandIds);
	}
}
