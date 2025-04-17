package net.runelite.client.plugins.microbot.aiofighter.safety;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterPlugin;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.Microbot.log;

@Slf4j
public class SafetyScript extends Script {
    public boolean run(AIOFighterConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!config.useSafety()) return;
                if (config.missingRunes() && config.useMagic() && !Rs2Magic.hasRequiredRunes(config.magicSpell())){
                    stopAndLog("Missing runes for spell: " + config.magicSpell());
                }
                if (config.missingFood() && Rs2Inventory.getInventoryFood().isEmpty() && !config.bank()){
                    stopAndLog("Missing food in inventory. Turn Missing Food config off if you don't want this.");
                }
                if (config.missingArrows() && !Rs2Equipment.contains(x -> x.getName().toLowerCase().contains("arrow") || x.getName().toLowerCase().contains("bolt") || x.getName().toLowerCase().contains("dart") || x.getName().toLowerCase().contains("knife"))){
                    stopAndLog("Missing arrows in inventory/equipment");
                }
                if (config.lowHealth() && Rs2Inventory.getInventoryFood().isEmpty() && !config.bank()){
                    if (Rs2Player.getHealthPercentage() < config.healthSafetyValue()){
                        stopAndLog("Low health: " + Rs2Player.getHealthPercentage() + "%");
                    }
                }
            } catch(Exception ex) {
                System.out.println("Safety script error: "+ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    public void stopAndLog(String reason) {
        log(reason);
        if(Rs2Bank.walkToBank()){
            Rs2Player.logout();
            Plugin PlayerAssistPlugin = Microbot.getPlugin(AIOFighterPlugin.class.getName());
            Microbot.stopPlugin(PlayerAssistPlugin);
        }
    }
}
