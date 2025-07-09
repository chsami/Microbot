package net.runelite.client.plugins.microbot.virewatch;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import java.util.concurrent.TimeUnit;

public class PLooter extends Script {

    public PLooter() {}
    public boolean run(PVirewatchKillerConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run()) return;
            if (Rs2Inventory.isFull() || Rs2Inventory.getEmptySlots() <= 1|| Rs2Combat.inCombat())
                return;

            if (!config.toggleLootItems()) return;

            lootItemsByValue(config);
            // Normal looter also does this but its better to have it as an extra priority
            lootItemByName(config, "Blood shard");
            if(config.lootRunes()) lootItemByName(config, "rune");
            if(config.lootCoins()) lootItemByName(config, "coins");


        }, 0, 200, TimeUnit.MILLISECONDS);
        return true;
    }

    private void lootItemByName(PVirewatchKillerConfig config, String name) {
        LootingParameters runeParams = new LootingParameters(
                config.radius(),
                1,
                5,
                1,
                config.toggleDelayedLooting(),
                config.toggleOnlyLootMyItems(),
                name
        );
        if (Rs2GroundItem.lootItemsBasedOnNames(runeParams)) {
            Microbot.pauseAllScripts.compareAndSet(true, false);
        }

    }

    private void lootItemsByValue(PVirewatchKillerConfig config) {
        LootingParameters valueParams = new LootingParameters(
                config.minPriceOfItemsToLoot(),
                config.maxPriceOfItemsToLoot(),
                config.radius(),
                1,
                1,
                config.toggleDelayedLooting(),
                config.toggleOnlyLootMyItems()
        );
        if (Rs2GroundItem.lootItemBasedOnValue(valueParams)) {
            Microbot.pauseAllScripts.compareAndSet(true, false);
        }
    }

    public void shutdown() {
        super.shutdown();
    }
}
