package net.runelite.client.plugins.microbot.nateplugins.skilling.arrowmaker;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.concurrent.TimeUnit;

public class ArrowScript extends Script {

    public static double version = 1.0;

    public boolean run(ArrowConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run()) return;
            if (!Microbot.isLoggedIn()) return;
            try {
                if (Microbot.pauseAllScripts) return;
                if (config.ARROWBool()) {
                    checkAndUseItem(config.ARROW().getItem1(), config.ARROW().getItem2());
                }
                if (config.BOLTBool()) {
                    checkAndUseItem(config.BOLT().getItem1(), config.BOLT().getItem2());
                }
                if (config.DARTBool()) {
                    checkAndUseItem(config.DART().getItem1(), config.DART().getItem2());
                }
                if (config.TIPPINGBool()) {
                    checkAndUseItem(config.TIP().getItem1(), config.TIP().getItem2());
                }
                if (config.DRAGONTIPPINGBool()) {
                    checkAndUseItem(config.DragonTIP().getItem1(), config.DragonTIP().getItem2());
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void checkAndUseItem(String item1, String item2) {
        if (Rs2Inventory.count(item1) > 0 && Rs2Inventory.count(item2) > 0) {
            Rs2Inventory.combine(item1, item2);
            handleSleep();
        }
    }
    private void handleSleep(){
        sleepUntilOnClientThread(() -> Rs2Widget.getWidget(17694733) != null);
        keyPress('1');
        sleep(12000,14000);
    }
}
