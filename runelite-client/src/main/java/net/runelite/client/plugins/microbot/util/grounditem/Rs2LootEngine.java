package net.runelite.client.plugins.microbot.util.grounditem;

import net.runelite.api.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.grounditems.GroundItem;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.runelite.api.TileItem.OWNERSHIP_SELF;
import static net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem.*;

// import static your.package.OwnershipConstants.OWNERSHIP_SELF;

public final class Rs2LootEngine {
    private static final int DESPAWN_DELAY_THRESHOLD_TICKS = 150;

    private Rs2LootEngine() {}

    public static Builder with(LootingParameters params) {
        return new Builder(params);
    }

    public static final class Builder {
        private final LootingParameters params;
        private Consumer<GroundItem> lootAction = gi -> {};
        private final Map<String, List<GroundItem>> candidateBuckets = new LinkedHashMap<>();

        private Builder(LootingParameters params) {
            this.params = Objects.requireNonNull(params, "params");
        }

        public Builder withLootAction(Consumer<GroundItem> lootAction) {
            this.lootAction = Objects.requireNonNull(lootAction, "lootAction");
            return this;
        }

        /** Existing intents (kept for completeness) */
        public Builder addByValue() {
            Predicate<GroundItem> byValue = gi -> {
                final int qty = Math.max(1, gi.getQuantity());
                final int price = gi.getGePrice();
                return price > params.getMinValue() && (price / qty) < params.getMaxValue();
            };
            final Set<String> ignoredLower = toLowerTrimmedSet(params.getIgnoredNames());
            collect("byValue", byValue, ignoredLower);
            return this;
        }

        public Builder addByNames() {
            final Set<String> needles = toLowerTrimmedSet(params.getNames());
            if (needles.isEmpty()) return this;

            Predicate<GroundItem> byNames = gi -> {
                final String n = safeLower(gi.getName());
                for (String needle : needles) {
                    if (n.contains(needle)) return true;
                }
                return false;
            };
            collect("byNames", byNames, null);
            return this;
        }

        public Builder addUntradables() {
            Predicate<GroundItem> untradables = gi -> !gi.isTradeable() && gi.getId() != ItemID.COINS_995;
            collect("untradables", untradables, null);
            return this;
        }

        public Builder addCoins() {
            Predicate<GroundItem> coins = gi -> gi.getId() == ItemID.COINS_995;
            collect("coins", coins, null);
            return this;
        }

        /** ── NEW INTENTS ───────────────────────────────────────────────────────── */

        /** Arrows: by name contains "arrow". Avoid singleton stacks by default (qty > 1 on stackables). */
        public Builder addArrows() { return addArrows(1); }

        /**
         * Arrows with custom exclusive min stack threshold for stackables.
         * Example: minStackExclusive=1 → allow only stacks with qty >= 2.
         */
        public Builder addArrows(int minStackExclusive) {
            Predicate<GroundItem> arrows = gi -> {
                final String n = safeLower(gi.getName());
                if (!n.contains("arrow")) return false;
                // Only apply stack filter if the item is actually stackable
                return !gi.isStackable() || gi.getQuantity() > minStackExclusive;
            };
            collect("arrows[min>" + minStackExclusive + "]", arrows, null);
            return this;
        }

        /** Bones: by name contains "bones". */
        public Builder addBones() {
            Predicate<GroundItem> bones = gi -> safeLower(gi.getName()).contains("bones");
            collect("bones", bones, null);
            return this;
        }

        /** Ashes: by name contains " ashes" OR equals "ashes" (to catch the exact item). */
        public Builder addAshes() {
            Predicate<GroundItem> ashes = gi -> {
                final String n = safeLower(gi.getName());
                return n.equals("ashes") || n.contains(" ashes");
            };
            collect("ashes", ashes, null);
            return this;
        }

        /** Runes: by name contains " rune" (leading space so it won't hit "rune scimitar"). */
        public Builder addRunes() { return addRunes(1); }

        /**
         * Runes with custom exclusive min stack threshold for stackables.
         * Example: minStackExclusive=1 → avoid stacks of 1 rune.
         */
        public Builder addRunes(int minStackExclusive) {
            Predicate<GroundItem> runes = gi -> {
                final String n = safeLower(gi.getName());
                if (!n.contains(" rune")) return false;
                return !gi.isStackable() || gi.getQuantity() > minStackExclusive;
            };
            collect("runes[min>" + minStackExclusive + "]", runes, null);
            return this;
        }

        /** Add any custom predicate-based intent. */
        public Builder addCustom(String label, Predicate<GroundItem> predicate, Set<String> ignoredLower) {
            collect(label == null ? "custom" : label, predicate, ignoredLower);
            return this;
        }

