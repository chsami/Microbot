package net.runelite.client.plugins.microbot.aiofighter.combat;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.List;
import java.util.concurrent.TimeUnit;
public class BuryScatterScript extends Script {
public boolean run(AIOFighterConfig config) {
    mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
        try {
            if (!Microbot.isLoggedIn() || !super.run() || (!config.toggleBuryBones() && !config.toggleScatter())) return;

            processItems(config.toggleBuryBones(), Rs2Inventory.getBones(), "bury");
            processItems(config.toggleScatter(), Rs2Inventory.getAshes(), "scatter");

        } catch(Exception ex) {
            Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
    }, 0, 600, TimeUnit.MILLISECONDS);
    return true;
}

private void processItems(boolean toggle, List<Rs2ItemModel> items, String action) {
    if (!toggle || items == null || items.isEmpty()) return;
    Rs2Inventory.interact(items.get(0), action);
    Rs2Player.waitForAnimation();
}

    public void shutdown() {
        super.shutdown();
    }
}
