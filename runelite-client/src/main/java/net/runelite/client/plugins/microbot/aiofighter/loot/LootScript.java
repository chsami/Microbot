package net.runelite.client.plugins.microbot.aiofighter.loot;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterPlugin;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.aiofighter.enums.DefaultLooterStyle;
import net.runelite.client.plugins.microbot.aiofighter.enums.State;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import java.util.concurrent.TimeUnit;

@Slf4j
public class LootScript extends Script {


    public LootScript() {

    }


    public boolean run(AIOFighterConfig config) {

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (AIOFighterPlugin.getState() == State.BANKING.name() || AIOFighterPlugin.getState() == State.WALKING.name()) return;
                if (Rs2Inventory.isFull() || Rs2Inventory.getEmptySlots() <= config.minFreeSlots() || (Rs2Combat.inCombat() && !config.toggleForceLoot()))
                    return;

                if (config.state().equals(State.BANKING)) return;



                if (!config.toggleLootItems()) return;
                if (config.looterStyle().equals(DefaultLooterStyle.MIXED) || config.looterStyle().equals(DefaultLooterStyle.ITEM_LIST)) {
                    lootItemsOnName(config);
                }

                if (config.looterStyle().equals(DefaultLooterStyle.GE_PRICE_RANGE) || config.looterStyle().equals(DefaultLooterStyle.MIXED)) {
                    lootItemsByValue(config);
                }
                lootBones(config);
                lootAshes(config);
                lootRunes(config);
                lootCoins(config);
                lootUntradeableItems(config);
                lootArrows(config);

            } catch(Exception ex) {
                Microbot.log("Looterscript: " + ex.getMessage());
                ex.printStackTrace();
            }

        }, 0, 200, TimeUnit.MILLISECONDS);
        return true;
    }

    private void lootArrows(AIOFighterConfig config) {
        if (config.toggleLootArrows()) {
            LootingParameters arrowParams = new LootingParameters(
                    config.attackRadius(),
                    1,
                    10,
                    config.minFreeSlots(),
                    config.toggleDelayedLooting(),
                    config.toggleOnlyLootMyItems(),
                    "arrow"
            );
            if (Rs2GroundItem.lootItemsBasedOnNames(arrowParams)) {
                Microbot.pauseAllScripts = false;
            }
        }
    }

    private void lootBones(AIOFighterConfig config) {
        if (config.toggleBuryBones()) {
            LootingParameters bonesParams = new LootingParameters(
                    config.attackRadius(),
                    1,
                    1,
                    config.minFreeSlots(),
                    config.toggleDelayedLooting(),
                    config.toggleOnlyLootMyItems(),
                    "bones"
            );
            if (Rs2GroundItem.lootItemsBasedOnNames(bonesParams)) {
                Microbot.pauseAllScripts = false;
            }
        }
    }

    private void lootAshes(AIOFighterConfig config) {
        if (config.toggleScatter()) {
            LootingParameters ashesParams = new LootingParameters(
                    config.attackRadius(),
                    1,
                    1,
                    config.minFreeSlots(),
                    config.toggleDelayedLooting(),
                    config.toggleOnlyLootMyItems(),
                    " ashes"
            );
            if (Rs2GroundItem.lootItemsBasedOnNames(ashesParams)) {
                Microbot.pauseAllScripts = false;
            }
        }
    }

    // loot runes
    private void lootRunes(AIOFighterConfig config) {
        if (config.toggleLootRunes()) {
            LootingParameters runesParams = new LootingParameters(
                    config.attackRadius(),
                    1,
                    1,
                    config.minFreeSlots(),
                    config.toggleDelayedLooting(),
                    config.toggleOnlyLootMyItems(),
                    " rune"
            );
            if (Rs2GroundItem.lootItemsBasedOnNames(runesParams)) {
                Microbot.pauseAllScripts = false;
            }
        }
    }

    // loot coins
    private void lootCoins(AIOFighterConfig config) {
        if (config.toggleLootCoins()) {
            LootingParameters coinsParams = new LootingParameters(
                    config.attackRadius(),
                    1,
                    1,
                    config.minFreeSlots(),
                    config.toggleDelayedLooting(),
                    config.toggleOnlyLootMyItems(),
                    "coins"
            );
            if (Rs2GroundItem.lootCoins(coinsParams)) {
                Microbot.pauseAllScripts = false;
            }
        }
    }

    // loot untreadable items
    private void lootUntradeableItems(AIOFighterConfig config) {
        if (config.toggleLootUntradables()) {
            LootingParameters untradeableItemsParams = new LootingParameters(
                    config.attackRadius(),
                    1,
                    1,
                    config.minFreeSlots(),
                    config.toggleDelayedLooting(),
                    config.toggleOnlyLootMyItems(),
                    "untradeable"
            );
            if (Rs2GroundItem.lootUntradables(untradeableItemsParams)) {
                Microbot.pauseAllScripts = false;
            }
        }
    }

    private void lootItemsByValue(AIOFighterConfig config) {
        LootingParameters valueParams = new LootingParameters(
                config.minPriceOfItemsToLoot(),
                config.maxPriceOfItemsToLoot(),
                config.attackRadius(),
                1,
                config.minFreeSlots(),
                config.toggleDelayedLooting(),
                config.toggleOnlyLootMyItems()
        );
        if (Rs2GroundItem.lootItemBasedOnValue(valueParams)) {
            Microbot.pauseAllScripts = false;
        }
    }

    private void lootItemsOnName(AIOFighterConfig config) {
        LootingParameters valueParams = new LootingParameters(
                config.attackRadius(),
                1,
                1,
                config.minFreeSlots(),
                config.toggleDelayedLooting(),
                config.toggleOnlyLootMyItems(),
                config.listOfItemsToLoot().trim().split(",")
        );
        if (Rs2GroundItem.lootItemsBasedOnNames(valueParams)) {
            Microbot.pauseAllScripts = false;
        }
    }

    public void shutdown() {
        super.shutdown();
    }
}
