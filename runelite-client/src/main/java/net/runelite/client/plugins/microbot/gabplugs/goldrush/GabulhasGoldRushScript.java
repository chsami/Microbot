package net.runelite.client.plugins.microbot.gabplugs.goldrush;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.Notifier;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.gabplugs.goldrush.GabulhasGoldRushInfo.botStatus;
import static net.runelite.client.plugins.microbot.gabplugs.goldrush.GabulhasGoldRushInfo.states;

@Slf4j
public class GabulhasGoldRushScript extends Script {
    public static double version = 1.0;
    @Inject
    private Notifier notifier;

    private WorldPoint zanarisRing = new WorldPoint(2412, 4434, 0);

    private WorldPoint bankPoint = new WorldPoint(2381, 4455, 0);

    public boolean run(GabulhasGoldRushConfig config) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                switch (botStatus) {
                    case STARTING:
                        Rs2Camera.setZoom(181);
                        Rs2Camera.setAngle(90, 90);
                        Rs2Camera.adjustPitch(383);

                        break;
                    case GETTING_BARS:
                        Rs2Inventory.wield("Goldsmith gauntlets");
                        sleep(100, 1000);
                        Rs2Bank.useBank();
                        sleep(100, 1000);
                        while(Rs2Inventory.contains("Gold bar")){
                            if(Rs2Bank.isOpen()){
                                sleep(100, 600);
                                Rs2Bank.depositAll("Gold bar");
                            }
                            sleep(100, 1000);
                        }

                        sleep(100, 2000);
                        while(!Rs2Inventory.contains("Gold ore")){
                            Rs2Bank.withdrawAll("Gold ore");
                            sleep(100, 1000);
                        }
                        Rs2Bank.closeBank();
                        if(Microbot.getClient().getEnergy() > Rs2Random.nextInt(2000, 10000, 2, true)) {
                            sleep(100, 2000);
                            Rs2Player.toggleRunEnergy(true);
                        }
                        botStatus = states.USING_BARS;
                        break;
                    case USING_BARS:
                        int currentXP =Microbot.getClient().getSkillExperience(Skill.SMITHING);
                        Rs2GameObject.interact(9100, "Put-ore-on");
                        while(Rs2Inventory.contains("Gold ore")){
                            sleep(100);
                        }

                        Rs2Walker.walkTo(new WorldPoint(1940,4964,0));

                        while (Microbot.getClient().getSkillExperience(Skill.SMITHING) == currentXP){
                            sleep(100, 600);
                        }
                        botStatus = states.RETRIEVING_BARS;
                        break;
                    case RETRIEVING_BARS:
                        Rs2Inventory.wield("Ice gloves");
                        Rs2GameObject.interact(9092, "Take");                            Rs2Keyboard.keyPress(' ');
                        while(!Rs2Inventory.contains("Gold bar")){
                            Rs2Keyboard.keyPress(32);
                            sleep(100, 1000);
                        }

                        botStatus = states.GETTING_BARS;

                        break;
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

