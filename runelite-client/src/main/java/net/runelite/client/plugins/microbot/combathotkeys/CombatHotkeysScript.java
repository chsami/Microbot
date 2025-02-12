package net.runelite.client.plugins.microbot.combathotkeys;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;


public class CombatHotkeysScript extends Script {
    public boolean isSwitchingGear = false;
    public boolean dance = false;
    public ArrayList<Rs2ItemModel> gearToSwitch = new ArrayList<>();

    public boolean run(CombatHotkeysConfig config) {
        Microbot.enableAutoRunOn = true;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                if (dance) {
                    if(Rs2Player.getWorldLocation().equals(config.tile1())){
                        Rs2Walker.walkFastCanvas(config.tile2());
                        Global.sleepUntil(() -> Rs2Player.getWorldLocation().equals(config.tile2()), 1000);
                    }
                    else if(Rs2Player.getWorldLocation().equals(config.tile2())){
                        Rs2Walker.walkFastCanvas(config.tile1());
                        Global.sleepUntil(() -> Rs2Player.getWorldLocation().equals(config.tile1()), 1000);
                    }
                    else{
                        Rs2Walker.walkFastCanvas(config.tile1());
                        Global.sleepUntil(() -> Rs2Player.getWorldLocation().equals(config.tile1()), 1000);
                    }
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}