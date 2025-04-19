package net.runelite.client.plugins.microbot.GirdyScripts.cannonballsmelter;


import net.runelite.api.Client;
import net.runelite.api.ItemID;
import net.runelite.api.ObjectID;
import net.runelite.api.TileObject;
import net.runelite.client.plugins.microbot.GirdyScripts.cannonballsmelter.enums.CannonballSmelterStates;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;


public class CannonballSmelterScript extends Script {

    public static String version = "1.0.1";
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    @Inject
    private CannonballSmelterConfig config;
    @Inject
    private Client client;

    long startTime;
    long endTime;

    CannonballSmelterStates state = CannonballSmelterStates.IDLING;


    private boolean hasBalls() {
        return Rs2Inventory.hasItem(ItemID.CANNONBALL);
    }
    private boolean hasBars() {
        return Rs2Inventory.hasItem(ItemID.STEEL_BAR);
    }
    private boolean required() {return (Rs2Inventory.hasItem(ItemID.AMMO_MOULD) || Rs2Inventory.hasItem(ItemID.DOUBLE_AMMO_MOULD));}

    public boolean run(CannonballSmelterConfig config) {
        Rs2Antiban.resetAntibanSettings();
        cannonballAntiBan();
        Rs2AntibanSettings.actionCooldownChance = 0.1;
        Microbot.enableAutoRunOn = true;
        Microbot.runEnergyThreshold = 5000;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !Microbot.isLoggedIn() || Microbot.pauseAllScripts) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;
                startTime = System.currentTimeMillis();


                getState();

                switch (state) {
                    case GET_MOULD:
                        getMould();
                        break;
                    case BANKING:
                        bank();
                        break;
                    case SMELTING:
                        smelt();
                        break;
                }
                endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    private void getState() {
        if (!required()) {
           state = CannonballSmelterStates.GET_MOULD;
        }
        else if(hasBars()) {
            state = CannonballSmelterStates.SMELTING;
        } else if (!hasBars() || (!hasBars() && hasBalls())){
            state = CannonballSmelterStates.BANKING;
        }
    }

    public void smelt() {
        TileObject furnace = Rs2GameObject.findObjectById(ObjectID.FURNACE_16469);
        if (furnace != null) {
            Rs2GameObject.interact(furnace, "Smelt");
            Microbot.status = "Moving to furnace...";
            sleepUntil(() -> Rs2Widget.getWidget(17694733) != null);
            if(Rs2Widget.getWidget(17694733) != null) {
                Rs2Widget.clickWidget(17694734);
                Microbot.status = "Smelting Cannonballs...";
                sleep(200,600);
                mouseOff();
                sleepUntil(() -> !hasBars(), 162000);
                Rs2Antiban.actionCooldown();
                Rs2Antiban.takeMicroBreakByChance();
            }
        } else {
            Microbot.log("Cannot find furnace...");
            sleep(10000);
        }
    }

    public void bank() {
        if (hasBalls() && !hasBars()) {
            Microbot.status = "Banking...";
            if(!Rs2Bank.isOpen()) {
                Rs2Bank.openBank();
                Microbot.status = "Banking...";
                sleepUntil(() -> Rs2Bank.isOpen());
            }
            if(!Rs2Bank.hasItem(ItemID.STEEL_BAR)) {
                Microbot.showMessage("Can't find Steel bars in bank, exiting...");
                sleep(3000,5000);
                shutdown();
            }
            Rs2Bank.withdrawAll(ItemID.STEEL_BAR);
            sleepUntil(() -> hasBars());
            if(hasBars()) {
                Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            }
            if (!hasBars()) {
                Microbot.showMessage("Could not find item in bank.");
                shutdown();
            }
        }
    }

    public void getMould() {
        if(!Rs2Inventory.hasItem("ammo mould")) {
            if(!Rs2Bank.isOpen()) {
                Rs2Bank.openBank();
            }
            sleepUntil(() -> Rs2Bank.isOpen());
            if(!Rs2Bank.hasItem("ammo mould")) {
                Microbot.showMessage("Could not find ammo mould in bank, exiting...");
                sleep(3000, 5000);
                shutdown();
            }
            Rs2Bank.withdrawOne("ammo mould");
            sleepUntil(() -> required(), 3000);
        }
        if(!Rs2Bank.hasItem("steel bar")) {
            Microbot.showMessage("Can't find Steel bars in bank, exiting...");
            sleep(3000,5000);
            shutdown();
        }
        Rs2Bank.withdrawAll(2353);
        sleepUntil(() -> Rs2Inventory.hasItem("steel bar"));
        if(Rs2Inventory.hasItem("steel bar")) {
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        }
        if (!Rs2Inventory.hasItem("steel bar")) {
            Microbot.showMessage("Could not find item in bank.");
            shutdown();
        }
    }

    public void cannonballAntiBan() {
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = false;
        Rs2AntibanSettings.randomIntervals = false;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.profileSwitching = true;
        Rs2AntibanSettings.timeOfDayAdjust = false;
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.moveMouseRandomly = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.dynamicIntensity = true;
        Rs2AntibanSettings.dynamicActivity = false;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.takeMicroBreaks = true;
        Rs2AntibanSettings.playSchedule = false;
        Rs2AntibanSettings.universalAntiban = false;
        Rs2AntibanSettings.microBreakDurationLow = 2;
        Rs2AntibanSettings.microBreakDurationHigh = 10;
        Rs2AntibanSettings.actionCooldownChance = 1.00;
        Rs2AntibanSettings.microBreakChance = 0.15;
        Rs2Antiban.setActivity(Activity.GENERAL_SMITHING);
    }

    public void mouseOff() {
        int horizontal = random.nextBoolean() ? -1 : client.getCanvasWidth() + 1;
        int vertical = random.nextBoolean() ? -1 : client.getCanvasHeight() + 1;

        boolean exitHorizontally = random.nextBoolean();
        if (exitHorizontally) {
            Microbot.naturalMouse.moveTo(horizontal, random.nextInt(0, client.getCanvasHeight() + 1));
        } else {
            Microbot.naturalMouse.moveTo(random.nextInt(0, client.getCanvasWidth() + 1), vertical);
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
    }
}
