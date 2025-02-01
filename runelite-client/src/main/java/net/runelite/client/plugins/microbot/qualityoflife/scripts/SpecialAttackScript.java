package net.runelite.client.plugins.microbot.qualityoflife.scripts;

import net.runelite.api.NPC;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.qualityoflife.QoLConfig;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class SpecialAttackScript extends Script {

    public boolean run(QoLConfig config) {
        AtomicReference<NPC> npc = new AtomicReference<>();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!config.useSpecWeapon()) return;
                if (Rs2Equipment.isWearingFullGuthan()) return;
                if (Rs2Player.isInteracting()) {
                    npc.set((NPC) Microbot.getClient().getLocalPlayer().getInteracting());
                    if (Microbot.getSpecialAttackConfigs().useSpecWeapon()) {
                        Rs2Npc.attack(npc.get());
                    }
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    public void shutdown() {
        super.shutdown();
    }

}