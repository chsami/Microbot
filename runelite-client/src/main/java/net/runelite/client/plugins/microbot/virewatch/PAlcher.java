package net.runelite.client.plugins.microbot.virewatch;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import java.util.concurrent.TimeUnit;

public class PAlcher extends Script {

    PVirewatchKillerPlugin plugin;

    private static final String[] itemsToAlch = {
            "Rune dagger",
            "Adamant platelegs",
            "Adamant platebody",
            "Rune full helm",
            "Rune kiteshield",
            "Rune 2h sword"
    };

    public boolean run(PVirewatchKillerConfig config, PVirewatchKillerPlugin plugin) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            this.plugin = plugin;

            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!config.alchItems()) return;

                for (String item : itemsToAlch) {
                    if (Rs2Inventory.contains(item)) {
                        alchItem(item);
                        break;
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
        if (Rs2Magic.canCast(MagicAction.HIGH_LEVEL_ALCHEMY)){
            Rs2Magic.alch(item);
        }else{
            Microbot.log("Please check, unable to cast High Alchemy");
        }
        sleepUntil(() -> !Rs2Inventory.contains(item));
        plugin.alchingDrop = false;
        Rs2Tab.switchToInventoryTab();
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
