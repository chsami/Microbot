package net.runelite.client.plugins.microbot.f2pAccountBuilder;

import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.smelting.enums.Bars;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class f2pAccountBuilderScript extends Script {

    public static boolean test = false;
    public volatile boolean shouldThink = true;
    public volatile long scriptStartTime = System.currentTimeMillis();
    private long howLongUntilThink = Rs2Random.between(10,40);

    private boolean shouldWoodcut = false;
    private boolean shouldMine = false;
    private boolean shouldFish = false;
    private boolean shouldSmelt = false;
    private boolean shouldFiremake = false;
    private boolean shouldCook = false;
    private boolean shouldCraft = false;

    private WorldPoint chosenSpot = null;



    public boolean run(f2pAccountBuilderConfig config) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                thinkVoid(); // decide what we're going to do.

                thinkBasedOnTime(); // Change our activity if it's been X amount of time.


                //Skilling
                woodCutting();
                mining();
                fishing();
                smelting();
                firemake();
                cook();
                craft();

                //Skilling



                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    public void thinkVoid(){
        if(shouldThink){
            //set our booleans to false
            this.shouldWoodcut = false;
            this.shouldMine = false;
            this.shouldFish = false;
            this.shouldSmelt = false;
            this.shouldFiremake = false;
            this.shouldCook = false;
            this.shouldCraft = false;

            this.chosenSpot = null;

            int random = Rs2Random.between(0,1000);
            if(random <= 100){
                Microbot.log("We're going woodcutting.");
                shouldWoodcut = true;
                shouldThink = false;
                return;
            }
            if(random > 100 && random <= 200){
                Microbot.log("We're going mining.");
                shouldMine = true;
                shouldThink = false;
                return;
            }
            if(random > 200 && random <= 300){
                Microbot.log("We're going fishing.");
                shouldFish = true;
                shouldThink = false;
                return;
            }
            if(random > 300 && random <= 400){
                Microbot.log("We're going smelting.");
                shouldSmelt = true;
                shouldThink = false;
                return;
            }
            if(random > 400 && random <= 500){
                Microbot.log("We're going firemaking.");
                shouldFiremake = true;
                shouldThink = false;
                return;
            }
            if(random > 500 && random <= 600){
                Microbot.log("We're going to cook.");
                shouldCook = true;
                shouldThink = false;
                return;
            }
            if(random > 600 && random <= 700){
                Microbot.log("We're going to craft.");
                shouldCraft = true;
                shouldThink = false;
                return;
            }

        }
    }

    public void thinkBasedOnTime(){
            long currentTime = System.currentTimeMillis();
            if (currentTime - scriptStartTime >= howLongUntilThink * 60 * 1000) {
                Microbot.log("Changing activity it's been "+howLongUntilThink+" minutes");

                shouldThink = true;

                scriptStartTime = currentTime;

                howLongUntilThink = Rs2Random.between(8,40);

                Microbot.log("We'll change activity again in "+howLongUntilThink+" minutes");
            }
    }

    public void goToBankandGrabAnItem(String item, int howMany){
        if(!Rs2Bank.isOpen()){
            Rs2Bank.walkToBankAndUseBank();
            sleepUntil(()-> Rs2Bank.isOpen(), Rs2Random.between(2000,5000));
        }
        if(Rs2Bank.isOpen()){
            if(Rs2Bank.getBankItem(item, true) != null){
                if(Rs2Inventory.getEmptySlots() < 10){
                    Rs2Bank.depositAll();
                    sleepUntil(() -> Rs2Inventory.getEmptySlots() > 10, Rs2Random.between(2000, 5000));
                }
                if(!Rs2Inventory.contains(item)){
                    Rs2Bank.withdrawX(item, howMany, true);
                    sleepUntil(() -> Rs2Inventory.contains(item), Rs2Random.between(2000, 5000));
                }
            } else {
                //We need to buy the item
                if(Rs2Bank.getBankItem("Coins") != null){
                    if(Rs2Bank.getBankItem("Coins").getQuantity() >= 1000){
                        if(Rs2Inventory.get("Coins") == null || Rs2Inventory.get("Coins").getQuantity() < 1000) {
                            Rs2Bank.withdrawX("Coins", Rs2Random.between(900,1100));
                            sleepUntil(() -> Rs2Inventory.contains("Coins"), Rs2Random.between(2000, 5000));
                        }
                    }
                } else {
                    Microbot.log("Can't buy the "+item+" changing activity.");
                    shouldThink = true;
                    return;
                }

                if(!Rs2Inventory.contains(item) && Rs2Inventory.get("Coins").getQuantity() >= 1000){
                    openGEandBuyItem(item, howMany);
                }
            }
        }
    }

    public void walkToBankAndOpenIt(){
        if (!Rs2Bank.isOpen()) {
            if (Rs2Bank.walkToBank()) {
                if (Rs2Npc.interact(Rs2Npc.getNearestNpcWithAction("Bank"), "Bank") || Rs2GameObject.interact(Rs2GameObject.getGameObject(it->it!=null&&it.getId() == ObjectID.BANKBOOTH &&it.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) < 15), "Bank")) {
                    sleepUntil(Rs2Bank::isOpen, Rs2Random.between(3000, 6000));
                }
            }
        }
    }

    public void openGEandBuyItem(String item, int howMany){
        if(Rs2Bank.isOpen()){
            Rs2Bank.closeBank();
            sleepUntil(()-> !Rs2Bank.isOpen(), Rs2Random.between(2000,5000));
        }
        if(Rs2Player.getWorldLocation().distanceTo(BankLocation.GRAND_EXCHANGE.getWorldPoint()) > 7){
            Rs2Walker.walkTo(BankLocation.GRAND_EXCHANGE.getWorldPoint());
        }
        if(!Rs2GrandExchange.isOpen()){
            Rs2GrandExchange.openExchange();
            sleepUntil(()-> Rs2GrandExchange.isOpen(), Rs2Random.between(2000,5000));
        }
        if(Rs2GrandExchange.isOpen()){

            if(Rs2GrandExchange.buyItemAboveXPercent(item, howMany, 20)){
                sleepUntil(()-> Rs2GrandExchange.hasFinishedBuyingOffers(), Rs2Random.between(2000,5000));
            }

            if(Rs2GrandExchange.hasFinishedBuyingOffers()){
                Rs2GrandExchange.collectToInventory();
                sleepUntil(()-> Rs2Inventory.contains(item), Rs2Random.between(2000,5000));
            }
        }
    }

    //skilling

    public void craft(){
        if(shouldCraft){
            String craftingMaterial = "Unknown";
            String craftingProduct = "Unknown";
            int craftingLvl = Rs2Player.getRealSkillLevel(Skill.CRAFTING);
            if(craftingLvl < 7){craftingMaterial = "Leather"; craftingProduct = "Leather gloves";}
            if(craftingLvl >= 7 && craftingLvl < 9){craftingMaterial = "Leather"; craftingProduct = "Leather boots";}
            if(craftingLvl >= 9 && craftingLvl < 11){craftingMaterial = "Leather"; craftingProduct = "Leather cowl";}
            if(craftingLvl >= 11 && craftingLvl < 14){craftingMaterial = "Leather"; craftingProduct = "Leather vambraces";}
            if(craftingLvl >= 14 && craftingLvl < 18){craftingMaterial = "Leather"; craftingProduct = "Leather body";}
            if(craftingLvl >= 18 && craftingLvl < 28){craftingMaterial = "Leather"; craftingProduct = "Leather chaps";}


            if(chosenSpot == null){
                chosenSpot = BankLocation.GRAND_EXCHANGE.getWorldPoint();
            }

            if(chosenSpot != null){
                if(Rs2Player.getWorldLocation().distanceTo(chosenSpot) > 12){
                    Rs2Walker.walkTo(chosenSpot);
                } else {
                    if(Rs2Inventory.contains(craftingMaterial) && Rs2Inventory.contains("Thread") && Rs2Inventory.contains("Needle")){
                        if(Rs2Bank.isOpen()){
                            Rs2Bank.closeBank();
                            sleepUntil(()-> !Rs2Bank.isOpen(), Rs2Random.between(2000,5000));
                        }

                        Rs2Inventory.combine("Needle", "Leather");
                        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                        sleepThroughMulipleAnimations();
                    }
                    if(!Rs2Inventory.contains(craftingMaterial) || Rs2Inventory.count(craftingMaterial) < 3 || !Rs2Inventory.contains("Thread") || !Rs2Inventory.contains("Needle")  || Rs2Inventory.contains(it->it!=null&&it.isNoted())){
                        walkToBankAndOpenIt();
                        if(Rs2Inventory.contains(craftingProduct) || Rs2Inventory.isFull() || Rs2Inventory.contains(it->it!=null&&it.isNoted())){
                            Rs2Bank.depositAll();
                            sleepUntil(()-> Rs2Inventory.isEmpty(), Rs2Random.between(2000,5000));
                        }
                        if(!Rs2Inventory.contains("Thread")){
                            if(Rs2Bank.isOpen()){
                                if(Rs2Bank.getBankItem("Thread", true) != null && Rs2Bank.getBankItem("Thread", true).getQuantity() > 10){
                                    Rs2Bank.withdrawAll("Thread", true);
                                    sleepUntil(()-> Rs2Inventory.contains("Thread"), Rs2Random.between(2000,5000));
                                } else {
                                    openGEandBuyItem("Thread", Rs2Random.between(100,200));
                                }
                            }
                        }
                        if(!Rs2Inventory.contains("Needle")){
                            if(Rs2Bank.isOpen()){
                                if(Rs2Bank.getBankItem("Needle", true) != null){
                                    Rs2Bank.withdrawAll("Needle", true);
                                    sleepUntil(()-> Rs2Inventory.contains("Needle"), Rs2Random.between(2000,5000));
                                } else {
                                    openGEandBuyItem("Needle", 1);
                                }
                            }
                        }
                        if(Rs2Inventory.contains("Needle") && Rs2Inventory.contains("Thread") && !Rs2Inventory.isFull() && !Rs2Inventory.contains(craftingMaterial) || Rs2Inventory.count(craftingMaterial) < 3){
                            if(Rs2Bank.isOpen()){
                                if(Rs2Bank.getBankItem(craftingMaterial, true) != null){
                                    Rs2Bank.withdrawAll(craftingMaterial, true);
                                    Rs2Inventory.waitForInventoryChanges(5000);
                                } else {
                                    openGEandBuyItem(craftingMaterial, Rs2Random.between(100,300));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void cook(){
        if(shouldCook){
            String whatToCook = "Unknown";
            int cookingLvl = Rs2Player.getRealSkillLevel(Skill.COOKING);
            if(cookingLvl < 15){whatToCook = "Raw chicken";}
            if(cookingLvl >= 15){whatToCook = "SwitchToFishing";}

            if(whatToCook.equals("SwitchToFishing")){
                Microbot.log("We're going fishing.");
                shouldFish = true;
                shouldCook = false;
                shouldThink = false;
                chosenSpot = null;
                return;
            }


                if(chosenSpot == null){
                    chosenSpot = new WorldPoint(3274,3180,0);
                }

                if(chosenSpot != null){
                    if(Rs2Player.getWorldLocation().distanceTo(chosenSpot) > 12){
                        Rs2Walker.walkTo(chosenSpot);
                    } else {
                        if(Rs2Inventory.contains(whatToCook)){
                            GameObject range = Rs2GameObject.getGameObject("Range");
                            if (range != null) {
                                if (!Rs2Camera.isTileOnScreen(range.getLocalLocation())) {
                                    Rs2Camera.turnTo(range.getLocalLocation());
                                    return;
                                }
                                Rs2Inventory.useItemOnObject(Rs2Inventory.get(whatToCook).getId(), range.getId());
                                sleepUntil(() -> !Rs2Player.isMoving() && Rs2Widget.findWidget("like to cook?", null, false) != null);

                                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                                sleepThroughMulipleAnimations();
                            }
                        }
                        if(!Rs2Inventory.contains(whatToCook)){
                            walkToBankAndOpenIt();

                            if (Rs2Bank.isOpen()) {
                                if(Rs2Inventory.contains("Cooked chicken")){
                                    Rs2Bank.depositAll();
                                    sleepUntil(()->!Rs2Inventory.contains("Cooked chicken"), Rs2Random.between(3000, 6000));
                                }
                                if(!Rs2Inventory.contains(whatToCook) && !Rs2Inventory.isFull()){
                                    if(Rs2Bank.getBankItem(whatToCook, true) != null) {
                                        Rs2Bank.withdrawAll(whatToCook, true);
                                        String cooked = whatToCook;
                                        sleepUntil(() -> !Rs2Inventory.contains(cooked), Rs2Random.between(3000, 6000));
                                    } else {
                                        openGEandBuyItem(whatToCook, Rs2Random.between(100,200));
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }

    public void woodCutting(){
        if(shouldWoodcut){
            String treeToChop = "Unknown";
            String axeToUse = "Unknown";
            int wcLvl = Rs2Player.getRealSkillLevel(Skill.WOODCUTTING);
            if(wcLvl < 15){axeToUse = "Iron axe"; treeToChop = "Tree";}
            if(wcLvl >= 15 && wcLvl < 30){axeToUse = "Steel axe"; treeToChop = "Oak tree";}
            if(wcLvl == 30){axeToUse = "Mithril axe"; treeToChop = "Willow tree";}
            if(wcLvl >= 31 && wcLvl < 41){axeToUse = "Adamant axe"; treeToChop = "Willow tree";}
            if(wcLvl >= 41){axeToUse = "Rune axe"; treeToChop = "Willow tree";}
            String finalaxe = axeToUse;

            if(Rs2Inventory.contains(axeToUse) || Rs2Equipment.contains(it->it!=null&&it.getName().equals(finalaxe))){

                if(chosenSpot == null){
                    WorldPoint spot1 = null;
                    WorldPoint spot2 = null;
                    if(treeToChop.equals("Tree")) {
                        spot1 = new WorldPoint(3157, 3456, 0);
                        spot2 = new WorldPoint(3164, 3406, 0);
                    }
                    if(treeToChop.equals("Oak tree")) {
                        spot1 = new WorldPoint(3164, 3419, 0);
                        spot2 = new WorldPoint(3127, 3433, 0);
                    }
                    if(treeToChop.equals("Willow tree")) {
                        spot1 = new WorldPoint(3087, 3236, 0);
                        spot2 = new WorldPoint(3087, 3236, 0);
                    }
                    if (Rs2Random.between(0, 100) <= 50) {
                        chosenSpot = spot1;
                    } else {
                        chosenSpot = spot2;
                    }
                }

                if(chosenSpot != null){
                    if(Rs2Player.getWorldLocation().distanceTo(chosenSpot) > 12){
                        Rs2Walker.walkTo(chosenSpot);
                    } else {
                        if(Rs2Inventory.isFull()){
                            walkToBankAndOpenIt();

                            if(Rs2Bank.isOpen()){
                                Rs2Bank.depositAllExcept(axeToUse);
                                sleepUntil(()-> !Rs2Inventory.isFull(), Rs2Random.between(2000,5000));
                            }
                            if(Rs2Bank.isOpen()&&!Rs2Inventory.isFull()){
                                Rs2Bank.closeBank();
                            }
                        } else {
                            GameObject ourTree = Rs2GameObject.getGameObject(treeToChop, true);
                            if(ourTree!=null){
                               if(!Rs2Player.isAnimating()){
                                   if(Rs2GameObject.interact(ourTree, "Chop down")){
                                       sleepUntil(()-> !Rs2Player.isAnimating(), Rs2Random.between(20000,50000));
                                       sleep(0,500);
                                   }
                               }
                            }
                        }
                    }
                }

            } else {
                goToBankandGrabAnItem(axeToUse, 1);
            }
        }
    }

    public void mining(){
        if(shouldMine){
            String rockToMine = "Unknown";
            String axeToUse = "Unknown";
            int miningLvl = Rs2Player.getRealSkillLevel(Skill.MINING);
            if(miningLvl < 21){axeToUse = "Iron pickaxe"; rockToMine = "Tin rocks";}
            if(miningLvl >= 21 && miningLvl < 31){axeToUse = "Mithril pickaxe"; rockToMine = "Iron rocks";}
            if(miningLvl >= 31 && miningLvl < 41){axeToUse = "Adamant pickaxe"; rockToMine = "Iron rocks";}
            if(miningLvl >= 41){axeToUse = "Rune pickaxe"; rockToMine = "Iron rocks";}
            String finalaxe = axeToUse;

            if(Rs2Inventory.contains(axeToUse) || Rs2Equipment.contains(it->it!=null&&it.getName().equals(finalaxe))){

                if(chosenSpot == null){
                    WorldPoint spot1 = null;
                    WorldPoint spot2 = null;
                    if(rockToMine.equals("Tin rocks")) {
                        spot1 = new WorldPoint(3183, 3374, 0);
                        spot2 = new WorldPoint(3283, 3362, 0);
                    }
                    if(rockToMine.equals("Iron rocks")) {
                        spot1 = new WorldPoint(3174, 3366, 0);
                        spot2 = new WorldPoint(3296, 3309, 0);
                    }
                    if (Rs2Random.between(0, 100) <= 50) {
                        chosenSpot = spot1;
                    } else {
                        chosenSpot = spot2;
                    }

                }

                if(chosenSpot != null){
                    if(Rs2Player.getWorldLocation().distanceTo(chosenSpot) > 15){
                        Rs2Walker.walkTo(chosenSpot);
                    } else {
                        if(Rs2Inventory.isFull()){
                            if(rockToMine.equals("Tin rocks")) {
                                walkToBankAndOpenIt();

                                if (Rs2Bank.isOpen()) {
                                    Rs2Bank.depositAllExcept(axeToUse);
                                    sleepUntil(() -> !Rs2Inventory.isFull(), Rs2Random.between(2000, 5000));
                                }
                                if (Rs2Bank.isOpen() && !Rs2Inventory.isFull()) {
                                    Rs2Bank.closeBank();
                                }
                            }
                            if(rockToMine.equals("Iron rocks")) {
                                Rs2Inventory.dropAllExcept(axeToUse);
                            }
                        } else {
                            GameObject ourRock = Rs2GameObject.getGameObject(rockToMine);
                            if(ourRock!=null){
                                if(!Rs2Player.isAnimating()){
                                    if(Rs2GameObject.interact(ourRock, "Mine")){
                                        sleepUntil(()-> !Rs2Player.isAnimating() || ourRock == null, Rs2Random.between(20000,50000));
                                        sleep(0,500);
                                    }
                                }
                            }
                        }
                    }
                }

            } else {
                goToBankandGrabAnItem(axeToUse,1);
            }
        }
    }

    public void fishing(){
        String fishingAction = "Unknown";
        String fishingGear = "Unknown";
        int fishingLvl = Rs2Player.getRealSkillLevel(Skill.FISHING);
        if(fishingLvl < 20){fishingGear = "Small fishing net"; fishingAction = "Net";}
        if(fishingLvl >= 21){fishingGear = "Fly fishing rod"; fishingAction = "Lure";}
        String finalGear = fishingGear;

        if(shouldFish){
            if(Rs2Inventory.contains(it->it!=null&&it.getName().contains(finalGear))){

                if(chosenSpot == null){
                    WorldPoint spot1 = null;
                    WorldPoint spot2 = null;

                    if(fishingGear.equals("Small fishing net")){
                        spot1 = new WorldPoint(3241,3151,0);
                        spot2 = new WorldPoint(3241,3151,0);
                    }

                    if(fishingGear.equals("Fly fishing rod")){
                        spot1 = new WorldPoint(3104,3430,0);
                        spot2 = new WorldPoint(3104,3430,0);
                    }

                    if(Rs2Random.between(0,100) <=50){
                        chosenSpot = spot1;
                    } else {
                        chosenSpot = spot2;
                    }
                }

                if(chosenSpot != null){
                    if(Rs2Player.getWorldLocation().distanceTo(chosenSpot) > 15){
                        Rs2Walker.walkTo(chosenSpot);
                    } else {
                        if(Rs2Inventory.isFull()){
                            if(fishingGear.equals("Small fishing net")){
                                if(Rs2Inventory.dropAllExcept(fishingGear)){
                                    sleepUntil(()-> !Rs2Inventory.isFull(), Rs2Random.between(2000,5000));
                                }
                            }
                            if(fishingGear.equals("Fly fishing rod")){

                                if(!Rs2Inventory.contains("Feather")) {
                                    goToBankandGrabAnItem("Feather", Rs2Random.between(500,2000));
                                }

                                int cookingLvl = Rs2Player.getRealSkillLevel(Skill.COOKING);
                                if(cookingLvl < 15){
                                    if(Rs2Inventory.dropAllExcept(fishingGear, "Feather")){
                                        sleepUntil(()-> !Rs2Inventory.isFull(), Rs2Random.between(2000,5000));
                                    }
                                }
                                if(cookingLvl >= 15 && cookingLvl < 25){
                                    if(Rs2Inventory.contains("Raw trout")){
                                        cookFish(Rs2Inventory.get("Raw trout").getId());
                                        sleepThroughMulipleAnimations();
                                    }
                                    if(Rs2Inventory.dropAllExcept(fishingGear, "Feather")){
                                        sleepUntil(()-> !Rs2Inventory.isFull(), Rs2Random.between(2000,5000));
                                    }
                                }
                                if(cookingLvl >= 25){
                                    if(Rs2Random.between(0,100) < 50){
                                        if(Rs2Inventory.contains("Raw trout")){
                                            cookFish(Rs2Inventory.get("Raw trout").getId());
                                            sleepThroughMulipleAnimations();
                                        }
                                        if(Rs2Inventory.contains("Raw salmon")){
                                            cookFish(Rs2Inventory.get("Raw salmon").getId());
                                            sleepThroughMulipleAnimations();
                                        }
                                    } else {
                                        if(Rs2Inventory.contains("Raw salmon")){
                                            cookFish(Rs2Inventory.get("Raw salmon").getId());
                                            sleepThroughMulipleAnimations();
                                        }
                                        if(Rs2Inventory.contains("Raw trout")){
                                            cookFish(Rs2Inventory.get("Raw trout").getId());
                                            sleepThroughMulipleAnimations();
                                        }
                                    }
                                    if(Rs2Inventory.dropAllExcept(fishingGear, "Feather")){
                                        sleepUntil(()-> !Rs2Inventory.isFull(), Rs2Random.between(2000,5000));
                                    }
                                }
                            }

                        } else {
                            Rs2NpcModel ourFishingSpot = Rs2Npc.getNpc("Fishing spot");
                            if(ourFishingSpot!=null){
                                if(!Rs2Player.isAnimating()){
                                    if(Rs2Npc.interact(ourFishingSpot, fishingAction)){
                                        sleepUntil(()-> !Rs2Player.isAnimating() || ourFishingSpot == null, Rs2Random.between(20000,50000));
                                        sleep(0,500);
                                    }
                                }
                            }
                        }
                    }
                }

            } else {
                goToBankandGrabAnItem(fishingGear, 1);
            }
        }
    }

    public void cookFish(int fishesID){
        if (Rs2Inventory.contains(fishesID)) {
            Rs2Inventory.useItemOnObject(fishesID, 43475);
            sleepUntil(() -> !Rs2Player.isMoving() && Rs2Widget.findWidget("How many would you like to cook?", null, false) != null, 5000);
            sleep(180, 540);
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        }
    }

    public void smelting(){
        if(shouldSmelt){

                if(chosenSpot == null){
                    WorldPoint spot1 = new WorldPoint(3106,3498,0);
                    WorldPoint spot2 = new WorldPoint(3106,3498,0);
                    if(Rs2Random.between(0,100) <=50){
                        chosenSpot = spot1;
                    } else {
                        chosenSpot = spot2;
                    }
                }

                if(chosenSpot != null){
                    if(Rs2Player.getWorldLocation().distanceTo(chosenSpot) > 15){
                        Rs2Walker.walkTo(chosenSpot);
                    } else {
                        //smelting bronze or silver
                        boolean smeltingBronze = false;
                        boolean smeltingSilver = false;

                        if (Rs2Player.getRealSkillLevel(Skill.SMITHING) >= 20) {
                            smeltingSilver = true;
                        } else {
                            smeltingBronze = true;
                        }

                        if (smeltingSilver) {
                            if (Rs2Inventory.contains("Silver bar") || !Rs2Inventory.contains("Silver ore")) {
                                walkToBankAndOpenIt();

                                if (Rs2Bank.isOpen()) {
                                    if (Rs2Inventory.contains("Silver bar") || Rs2Inventory.contains(it->it!=null&&it.isNoted())) {
                                        int random = Rs2Random.between(0, 100);
                                        if (random <= 75) {
                                            Rs2Bank.depositAll();
                                            sleepUntil(() -> !Rs2Inventory.isFull(), Rs2Random.between(2000, 5000));
                                        } else {
                                            Rs2Bank.depositAll("Silver bar", true);
                                            sleepUntil(() -> !Rs2Inventory.isFull(), Rs2Random.between(2000, 5000));
                                        }
                                    }
                                    if (!Rs2Inventory.contains("Silver bar") && !Rs2Inventory.isFull()) {
                                        if (Rs2Bank.getBankItem("Silver ore", true) != null) {
                                            Rs2Bank.withdrawAll("Silver ore", true);
                                            sleepUntil(() -> Rs2Inventory.isFull(), Rs2Random.between(2000, 5000));
                                        } else {
                                            //we need to buy silver ore
                                            openGEandBuyItem("Silver ore", Rs2Random.between(100,200));
                                        }
                                    }
                                    if (Rs2Inventory.contains("Silver ore")) {
                                        // walk to the initial position (near furnace)
                                        smeltTheBar(Bars.SILVER);
                                    }
                                }
                            }
                        }

                        if (smeltingBronze) {
                            if (Rs2Inventory.contains("Bronze bar") || !Rs2Inventory.contains("Copper ore") || !Rs2Inventory.contains("Tin ore")) {
                                walkToBankAndOpenIt();

                                if (Rs2Bank.isOpen()) {
                                    if (Rs2Inventory.contains("Bronze bar") || Rs2Inventory.isFull() || Rs2Inventory.contains(it->it!=null&&it.isNoted())) {
                                        int random = Rs2Random.between(0, 100);
                                        if (random <= 75) {
                                            Rs2Bank.depositAll();
                                            sleepUntil(() -> !Rs2Inventory.isFull(), Rs2Random.between(2000, 5000));
                                        } else {
                                            Rs2Bank.depositAll("Bronze bar", true);
                                            sleepUntil(() -> !Rs2Inventory.isFull(), Rs2Random.between(2000, 5000));
                                        }
                                    }
                                    if ((!Rs2Inventory.contains("Copper ore") && !Rs2Inventory.contains("Tin ore")) && !Rs2Inventory.isFull()) {
                                        if (Rs2Bank.getBankItem("Copper ore") != null && Rs2Bank.getBankItem("Tin ore") != null) {
                                            if(Rs2Bank.getBankItem("Copper ore").getQuantity() < 14 || Rs2Bank.getBankItem("Tin ore").getQuantity() < 14){
                                                outOfOre();
                                                return;
                                            }
                                            int random = Rs2Random.between(0, 100);
                                            if (random <= 50) {
                                                if (Rs2Inventory.count("Copper ore") < 14) {
                                                    Rs2Bank.withdrawX("Copper ore", 14);
                                                    sleepUntil(() -> Rs2Inventory.count("Copper ore") >= 14, Rs2Random.between(2000, 5000));
                                                }
                                                if (Rs2Inventory.count("Tin ore") < 14) {
                                                    Rs2Bank.withdrawX("Tin ore", 14);
                                                    sleepUntil(() -> Rs2Inventory.count("Tin ore") >= 14, Rs2Random.between(2000, 5000));
                                                }
                                            } else {
                                                if (Rs2Inventory.count("Tin ore") < 14) {
                                                    Rs2Bank.withdrawX("Tin ore", 14);
                                                    sleepUntil(() -> Rs2Inventory.count("Tin ore") >= 14, Rs2Random.between(2000, 5000));
                                                }
                                                if (Rs2Inventory.count("Copper ore") < 14) {
                                                    Rs2Bank.withdrawX("Copper ore", 14);
                                                    sleepUntil(() -> Rs2Inventory.count("Copper ore") >= 14, Rs2Random.between(2000, 5000));
                                                }
                                            }
                                        } else {
                                            //we need to buy copper ore
                                            outOfOre();
                                        }
                                    }
                                    if ((Rs2Inventory.contains("Copper ore") && Rs2Inventory.contains("Tin ore"))) {
                                        // walk to the initial position (near furnace)
                                        smeltTheBar(Bars.BRONZE);
                                    }
                                }
                            }

                        }
                    }
                }

        }
    }

    public void outOfOre(){
        //we need to buy copper ore
        if (Rs2Bank.getBankItem("Tin ore") == null || Rs2Bank.getBankItem("Tin ore").getQuantity() < 14) {
            this.shouldThink = true;
            Microbot.log("We need to mine more tin");
            return;
        }
        if (Rs2Bank.getBankItem("Copper ore") == null || Rs2Bank.getBankItem("Copper ore").getQuantity() < 14) {
            openGEandBuyItem("Copper ore", Rs2Random.between(100,200));
        }
    }

    public void smeltTheBar(Bars bar){
        // walk to the initial position (near furnace)
        if (chosenSpot.distanceTo(Rs2Player.getWorldLocation()) > 4) {
            if (Rs2Bank.isOpen())
                Rs2Bank.closeBank();
            Rs2Walker.walkTo(chosenSpot, 4);
        }

        // interact with the furnace until the smelting dialogue opens in chat, click the selected bar icon
        GameObject furnace = Rs2GameObject.findObject("furnace", true, 10, false, chosenSpot);
        if (furnace != null) {
            Rs2GameObject.interact(furnace, "smelt");
            Rs2Widget.sleepUntilHasWidgetText("What would you like to smelt?", 270, 5, false, 5000);
            Rs2Widget.clickWidget(bar.getName());
            Rs2Widget.sleepUntilHasNotWidgetText("What would you like to smelt?", 270, 5, false, 5000);
            Rs2Antiban.actionCooldown();
            Rs2Antiban.takeMicroBreakByChance();
        }

        sleepThroughMulipleAnimations();
    }

    public void firemake(){
        if(shouldFiremake){

            String logsToBurn = "Unknown";
            int fireMakingLvl = Rs2Player.getRealSkillLevel(Skill.FIREMAKING);
            if(fireMakingLvl < 15){ logsToBurn = "Logs";}
            if(fireMakingLvl >= 15 && fireMakingLvl < 30){ logsToBurn = "Oak logs";}
            if(fireMakingLvl >= 30){ logsToBurn = "Willow logs";}

                if(chosenSpot == null){
                    WorldPoint spot1 = new WorldPoint(3171,3495,0);
                    WorldPoint spot2 = new WorldPoint(3171,3484,0);
                    if(Rs2Random.between(0,100) <=50){
                        chosenSpot = spot1;
                    } else {
                        chosenSpot = spot2;
                    }
                }

                if(chosenSpot != null){
                    if(Rs2Player.getWorldLocation().distanceTo(chosenSpot) > 15){
                        Rs2Walker.walkTo(chosenSpot);
                    } else {
                        if(!Rs2Inventory.contains(logsToBurn) || !Rs2Inventory.contains(ItemID.TINDERBOX) || Rs2Inventory.contains(it->it!=null&&it.isNoted())){
                            walkToBankAndOpenIt();

                            if(Rs2Bank.isOpen()){
                                if(Rs2Inventory.contains(it->it!=null&&it.isNoted())){
                                    Rs2Bank.depositAll();
                                    sleepUntil(()-> Rs2Inventory.isEmpty(), Rs2Random.between(2000,5000));
                                }
                                if(!Rs2Inventory.contains(ItemID.TINDERBOX)){
                                    if(Rs2Bank.getBankItem(ItemID.TINDERBOX) != null){
                                        Rs2Bank.withdrawOne(ItemID.TINDERBOX);
                                        sleepUntil(()-> Rs2Inventory.contains(ItemID.TINDERBOX), Rs2Random.between(2000,5000));
                                    } else {
                                        openGEandBuyItem("Tinderbox", 1);
                                    }
                                }
                                if(!Rs2Inventory.contains(logsToBurn)){
                                    if(Rs2Bank.getBankItem(logsToBurn, true) != null){
                                        Rs2Bank.withdrawAll(logsToBurn, true);
                                        String logs = logsToBurn;
                                        sleepUntil(()-> Rs2Inventory.contains(logs), Rs2Random.between(2000,5000));
                                    } else {
                                        openGEandBuyItem(logsToBurn, Rs2Random.between(100,300));
                                    }
                                }
                            }
                        }
                        if(Rs2Inventory.contains(logsToBurn) && Rs2Inventory.contains(ItemID.TINDERBOX)){
                            if(Rs2Bank.isOpen()){
                                Rs2Bank.closeBank();
                                sleepUntil(()-> Rs2Bank.isOpen(), Rs2Random.between(2000,5000));
                            }

                            if(Rs2Player.isStandingOnGameObject()){
                                Microbot.log("We're standing on an object, moving.");
                                if(Rs2Player.getWorldLocation().equals(chosenSpot)){
                                    //we're standing on the starting tile and there's already a fire here. Grab a new starting tile.
                                    chosenSpot = null;
                                }
                                if(Rs2Player.distanceTo(chosenSpot) > 4){
                                    Rs2Walker.walkTo(chosenSpot);
                                } else {
                                    Rs2Walker.walkCanvas(chosenSpot);
                                }
                            }

                            TileObject geBooth = Rs2GameObject.getTileObject("Grand Exchange booth", true);
                            if(geBooth.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= 2){
                                Microbot.log("We're too close to the GE, moving.");
                                if(Rs2Player.distanceTo(chosenSpot) > 4){
                                    Rs2Walker.walkTo(chosenSpot);
                                } else {
                                    Rs2Walker.walkCanvas(chosenSpot);
                                }
                            }

                            Rs2Inventory.use("tinderbox");
                            sleepUntil(Rs2Inventory::isItemSelected);
                            int id = Rs2Inventory.get(logsToBurn).getId();
                            Rs2Inventory.useLast(id);

                            sleepThroughMulipleAnimations();
                        }
                    }
                }

        }
    }

    //skilling

    public void sleepThroughMulipleAnimations(){
        boolean stillAnimating = true;
        while (stillAnimating) {
            if (!super.isRunning()) {
                break;
            }
            if (Rs2Player.isAnimating()) {
                sleepUntil(() -> !Rs2Player.isAnimating(), Rs2Random.between(3000, 5000));
            }
            if (!Rs2Player.isAnimating()) {
                sleepUntil(() -> Rs2Player.isAnimating(), Rs2Random.between(3000, 5000));
            }
            if (!Rs2Player.isAnimating()) {
                stillAnimating = false;
            }
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}