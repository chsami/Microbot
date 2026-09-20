package net.runelite.client.plugins.microbot.util.grounditem;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.runelite.api.ItemID;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.grounditems.GroundItem;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/** A reusable selection plan. Candidates are freshly captured when loot() executes. */
public final class Rs2LootEngine {
    private Rs2LootEngine() { }
    public static Builder with(LootingParameters params) { return new Builder(params); }

    public static final class Builder {
        private final LootingParameters params;
        private final Map<String, Predicate<GroundItemPickup.Snapshot>> intents = new LinkedHashMap<>();
        private Consumer<GroundItem> customAction;
        private GroundItemPickup.Result lastResult = GroundItemPickup.Result.REJECTED;
        private Builder(LootingParameters params) { this.params = Objects.requireNonNull(params); }

        /** Optional custom action. Completion is still checked against the target's ground quantity. */
        public Builder withLootAction(Consumer<GroundItem> action) { customAction = Objects.requireNonNull(action); return this; }
        public GroundItemPickup.Result getLastResult() { return lastResult; }

        /** Inclusive stack-value bounds. A maximum of zero means no upper bound. */
        public Builder addByValue() {
            intents.put("value", s -> s.totalValue >= params.getMinValue()
                    && (params.getMaxValue() == 0 || s.totalValue <= params.getMaxValue()));
            return this;
        }
        public Builder addByNames() {
            Set<String> names = normalized(params.getNames());
            return addCustom("names", item -> names.stream().anyMatch(n -> lower(item.getName()).contains(n)), null);
        }
        public Builder addUntradables() { return addCustom("untradables", item -> !item.isTradeable() && item.getId() != ItemID.COINS_995, null); }
        public Builder addCoins() { return addCustom("coins", item -> item.getId() == ItemID.COINS_995, null); }
        public Builder addArrows() { return addArrows(1); }
        public Builder addArrows(int minimum) { return addCustom("arrows", item -> lower(item.getName()).contains("arrow") && (!item.isStackable() || item.getQuantity() > minimum), null); }
        public Builder addBones() { return addCustom("bones", item -> lower(item.getName()).contains("bones"), null); }
        public Builder addAshes() { return addCustom("ashes", item -> lower(item.getName()).equals("ashes") || lower(item.getName()).contains(" ashes"), null); }
        public Builder addRunes() { return addRunes(1); }
        public Builder addRunes(int minimum) { return addCustom("runes", item -> lower(item.getName()).contains(" rune") && (!item.isStackable() || item.getQuantity() > minimum), null); }
        public Builder addCustom(String label, Predicate<GroundItem> predicate, Set<String> ignoredNames) {
            Objects.requireNonNull(predicate);
            Set<String> ignored = normalized(ignoredNames == null ? null : ignoredNames.toArray(new String[0]));
            intents.put(label == null ? "custom" : label, s -> predicate.test(s.item) && !ignored(s.item, ignored));
            return this;
        }

