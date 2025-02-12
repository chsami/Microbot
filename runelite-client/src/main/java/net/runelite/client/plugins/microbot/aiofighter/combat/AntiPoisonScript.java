package net.runelite.client.plugins.microbot.aiofighter.combat;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class AntiPoisonScript extends Script {
    public boolean run(AIOFighterConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!config.useAntiPoison()) return;
                if (Rs2Player.hasAntiPoisonActive()) {
                    List<Rs2ItemModel> potions = Microbot.getClientThread().runOnClientThread(Rs2Inventory::getPotions);
                    if (potions == null || potions.isEmpty()) {
                        return;
                    }
                    for (Rs2ItemModel potion: potions) {
                        if (potion.name.toLowerCase().contains("poison")) {
                            Rs2Inventory.interact(potion, "drink");
                            Rs2Player.waitForAnimation();
                            break;
                        }
                    }
                }
            } catch(Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }
}
