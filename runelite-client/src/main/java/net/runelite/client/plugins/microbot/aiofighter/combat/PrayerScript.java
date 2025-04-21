package net.runelite.client.plugins.microbot.aiofighter.combat;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.aiofighter.enums.PrayerStyle;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcManager;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;

import java.util.concurrent.TimeUnit;

@Slf4j
public class PrayerScript extends Script {
    public boolean run(AIOFighterConfig config) {
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
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handlePrayer(AIOFighterConfig config) {
        if (!Microbot.isLoggedIn() || !config.togglePrayer()) return;
        if (config.prayerStyle() != PrayerStyle.CONTINUOUS && config.prayerStyle() != PrayerStyle.ALWAYS_ON) return;
        if (config.prayerStyle() == PrayerStyle.CONTINUOUS) {
            boolean underAttack = Rs2Npc.getNpcsForPlayer().findAny().isPresent() || Rs2Combat.inCombat();
            Rs2Prayer.toggleQuickPrayer(underAttack);
        } else {
            if (super.run())
                Rs2Prayer.toggleQuickPrayer(config.prayerStyle() == PrayerStyle.ALWAYS_ON);
        }
    }


    public void shutdown() {
        super.shutdown();
    }
}
