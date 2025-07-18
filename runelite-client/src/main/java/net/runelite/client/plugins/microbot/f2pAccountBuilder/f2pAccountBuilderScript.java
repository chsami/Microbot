package net.runelite.client.plugins.microbot.f2pAccountBuilder;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.smelting.enums.Bars;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.item.Rs2ItemManager;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.http.api.worlds.World;

import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;


public class f2pAccountBuilderScript extends Script {

    public static boolean test = false;
    public volatile boolean shouldThink = true;
    public volatile long scriptStartTime = System.currentTimeMillis();
    private long howLongUntilThink = Rs2Random.between(10,40);
    private int totalGP = 0;

    private boolean shouldWoodcut = false;
    private boolean shouldMine = false;
    private boolean shouldFish = false;
    private boolean shouldSmelt = false;
    private boolean shouldFiremake = false;
    private boolean shouldCook = false;
    private boolean shouldCraft = false;
    private boolean shouldSellItems = false;

    private boolean weChangeActivity = false;

    private WorldPoint chosenSpot = null;



    public boolean run(f2pAccountBuilderConfig config) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if(BreakHandlerScript.breakIn != -1 && BreakHandlerScript.isBreakActive()) return;

                long startTime = System.currentTimeMillis();

                thinkVoid(); // decide what we're going to do.

                thinkBasedOnTime(); // Change our activity if it's been X amount of time.

                handleBreaks();

                getBuyAndEquipP2PTeles();

                //Skilling
                woodCutting();
                mining();
                fishing();
                smelting();
                firemake();
                cook();
                craft();

                //Skilling

