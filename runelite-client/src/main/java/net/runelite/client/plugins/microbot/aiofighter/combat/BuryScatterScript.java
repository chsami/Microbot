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
                if (!Microbot.isLoggedIn() || !super.run() || (!config.toggleBuryBones() && !config.toggleScatter())) {
                    return;
                }

                List<Rs2ItemModel> bones = Rs2Inventory.getBones();
                List<Rs2ItemModel> ashes = Rs2Inventory.getAshes();
                if (!bones.isEmpty()) {
                    if (Rs2Magic.canCast(Rs2Spells.SINISTER_OFFERING)) {
                        if (bones.size() >= 3) {
                            if (Rs2Magic.cast(Rs2Spells.SINISTER_OFFERING)) {
                                sleepUntil(() -> Rs2Inventory.getBones().isEmpty());
                            }
                        }
                    } else {
                        processItems(config.toggleBuryBones(), bones, "bury");
                    }
                } else if (!ashes.isEmpty()) {
                    if (Rs2Magic.canCast(Rs2Spells.DEMONIC_OFFERING)) {
                        if (ashes.size() >= 3) {
                            if (Rs2Magic.cast(Rs2Spells.DEMONIC_OFFERING)) {
                                sleepUntil(() -> Rs2Inventory.getAshes().isEmpty());
                            }
                        }
                    } else {
                        processItems(config.toggleScatter(), ashes, "scatter");
                    }
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }


    private void processItems(boolean toggle, List<Rs2ItemModel> items, String action) {
        if (!toggle || items == null || items.isEmpty()) {
            return;
        }
        Rs2Inventory.interact(items.get(0), action);
        Rs2Player.waitForAnimation();
    }

    public void shutdown() {
        super.shutdown();
    }
}