        /** True when every eligible target progressed; use lastResult to distinguish collection from disappearance. */
        public boolean loot() {
            lastResult = GroundItemPickup.Result.REJECTED;
            if (GroundItemPickup.cancelled() || Microbot.getClient().isClientThread()) { lastResult = GroundItemPickup.Result.CANCELLED; return false; }
            if (params.getRange() < 0 || params.getMinQuantity() < 1 || params.getMinItems() < 0
                    || params.getMinInvSlots() < 0 || params.getMinInvSlots() > 28
                    || params.getMinValue() < 0 || params.getMaxValue() < 0
                    || params.getMaxValue() > 0 && params.getMaxValue() < params.getMinValue()) throw new IllegalArgumentException("Invalid looting parameters");
            return Rs2GroundItem.runWhilePaused(() -> {
                List<GroundItemPickup.Snapshot> candidates = eligible();
                if (candidates.isEmpty() || candidates.size() < params.getMinItems()) return false;
                if (params.isDelayedLooting() && candidates.stream().allMatch(s -> s.despawnTicks > 150)) return false;
                boolean complete = true;
                for (GroundItemPickup.Snapshot candidate : candidates) {
                    if (GroundItemPickup.cancelled()) { lastResult = GroundItemPickup.Result.CANCELLED; return false; }
                    // Refresh eligibility as well as identity after earlier pickups, movement, or a hop.
                    GroundItemPickup.Snapshot current = eligible().stream().filter(s -> s.view == candidate.view
                            && s.model.getTileItem() == candidate.model.getTileItem()).findFirst().orElse(null);
                    if (current == null) { lastResult = GroundItemPickup.Result.GROUND_CHANGED; continue; }
                    if (!ensureSpace(current)) { lastResult = GroundItemPickup.Result.NO_SPACE; complete = false; continue; }
                    if (customAction == null) lastResult = GroundItemPickup.take(current, params.getMinInvSlots());
                    else {
                        int before = Rs2Inventory.itemQuantity(current.item.getId());
                        customAction.accept(current.item);
                        boolean changed = Global.sleepUntil(() -> GroundItemPickup.cancelled()
                                || GroundItemPickup.currentQuantity(current.model) < current.quantity
                                || Rs2Inventory.itemQuantity(current.item.getId()) > before, 5000);
                        lastResult = GroundItemPickup.cancelled() ? GroundItemPickup.Result.CANCELLED
                                : Rs2Inventory.itemQuantity(current.item.getId()) > before ? GroundItemPickup.Result.COLLECTED
                                : changed ? GroundItemPickup.Result.GROUND_CHANGED : GroundItemPickup.Result.TIMED_OUT;
                    }
                    if (!lastResult.madeProgress()) complete = false;
                }
                return complete;
            });
        }

        private List<GroundItemPickup.Snapshot> eligible() {
            WorldPoint me = Microbot.getClientThread().runOnClientThreadOptional(() ->
                    Microbot.getClient().getLocalPlayer() == null ? null : Microbot.getClient().getLocalPlayer().getWorldLocation()).orElse(null);
            if (me == null) return Collections.emptyList();
            Set<String> ignored = normalized(params.getIgnoredNames());
            List<GroundItemPickup.Snapshot> result = new ArrayList<>();
            for (GroundItemPickup.Snapshot s : GroundItemPickup.snapshot()) {
                if (s.item.getLocation().getPlane() != me.getPlane() || s.item.getLocation().distanceTo(me) > params.getRange()
                        || s.quantity < params.getMinQuantity() || ignored(s.item, ignored)
                        || params.isAntiLureProtection() && s.item.getOwnership() != TileItem.OWNERSHIP_SELF) continue;
                if (intents.values().stream().anyMatch(p -> p.test(s))) result.add(s);
            }
            result.sort(Comparator.comparingInt(s -> s.item.getLocation().distanceTo(me)));
            return result;
        }

        private boolean ensureSpace(GroundItemPickup.Snapshot item) {
            if (hasSpace(item)) return true;
            if (!params.isEatFoodForSpace() || Rs2Inventory.getInventoryFood().isEmpty()) return false;
            int before = Rs2Inventory.emptySlotCount();
            if (!Rs2Player.eatAt(100)) return false;
            Global.sleepUntil(() -> Rs2Inventory.emptySlotCount() > before || GroundItemPickup.cancelled(), 1800);
            return !GroundItemPickup.cancelled() && hasSpace(item);
        }
        private boolean hasSpace(GroundItemPickup.Snapshot item) {
            int needed = item.item.isStackable() && Rs2Inventory.hasItem(item.item.getId()) ? 0 : 1;
            return Rs2Inventory.emptySlotCount() - needed >= params.getMinInvSlots();
        }
    }

    private static String lower(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private static Set<String> normalized(String[] values) {
        Set<String> result = new HashSet<>();
        if (values != null) for (String value : values) { String normalized = lower(value); if (!normalized.isEmpty()) result.add(normalized); }
        return result;
    }
    private static boolean ignored(GroundItem item, Set<String> ignored) { return ignored.stream().anyMatch(n -> lower(item.getName()).contains(n)); }
}
