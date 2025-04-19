package net.runelite.client.plugins.microbot.gabplugs.sandminer;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.Notifier;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.gabplugs.sandminer.GabulhasSandMinerInfo.botStatus;
import static net.runelite.client.plugins.microbot.gabplugs.sandminer.GabulhasSandMinerInfo.states;

@Slf4j
public class GabulhasSandMinerScript extends Script {
    public static double version = 1.0;
    @Inject
    private Notifier notifier;

    private WorldPoint miningPoint  = new WorldPoint(3165, 2906, 0);


    public boolean run(GabulhasSandMinerConfig config) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                switch (botStatus) {
                    case Mining:
                        humidifyIfNeeded();
                        miningLoop();
                        botStatus=states.Depositing;
                        break;
                    case Depositing:

                        deposit();
                        botStatus=states.Mining;

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


    private void miningLoop() {

        Rs2Walker.walkTo(miningPoint);
        sleep(100, 4000);
        while (!Rs2Inventory.isFull() && super.isRunning()) {
           while(Rs2Player.hopIfPlayerDetected(1, 3000,100)){
               sleep(10000);
           }

            sleep(Rs2Random.nextInt(300, 5000, 0.1, true));
            if (!Rs2Player.isInteracting() || !Rs2Player.isAnimating()) {

               GameObject sandstoneRock =  Rs2GameObject.findObject("Sandstone rocks", true, 5,  true, miningPoint);


                if(sandstoneRock != null){
                   Rs2GameObject.interact(sandstoneRock, "Mine");
                   Rs2Player.waitForAnimation();
                   Rs2Antiban.actionCooldown();
               }
            }
        }
    }

    private void humidifyIfNeeded() {
        String[] waterskinNames = {"Waterskin(4)", "Waterskin(3)",  "Waterskin(2)",  "Waterskin(1)"};
        boolean containsAnyWaterskin = false;
        for(int i = 0; i < waterskinNames.length; i++){
           containsAnyWaterskin =  Rs2Inventory.contains(waterskinNames[i]);

        }
        if(!containsAnyWaterskin){
            System.out.println("Humidified");
            Rs2Magic.cast(MagicAction.HUMIDIFY);
            sleepUntilOnClientThread(() -> Rs2Inventory.hasItem("Waterskin(4)"));
            Rs2Antiban.actionCooldown();
            Rs2Antiban.takeMicroBreakByChance();
            sleep(1000, 2000);
        }
    }

    private void deposit() {
        WorldPoint grinder =  new WorldPoint(3150, 2908, 0);
        Rs2Walker.walkTo(grinder);
        GameObject sandstoneRock =  Rs2GameObject.findObject(26199, grinder);
        Rs2GameObject.interact(sandstoneRock, "Deposit");
        while (Rs2Inventory.contains("Sandstone (1kg)", "Sandstone (2kg)", "Sandstone (5kg)", "Sandstone (10kg)")) {
            sleep(100, 3000);
        }


    }


    private int distanceBetweenPoints(int x1, int y1, int x2, int y2) {
            return Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));

    }

    private void useBank() {
        Rs2Bank.depositAll(3142);
    }

    private void interactWithFishingSpot() {
        Rs2Npc.interact(4712, "Fish");
    }

    private void walkToDeposit() {

    }

    private void walkToQuary() {

    }





}