                sellItems();


                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    public void handleBreaks(){
        if(BreakHandlerScript.breakIn != -1 && BreakHandlerScript.breakIn < 30) {
            if (Microbot.loggedIn) {
                Rs2Player.logout();
                sleepUntil(() -> !Microbot.loggedIn, Rs2Random.between(2000, 5000));
            }
            sleepUntil(() -> BreakHandlerScript.isBreakActive(), Rs2Random.between(30000, 60000));
        }
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

            this.shouldSellItems = false;

            this.chosenSpot = null;
            this.weChangeActivity = true;

            int random = Rs2Random.between(0,1000);
            if(random <= 100){
                Microbot.log("We're going woodcutting.");
                Rs2Antiban.antibanSetupTemplates.applyWoodcuttingSetup();
                Rs2Antiban.setActivity(Activity.GENERAL_WOODCUTTING);
                shouldWoodcut = true;
                shouldThink = false;
                return;
            }
            if(random > 100 && random <= 200){
                Microbot.log("We're going mining.");
                Rs2Antiban.antibanSetupTemplates.applyMiningSetup();
                Rs2Antiban.setActivity(Activity.GENERAL_MINING);
                shouldMine = true;
                shouldThink = false;
                return;
            }
            if(random > 200 && random <= 300){
                Microbot.log("We're going fishing.");
                Rs2Antiban.antibanSetupTemplates.applyFishingSetup();
                Rs2Antiban.setActivity(Activity.GENERAL_FISHING);
                shouldFish = true;
                shouldThink = false;
                return;
            }
            if(random > 300 && random <= 400){
                Microbot.log("We're going smelting.");
                Rs2Antiban.antibanSetupTemplates.applySmithingSetup();
                Rs2Antiban.setActivity(Activity.SMELTING_BRONZE_BARS);
                shouldSmelt = true;
                shouldThink = false;
                return;
            }
            if(random > 400 && random <= 500){
                Microbot.log("We're going firemaking.");
                Rs2Antiban.antibanSetupTemplates.applyFiremakingSetup();
                Rs2Antiban.setActivity(Activity.GENERAL_FIREMAKING);
                shouldFiremake = true;
                shouldThink = false;
                return;
            }
            if(random > 500 && random <= 600){
                Microbot.log("We're going to cook.");
                Rs2Antiban.antibanSetupTemplates.applyCookingSetup();
                Rs2Antiban.setActivity(Activity.GENERAL_COOKING);
                shouldCook = true;
                shouldThink = false;
                return;
            }
            if(random > 600 && random <= 700){
                Microbot.log("We're going to craft.");
                Rs2Antiban.antibanSetupTemplates.applyCraftingSetup();
                Rs2Antiban.setActivity(Activity.GENERAL_CRAFTING);
                shouldCraft = true;
                shouldThink = false;
                return;
            }
            if(random > 700 && random <= 800){
                Microbot.log("We're going to sell what we have.");
                Rs2Antiban.antibanSetupTemplates.applyGeneralBasicSetup();
                shouldSellItems = true;
                shouldThink = false;
                return;
            }

        }
    }

    public void closeTheBank(){
        if(Rs2Bank.isOpen()){
            Rs2Bank.closeBank();
            sleepUntil(()-> !Rs2Bank.isOpen(), Rs2Random.between(2000,5000));
            sleepHumanReaction();
        }
    }

    public void getBuyAndEquipP2PTeles(){
        if(Rs2Player.isInMemberWorld()){
            if(!Rs2Equipment.isWearing(it->it!=null&&it.getName().contains("Amulet of glory("))
                    || !Rs2Equipment.isWearing(it->it!=null&&it.getName().contains("Ring of wealth ("))
                        || !Rs2Equipment.isWearing(it->it!=null&&it.getName().contains("Combat bracelet("))){
                Microbot.log("We need our teleports");

                this.weChangeActivity = true;

                if(!Rs2Equipment.isWearing(it->it!=null&&it.getName().contains("Amulet of glory("))){
                    goToBankandGrabAnItem("Amulet of glory(6)", 1);
                }

                if(!Rs2Equipment.isWearing(it->it!=null&&it.getName().contains("Ring of wealth ("))){
                    goToBankandGrabAnItem("Ring of wealth (5)", 1);
                }

                if(!Rs2Equipment.isWearing(it->it!=null&&it.getName().contains("Combat bracelet("))){
                    goToBankandGrabAnItem("Combat bracelet(6)", 1);
                }

                if(Rs2GrandExchange.isOpen()){
                    Rs2GrandExchange.closeExchange();
                    sleepUntil(()-> !Rs2GrandExchange.isOpen(), Rs2Random.between(2000,5000));
                    sleepHumanReaction();
                }

                if(Rs2Bank.isOpen()){
                    Rs2Bank.closeBank();
                    sleepUntil(()-> !Rs2Bank.isOpen(), Rs2Random.between(2000,5000));
                    sleepHumanReaction();
                }

                if(Rs2Inventory.contains(it->it!=null&&it.getName().contains("Amulet of glory(")) && !Rs2Equipment.isWearing(it->it!=null&&it.getName().contains("Amulet of glory("))){
                    if(Rs2Inventory.interact("Amulet of glory(6)", "Wear")){
                        sleepUntil(()-> Rs2Equipment.isWearing(it->it!=null&&it.getName().equals("Amulet of glory(6)")), Rs2Random.between(2000,5000));
                        sleepHumanReaction();
                    }
                }

                if(Rs2Inventory.contains(it->it!=null&&it.getName().contains("Ring of wealth (")) && !Rs2Equipment.isWearing(it->it!=null&&it.getName().contains("Ring of wealth ("))){
                    if(Rs2Inventory.interact("Ring of wealth (5)", "Wear")){
                        sleepUntil(()-> Rs2Equipment.isWearing(it->it!=null&&it.getName().equals("Ring of wealth (5)")), Rs2Random.between(2000,5000));
                        sleepHumanReaction();
                    }
                }

                if(Rs2Inventory.contains(it->it!=null&&it.getName().contains("Combat bracelet(")) && !Rs2Equipment.isWearing(it->it!=null&&it.getName().contains("Combat bracelet("))){
                    if(Rs2Inventory.interact("Combat bracelet(6)", "Wear")){
                        sleepUntil(()-> Rs2Equipment.isWearing(it->it!=null&&it.getName().equals("Combat bracelet(6)")), Rs2Random.between(2000,5000));
                        sleepHumanReaction();
                    }
                }

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

    public void depositAllIfWeChangeActivity(){
        if(weChangeActivity){
            if(Rs2Bank.isOpen()) {
                if (!Rs2Inventory.isEmpty()) {
                    BreakHandlerScript.lockState.set(true);
                    while (!Rs2Inventory.isEmpty()) {
                        if (!super.isRunning()) {
                            break;
                        }
                        if (BreakHandlerScript.breakIn != -1 && BreakHandlerScript.breakIn < 30 || BreakHandlerScript.isBreakActive()) {
                            Rs2Bank.closeBank();
                            Microbot.log("We're going on break");
                            break;
                        }
                        Rs2Bank.depositAll();
                        sleepUntil(() -> Rs2Inventory.isEmpty(), Rs2Random.between(2000, 5000));
                        sleepHumanReaction();
                    }
                    BreakHandlerScript.lockState.set(false);
                }
                if(Rs2Inventory.isEmpty()){
                    weChangeActivity = false;
                }
            }
        }
    }

    public void goToBankandGrabAnItem(String item, int howMany){
        if(!Rs2Bank.isOpen()){
            this.walkToBankAndOpenIt();
            sleepUntil(()-> Rs2Bank.isOpen(), Rs2Random.between(2000,5000));
            sleepHumanReaction();
        }
        if(Rs2Bank.isOpen()){
            chosenSpot = null;

            if(Rs2Bank.getBankItem("Coins") != null){totalGP = Rs2Bank.getBankItem("Coins").getQuantity();}

            depositAllIfWeChangeActivity();

            if(Rs2Bank.getBankItem(item, true) != null && Rs2Bank.getBankItem(item, true).getQuantity() >= howMany){
                if(!Rs2Inventory.contains(item)){
                    Rs2Bank.withdrawX(item, howMany, true);
                    sleepUntil(() -> Rs2Inventory.contains(item), Rs2Random.between(2000, 5000));
                    sleepHumanReaction();
                }
            } else {
                Rs2ItemManager itemManager = new Rs2ItemManager();
                int itemsID = itemManager.getItemId(item); // Get our Items ID
                if(item.equals("Leather")){itemsID = ItemID.LEATHER;} //Needed because getItemID returns the wrong itemID for Leather
                int itemsPrice = itemManager.getGEPrice(itemsID); // Get our items price
                int totalCost = itemsPrice * howMany; // Get how much it'll cost all together
                double getTwentyPercent = totalCost * 0.30; // Get 20 percent of the total cost
                double addTwentyPercent = getTwentyPercent + totalCost; // add the 20% we calculated to the total cost
                totalCost = (int) addTwentyPercent; // convert it back to an int

                Microbot.log("This will cost "+totalCost+" and we have "+totalGP);

                if(totalCost > totalGP){
                    Microbot.log("We don't have enough GP :( re-rolling");
                    this.shouldThink = true;
                    return;
                }

                if(!Rs2Inventory.contains(item) && totalCost < totalGP){
                    openGEandBuyItem(item, howMany);
                }
            }
        }
    }

    public void walkToBankAndOpenIt() {
        if (!Rs2Bank.isOpen()) {

            if (Rs2Bank.walkToBank()) {
                Microbot.log("Walking to the bank");
            }
            BreakHandlerScript.lockState.set(true);
            while(!Rs2Bank.isOpen()) {
                if(!super.isRunning()){break;}
                if (BreakHandlerScript.breakIn != -1 && BreakHandlerScript.breakIn < 30 || BreakHandlerScript.isBreakActive()) {
                    Microbot.log("We're going on break");
                    break;
                }
                if (Rs2Bank.openBank()) {
                    Microbot.log("We opened the bank");
                    sleepUntil(()-> Rs2Bank.isOpen(), Rs2Random.between(10000,15000));
                    sleepHumanReaction();
                }
            }
            BreakHandlerScript.lockState.set(false);
            if (Rs2Bank.isOpen()) {
                if (Rs2Bank.getBankItem("Coins") != null) {
                    totalGP = Rs2Bank.getBankItem("Coins").getQuantity();
                }
            }
        }
    }

    public void openGEandBuyItem(String item, int howMany){
        closeTheBank();

        if(Rs2Player.getWorldLocation().distanceTo(BankLocation.GRAND_EXCHANGE.getWorldPoint()) > 7){
            Rs2Walker.walkTo(BankLocation.GRAND_EXCHANGE.getWorldPoint());
        }
        if(!Rs2GrandExchange.isOpen()){
            Rs2GrandExchange.openExchange();
            sleepUntil(()-> Rs2GrandExchange.isOpen(), Rs2Random.between(2000,5000));
            sleepHumanReaction();
        }
        if(Rs2GrandExchange.isOpen()){
            chosenSpot = null;
            weChangeActivity = true;

            if(Rs2GrandExchange.hasFinishedBuyingOffers() || Rs2GrandExchange.hasFinishedSellingOffers()){
                Rs2GrandExchange.collectAllToInventory();
                sleepUntil(()-> Rs2Inventory.contains(item), Rs2Random.between(2000,5000));
                sleepHumanReaction();
            }

            Rs2ItemManager itemManager = new Rs2ItemManager();
            int itemsID = itemManager.getItemId(item); // Get our Items ID
            if(item.equals("Leather")){itemsID = ItemID.LEATHER;} //Needed because getItemID returns the wrong itemID for Leather
            int itemsPrice = itemManager.getGEPrice(itemsID); // Get our items price
            int totalCost = itemsPrice * howMany; // Get how much it'll cost all together
            double getTwentyPercent = totalCost * 0.30; // Get 20 percent of the total cost
            double addTwentyPercent = getTwentyPercent + totalCost; // add the 20% we calculated to the total cost
            totalCost = (int) addTwentyPercent; // convert it back to an int

            Microbot.log("This will cost "+totalCost+" and we have "+totalGP);

            if(totalCost > totalGP){
                Microbot.log("We don't have enough GP :( re-rolling");
                this.shouldThink = true;
                return;
            }


            if(Rs2GrandExchange.buyItem(item, itemsPrice, howMany)){
                sleepUntil(()-> Rs2GrandExchange.hasFinishedBuyingOffers(), Rs2Random.between(20000,60000));
                sleepHumanReaction();
            }



            if(Rs2GrandExchange.hasFinishedBuyingOffers()){
                Rs2GrandExchange.collectAllToInventory();
                sleepUntil(()-> Rs2Inventory.contains(item), Rs2Random.between(2000,5000));
                sleepHumanReaction();
            }
        }
    }

    public void sellItems(){
        if(shouldSellItems){
            String[] items = {"Bronze bar", "Silver bar", "Diamond necklace", "Sapphire necklace", "Emerald necklace", "Ruby necklace", "Cooked chicken", "Leather", "Sapphire",
            "Emerald", "Ruby", "Diamond", "Raw chicken"};

            if(!Rs2Bank.isOpen()){
                walkToBankAndOpenIt();
            }

            if(Rs2Bank.isOpen()){
                depositAllIfWeChangeActivity();

                Rs2Bank.setWithdrawAsNote();

                for (String item : items) {
                    if(Rs2Bank.getBankItem(item) != null){
                        Rs2Bank.withdrawAll(item);
                        sleepUntil(()-> Rs2Inventory.contains(item), Rs2Random.between(2000,5000));
                        sleepHumanReaction();
                    }
                }

            }

            closeTheBank();

            if(!Rs2Inventory.isEmpty()) {

                if(Rs2Player.getWorldLocation().distanceTo(BankLocation.GRAND_EXCHANGE.getWorldPoint()) > 12){
                    Rs2Walker.walkTo(BankLocation.GRAND_EXCHANGE.getWorldPoint());
                }

                if(Rs2GrandExchange.openExchange()){
                    sleepUntil(() -> Rs2GrandExchange.isOpen(), Rs2Random.between(5000, 15000));
                    sleepHumanReaction();
                }

                if (Rs2GrandExchange.isOpen()) {
                    for (String item : items) {
                        if (Rs2Inventory.get(item) != null) {
                            Rs2GrandExchange.sellItem(item, Rs2Inventory.get(item).getQuantity(), 1);
                            sleepUntil(() -> Rs2GrandExchange.hasFinishedSellingOffers(), Rs2Random.between(20000,60000));
                            sleepHumanReaction();
                        }
                    }
                    if (Rs2GrandExchange.hasFinishedBuyingOffers() || Rs2GrandExchange.hasFinishedSellingOffers()) {
                        Rs2GrandExchange.collectAllToBank();
                    }
                }
            }

            shouldThink = true;
        }
    }

    //skilling

    public void craft(){
        if(shouldCraft){
            String craftingMaterial = "Unknown";
            String craftingProduct = "Unknown";
            String mould = "Unknown";
            String gem = "Unknown";
            String bar = "Unknown";

            int craftingLvl = Rs2Player.getRealSkillLevel(Skill.CRAFTING);
            if(craftingLvl < 7){craftingMaterial = "Leather"; craftingProduct = "Leather gloves";}
            if(craftingLvl >= 7 && craftingLvl < 9){craftingMaterial = "Leather"; craftingProduct = "Leather boots";}
            if(craftingLvl >= 9 && craftingLvl < 11){craftingMaterial = "Leather"; craftingProduct = "Leather cowl";}
            if(craftingLvl >= 11 && craftingLvl < 14){craftingMaterial = "Leather"; craftingProduct = "Leather vambraces";}
            if(craftingLvl >= 14 && craftingLvl < 18){craftingMaterial = "Leather"; craftingProduct = "Leather body";}
            if(craftingLvl >= 18 && craftingLvl < 22){craftingMaterial = "Leather"; craftingProduct = "Leather chaps";}
            if(craftingLvl >= 22 && craftingLvl < 29){mould = "Necklace mould"; gem = "Sapphire"; bar = "Gold bar"; craftingProduct ="Sapphire necklace";}
            if(craftingLvl >= 29 && craftingLvl < 40){mould = "Necklace mould"; gem = "Emerald"; bar = "Gold bar"; craftingProduct ="Emerald necklace";}
            if(craftingLvl >= 40 && craftingLvl < 56){mould = "Necklace mould"; gem = "Ruby"; bar = "Gold bar"; craftingProduct ="Ruby necklace";}
            if(craftingLvl >= 56){mould = "Necklace mould"; gem = "Diamond"; bar = "Gold bar"; craftingProduct ="Diamond necklace";}




            if(bar.equals("Gold bar")) {
                chosenSpot = new WorldPoint(3106, 3498, 0); // edgeville smelter
            }

            if(craftingMaterial.equals("Leather")) {
                chosenSpot = BankLocation.GRAND_EXCHANGE.getWorldPoint();
            }


            if(chosenSpot != null){
                if(Rs2Player.getWorldLocation().distanceTo(chosenSpot) > 12){
                    Rs2Walker.walkTo(chosenSpot);
                } else {
                    if(bar.equals("Gold bar")) {
                        if(!Rs2Inventory.contains(mould) || !Rs2Inventory.contains(gem) || !Rs2Inventory.contains(bar) || Rs2Inventory.contains(craftingProduct) || Rs2Inventory.contains(it -> it != null && it.isNoted()) || weChangeActivity){
                            walkToBankAndOpenIt();

                            if(weChangeActivity || Rs2Inventory.contains(it -> it != null && it.isNoted())){
                                Rs2Bank.depositAll();
                                sleepUntil(() -> Rs2Inventory.isEmpty(), Rs2Random.between(2000, 5000));
                                sleepHumanReaction();
                                if (Rs2Inventory.isEmpty()) {
                                    weChangeActivity = false;
                                }
                            }
                            if(Rs2Inventory.contains(craftingProduct) || Rs2Inventory.isFull()){
                                int random = Rs2Random.between(0,100);
                                if(random <= 75){
                                    Rs2Bank.depositAll(craftingProduct);
                                    String finalCraftingProduct = craftingProduct;
                                    sleepUntil(() -> !Rs2Inventory.contains(finalCraftingProduct), Rs2Random.between(2000, 5000));
                                    sleepHumanReaction();
                                } else {
                                    Rs2Bank.depositAll();
                                    sleepUntil(() -> Rs2Inventory.isEmpty(), Rs2Random.between(2000, 5000));
                                    sleepHumanReaction();
                                }

                            }

                            if(Rs2Bank.getBankItem(mould) == null){
                                goToBankandGrabAnItem(mould, 1);
                            }

                            int amt = Rs2Random.between(100,200);
                            if(Rs2Bank.getBankItem(gem, true) == null || Rs2Bank.getBankItem(bar, true) == null || Rs2Bank.getBankItem(gem, true).getQuantity() < 13 ||  Rs2Bank.getBankItem(bar, true).getQuantity() < 13){
                                goToBankandGrabAnItem(gem, amt);
                                goToBankandGrabAnItem(bar, amt);
                                return;
                            }
                            BreakHandlerScript.lockState.set(true);
                            while(Rs2Inventory.count(mould) < 1 || Rs2Inventory.count(gem) < 13 || Rs2Inventory.count(bar) < 13){
                                if(!super.isRunning()){break;}

                                if (BreakHandlerScript.breakIn != -1 && BreakHandlerScript.breakIn < 30 || BreakHandlerScript.isBreakActive()) {
                                    Rs2Bank.closeBank();
                                    Microbot.log("We're going on break");
                                    break;
                                }

                                if(Rs2Inventory.isFull()){Rs2Bank.depositAll();}

                                if(!Rs2Inventory.contains(mould) && Rs2Random.between(0,100) < 60){
                                    Rs2Bank.withdrawOne(mould);
                                    String finalMould = mould;
                                    sleepUntil(() -> Rs2Inventory.contains(finalMould), Rs2Random.between(2000, 5000));
                                    sleepHumanReaction();
                                }
                                if(Rs2Inventory.count(gem) < 13 && Rs2Random.between(0,100) < 60){
                                    Rs2Bank.withdrawX(gem, 13);
                                    String finalGem = gem;
                                    sleepUntil(() -> Rs2Inventory.contains(finalGem), Rs2Random.between(2000, 5000));
                                    sleepHumanReaction();
                                }
                                if(Rs2Inventory.count(bar) < 13 && Rs2Random.between(0,100) < 60){
                                    Rs2Bank.withdrawX(bar, 13);
                                    String finalBar = bar;
                                    sleepUntil(() -> Rs2Inventory.contains(finalBar), Rs2Random.between(2000, 5000));
                                    sleepHumanReaction();
                                }
                            }
                            BreakHandlerScript.lockState.set(false);
                        }

                        if(Rs2Inventory.contains(mould) && Rs2Inventory.contains(gem) && Rs2Inventory.contains(bar) && !Rs2Inventory.contains(it->it!=null&&it.isNoted())){
                            closeTheBank();

                            GameObject furnace = Rs2GameObject.findObject("furnace", true, 10, false, chosenSpot);

                            if (furnace == null) {
                                Rs2Walker.walkTo(chosenSpot);
                                return;
                            }

                            if (!Rs2Camera.isTileOnScreen(furnace.getLocalLocation())) {
                                Rs2Camera.turnTo(furnace.getLocalLocation());
                                return;
                            }

                            Rs2GameObject.interact(furnace, "smelt");
                            sleepUntilTrue(() -> Rs2Widget.isGoldCraftingWidgetOpen() || Rs2Widget.isSilverCraftingWidgetOpen(), 500, 20000);
                            sleepHumanReaction();
                            Rs2Widget.clickWidget(craftingProduct);
                            sleepThroughMulipleAnimations();
                        }

                    }

                    if(craftingMaterial.equals("Leather")) {
                        if (Rs2Inventory.contains(craftingMaterial) && Rs2Inventory.contains("Thread") && Rs2Inventory.contains("Needle") && !Rs2Inventory.contains(it->it!=null&&it.isNoted())) {
                            closeTheBank();

                            Rs2Inventory.combine(ItemID.NEEDLE, ItemID.LEATHER);

                            String whatWereCrafting = craftingProduct;
                            sleepUntil(() -> Rs2Widget.hasWidget(whatWereCrafting), Rs2Random.between(2000, 5000));
                            sleepHumanReaction();
                            Widget craftingWidget = Rs2Widget.findWidget(craftingProduct);
                            if (craftingWidget != null) {
                                Rs2Widget.clickWidget(craftingWidget);
                            } else {
                                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                            }

                            sleepThroughMulipleAnimations();
                        }
                        if (!Rs2Inventory.contains(craftingMaterial) || Rs2Inventory.count(craftingMaterial) < 3 || !Rs2Inventory.contains("Thread") || !Rs2Inventory.contains("Needle") || Rs2Inventory.contains(it -> it != null && it.isNoted())) {
                            walkToBankAndOpenIt();
                            if (Rs2Inventory.contains(craftingProduct) || Rs2Inventory.isFull() || Rs2Inventory.contains(it -> it != null && it.isNoted()) || weChangeActivity) {
                                Rs2Bank.depositAll();
                                sleepUntil(() -> Rs2Inventory.isEmpty(), Rs2Random.between(2000, 5000));
                                sleepHumanReaction();
                                if (Rs2Inventory.isEmpty()) {
                                    weChangeActivity = false;
                                }
                            }
                            if (!Rs2Inventory.contains("Thread")) {
                                if (Rs2Bank.isOpen()) {
                                    if (Rs2Bank.getBankItem("Thread", true) != null && Rs2Bank.getBankItem("Thread", true).getQuantity() > 10) {
                                        Rs2Bank.withdrawAll("Thread", true);
                                        sleepUntil(() -> Rs2Inventory.contains("Thread"), Rs2Random.between(2000, 5000));
                                        sleepHumanReaction();
                                    } else {
                                        openGEandBuyItem("Thread", Rs2Random.between(100, 200));
                                    }
                                }
                            }
                            if (!Rs2Inventory.contains("Needle")) {
                                if (Rs2Bank.isOpen()) {
                                    if (Rs2Bank.getBankItem("Needle", true) != null) {
                                        Rs2Bank.withdrawOne("Needle", true);
                                        sleepUntil(() -> Rs2Inventory.contains("Needle"), Rs2Random.between(2000, 5000));
                                        sleepHumanReaction();
                                    } else {
                                        openGEandBuyItem("Needle", 1);
                                    }
                                }
                            }
                            if (Rs2Inventory.contains("Needle") && Rs2Inventory.contains("Thread") && !Rs2Inventory.isFull() && !Rs2Inventory.contains(craftingMaterial) || Rs2Inventory.count(craftingMaterial) < 3) {
                                if (Rs2Bank.isOpen()) {
                                    if (Rs2Bank.getBankItem(craftingMaterial, true) != null) {
                                        Rs2Bank.withdrawAll(craftingMaterial, true);
                                        Rs2Inventory.waitForInventoryChanges(5000);
                                        sleepHumanReaction();
                                    } else {
                                        openGEandBuyItem(craftingMaterial, Rs2Random.between(100, 300));
                                    }
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
                weChangeActivity = true;
                return;
            }


                if(chosenSpot == null){
                    chosenSpot = new WorldPoint(3274,3180,0);
                }

                if(chosenSpot != null){
                    if(Rs2Player.getWorldLocation().distanceTo(chosenSpot) > 12){
                        Rs2Walker.walkTo(chosenSpot);
                    } else {
                        if(Rs2Inventory.contains(whatToCook) && !Rs2Inventory.get(whatToCook).isNoted()){
                            closeTheBank();

                            GameObject range = Rs2GameObject.getGameObject("Range");
                            if (range != null) {
                                if (!Rs2Camera.isTileOnScreen(range.getLocalLocation())) {
                                    Rs2Camera.turnTo(range.getLocalLocation());
                                    return;
                                }
                                Rs2Inventory.useItemOnObject(Rs2Inventory.get(whatToCook).getId(), range.getId());
                                sleepUntil(() -> !Rs2Player.isMoving() && Rs2Widget.findWidget("like to cook?", null, false) != null);
                                sleepHumanReaction();
                                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                                sleepHumanReaction();
                                sleepThroughMulipleAnimations();
                            }
                        }
                        if(!Rs2Inventory.contains(whatToCook) || Rs2Inventory.get(whatToCook).isNoted() || weChangeActivity){
                            walkToBankAndOpenIt();

                            if (Rs2Bank.isOpen()) {
                                if(Rs2Inventory.contains("Cooked chicken") || weChangeActivity || !Rs2Inventory.onlyContains("Raw chicken") || Rs2Inventory.contains(it->it!=null&&it.isNoted())){
                                    Rs2Bank.depositAll();
                                    sleepUntil(()->!Rs2Inventory.contains("Cooked chicken"), Rs2Random.between(3000, 6000));
                                    sleepHumanReaction();
                                    if(Rs2Inventory.isEmpty() && weChangeActivity){
                                        weChangeActivity = false;
                                    }
                                }
                                if(!Rs2Inventory.contains(whatToCook) && !Rs2Inventory.isFull()){
                                    if(Rs2Bank.getBankItem(whatToCook, true) != null) {
                                        Rs2Bank.withdrawAll(whatToCook, true);
                                        String cooked = whatToCook;
                                        sleepUntil(() -> !Rs2Inventory.contains(cooked), Rs2Random.between(3000, 6000));
                                        sleepHumanReaction();
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

            if(Rs2Inventory.contains(axeToUse) || Rs2Equipment.isWearing(it->it!=null&&it.getName().equals(finalaxe))){

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
                                sleepHumanReaction();
                            }
                            if(Rs2Bank.isOpen()&&!Rs2Inventory.isFull()){
                                Rs2Bank.closeBank();
                            }
                        } else {
                            closeTheBank();

                            GameObject ourTree;
                            ourTree = Rs2GameObject.getGameObject(treeToChop, true);

                            if(ourTree == null){
                                Microbot.log("Tree is null");
                                return;
                            }

                            if(ourTree.getWorldLocation().distanceTo(chosenSpot) >= 12){
                                Microbot.log("Tree is too far from our spot");
                                Rs2Walker.walkTo(chosenSpot);
                                return;
                            }

                            if(!Rs2Player.isAnimating() && !Rs2Player.isMoving()){
                                ourTree = Rs2GameObject.getGameObject(treeToChop, true);
                                if(Rs2GameObject.interact(ourTree, "Chop down")){
                                    sleepThroughMulipleAnimations();
                                    sleepHumanReaction();
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
    public void sleepHumanReaction(){
        if(Rs2Random.between(0,100) < 98) {
            sleep(0, 1000);
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

            if(Rs2Inventory.contains(axeToUse) || Rs2Equipment.isWearing(it->it!=null&&it.getName().equals(finalaxe))){

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
                        if(rockToMine.equals("Iron rocks")) {
                            if (Rs2Player.getCombatLevel() < 30) {
                                Microbot.log("We can't mine at Al Kharid until we get 30 combat");
                                chosenSpot = spot1;
                            }
                        }
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
                                    sleepHumanReaction();
                                }
                                if (Rs2Bank.isOpen() && !Rs2Inventory.isFull()) {
                                    Rs2Bank.closeBank();
                                }
                            }
                            if(rockToMine.equals("Iron rocks")) {
                                Rs2Inventory.dropAllExcept(axeToUse);
                            }
                        } else {
                            closeTheBank();

                            GameObject ourRock = Rs2GameObject.getGameObject(rockToMine);
                            if(ourRock!=null){
                                if(!Rs2Player.isAnimating()){
                                    if(Rs2GameObject.interact(ourRock, "Mine")){
                                        sleepUntil(()-> !Rs2Player.isAnimating() || ourRock == null, Rs2Random.between(20000,50000));
                                        sleepHumanReaction();
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
        if(fishingLvl < 21){fishingGear = "Small fishing net"; fishingAction = "Net";}
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
                                    sleepHumanReaction();
                                }
                            }
                            if(fishingGear.equals("Fly fishing rod")){

                                int cookingLvl = Rs2Player.getRealSkillLevel(Skill.COOKING);
                                if(cookingLvl < 15){
                                    if(Rs2Inventory.dropAllExcept(fishingGear, "Feather")){
                                        sleepUntil(()-> !Rs2Inventory.isFull(), Rs2Random.between(2000,5000));
                                        sleepHumanReaction();
                                    }
                                }
                                if(cookingLvl >= 15 && cookingLvl < 25){
                                    if(Rs2Inventory.contains("Raw trout")){
                                        cookFish(Rs2Inventory.get("Raw trout").getId());
                                        sleepThroughMulipleAnimations();
                                    }
                                    if(Rs2Inventory.dropAllExcept(fishingGear, "Feather")){
                                        sleepUntil(()-> !Rs2Inventory.isFull(), Rs2Random.between(2000,5000));
                                        sleepHumanReaction();
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
                                        sleepHumanReaction();
                                    }
                                }
                            }

                        } else {
                            if(fishingGear.equals("Fly fishing rod")) {
                                if (!Rs2Inventory.contains("Feather")) {
                                    goToBankandGrabAnItem("Feather", Rs2Random.between(500, 2000));
                                    return;
                                }
                            }

                            closeTheBank();

                            Rs2NpcModel ourFishingSpot = Rs2Npc.getNpc("Fishing spot");
                            if(ourFishingSpot!=null){
                                if(!Rs2Player.isAnimating()){
                                    if(Rs2Npc.interact(ourFishingSpot, fishingAction)){
                                        sleepUntil(()-> !Rs2Player.isAnimating() || ourFishingSpot == null, Rs2Random.between(20000,50000));
                                        sleepHumanReaction();
                                    }
                                }
                            }
                        }
                    }
                }

            } else {
                goToBankandGrabAnItem(fishingGear, 1);
                if(fishingGear.equals("Fly fishing rod")) {
                    if (!Rs2Inventory.contains("Feather")) {
                        goToBankandGrabAnItem("Feather", Rs2Random.between(500, 2000));
                    }
                }
            }
        }
    }

    public void cookFish(int fishesID){
        if (Rs2Inventory.contains(fishesID)) {
            Rs2Inventory.useItemOnObject(fishesID, 43475);
            sleepUntil(() -> !Rs2Player.isMoving() && Rs2Widget.findWidget("How many would you like to cook?", null, false) != null, 10000);
            sleepHumanReaction();
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
                            if (Rs2Inventory.contains("Silver bar") || !Rs2Inventory.contains("Silver ore") || Rs2Inventory.contains(it->it!=null&&it.isNoted())) {
                                walkToBankAndOpenIt();

                                if (Rs2Bank.isOpen()) {
                                    if (Rs2Inventory.contains("Silver bar") || Rs2Inventory.contains(it->it!=null&&it.isNoted()) || weChangeActivity) {
                                        int random = Rs2Random.between(0, 100);
                                        if (random <= 75) {
                                            Rs2Bank.depositAll();
                                            sleepUntil(() -> !Rs2Inventory.isFull(), Rs2Random.between(2000, 5000));
                                            sleepHumanReaction();
                                        } else {
                                            Rs2Bank.depositAll("Silver bar", true);
                                            sleepUntil(() -> !Rs2Inventory.isFull(), Rs2Random.between(2000, 5000));
                                            sleepHumanReaction();
                                        }
                                        if(Rs2Inventory.isEmpty()){
                                            weChangeActivity = false;
                                        }
                                    }
                                    if (!Rs2Inventory.contains("Silver bar") && !Rs2Inventory.isFull()) {
                                        if (Rs2Bank.getBankItem("Silver ore", true) != null) {
                                            Rs2Bank.withdrawAll("Silver ore", true);
                                            sleepUntil(() -> Rs2Inventory.isFull(), Rs2Random.between(2000, 5000));
                                            sleepHumanReaction();
                                        } else {
                                            //we need to buy silver ore
                                            openGEandBuyItem("Silver ore", Rs2Random.between(100,200));
                                        }
                                    }
                                }
                            }
                            if (Rs2Inventory.contains("Silver ore") && !Rs2Inventory.contains(it->it!=null&&it.isNoted())) {
                                smeltTheBar(Bars.SILVER);
                            }
                        }

                        if (smeltingBronze) {
                            if (Rs2Inventory.contains("Bronze bar") || !Rs2Inventory.contains("Copper ore") || !Rs2Inventory.contains("Tin ore") || Rs2Inventory.contains(it->it!=null&&it.isNoted())) {
                                walkToBankAndOpenIt();

                                if (Rs2Bank.isOpen()) {
                                    if (Rs2Inventory.contains("Bronze bar") || Rs2Inventory.isFull() || Rs2Inventory.contains(it->it!=null&&it.isNoted()) || weChangeActivity) {
                                        int random = Rs2Random.between(0, 100);
                                        if (random <= 75) {
                                            Rs2Bank.depositAll();
                                            sleepUntil(() -> !Rs2Inventory.isFull(), Rs2Random.between(2000, 5000));
                                            sleepHumanReaction();
                                        } else {
                                            Rs2Bank.depositAll("Bronze bar", true);
                                            sleepUntil(() -> !Rs2Inventory.isFull(), Rs2Random.between(2000, 5000));
                                            sleepHumanReaction();
                                        }
                                        if(Rs2Inventory.isEmpty()){
                                            weChangeActivity = false;
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
                                                    sleepHumanReaction();
                                                }
                                                if (Rs2Inventory.count("Tin ore") < 14) {
                                                    Rs2Bank.withdrawX("Tin ore", 14);
                                                    sleepUntil(() -> Rs2Inventory.count("Tin ore") >= 14, Rs2Random.between(2000, 5000));
                                                    sleepHumanReaction();
                                                }
                                            } else {
                                                if (Rs2Inventory.count("Tin ore") < 14) {
                                                    Rs2Bank.withdrawX("Tin ore", 14);
                                                    sleepUntil(() -> Rs2Inventory.count("Tin ore") >= 14, Rs2Random.between(2000, 5000));
                                                    sleepHumanReaction();
                                                }
                                                if (Rs2Inventory.count("Copper ore") < 14) {
                                                    Rs2Bank.withdrawX("Copper ore", 14);
                                                    sleepUntil(() -> Rs2Inventory.count("Copper ore") >= 14, Rs2Random.between(2000, 5000));
                                                    sleepHumanReaction();
                                                }
                                            }
                                        } else {
                                            //we need to buy copper ore
                                            outOfOre();
                                        }
                                    }
                                }
                            }
                            if ((Rs2Inventory.contains("Copper ore") && Rs2Inventory.contains("Tin ore")) && !Rs2Inventory.contains(it->it!=null&&it.isNoted())) {
                                smeltTheBar(Bars.BRONZE);
                            }

                        }
                    }
                }

        }
    }

    public void outOfOre(){
        //we need to buy
        int amt = Rs2Random.between(100,200);
        if (Rs2Bank.getBankItem("Tin ore") == null || Rs2Bank.getBankItem("Tin ore").getQuantity() < 14) {
            openGEandBuyItem("Tin ore", amt);
        }
        if (Rs2Bank.getBankItem("Copper ore") == null || Rs2Bank.getBankItem("Copper ore").getQuantity() < 14) {
            openGEandBuyItem("Copper ore", amt);
        }
    }

    public void smeltTheBar(Bars bar){
        // interact with the furnace until the smelting dialogue opens in chat, click the selected bar icon
        GameObject furnace = Rs2GameObject.findObject("furnace", true, 10, false, chosenSpot);
        if(furnace == null){
            if (Rs2Bank.isOpen())
                Rs2Bank.closeBank();
            Rs2Walker.walkTo(chosenSpot, 4);
            return;
        }
        if (furnace != null) {
            Rs2GameObject.interact(furnace, "smelt");
            Rs2Widget.sleepUntilHasWidgetText("What would you like to smelt?", 270, 5, false, 20000);
            sleepHumanReaction();
            Rs2Widget.clickWidget(bar.getName());
            Rs2Widget.sleepUntilHasNotWidgetText("What would you like to smelt?", 270, 5, false, 5000);
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
                        String fixedLogstoBurn = logsToBurn;
                        if(!Rs2Inventory.contains(logsToBurn) || !Rs2Inventory.contains(ItemID.TINDERBOX) || Rs2Inventory.contains(it->it!=null&&it.isNoted()) || weChangeActivity){
                            walkToBankAndOpenIt();

                            if(Rs2Bank.isOpen()){
                                if(Rs2Inventory.contains(it->it!=null&&it.isNoted()) || weChangeActivity){
                                    Rs2Bank.depositAll();
                                    sleepUntil(()-> Rs2Inventory.isEmpty(), Rs2Random.between(2000,5000));
                                    sleepHumanReaction();
                                    if(Rs2Inventory.isEmpty()){
                                        weChangeActivity = false;
                                    }
                                }
                                if(!Rs2Inventory.contains(ItemID.TINDERBOX)){
                                    if(Rs2Bank.getBankItem(ItemID.TINDERBOX) != null){
                                        Rs2Bank.withdrawOne(ItemID.TINDERBOX);
                                        sleepUntil(()-> Rs2Inventory.contains(ItemID.TINDERBOX), Rs2Random.between(2000,5000));
                                        sleepHumanReaction();
                                    } else {
                                        openGEandBuyItem("Tinderbox", 1);
                                    }
                                }
                                if(!Rs2Inventory.contains(logsToBurn)){
                                    if(Rs2Bank.getBankItem(logsToBurn, true) != null){
                                        Rs2Bank.withdrawAll(logsToBurn, true);
                                        String logs = logsToBurn;
                                        sleepUntil(()-> Rs2Inventory.contains(logs), Rs2Random.between(2000,5000));
                                        sleepHumanReaction();
                                    } else {
                                        openGEandBuyItem(logsToBurn, Rs2Random.between(100,300));
                                    }
                                }
                            }
                        }
                        if(Rs2Inventory.contains(logsToBurn) && Rs2Inventory.contains(ItemID.TINDERBOX) && !Rs2Inventory.contains(it->it!=null&&it.isNoted())){
                            closeTheBank();

                            GameObject fire = Rs2GameObject.getGameObject(it->it!=null&&it.getId()==ObjectID.FIRE&&it.getWorldLocation().equals(Rs2Player.getWorldLocation()));

                            if(Rs2Player.isStandingOnGameObject() || fire != null){
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
                                return;
                            }

                            NPC banker = Rs2Npc.getNearestNpcWithAction("Bank");
                            NPC geClerk = Rs2Npc.getNearestNpcWithAction("Exchange");
                            if(banker == null || geClerk == null){
                                Microbot.log("Couldn't find GE Clerk or Banker, walking to the GE");
                                Rs2Walker.walkTo(BankLocation.GRAND_EXCHANGE.getWorldPoint());
                                return;
                            }

                            if (banker.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= 2 || geClerk.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= 2) {
                                Microbot.log("We're too close to the GE, moving.");
                                if (Rs2Player.distanceTo(chosenSpot) > 4) {
                                    Rs2Walker.walkTo(chosenSpot);
                                } else {
                                    Rs2Walker.walkCanvas(chosenSpot);
                                }
                                return;
                            }

                            if(Rs2Inventory.contains("Ashes")){
                                Rs2Inventory.dropAll("Ashes");
                                sleepHumanReaction();
                            }

                            Rs2Inventory.use("tinderbox");
                            sleepHumanReaction();
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
        BreakHandlerScript.lockState.set(true);
        if(Rs2Player.isMoving()){
            sleepUntil(()-> !Rs2Player.isMoving(), Rs2Random.between(10000,15000));
        }
        if(!Rs2Player.isAnimating()){
            sleepUntil(()-> Rs2Player.isAnimating(), Rs2Random.between(10000,15000));
        }
        if(!this.shouldFiremake) {
            int timeoutMs = Rs2Random.between(3000, 5000);
            while (Rs2Player.isAnimating() || Rs2Player.isAnimating(timeoutMs)) {
                if (!super.isRunning()) {
                    break;
                }
                sleepHumanReaction();
            }
        } else {
            int timeoutMs = Rs2Random.between(1500, 3000);
            while (Rs2Player.isAnimating() || Rs2Player.isAnimating(timeoutMs)) {
                if (!super.isRunning()) {
                    break;
                }
                sleepHumanReaction();
            }
        }
        BreakHandlerScript.lockState.set(false);
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}