package net.runelite.client.plugins.microbot.aiofighter.combat;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class BuryScatterScript extends Script {
    public boolean run(AIOFighterConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) {
                    return;
                }

                if (config.toggleBuryBones()) {
                    List<Rs2ItemModel> bones = Rs2Inventory.getBones();
                    processItems(bones, Rs2Spells.SINISTER_OFFERING, "bury");
                }
                if (config.toggleScatter()) {
                    List<Rs2ItemModel> ashes = Rs2Inventory.getAshes();
                    processItems(ashes, Rs2Spells.DEMONIC_OFFERING, "scatter");
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }


    private void processItems(List<Rs2ItemModel> items, Rs2Spells spell, String action) {
        if (items == null || items.isEmpty()) {
            return;
        }
        if (Rs2Magic.canCast(spell)) {
            if (items.size() >= 3) {
                if (Rs2Magic.cast(spell)) {
                    sleepUntil(() -> Rs2Inventory.getList(item -> item.getName().equals(items.get(0).getName())).isEmpty());
                }
            }
        } else {
            Rs2Inventory.interact(items.get(0), action);
            Rs2Player.waitForAnimation();
        }
    }

    public void shutdown() {
        super.shutdown();
    }
}
