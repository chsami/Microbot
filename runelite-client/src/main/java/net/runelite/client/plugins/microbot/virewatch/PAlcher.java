package net.runelite.client.plugins.microbot.virewatch;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

public class PAlcher extends Script {

    PVirewatchKillerPlugin plugin;

    public boolean run(PVirewatchKillerConfig config, PVirewatchKillerPlugin plugin) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            this.plugin = plugin;
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if(!config.alchItems()) return;

                if(Rs2Inventory.contains("Nature rune", "Fire rune")) {
                    if(Rs2Inventory.contains("Rune dagger")) {
                        alchItem("Rune dagger");
                    } else if (Rs2Inventory.contains("Adamant platelegs")) {
                        alchItem("Adamant platelegs");
                    } else if (Rs2Inventory.contains("Adamant platebody")) {
                        alchItem("Adamant platebody");
                    } else if (Rs2Inventory.contains("Rune full helm")) {
                        alchItem("Rune full helm");
                    } else if (Rs2Inventory.contains("Rune kiteshield")) {
                        //alchItemWithConfirm("Rune kiteshield");

                    }

                }


            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 2000, TimeUnit.MILLISECONDS);
        return true;
    }

    private void alchItem(String item) {
        plugin.alchedItems++;
        plugin.alchingDrop = true;
        Rs2Magic.alch(item);
        sleepUntil(() -> !Rs2Inventory.contains(item));
        plugin.alchingDrop = false;
        Rs2Tab.switchToInventoryTab();

    }

    // Does not work yet
    private void alchItemWithConfirm(String item) {
        plugin.alchedItems++;
        plugin.alchingDrop = true;
        Rs2Magic.alch(item);
        Rs2Widget.sleepUntilHasWidget("Really cast High");
        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        sleep(300);
        Rs2Keyboard.keyPress(KeyEvent.VK_1);
        plugin.alchingDrop = false;
        Rs2Tab.switchToInventoryTab();
    }
    @Override
    public void shutdown() {
        super.shutdown();
    }
}
