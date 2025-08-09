package net.runelite.client.plugins.microbot.npcTanner;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.*;
import java.util.Random;
import java.util.concurrent.TimeUnit;


public class npcTannerScript extends Script {

    public static String whattotan = "Unset";
    private String product = "Unset";
    public static boolean test = false;
    public boolean run(npcTannerConfig config) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                WhatToTan(config);

                if(!whattotan.equals("Unset")) {
                    Microbot.status="Tanning: "+npcTannerScript.whattotan;
                    if (!Rs2Inventory.contains(whattotan)||!Rs2Inventory.contains("Coins")||Rs2Inventory.onlyContains(product)) {
                        TakeWhatWeNeed();
                    }
                    if(Rs2Inventory.contains(whattotan)&&Rs2Inventory.contains("Coins")&&!Rs2Inventory.onlyContains(product)){
                        WalkToandTan(config);
                    }
                }

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
    public int generateRandomNumber(int min, int max) {
        return Rs2Random.nextInt(min, max, 1000, true);
    }
    public static Point getRandomPoint(Rectangle bounds) {
        Random random = new Random();
        int randomX = bounds.x + random.nextInt(bounds.width);
        int randomY = bounds.y + random.nextInt(bounds.height);
        return new Point(randomX, randomY);
    }

    public void WalkToandTan(npcTannerConfig config){
        WorldPoint Tanman =(new WorldPoint(3273, 3192, 0));
        if(Rs2Player.getWorldLocation().distanceTo(Tanman)>=6){
            if(Rs2Walker.walkTo(Tanman)){
                sleep(1000,3000);
            }
        } else {
            if(Rs2Widget.getWidget(21233789)!=null || Rs2Widget.getWidget(21233753)!=null){
                Microbot.log("Tanning All");
                if(whattotan.equals("Cowhide")){
                    Widget tagetWidget;
                    if (config.tanLeather())
                        tagetWidget = Rs2Widget.getWidget(21233788);
                    else
                        tagetWidget = Rs2Widget.getWidget(21233789);
                    Rs2Widget.clickWidget(tagetWidget);
                }
                if(whattotan.equals("Green dragonhide")){
                    Widget tagetWidget = Rs2Widget.getWidget(21233768);
                    Rs2Widget.clickWidget(tagetWidget);
                }
                if(whattotan.equals("Blue dragonhide")){
                    Widget tagetWidget = Rs2Widget.getWidget(21233769);
                    Rs2Widget.clickWidget(tagetWidget);
                }
                if(whattotan.equals("Red dragonhide")){
                    Widget tagetWidget = Rs2Widget.getWidget(21233770);
                    Rs2Widget.clickWidget(tagetWidget);
                }
                if(whattotan.equals("Black dragonhide")){
                    Widget tagetWidget = Rs2Widget.getWidget(21233771);
                    Rs2Widget.clickWidget(tagetWidget);
                }
            } else {
                Microbot.status="Tanning: "+npcTannerScript.whattotan;
                if(Rs2Npc.interact(Rs2Npc.getNpc("Ellis"), "Trade")){
                    sleep(1000, 3000);
                }
            }

        }
    }
    public void TakeWhatWeNeed(){
        BankLocation Alkarid = BankLocation.AL_KHARID;
        if(Rs2Player.getWorldLocation().distanceTo(Alkarid.getWorldPoint())>=7||!Rs2Bank.isOpen()){
            // we need to walk to the bank
            Rs2Bank.walkToBankAndUseBank(Alkarid);
        }
        if(Rs2Bank.isOpen()){
            if(!Rs2Inventory.contains(whattotan)||!Rs2Inventory.contains("Coins")||Rs2Inventory.contains(product)){
                int tries = 0;
                while(Rs2Inventory.contains(product)){
                    int random = generateRandomNumber(0,100);
                    if(random<=75){
                        //depo just product
                        Microbot.log("reach here");

                        Rs2Bank.depositAll(product);
                        sleep(500,1000);
                    } else {
                        Rs2Bank.depositAll();
                        sleep(500,1000);
                    }
                    if(tries>=10){
                        break;
                    }
                    tries++;
                }
                tries=0;
                while(!Rs2Inventory.contains(whattotan) || !Rs2Inventory.contains("Coins")){
                    // We always do coin first
                    if(Rs2Inventory.contains(product) || !Rs2Inventory.onlyContains(it->it!=null && it.getName().equals("Coins") || it.getName().equals(whattotan))){
                        if(generateRandomNumber(0,100)<75){
                            Rs2Bank.depositAll(product);
                            sleep(500,1000);
                        } else {
                            Rs2Bank.depositAll();
                            sleep(500,1000);
                        }
                    }

                    if(Rs2Player.isInMemberWorld()) {
                        //credit to FunkyRhythm
                        if (!Rs2Player.hasStaminaBuffActive() && Microbot.getClient().getEnergy() < 8100) {
                            this.useStaminaPotions();
                        }
                    }

                    if(!Rs2Inventory.contains("Coins")||Rs2Inventory.count("Coins")<=1000){
                        Rs2Bank.withdrawAll("Coins");
                        sleep(500,1000);
                    }
                    if(Rs2Inventory.contains("Coins")&&!Rs2Inventory.isFull()){
                        Rs2Bank.withdrawAll(whattotan);
                        sleep(500,1000);
                    }
                    if(Rs2Inventory.contains(whattotan)&&Rs2Inventory.contains("Coins")){
                        break;
                    }
                    if(tries>=10){
                        break;
                    }
                    tries++;
                }
                if((!Rs2Inventory.contains("Coins"))||(Rs2Inventory.get("Coins").getQuantity()<=80)||(Rs2Inventory.count(whattotan)==0)){
                    if(Rs2Inventory.count(whattotan)==0){
                        whattotan = "Unset";
                        return;
                    }
                    Microbot.log("Out of Coins or "+whattotan);
                    shutdown();
                }
            }
        }
    }

