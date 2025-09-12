package net.runelite.client.plugins.microbot.aiofighter.combat;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j

public class ArrowWieldScript extends Script {
    public boolean run(AIOFighterConfig config) {
        log.info("ArrowWieldScript: Starting arrow wield script");
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) {
                    log.debug("ArrowWieldScript: Not logged in or script not running");
                    return;
                }

                if (config.toggleWieldArrows()) {
                    String arrowName = config.arrowType().getName();
                    List<Rs2ItemModel> arrows = Rs2Inventory.getList(item -> item.getName().equalsIgnoreCase(arrowName));
                    processArrows(arrows, arrowName);
                }
            } catch (Exception ex) {
                log.error("ArrowWieldScript error: ", ex);
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private void processArrows(List<Rs2ItemModel> arrows, String arrowName) {
        if (arrows == null || arrows.isEmpty()) {
            return;
        }
        
        // Wield the arrows (similar to how bones are buried)
        Rs2Inventory.interact(arrows.get(0), "Wield");
        Rs2Player.waitForAnimation();
    }

    public void shutdown() {
        super.shutdown();
    }
}
