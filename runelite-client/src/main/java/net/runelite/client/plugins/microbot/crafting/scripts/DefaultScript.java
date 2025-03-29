package net.runelite.client.plugins.microbot.crafting.scripts;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.crafting.CraftingConfig;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Random;

import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

public class DefaultScript extends Script {
    public boolean run(CraftingConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn())
                    return;
                if (!super.run())
                    return;
                if (Random.random(1, 255) == 2)
                    sleep(3000, 60000);
                String leather = "green dragon leather";
                String craftedItem = "green d'hide body";
                if (Microbot.isGainingExp)
                    return;
                if (!Rs2Inventory.hasItem(craftedItem)) {
                    if (!Rs2Inventory.isFull()) {
                        Rs2Bank.openBank();
                        sleepUntil(() -> Rs2Bank.isOpen(), 5000);
                        if (!Rs2Bank.isOpen())
                            return;
                        Rs2Bank.withdrawItem(true, "needle");
                        Rs2Bank.withdrawAll(true, "thread");
                        if (!Rs2Inventory.hasItem("needle") || !Rs2Inventory.hasItem("thread"))
                            return;
                        Rs2Bank.withdrawAll(leather);
                    } else if (Rs2Inventory.hasItem(leather)) {
                        Rs2Bank.closeBank();
                        Rs2Inventory.combine("needle", leather);
                        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                        sleep(3000);
                    } else {
                        shutDown();
                    }
                } else {
                    Rs2Bank.openBank();
                    Rs2Bank.depositAll(craftedItem);
                    Rs2Bank.withdrawAll(leather);
                    Rs2Bank.closeBank();
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
                Microbot.getNotifier().notify("Script failure");
            }

        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    public void shutDown() {
        if (mainScheduledFuture != null && !mainScheduledFuture.isDone()) {
            mainScheduledFuture.cancel(true);
        }
        super.shutdown();
    }
}
