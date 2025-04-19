package net.runelite.client.plugins.microbot.aiofighter.combat;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.concurrent.TimeUnit;

public class UseSpecialAttackScript extends Script {

    public boolean run(AIOFighterConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!config.useSpecialAttack()) return;
                if (Rs2Equipment.isWearingFullGuthan()) return;
                if (Rs2Player.isInteracting())
                    Microbot.getSpecialAttackConfigs().useSpecWeapon();
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    public void shutdown() {
        super.shutdown();
    }

}