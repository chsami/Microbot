package net.runelite.client.plugins.microbot.aiofighter.combat;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.nmz.NmzConfig;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class PrayerPotionScript extends Script {
    public boolean run(AIOFighterConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if ((Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) * 100) / Microbot.getClient().getRealSkillLevel(Skill.PRAYER) > Rs2Random.between(25, 30))
                    return;
                List<Rs2ItemModel> potions = Microbot.getClientThread().runOnClientThreadOptional(Rs2Inventory::getPotions).orElse(null);
                if (potions == null || potions.isEmpty()) {
                    return;
                }
                for (Rs2ItemModel potion : potions) {
                    if (potion.name.toLowerCase().contains("prayer") || potion.name.toLowerCase().contains("super restore") || potion.name.toLowerCase().contains("moonlight potion")) {
                        Rs2Inventory.interact(potion, "drink");
                        sleep(1200, 2000);
                        Rs2Inventory.dropAll("Vial");
                        break;
                    }
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    public boolean run(NmzConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!config.togglePrayerPotions()) return;
                if ((Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) * 100) / Microbot.getClient().getRealSkillLevel(Skill.PRAYER) > Rs2Random.between(25, 30))
                    return;
                List<Rs2ItemModel> potions = Microbot.getClientThread().runOnClientThreadOptional(Rs2Inventory::getPotions).orElse(null);
                if (potions == null || potions.isEmpty()) {
                    return;
                }
                for (Rs2ItemModel potion : potions) {
                    if (potion.name.toLowerCase().contains("prayer")) {
                        Rs2Inventory.interact(potion, "drink");
                        sleep(1200, 2000);
                        Rs2Inventory.dropAll("Vial");
                        break;
                    }
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }
}
