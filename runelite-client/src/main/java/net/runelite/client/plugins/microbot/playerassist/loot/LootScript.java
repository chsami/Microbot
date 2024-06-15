package net.runelite.client.plugins.microbot.playerassist.loot;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.playerassist.PlayerAssistConfig;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;

import java.util.concurrent.TimeUnit;

@Slf4j
public class LootScript extends Script {


    public LootScript() {

    }


    public boolean run(PlayerAssistConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run()) return;

            lootArrows(config);

            if (!config.toggleLootItems()) return;

            lootBones(config);
            lootAshes(config);
            lootItemsByValue(config);

        }, 0, 200, TimeUnit.MILLISECONDS);
        return true;
    }

    private void lootArrows(PlayerAssistConfig config) {
        if (config.toggleLootArrows()) {
            LootingParameters arrowParams = new LootingParameters(
                    config.attackRadius(),
                    1,
                    14,
                    config.toggleDelayedLooting(),
                    config.toggleOnlyLootMyItems(),
                    "arrow"
            );
            if (Rs2GroundItem.lootItemsBasedOnNames(arrowParams)) {
                Microbot.pauseAllScripts = false;
            }
        }
    }

    private void lootBones(PlayerAssistConfig config) {
        if (config.toggleBuryBones()) {
            LootingParameters bonesParams = new LootingParameters(
                    config.attackRadius(),
                    1,
                    1,
                    config.toggleDelayedLooting(),
                    config.toggleOnlyLootMyItems(),
                    "bones"
            );
            if (Rs2GroundItem.lootItemsBasedOnNames(bonesParams)) {
                Microbot.pauseAllScripts = false;
            }
        }
    }

    private void lootAshes(PlayerAssistConfig config) {
        if (config.toggleScatter()) {
            LootingParameters ashesParams = new LootingParameters(
                    config.attackRadius(),
                    1,
                    1,
                    config.toggleDelayedLooting(),
                    config.toggleOnlyLootMyItems(),
                    "ashes"
            );
            if (Rs2GroundItem.lootItemsBasedOnNames(ashesParams)) {
                Microbot.pauseAllScripts = false;
            }
        }
    }

    private void lootItemsByValue(PlayerAssistConfig config) {
        LootingParameters valueParams = new LootingParameters(
                config.minPriceOfItemsToLoot(),
                config.maxPriceOfItemsToLoot(),
                config.attackRadius(),
                1,
                config.toggleDelayedLooting(),
                config.toggleOnlyLootMyItems()
        );
        if (Rs2GroundItem.lootItemBasedOnValue(valueParams)) {
            Microbot.pauseAllScripts = false;
        }
    }

    public void shutdown() {
        super.shutdown();
    }
}
