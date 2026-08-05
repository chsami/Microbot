package net.runelite.client.plugins.microbot.shortestpath;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/**
 * One item requirement for a transport.
 *
 * <p>The alternatives are OR-ed and retain their individual quantities. A transport may contain
 * multiple instances of this class; those requirements are AND-ed. The upstream parser normalizes
 * every alternative in an OR group to that group's maximum quantity; {@link #parseRequirements}
 * deliberately applies the same rule.</p>
 */
public final class TransportItemRequirement {
    @Getter
    private final Map<Integer, Integer> alternatives;
    @Getter
    private final Set<Integer> staffAlternatives;
    @Getter
    private final Set<Integer> offhandAlternatives;
    @Getter
    private final boolean runeOnly;

    public TransportItemRequirement(Map<Integer, Integer> alternatives) {
        this(alternatives, Collections.emptySet(), Collections.emptySet(), false);
    }

    public TransportItemRequirement(Map<Integer, Integer> alternatives,
            Set<Integer> staffAlternatives, Set<Integer> offhandAlternatives, boolean runeOnly) {
        if (alternatives == null || alternatives.isEmpty()) {
            throw new IllegalArgumentException("item requirement must contain an alternative");
        }
        Map<Integer, Integer> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> entry : alternatives.entrySet()) {
            Integer itemId = entry.getKey();
            Integer quantity = entry.getValue();
            if (itemId == null || itemId <= 0) {
                throw new IllegalArgumentException("item id must be positive: " + itemId);
            }
            if (quantity == null || quantity < 0) {
                throw new IllegalArgumentException("item quantity must be non-negative: " + quantity);
            }
            if (copy.put(itemId, quantity) != null) {
                throw new IllegalArgumentException("duplicate item alternative: " + itemId);
            }
        }
        this.alternatives = Collections.unmodifiableMap(copy);
        this.staffAlternatives = immutablePositiveIds(staffAlternatives, "staff");
        this.offhandAlternatives = immutablePositiveIds(offhandAlternatives, "offhand");
        this.runeOnly = runeOnly;
    }

    private static Set<Integer> immutablePositiveIds(Set<Integer> itemIds, String label) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Collections.emptySet();
        }
        LinkedHashSet<Integer> copy = new LinkedHashSet<>();
        for (Integer itemId : itemIds) {
            if (itemId == null || itemId <= 0) {
                throw new IllegalArgumentException(label + " item id must be positive: " + itemId);
            }
            copy.add(itemId);
        }
        return Collections.unmodifiableSet(copy);
    }

    public static TransportItemRequirement legacyAlternatives(Set<Integer> itemIds) {
        Map<Integer, Integer> alternatives = new LinkedHashMap<>();
        for (Integer itemId : itemIds) {
            alternatives.put(itemId, 1);
        }
        return new TransportItemRequirement(alternatives);
    }

    /**
     * Parses the numeric subset of the upstream item grammar. Symbolic item collections must be
     * resolved by the pinned schema adapter before reaching Microbot resources.
     */
    public static List<TransportItemRequirement> parseNumericRequirements(String value) {
        return parseRequirements(value, false);
    }

    /**
     * Parses numeric item ids plus explicitly supported symbolic collections from the pinned adapter.
     * Unsupported collections fail closed rather than being omitted from the transport.
     */
    public static List<TransportItemRequirement> parseRequirements(String value) {
        return parseRequirements(value, true);
    }

    private static List<TransportItemRequirement> parseRequirements(String value, boolean allowSymbols) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String normalized = value.replace(" ", "")
                .replace("&&", "&")
                .replace("||", "|");
        List<TransportItemRequirement> requirements = new ArrayList<>();
        for (String andPart : normalized.split("&", -1)) {
            if (andPart.isEmpty()) {
                throw new IllegalArgumentException("empty AND item requirement in: " + value);
            }
            Map<Integer, Integer> parsedAlternatives = new LinkedHashMap<>();
            Set<Integer> staffAlternatives = new LinkedHashSet<>();
            Set<Integer> offhandAlternatives = new LinkedHashSet<>();
            boolean runeOnly = true;
            int maximumQuantity = -1;
            for (String orPart : andPart.split("\\|", -1)) {
                String[] itemAndQuantity = orPart.split("=", -1);
                if (itemAndQuantity.length != 2) {
                    throw new IllegalArgumentException("invalid item requirement: " + orPart);
                }
                final int quantity;
                try {
                    quantity = Integer.parseInt(itemAndQuantity[1]);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "unresolved symbolic or invalid item requirement: " + orPart, e);
                }
                Set<Integer> itemIds;
                try {
                    itemIds = Collections.singleton(Integer.parseInt(itemAndQuantity[0]));
                    runeOnly = false;
                } catch (NumberFormatException e) {
                    TransportItemResolver.Resolution resolution = allowSymbols
                            ? TransportItemResolver.resolve(itemAndQuantity[0]) : null;
                    if (resolution == null) {
                        throw new IllegalArgumentException(
                                "unresolved symbolic or invalid item requirement: " + orPart, e);
                    }
                    itemIds = resolution.getItemIds();
                    staffAlternatives.addAll(resolution.getStaffIds());
                    offhandAlternatives.addAll(resolution.getOffhandIds());
                    runeOnly &= resolution.isRune();
                }
                for (Integer itemId : itemIds) {
                    parsedAlternatives.merge(itemId, quantity, Math::max);
                }
                maximumQuantity = Math.max(maximumQuantity, quantity);
            }
            Map<Integer, Integer> alternatives = new LinkedHashMap<>();
            for (Integer itemId : parsedAlternatives.keySet()) {
                alternatives.put(itemId, maximumQuantity);
            }
            requirements.add(new TransportItemRequirement(
                    alternatives, staffAlternatives, offhandAlternatives, runeOnly));
        }
        return Collections.unmodifiableList(requirements);
    }

    public Set<Integer> getItemIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(alternatives.keySet()));
    }

    public int getRequiredQuantity(int itemId) {
        return alternatives.getOrDefault(itemId, -1);
    }

    public boolean isSatisfiedBy(IntUnaryOperator availableQuantity) {
        for (Map.Entry<Integer, Integer> alternative : alternatives.entrySet()) {
            int required = alternative.getValue();
            int available = Math.max(0, availableQuantity.applyAsInt(alternative.getKey()));
            if ((required == 0 && available == 0) || (required > 0 && available >= required)) {
                return true;
            }
        }
        return false;
    }

    boolean isSatisfiedBy(IntUnaryOperator availableQuantity, int staffItemId, int offhandItemId) {
        return isSatisfiedBy(availableQuantity)
                || staffAlternatives.contains(staffItemId)
                || offhandAlternatives.contains(offhandItemId);
    }

    /**
     * Select at most one future weapon and one future offhand that make every AND-clause true.
     * The same combination staff may satisfy multiple elemental rune clauses, matching the game.
     */
    public static Optional<ProviderSelection> selectProviders(
            List<TransportItemRequirement> requirements,
            IntUnaryOperator availableQuantity,
            IntPredicate staffAvailable,
            IntPredicate offhandAvailable) {
        if (requirements == null || requirements.isEmpty()) {
            return Optional.of(ProviderSelection.NONE);
        }
        TreeSet<Integer> staffs = new TreeSet<>();
        TreeSet<Integer> offhands = new TreeSet<>();
        for (TransportItemRequirement requirement : requirements) {
            requirement.staffAlternatives.stream().filter(staffAvailable::test).forEach(staffs::add);
            requirement.offhandAlternatives.stream().filter(offhandAvailable::test).forEach(offhands::add);
        }
        List<Integer> staffCandidates = new ArrayList<>();
        staffCandidates.add(ProviderSelection.NO_ITEM);
        staffCandidates.addAll(staffs);
        List<Integer> offhandCandidates = new ArrayList<>();
        offhandCandidates.add(ProviderSelection.NO_ITEM);
        offhandCandidates.addAll(offhands);
        for (Integer staff : staffCandidates) {
            for (Integer offhand : offhandCandidates) {
                boolean satisfied = true;
                for (TransportItemRequirement requirement : requirements) {
                    if (!requirement.isSatisfiedBy(availableQuantity, staff, offhand)) {
                        satisfied = false;
                        break;
                    }
                }
                if (satisfied) {
                    return Optional.of(new ProviderSelection(staff, offhand));
                }
            }
        }
        return Optional.empty();
    }

    public Set<Integer> getAllItemIds() {
        LinkedHashSet<Integer> itemIds = new LinkedHashSet<>(alternatives.keySet());
        itemIds.addAll(staffAlternatives);
        itemIds.addAll(offhandAlternatives);
        return Collections.unmodifiableSet(itemIds);
    }

    public static final class ProviderSelection {
        static final int NO_ITEM = -1;
        static final ProviderSelection NONE = new ProviderSelection(NO_ITEM, NO_ITEM);

        private final int staffItemId;
        private final int offhandItemId;

        private ProviderSelection(int staffItemId, int offhandItemId) {
            this.staffItemId = staffItemId;
            this.offhandItemId = offhandItemId;
        }

        public int getStaffItemId() { return staffItemId; }
        public int getOffhandItemId() { return offhandItemId; }
        public boolean hasStaff() { return staffItemId > 0; }
        public boolean hasOffhand() { return offhandItemId > 0; }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TransportItemRequirement)) {
            return false;
        }
        TransportItemRequirement that = (TransportItemRequirement) other;
        return alternatives.equals(that.alternatives)
                && staffAlternatives.equals(that.staffAlternatives)
                && offhandAlternatives.equals(that.offhandAlternatives)
                && runeOnly == that.runeOnly;
    }

    @Override
    public int hashCode() {
        int result = alternatives.hashCode();
        result = 31 * result + staffAlternatives.hashCode();
        result = 31 * result + offhandAlternatives.hashCode();
        return 31 * result + Boolean.hashCode(runeOnly);
    }

    @Override
    public String toString() {
        return alternatives + " staff=" + staffAlternatives + " offhand=" + offhandAlternatives;
    }
}
