package net.runelite.client.plugins.microbot.aiofighter.loot;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.grounditems.GroundItem;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterPlugin;
import net.runelite.client.plugins.microbot.aiofighter.enums.DefaultLooterStyle;
import net.runelite.client.plugins.microbot.aiofighter.enums.State;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2LootEngine;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

@Slf4j
public class LootScript extends Script {

    private static final int DEFAULT_MIN_STACK_EXCLUSIVE_ARROWS = 9; // allow 2+
    private static final int DEFAULT_MIN_STACK_EXCLUSIVE_RUNES  = 1; // allow 2+

    private int minFreeSlots = 0;
    private int logCounter = 0;

    public LootScript() {}

    public boolean run(AIOFighterConfig config) {
        // DEBUG: Log all loot config values on startup
        Microbot.log("[LOOT STARTUP DEBUG] Loot configuration:");
        Microbot.log("  - toggleLootItems: " + config.toggleLootItems());
        Microbot.log("  - looterStyle: " + config.looterStyle());
        Microbot.log("  - delayLootUntilKills: " + config.delayLootUntilKills() + " (attacks required)");
        Microbot.log("  - minPriceOfItemsToLoot: " + config.minPriceOfItemsToLoot());
        Microbot.log("  - maxPriceOfItemsToLoot: " + config.maxPriceOfItemsToLoot());
        Microbot.log("  - toggleForceLoot: " + config.toggleForceLoot());
        Microbot.log("  - eatFoodForSpace: " + config.eatFoodForSpace());
        
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                minFreeSlots = config.bank() ? config.minFreeSlots() : 0;

                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (!config.toggleLootItems()) {
                    // DEBUG: Log when looting is disabled in config
                    if (logCounter++ % 100 == 0) { // Log every 100 checks to avoid spam
                        Microbot.log("[LOOT CONFIG DEBUG] Looting disabled in config (toggleLootItems = false)");
                    }
                    return;
                }

                final State st = AIOFighterPlugin.getState();
                if (st == State.BANKING || st == State.WALKING) {
                    // DEBUG: Log state blocking
                    if (logCounter % 50 == 0) { // Log every 50 checks to avoid spam
                        Microbot.log("[LOOT STATE DEBUG] Looting blocked by state: " + st);
                    }
                    return;
                }

                // Check if enough monsters have been attacked before allowing loot
                int currentAttackCount = AIOFighterPlugin.getKillCount();
                int requiredAttacks = config.delayLootUntilKills();
                
                // DEBUG: Always log attack count and required attacks
                if (requiredAttacks > 0) {
                    Microbot.log("[LOOT DELAY DEBUG] Attack Count: " + currentAttackCount + "/" + requiredAttacks + " (Required: " + requiredAttacks + ")");
                }
                
                if (currentAttackCount < requiredAttacks) {
                    // Not enough attacks yet, don't loot
                    Microbot.log("[LOOT DEBUG] 🚫 Looting delayed until " + requiredAttacks + " monsters attacked (current: " + currentAttackCount + ")");
                    return;
                } else if (requiredAttacks > 0) {
                    // We've reached the threshold, log once
                    Microbot.log("[LOOT DEBUG] ✅ Attack threshold reached! Looting enabled (attacks: " + currentAttackCount + "/" + requiredAttacks + ")");
                }

                if (((Rs2Inventory.isFull() || Rs2Inventory.emptySlotCount() <= minFreeSlots) && !config.eatFoodForSpace())
                        || (Rs2Player.isInCombat() && !config.toggleForceLoot())) {
                    // DEBUG: Log inventory/combat blocking  
                    if (logCounter % 25 == 0) { // Log every 25 checks to avoid spam
                        boolean inventoryFull = Rs2Inventory.isFull();
                        int emptySlots = Rs2Inventory.emptySlotCount();
                        boolean inCombat = Rs2Player.isInCombat();
                        boolean canEat = config.eatFoodForSpace();
                        boolean forceLoot = config.toggleForceLoot();
                        Microbot.log("[LOOT INVENTORY DEBUG] InventoryFull: " + inventoryFull + 
                                   " | EmptySlots: " + emptySlots + "/" + minFreeSlots + 
                                   " | InCombat: " + inCombat + 
                                   " | CanEat: " + canEat + 
                                   " | ForceLoot: " + forceLoot);
                    }
                    return;
                }
                
                // DEBUG: We made it to the actual looting part!
                if (logCounter % 10 == 0) { // Log every 10 checks
                    Microbot.log("[LOOT DEBUG] 🎯 Attempting to loot! Attack count: " + AIOFighterPlugin.getKillCount());
                }
                
                // Reset attack count when we start looting (user requested feature)
                int currentAttacks = AIOFighterPlugin.getKillCount();
                if (currentAttacks > 0) {
                    AIOFighterPlugin.resetKillCount("starting to loot after " + currentAttacks + " attacks");
                }

                LootingParameters params = new LootingParameters(
                        config.minPriceOfItemsToLoot(),
                        config.maxPriceOfItemsToLoot(),
                        config.attackRadius(),
                        /* minQuantity */ 1,
                        /* minInvSlots */ minFreeSlots,
                        config.toggleDelayedLooting(),
                        config.toggleOnlyLootMyItems()
                );
                params.setEatFoodForSpace(config.eatFoodForSpace());

                Rs2LootEngine.Builder builder = Rs2LootEngine.with(params)
                        .withLootAction(Rs2GroundItem::coreLoot);

                // custom filter
                if (config.looterStyle() == DefaultLooterStyle.ITEM_LIST || config.looterStyle() == DefaultLooterStyle.MIXED) {
                    addCustomNames(builder, config.listOfItemsToLoot());
                }

                if (config.looterStyle() == DefaultLooterStyle.GE_PRICE_RANGE || config.looterStyle() == DefaultLooterStyle.MIXED) builder.addByValue();
                if (config.toggleBuryBones())       builder.addBones();
                if (config.toggleScatter())         builder.addAshes();
                if (config.toggleLootCoins())       builder.addCoins();
                if (config.toggleLootUntradables()) builder.addUntradables();
                if (config.toggleLootArrows())      builder.addArrows(DEFAULT_MIN_STACK_EXCLUSIVE_ARROWS);
                if (config.toggleLootRunes())       builder.addRunes(DEFAULT_MIN_STACK_EXCLUSIVE_RUNES);

                // Execute one combined, distance-sorted looting pass
                builder.loot();

            } catch (Exception ex) {
                Microbot.log("LootScript: " + ex.getMessage());
            }
        }, 0, 200, TimeUnit.MILLISECONDS);

        return true;
    }

    /**
     * Adds a custom "by names" intent sourced from the config's comma-separated list.
     * (We use a custom predicate so we don't depend on params.getNames()).
     */
    private void addCustomNames(Rs2LootEngine.Builder builder, String csvNames) {
        if (csvNames == null) return;
        final Set<String> needles = new HashSet<>();
        Arrays.stream(csvNames.split(","))
                .map(s -> s == null ? "" : s.trim().toLowerCase())
                .filter(s -> !s.isEmpty())
                .forEach(needles::add);

        if (needles.isEmpty()) return;

        Predicate<GroundItem> byNames = gi -> {
            final String n = gi.getName() == null ? "" : gi.getName().trim().toLowerCase();
            for (String needle : needles) {
                if (n.contains(needle)) return true;
            }
            return false;
        };

        builder.addCustom("names", byNames, /*ignoredLower*/ null);
    }


    @Override
    public void shutdown() {
        super.shutdown();
    }
}
