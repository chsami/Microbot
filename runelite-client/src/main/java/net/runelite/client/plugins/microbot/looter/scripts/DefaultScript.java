package net.runelite.client.plugins.microbot.looter.scripts;

import net.runelite.api.GameState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.looter.AutoLooterConfig;
import net.runelite.client.plugins.microbot.looter.enums.DefaultLooterStyle;
import net.runelite.client.plugins.microbot.looter.enums.LooterState;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity.EXTREME;

public class DefaultScript extends Script {

    LooterState state = LooterState.LOOTING;
    boolean lootExists;

    public boolean run(AutoLooterConfig config) {
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.usePlayStyle = false;
        Rs2AntibanSettings.simulateFatigue = false;
        Rs2AntibanSettings.simulateAttentionSpan = false;
        Rs2AntibanSettings.behavioralVariability = false;
        Rs2AntibanSettings.nonLinearIntervals = false;
        Rs2AntibanSettings.dynamicActivity = false;
        Rs2AntibanSettings.profileSwitching = false;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.simulateMistakes = false;
        Rs2AntibanSettings.moveMouseOffScreen = false;
        Rs2AntibanSettings.moveMouseRandomly = false;
        Rs2AntibanSettings.moveMouseRandomlyChance = 0.04;
        Rs2Antiban.setActivityIntensity(EXTREME);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (Microbot.pauseAllScripts) return;
                if (Rs2Combat.inCombat() && !config.toggleForceLoot()) return;

                // Check if inventory is full
                if (Rs2Inventory.isFull() || Rs2Inventory.getEmptySlots() <= config.minFreeSlots()) {
                    if (!config.bankingEnabled()) {
                        shutdown();
                        return;
                    }
                    state = LooterState.BANKING;
                }

                switch (state) {
                    case LOOTING:
                        if (config.worldHop()) {
                            checkLootExists(config);
                        } else {
                            lootExists = true;
                        }
                        
                        if (lootExists) {
                            performLooting(config);
                            Microbot.pauseAllScripts = false;
                        }
                        break;
                    case BANKING:
                        if (config.bankingEnabled()) {
                            handleBanking(config);
                        }
                        break;
                }

            } catch(Exception ex) {
                System.out.println("DefaultScript error: " + ex.getMessage());
            }
        }, 0, 200, TimeUnit.MILLISECONDS);
        return true;
    }

    private void checkLootExists(AutoLooterConfig config) {
        if (config.looterStyle() == DefaultLooterStyle.ITEM_LIST || config.looterStyle() == DefaultLooterStyle.MIXED) {
            lootExists = Arrays.stream(config.listOfItemsToLoot().trim().split(","))
                    .anyMatch(itemName -> Rs2GroundItem.exists(itemName, config.distanceToStray()));
        }
        if (config.looterStyle() == DefaultLooterStyle.GE_PRICE_RANGE || config.looterStyle() == DefaultLooterStyle.MIXED) {
            lootExists = lootExists || Rs2GroundItem.isItemBasedOnValueOnGround(config.minPriceOfItem(), config.distanceToStray());
        }
    }

    private void performLooting(AutoLooterConfig config) {
        if (config.looterStyle() == DefaultLooterStyle.ITEM_LIST || config.looterStyle() == DefaultLooterStyle.MIXED) {
            LootingParameters itemLootParams = new LootingParameters(
                    config.distanceToStray(),
                    1,
                    1,
                    config.minFreeSlots(),
                    config.toggleDelayedLooting(),
                    config.toggleLootMyItemsOnly(),
                    config.listOfItemsToLoot().split(",")
            );
            Rs2GroundItem.lootItemsBasedOnNames(itemLootParams);
        }
        if (config.looterStyle() == DefaultLooterStyle.GE_PRICE_RANGE || config.looterStyle() == DefaultLooterStyle.MIXED) {
            LootingParameters valueParams = new LootingParameters(
                    config.minPriceOfItem(),
                    config.maxPriceOfItem(),
                    config.distanceToStray(),
                    1,
                    config.minFreeSlots(),
                    config.toggleDelayedLooting(),
                    config.toggleLootMyItemsOnly()
            );
            Rs2GroundItem.lootItemBasedOnValue(valueParams);
        }
    }

    private void handleBanking(AutoLooterConfig config) {
        if (config.bankingEnabled()) {
            //TODO: Implement banking logic
            state = LooterState.LOOTING;
        }
    }
}
