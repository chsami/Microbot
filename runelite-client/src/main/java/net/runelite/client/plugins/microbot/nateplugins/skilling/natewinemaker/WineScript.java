package net.runelite.client.plugins.microbot.nateplugins.skilling.natewinemaker;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.concurrent.TimeUnit;

public class WineScript extends Script {

    public static String version = "1.1.1";

    public boolean run(WineConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
				if (!super.run()) return;
				if (!Microbot.isLoggedIn()) return;
                if (Rs2Inventory.count("grapes") > 0 && (Rs2Inventory.count("jug of water") > 0)) {
                    Rs2Inventory.combine("jug of water", "grapes");
                    sleepUntil(() -> Rs2Widget.getWidget(17694734) != null);
                    Rs2Keyboard.keyPress('1');
                    sleepUntil(() -> !Rs2Inventory.hasItem("jug of water"),25000);
                } else {
                    bank();
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private void bank(){
        Rs2Bank.openBank();
        if(Rs2Bank.isOpen()){
            Rs2Bank.depositAll();
            if(Rs2Bank.hasBankItem("jug of water",14) &&  Rs2Bank.hasBankItem("grapes",14)) {
                Rs2Bank.withdrawDeficit("jug of water", 14);
                sleepUntil(() -> Rs2Inventory.hasItem("jug of water"));
                Rs2Bank.withdrawDeficit("grapes", 14);
                sleepUntil(() -> Rs2Inventory.hasItem("grapes"));
            } else {
                Microbot.getNotifier().notify("Run out of Materials");
                Rs2Bank.closeBank();
                Rs2Player.logout();
                Plugin wineMakerPlugin = Microbot.getPluginManager().getPlugins().stream()
                        .filter(x -> x.getClass().getName().equals(WinePlugin.class.getName()))
                        .findFirst()
                        .orElse(null);
                Microbot.stopPlugin(wineMakerPlugin);
                shutdown();
            }
        }
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
