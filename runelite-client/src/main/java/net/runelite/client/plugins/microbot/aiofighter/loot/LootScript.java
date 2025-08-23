package net.runelite.client.plugins.microbot.aiofighter.loot;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.grounditems.GroundItem;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterPlugin;
import net.runelite.client.plugins.microbot.aiofighter.enums.DefaultLooterStyle;
import net.runelite.client.plugins.microbot.aiofighter.enums.State;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.runelite.api.TileItem.OWNERSHIP_SELF;
import static net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem.*;

@Slf4j
public class LootScript extends Script {
    int minFreeSlots = 0;

    public boolean run(AIOFighterConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                minFreeSlots = config.bank() ? config.minFreeSlots() : 0;
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (!config.toggleLootItems()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;
                if (AIOFighterPlugin.getState().equals(State.BANKING) || AIOFighterPlugin.getState().equals(State.WALKING)) {
                    return;
                }
                if (Rs2Player.isInCombat() && !config.toggleForceLoot()) {
                    return;
                }


                String[] itemNamesToLoot = lootItemNames(config);
                final Predicate<GroundItem> filter = groundItem ->
                        groundItem.getLocation().distanceTo(Microbot.getClient().getLocalPlayer().getWorldLocation()) < config.attackRadius() &&
                                (!config.toggleOnlyLootMyItems() || groundItem.getOwnership() == OWNERSHIP_SELF) &&
                                (shouldLootBasedOnName(groundItem, itemNamesToLoot) || shouldLootBasedOnValue(groundItem, config));
                List<GroundItem> groundItems = getGroundItems().values().stream()
                        .filter(filter)
                        .collect(Collectors.toList());

                if (groundItems.isEmpty()) {
                    return;
                }
                if (config.toggleDelayedLooting()) {
                    groundItems.sort(Comparator.comparingInt(Rs2GroundItem::calculateDespawnTime));
                }
                //Pause other scripts before looting
                Microbot.pauseAllScripts.getAndSet(true);
                for (GroundItem groundItem : groundItems) {
                    if (Rs2Inventory.emptySlotCount() <= minFreeSlots && !canStackItem(groundItem)) {
                        Microbot.log("Unable to pick loot: " + groundItem.getName() + " making space");
                        if (!config.eatFoodForSpace()) {
                            continue;
                        }
                        int emptySlots = Rs2Inventory.emptySlotCount();
                        if (Rs2Player.eatAt(100)) {
                            sleepUntil(() -> emptySlots < Rs2Inventory.emptySlotCount(), 1200);
                        }
                    }
                    Microbot.log("Picking up loot: " + groundItem.getName());
                    if (!waitForGroundItemDespawn(() -> interact(groundItem), groundItem)) {
                        return;
                    }
                }
                Microbot.log("Looting complete");
                Microbot.pauseAllScripts.compareAndSet(true, false);
            } catch (Exception ex) {
                Microbot.log("Looterscript: " + ex.getMessage());
            }

        }, 0, 200, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean canStackItem(GroundItem groundItem) {
        if (!groundItem.isStackable()) {
            return false;
        }
        int runePouchRunes = Rs2RunePouch.getQuantity(groundItem.getItemId());
        if (runePouchRunes > 0 && runePouchRunes <= 16000 - groundItem.getQuantity()) {
            return true;
        }
        //TODO("Coal bag, Herb Sack, Seed pack")
        return Rs2Inventory.contains(groundItem.getItemId());
    }

    private boolean shouldLootBasedOnValue(GroundItem groundItem, AIOFighterConfig config) {
        if (config.looterStyle() != DefaultLooterStyle.GE_PRICE_RANGE && config.looterStyle() != DefaultLooterStyle.MIXED)
            return false;
        int price = groundItem.getGePrice();
        return config.minPriceOfItemsToLoot() <= price && price / groundItem.getQuantity() <= config.maxPriceOfItemsToLoot();
    }

    private boolean shouldLootBasedOnName(GroundItem groundItem, String[] itemNamesToLoot) {
        return Arrays.stream(itemNamesToLoot).anyMatch(name -> groundItem.getName().trim().toLowerCase().contains(name.trim().toLowerCase()));
    }

    private String[] lootItemNames(AIOFighterConfig config) {
        ArrayList<String> itemNamesToLoot = new ArrayList<>();
        if (config.toggleLootArrows()) {
            itemNamesToLoot.add("arrow");
        }
        if (config.toggleBuryBones()) {
            itemNamesToLoot.add("bones");
        }
        if (config.toggleScatter()) {
            itemNamesToLoot.add(" ashes");
        }
        if (config.toggleLootRunes()) {
            itemNamesToLoot.add(" rune");
        }
        if (config.toggleLootCoins()) {
            itemNamesToLoot.add("coins");
        }
        if (config.toggleLootUntradables()) {
            itemNamesToLoot.add("untradeable");
            itemNamesToLoot.add("scroll box");
        }
        if (config.looterStyle().equals(DefaultLooterStyle.MIXED) || config.looterStyle().equals(DefaultLooterStyle.ITEM_LIST)) {
            itemNamesToLoot.addAll(Arrays.asList(config.listOfItemsToLoot().trim().split(",")));
        }
        return itemNamesToLoot.toArray(new String[0]);
    }

    public void shutdown() {
        super.shutdown();
    }
}
