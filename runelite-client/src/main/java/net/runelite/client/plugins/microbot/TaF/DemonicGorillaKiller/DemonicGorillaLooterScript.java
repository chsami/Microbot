package net.runelite.client.plugins.microbot.TaF.DemonicGorillaKiller;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import java.util.concurrent.TimeUnit;

public class DemonicGorillaLooterScript extends Script {
    int minFreeSlots = 0;
    public String itemsToLoot = "Zenyte shard,Ballista spring,Ballista limbs,Ballista frame,Monkey tail,Heavy frame,Light frame,Rune platelegs,Rune plateskirt,Rune chainbody,Dragon scimitar,Law rune,Death rune,Runite bolts,Grimy kwuarm,Grimy cadantine,Grimy dwarf weed,Grimy lantadyme,Ranarr seed,Snapdragon seed,Torstol seed,Yew seed,Magic seed,Palm tree seed,Spirit seed,Dragonfruit tree seed,Celastrus seed,Redwood tree seed,Prayer potion(3),Shark,Coins,Saradomin brew(2),Rune javelin heads,Dragon javelin heads,Adamantite bar,Diamond,Runite bar";

    public DemonicGorillaLooterScript() {

    }

    public boolean run(DemonicGorillaConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (DemonicGorillaScript.BOT_STATUS.equals(DemonicGorillaScript.State.BANKING) || DemonicGorillaScript.BOT_STATUS.equals(DemonicGorillaScript.State.TRAVEL_TO_GORILLAS)) {
                    Microbot.pauseAllScripts.compareAndSet(true,  false);
                    return;
                }
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (Rs2Inventory.isFull() || Rs2Inventory.getEmptySlots() <= minFreeSlots) return;
                lootItemsOnName(config);
                if (config.scatterAshes()) {
                    lootAndScatterMalicious();
                }
                lootRunes(config);
                lootCoins(config);
                lootUntradeableItems(config);

            } catch(Exception ex) {
                System.out.println("Demonic Gorilla Looter: " + ex.getMessage());
            }

        }, 0, 200, TimeUnit.MILLISECONDS);
        return true;
    }

    private void lootUntradeableItems(DemonicGorillaConfig config) {
        LootingParameters untradeableItemsParams = new LootingParameters(
                15,
                1,
                1,
                minFreeSlots,
                false,
                config.lootMyLootOnly(),
                "untradeable"
        );
        if (Rs2GroundItem.lootUntradables(untradeableItemsParams)) {
            Microbot.pauseAllScripts.compareAndSet(true, false);
        }
    }

    private void lootRunes(DemonicGorillaConfig config) {
        LootingParameters runesParams = new LootingParameters(
                15,
                1,
                1,
                minFreeSlots,
                false,
                config.lootMyLootOnly(),
                " rune"
        );
        if (Rs2GroundItem.lootItemsBasedOnNames(runesParams)) {
            Microbot.pauseAllScripts.compareAndSet(true, false);
        }
    }
    private void lootCoins(DemonicGorillaConfig config) {
        LootingParameters coinsParams = new LootingParameters(
                15,
                1,
                1,
                minFreeSlots,
                false,
                config.lootMyLootOnly(),
                "coins"
        );
        if (Rs2GroundItem.lootCoins(coinsParams)) {
            Microbot.pauseAllScripts.compareAndSet(true, false);
        }
    }
    private void lootItemsOnName(DemonicGorillaConfig config) {
        LootingParameters valueParams = new LootingParameters(
                15,
                1,
                1,
                minFreeSlots,
                false,
                config.lootMyLootOnly(),
                itemsToLoot.trim().split(",")
        );
        if (Rs2GroundItem.lootItemsBasedOnNames(valueParams)) {
            Microbot.pauseAllScripts.compareAndSet(true, false);
        }
    }

    private void lootAndScatterMalicious() {
        String ashesName = "Malicious ashes";

        if (!Rs2Inventory.isFull() && Rs2GroundItem.lootItemsBasedOnNames(new LootingParameters(10, 1, 1, 0, false, true, ashesName))) {
            sleepUntil(() -> Rs2Inventory.contains(ashesName), 2000);

            if (Rs2Inventory.contains(ashesName)) {
                Rs2Inventory.interact(ashesName, "Scatter");
                sleep(600); // Wait briefly for scattering action
            }
        }
    }
}