    private void useStaminaPotions() {
        //credit to FunkyRhythm
        boolean usedPotion = false;

        // Step 1: Keep using Energy potions until energy is above 71%
        while (Microbot.getClient().getEnergy() < 6900) {
            usedPotion = usePotionIfNeeded("Energy potion", 6900);
            if (!usedPotion) {
                break; // Exit if no Energy potion is available
            }
        }

        // Step 2: If energy is above 71% but below 81%, use Stamina potion if no stamina buff is active
        if (Microbot.getClient().getEnergy() < 8100 && !Rs2Player.hasStaminaBuffActive()) {
            usedPotion = usePotionIfNeeded("Stamina potion", 8100);
        }

        // Sleep after using a potion
        if (usedPotion) {
            this.sleep(161, 197);
        }
    }

    private boolean usePotionIfNeeded(String potionName, int energyThreshold) {
        //credit to FunkyRhythm
        if (Microbot.getClient().getEnergy() < energyThreshold) {
            if (withdrawPotion(potionName)) {
                if (drinkPotion(potionName)) {
                    depositItems(potionName);
                    return true; // Potion was successfully used
                }
            }
        }
        return false; // Potion was not used
    }

    private boolean withdrawPotion(String potionName) {
        //credit to FunkyRhythm
        Rs2Bank.withdrawOne(potionName);
        sleep(900);
        return true;
    }

    private boolean drinkPotion(String potionName) {
        //credit to FunkyRhythm
        Rs2Inventory.interact(potionName, "Drink");
        sleep(900);
        return true;
    }

    private void depositItems(String potionName) {
        //credit to FunkyRhythm
        if (Rs2Inventory.hasItem(potionName)) {
            Rs2Bank.depositOne(potionName);
        }
        if (Rs2Inventory.hasItem(ItemID.VIAL_EMPTY)) {
            Rs2Bank.depositOne(ItemID.VIAL_EMPTY);
        }
    }

    public void WhatToTan(npcTannerConfig config){
        if(whattotan.equals("Unset")){
            if(Rs2Inventory.contains("Cowhide")){
                whattotan = "Cowhide";
                if (config.tanLeather())
                    product = "Leather";
                else
                    product = "Hard leather";
                return;
            }
            if(Rs2Inventory.contains("Green dragonhide")){
                whattotan = "Green dragonhide";
                product = "Green dragon leather";
                return;
            }
            if(Rs2Inventory.contains("Blue dragonhide")){
                whattotan = "Blue dragonhide";
                product = "Blue dragon leather";
                return;
            }
            if(Rs2Inventory.contains("Red dragonhide")){
                whattotan = "Red dragonhide";
                product = "Red dragon leather";
                return;
            }
            if(Rs2Inventory.contains("Black dragonhide")){
                whattotan = "Black dragonhide";
                product = "Black dragon leather";
                return;
            }
            BankLocation nearBank = Rs2Bank.getNearestBank();
            while(whattotan.equals("Unset")) {
                if (Rs2Player.getWorldLocation().distanceTo(nearBank.getWorldPoint()) > 10) {
                    // we need to walk to a bank
                    while (Rs2Player.getWorldLocation().distanceTo(nearBank.getWorldPoint()) > 10) {
                        if (Rs2Player.getWorldLocation().distanceTo(nearBank.getWorldPoint()) <= 10) {
                            break;
                        }
                        if(Rs2Walker.walkTo(nearBank.getWorldPoint())){
                            sleep(1000, 5000);
                        }
                    }
                } else {
                    // We're at the bank
                    if(Rs2Bank.openBank()){
                        if(Rs2Bank.count("Cowhide") > 10){
                            whattotan = "Cowhide";
                            if (config.tanLeather())
                                product = "Leather";
                            else
                                product = "Hard leather";                        }
                        if(Rs2Bank.count("Green dragonhide") > 10){
                            whattotan = "Green dragonhide";
                            product = "Green dragon leather";
                        }
                        if(Rs2Bank.count("Blue dragonhide") > 10){
                            whattotan = "Blue dragonhide";
                            product = "Blue dragon leather";
                        }
                        if(Rs2Bank.count("Red dragonhide") > 10){
                            whattotan = "Red dragonhide";
                            product = "Red dragon leather";
                        }
                        if(Rs2Bank.count("Black dragonhide") > 10){
                            whattotan = "Black dragonhide";
                            product = "Black dragon leather";
                        }
                        if(whattotan.equals("Unset")){
                            Microbot.log("We have nothing to tan! "+whattotan);
                            shutdown();
                        }
                    }
                }
            }
        }
    }
}