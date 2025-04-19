package net.runelite.client.plugins.microbot.virewatch;

import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

public class PVirewatchScript extends Script {

    @Inject
    Client client;
    public boolean run(PVirewatchKillerConfig config, PVirewatchKillerPlugin plugin) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                Rs2Combat.enableAutoRetialiate();

                if(plugin.fightArea.contains(client.getLocalPlayer().getWorldLocation())) {
                    Microbot.status = "Figthing";
                }

                if(Microbot.getClient().getLocalPlayer().getWorldLocation() != plugin.startingLocation) {
                    if(plugin.ticksOutOfArea > config.tickToReturn() || plugin.countedTicks > config.tickToReturnCombat()) {
                        Rs2Walker.walkTo(plugin.startingLocation, 0);
                    }
                }

                Rs2Player.eatAt(config.hitpoints());

                if(Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) <= config.prayAt()) {
                    plugin.rechargingPrayer = true;
                    var statue = Rs2GameObject.getGameObjects(39234);
                    if(statue != null && statue.get(0) != null) {
                        Rs2Walker.walkTo(statue.get(0).getWorldLocation(), 1);
                        sleepUntil(() -> Rs2GameObject.hasLineOfSight(statue.get(0)));
                        if(Rs2GameObject.hasLineOfSight(statue.get(0))) {
                            Microbot.status = "RECHARGING PRAYER";
                            Rs2GameObject.interact(39234);
                            sleep(100);
                            plugin.rechargingPrayer = false;
                            if(Rs2Player.isInteracting()) {
                                sleepUntil(() -> Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) > config.prayAt());
                                Microbot.status = "WALKING TO STARTING POINT";
                                Rs2Walker.walkTo(plugin.startingLocation, 0);

                            }
                        }
                    }

                }

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
