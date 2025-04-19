package net.runelite.client.plugins.microbot.nateplugins.arrowmaker;

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
                if (Rs2Inventory.count("feather") > 0 && (Rs2Inventory.count("arrow shaft") > 0)) {
                    Rs2Inventory.combine("feather", "arrow shaft");
                    sleepUntilOnClientThread(() -> Rs2Widget.getWidget(17694733) != null);
                    keyPress('1');
                    sleep(5000,7500);
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }


}
