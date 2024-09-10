package net.runelite.client.plugins.microbot.playerassist.combat;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.playerassist.PlayerAssistConfig;
import net.runelite.client.plugins.microbot.playerassist.enums.PrayerStyle;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcManager;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;

import java.util.concurrent.TimeUnit;

@Slf4j
public class PrayerScript extends Script {
    public boolean run(PlayerAssistConfig config) {
        try {
            Rs2NpcManager.loadJson();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;

                handlePrayer(config);
            } catch (Exception ex) {
                System.err.println("Error: " + ex.getMessage());
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handlePrayer(PlayerAssistConfig config) {
        if (!Microbot.isLoggedIn() || !config.togglePrayer()) return;
        if (config.prayerStyle() != PrayerStyle.CONTINUOUS && config.prayerStyle() != PrayerStyle.ALWAYS_ON) return;
        log.info("Prayer style: " + config.prayerStyle().getName());
        if (config.prayerStyle() == PrayerStyle.CONTINUOUS) {
            Rs2Prayer.toggleQuickPrayer(Rs2Combat.inCombat());
        } else {
            if (super.run())
                Rs2Prayer.toggleQuickPrayer(config.prayerStyle() == PrayerStyle.ALWAYS_ON);
        }
    }


    public void shutdown() {
        super.shutdown();
    }
}