        /** Final combined looting pass. */
        public boolean loot() {
            final WorldPoint me = Microbot.getClient().getLocalPlayer().getWorldLocation();

            final Map<String, GroundItem> unique = new LinkedHashMap<>();
            for (List<GroundItem> list : candidateBuckets.values()) {
                for (GroundItem gi : list) {
                    unique.putIfAbsent(uniqueKey(gi), gi);
                }
            }

            final List<GroundItem> toLoot = new ArrayList<>(unique.values());
            toLoot.sort(Comparator.comparingInt(gi -> gi.getLocation().distanceTo(me)));

            return runWhilePaused(() -> {
                for (GroundItem gi : toLoot) {
                    if (gi.getQuantity() < params.getMinQuantity()) continue;
                    if (!ensureSpaceFor(gi, params)) continue;
                    lootAction.accept(gi);
                }
                // Validate only items we targeted in this pass
                final Set<String> targetKeys = candidateBuckets.values().stream()
                        .flatMap(List::stream)
                        .map(Rs2LootEngine::uniqueKey)
                        .collect(Collectors.toSet());
                return getGroundItems().values().stream()
                        .noneMatch(gi -> targetKeys.contains(uniqueKey(gi)));
            });
        }

        /** Internal collector that applies base filters, delayed gate, and per-item prechecks. */
        private void collect(String label, Predicate<GroundItem> itemPredicate, Set<String> ignoredLower) {
            final Predicate<GroundItem> base = baseRangeAndOwnershipFilter(params);
            final Predicate<GroundItem> combined = base.and(itemPredicate);

            List<GroundItem> groundItems = getGroundItems().values().stream()
                    .filter(combined)
                    .collect(Collectors.toList());

            if (groundItems.size() < params.getMinItems()) {
                candidateBuckets.put(label, Collections.emptyList());
                return;
            }

            if (params.isDelayedLooting()) {
                final GroundItem soonest = groundItems.stream()
                        .min(Comparator.comparingInt(gi -> calculateDespawnTime(gi)))
                        .orElse(null);
                if (soonest == null || calculateDespawnTime(soonest) > DESPAWN_DELAY_THRESHOLD_TICKS) {
                    candidateBuckets.put(label, Collections.emptyList());
                    return;
                }
            }

            final List<GroundItem> filtered = new ArrayList<>(groundItems.size());
            for (GroundItem gi : groundItems) {
                if (gi.getQuantity() < params.getMinQuantity()) continue;
                if (!passesIgnoredNames(gi, ignoredLower)) continue;
                filtered.add(gi);
            }
            candidateBuckets.put(label, filtered);
        }
    }

    // ------------------ shared helpers (same logic as before) ------------------

    private static Predicate<GroundItem> baseRangeAndOwnershipFilter(LootingParameters params) {
        final WorldPoint me = Microbot.getClient().getLocalPlayer().getWorldLocation();
        final boolean anti = params.isAntiLureProtection();
        return gi ->
                gi.getLocation().distanceTo(me) < params.getRange()
                        && (!anti || gi.getOwnership() == OWNERSHIP_SELF);
    }

    private static boolean passesIgnoredNames(GroundItem gi, Set<String> ignoredLower) {
        if (ignoredLower == null || ignoredLower.isEmpty()) return true;
        final String name = safeLower(gi.getName());
        for (String needle : ignoredLower) {
            if (name.contains(needle)) return false;
        }
        return true;
    }

    private static boolean ensureSpaceFor(GroundItem gi, LootingParameters params) {
        if (Rs2Inventory.emptySlotCount() > params.getMinInvSlots()) {
            return true;
        }
        if (params.isEatFoodForSpace() && !canTakeGroundItem(gi) && !Rs2Inventory.getInventoryFood().isEmpty()) {
            if (Rs2Player.eatAt(100)) {
                Rs2Player.waitForAnimation();
            }
        }
        return canTakeGroundItem(gi);
    }

    private static Set<String> toLowerTrimmedSet(String[] arr) {
        if (arr == null || arr.length == 0) return Collections.emptySet();
        Set<String> out = new HashSet<>(arr.length);
        for (String s : arr) {
            if (s != null) {
                final String t = s.trim().toLowerCase();
                if (!t.isEmpty()) out.add(t);
            }
        }
        return out;
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private static boolean validateLoot(Predicate<GroundItem> filter) {
        return getGroundItems().values().stream().noneMatch(filter);
    }

    private static String uniqueKey(GroundItem gi) {
        final WorldPoint wp = gi.getLocation();
        return gi.getId() + "@" + wp.getX() + "," + wp.getY() + "," + wp.getPlane();
    }
}
