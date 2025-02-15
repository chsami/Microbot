package net.runelite.client.plugins.microbot.frosty.artioprayer;

import net.runelite.api.*;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.frosty.artioprayer.BossData;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.plugins.microbot.frosty.artioprayer.ArtioPrayerPlugin;
import net.runelite.client.plugins.microbot.Script;


import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

public class ArtioScript extends Script {

    @Inject
    private ArtioPrayerPlugin artioPrayerPlugin;

    public static State state = State.IDLE;
    private final BossData boss = BossData.ARTIO;


    public boolean run(ArtioPrayerConfig config) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();














                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}