package net.runelite.client.plugins.microbot.gabplugs.glassmake;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.Notifier;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.gabplugs.glassmake.GabulhasGlassMakeInfo.botStatus;
import static net.runelite.client.plugins.microbot.gabplugs.glassmake.GabulhasGlassMakeInfo.states;

@Slf4j
public class GabulhasGlassMakeScript extends Script {
    public static String version = "1.0.1";
    @Inject
    private Notifier notifier;

    private GabulhasGlassMakeInfo.items currentItem;

    private boolean oneTimeSpellBookCheck = false;

    public boolean run(GabulhasGlassMakeConfig config) {
        oneTimeSpellBookCheck = false;
        Rs2Antiban.antibanSetupTemplates.applyUniversalAntibanSetup();
        Rs2AntibanSettings.actionCooldownChance = 0.2;
       currentItem= config.ITEM();
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                switch (botStatus) {
                    case Starting:

                        botStatus=states.Banking;
                        break;
                    case Banking:
                        banking();
                        botStatus=states.Glassblowing;
                        break;
                    case Glassblowing:
                        glassblowing();
                        botStatus=states.Picking;
                        break;
                    case Picking:
                        picking();
                        botStatus=states.Banking;
                        break;

                }

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }

    private void takeBreak() {
        if(Rs2Random.nextInt(0, 20, 1, true) == 30) {
                   sleep(1000, 20000);
        }


    }

    private void banking() {
        takeBreak();


        while(!Rs2Bank.isOpen()){
            Rs2Bank.openBank();
            sleep(100, 200);
        }
        Rs2Bank.depositAll("Molten Glass");
        sleepUntil(() -> !Rs2Inventory.contains("Molten Glass"),  100);
        if(currentItem== GabulhasGlassMakeInfo.items.GiantSeaweed ) {
            if(Rs2Bank.count("Giant seaweed") < 3 || Rs2Bank.count("Bucket of sand") < 3) {
                notifier.notify("Out of materials");
                while(super.isRunning()) {
                    sleep(1000);
                }
            }

            for(int i =0 ; i <3; i++)   {
                Rs2Bank.withdrawOne("Giant seaweed");
            }

            Rs2Bank.withdrawX("Bucket of sand", 18);
        } else {
            if(Rs2Bank.count("Seaweed") < 3 || Rs2Bank.count("Bucket of sand") < 3) {
                notifier.notify("Out of materials");
                while(super.isRunning()) {
                    sleep(1000);
                }
            }

            Rs2Bank.withdrawX("Bucket of sand", 13);
            Rs2Bank.withdrawX(401, 13);


        }


        sleep(60, 100);
        Rs2Bank.closeBank();
        while (Rs2Bank.isOpen()){
            sleep(40, 100);
        }


    }

    private void glassblowing(){
        superglassmake();
        sleep(60, 100);
        sleepUntil(()-> Rs2Inventory.contains("Molten Glass"), 100);
    }

    private void superglassmake() {
        if(!oneTimeSpellBookCheck) {
            Rs2Magic.oneTimeSpellBookCheck();
            oneTimeSpellBookCheck = true;
        }
        if (Rs2Magic.quickCast(MagicAction.SUPERGLASS_MAKE)) {
            Rs2Bank.preHover();
            sleep(600*2, 600*4);
        }

    }


    private void picking() {
        while (!Rs2Bank.isOpen()) {
            Rs2Bank.openBank();
            sleep(60, 200);
        }
        Rs2Bank.depositAll("Molten Glass");
        sleepUntil(() -> !Rs2Inventory.contains("Molten Glass"), 100);
        if(Rs2GroundItem.exists("Molten Glass", 1)) {
            sleep(60, 100);
            Rs2Bank.closeBank();
            while(Rs2GroundItem.exists("Molten Glass", 1)) {
                Rs2GroundItem.loot("Molten Glass", 1);
                sleep(60, 100);
            }
        }

    }
}

