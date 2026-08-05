package net.runelite.client.plugins.microbot.util.walker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/** One immutable AND-clause whose item/quantity alternatives are OR-ed. */
public final class Rs2TransportItemRequirement
{
	private final Map<Integer, Integer> alternatives;
	private final Set<Integer> staffAlternatives;
	private final Set<Integer> offhandAlternatives;
	private final boolean runeOnly;

	public Rs2TransportItemRequirement(Map<Integer, Integer> alternatives)
	{
		this(alternatives, Collections.emptySet(), Collections.emptySet(), false);
	}

	public Rs2TransportItemRequirement(
		Map<Integer, Integer> alternatives,
		Set<Integer> staffAlternatives,
		Set<Integer> offhandAlternatives,
		boolean runeOnly)
	{
		if (alternatives == null || alternatives.isEmpty())
		{
			throw new IllegalArgumentException("item requirement must contain an alternative");
		}
		Map<Integer, Integer> copy = new LinkedHashMap<>();
		for (Map.Entry<Integer, Integer> alternative : alternatives.entrySet())
		{
			Integer itemId = Objects.requireNonNull(alternative.getKey(), "itemId");
			Integer quantity = Objects.requireNonNull(alternative.getValue(), "quantity");
			if (itemId <= 0 || quantity < 0)
			{
				throw new IllegalArgumentException("invalid item requirement: " + alternative);
			}
			copy.put(itemId, quantity);
		}
		this.alternatives = Collections.unmodifiableMap(copy);
		this.staffAlternatives = immutablePositiveIds(staffAlternatives, "staff");
		this.offhandAlternatives = immutablePositiveIds(offhandAlternatives, "offhand");
		this.runeOnly = runeOnly;
	}

	private static Set<Integer> immutablePositiveIds(Set<Integer> itemIds, String label)
	{
		if (itemIds == null || itemIds.isEmpty())
		{
			return Collections.emptySet();
		}
		LinkedHashSet<Integer> copy = new LinkedHashSet<>();
		for (Integer itemId : itemIds)
		{
			if (itemId == null || itemId <= 0)
			{
				throw new IllegalArgumentException(label + " item id must be positive: " + itemId);
			}
			copy.add(itemId);
		}
		return Collections.unmodifiableSet(copy);
	}

	public Map<Integer, Integer> getAlternatives()
	{
		return alternatives;
	}

	public Set<Integer> getStaffAlternatives() { return staffAlternatives; }
	public Set<Integer> getOffhandAlternatives() { return offhandAlternatives; }
	public boolean isRuneOnly() { return runeOnly; }

	public boolean isSatisfiedBy(IntUnaryOperator availableQuantity)
	{
		Objects.requireNonNull(availableQuantity, "availableQuantity");
		return alternatives.entrySet().stream().anyMatch(alternative ->
		{
			int required = alternative.getValue();
			int available = Math.max(0, availableQuantity.applyAsInt(alternative.getKey()));
			return (required == 0 && available == 0) || (required > 0 && available >= required);
		});
	}

	private boolean isSatisfiedBy(IntUnaryOperator availableQuantity, int staffItemId, int offhandItemId)
	{
		return isSatisfiedBy(availableQuantity)
			|| staffAlternatives.contains(staffItemId)
			|| offhandAlternatives.contains(offhandItemId);
	}

	public static Optional<ProviderSelection> selectProviders(
		List<Rs2TransportItemRequirement> requirements,
		IntUnaryOperator availableQuantity,
		IntPredicate staffAvailable,
		IntPredicate offhandAvailable)
	{
		return selectProviders(
			requirements, availableQuantity, staffAvailable, offhandAvailable, false);
	}

	/**
	 * Select equipment for provider-bearing clauses while deferring ordinary missing items to the
	 * withdrawal/purchase planner. This prevents an unbanked purchasable pass from rejecting the
	 * equipment phase before its fare fallback can be evaluated.
	 */
	public static Optional<ProviderSelection> selectEquipmentProviders(
		List<Rs2TransportItemRequirement> requirements,
		IntUnaryOperator availableQuantity,
		IntPredicate staffAvailable,
		IntPredicate offhandAvailable)
	{
		return selectProviders(
			requirements, availableQuantity, staffAvailable, offhandAvailable, true);
	}

	private static Optional<ProviderSelection> selectProviders(
		List<Rs2TransportItemRequirement> requirements,
		IntUnaryOperator availableQuantity,
		IntPredicate staffAvailable,
		IntPredicate offhandAvailable,
		boolean deferOrdinaryRequirements)
	{
		if (requirements == null || requirements.isEmpty())
		{
			return Optional.of(ProviderSelection.NONE);
		}
		TreeSet<Integer> staffs = new TreeSet<>();
		TreeSet<Integer> offhands = new TreeSet<>();
		for (Rs2TransportItemRequirement requirement : requirements)
		{
			requirement.staffAlternatives.stream().filter(staffAvailable::test).forEach(staffs::add);
			requirement.offhandAlternatives.stream().filter(offhandAvailable::test).forEach(offhands::add);
		}
		List<Integer> staffCandidates = new ArrayList<>();
		staffCandidates.add(ProviderSelection.NO_ITEM);
		staffCandidates.addAll(staffs);
		List<Integer> offhandCandidates = new ArrayList<>();
		offhandCandidates.add(ProviderSelection.NO_ITEM);
		offhandCandidates.addAll(offhands);
		for (Integer staff : staffCandidates)
		{
			for (Integer offhand : offhandCandidates)
			{
				boolean satisfied = true;
				for (Rs2TransportItemRequirement requirement : requirements)
				{
					if (deferOrdinaryRequirements
						&& requirement.staffAlternatives.isEmpty()
						&& requirement.offhandAlternatives.isEmpty())
					{
						continue;
					}
					if (!requirement.isSatisfiedBy(availableQuantity, staff, offhand))
					{
						satisfied = false;
						break;
					}
				}
				if (satisfied)
				{
					return Optional.of(new ProviderSelection(staff, offhand));
				}
			}
		}
		return Optional.empty();
	}

	public static final class ProviderSelection
	{
		private static final int NO_ITEM = -1;
		private static final ProviderSelection NONE = new ProviderSelection(NO_ITEM, NO_ITEM);

		private final int staffItemId;
		private final int offhandItemId;

		private ProviderSelection(int staffItemId, int offhandItemId)
		{
			this.staffItemId = staffItemId;
			this.offhandItemId = offhandItemId;
		}

		public int getStaffItemId() { return staffItemId; }
		public int getOffhandItemId() { return offhandItemId; }
		public boolean hasStaff() { return staffItemId > 0; }
		public boolean hasOffhand() { return offhandItemId > 0; }
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}
		if (!(other instanceof Rs2TransportItemRequirement))
		{
			return false;
		}
		Rs2TransportItemRequirement that = (Rs2TransportItemRequirement) other;
		return alternatives.equals(that.alternatives)
			&& staffAlternatives.equals(that.staffAlternatives)
			&& offhandAlternatives.equals(that.offhandAlternatives)
			&& runeOnly == that.runeOnly;
	}

	@Override
	public int hashCode()
	{
		int result = alternatives.hashCode();
		result = 31 * result + staffAlternatives.hashCode();
		result = 31 * result + offhandAlternatives.hashCode();
		return 31 * result + Boolean.hashCode(runeOnly);
	}
}
