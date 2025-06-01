package net.runelite.client.plugins.microbot.f2pAccountBuilder;

import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.barrows.BarrowsPlugin;
import net.runelite.client.plugins.microbot.example.ExampleConfig;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingPlugin;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class f2pAccountBuilderScript extends Script {

    public static boolean test = false;
    public volatile boolean shouldThink = true;
    long scriptStartTime = System.currentTimeMillis();
    private long howLongUntilThink = 0;

    private boolean shouldWoodcut = false;
    private boolean shouldMine = false;
    private boolean shouldFish = false;

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

            int random = Rs2Random.between(0,1000);
            if(random <= 100){
                Microbot.log("We're going woodcutting.");
                shouldWoodcut = true;
                shouldThink = false;
                chosenSpot = null;
                return;
            }
            if(random > 100 && random <= 200){
                Microbot.log("We're going mining.");
                shouldMine = true;
                shouldThink = false;
                chosenSpot = null;
                return;
            }
            if(random > 200 && random <= 300){
                Microbot.log("We're going fishing.");
                shouldFish = true;
                shouldThink = false;
                chosenSpot = null;
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

    public void goToBankandGrabAnItem(String item){
        if(!Rs2Bank.isOpen()){
            Rs2Bank.walkToBankAndUseBank();
            sleepUntil(()-> Rs2Bank.isOpen(), Rs2Random.between(2000,5000));
        }
        if(Rs2Bank.isOpen()){
            if(Rs2Bank.getBankItem(item) != null){
                if(!Rs2Inventory.contains(item)){
                    Rs2Bank.withdrawOne(item);
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
                    openGEandBuyItem(item);
                }
            }
        }
    }

    public void openGEandBuyItem(String item){
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
            if(Rs2GrandExchange.buyItemAboveXPercent(item, 1, 20)){
                sleepUntil(()-> Rs2GrandExchange.hasFinishedBuyingOffers(), Rs2Random.between(2000,5000));
            }
            if(Rs2GrandExchange.hasFinishedBuyingOffers()){
                Rs2GrandExchange.collectToInventory();
                sleepUntil(()-> Rs2Inventory.contains(item), Rs2Random.between(2000,5000));
            }
        }
    }

    //skilling

    public void woodCutting(){
        if(shouldWoodcut){
            if(Rs2Inventory.contains(it->it!=null&&it.getName().contains("axe")&&!it.getName().contains("pick"))){

                if(chosenSpot == null){
                    WorldPoint spot1 = new WorldPoint(3157,3456,0);
                    WorldPoint spot2 = new WorldPoint(3164,3406,0);
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
                            if(Rs2Bank.walkToBankAndUseBank()){
                                sleepUntil(()-> Rs2Bank.isOpen(), Rs2Random.between(2000,5000));
                            }
                            if(Rs2Bank.isOpen()){
                                Rs2Bank.depositAllExcept("Iron axe");
                                sleepUntil(()-> !Rs2Inventory.isFull(), Rs2Random.between(2000,5000));
                            }
                            if(Rs2Bank.isOpen()&&!Rs2Inventory.isFull()){
                                Rs2Bank.closeBank();
                            }
                        } else {
                            GameObject ourTree = Rs2GameObject.getGameObject("Tree", true);
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
                goToBankandGrabAnItem("Iron axe");
            }
        }
    }

    public void mining(){
        if(shouldMine){
            if(Rs2Inventory.contains(it->it!=null&&it.getName().contains("pickaxe"))){

                if(chosenSpot == null){
                    WorldPoint spot1 = new WorldPoint(3183,3374,0);
                    WorldPoint spot2 = new WorldPoint(3283,3362,0);
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
                            if(Rs2Bank.walkToBankAndUseBank()){
                                sleepUntil(()-> Rs2Bank.isOpen(), Rs2Random.between(2000,5000));
                            }
                            if(Rs2Bank.isOpen()){
                                Rs2Bank.depositAllExcept("Iron pickaxe");
                                sleepUntil(()-> !Rs2Inventory.isFull(), Rs2Random.between(2000,5000));
                            }
                            if(Rs2Bank.isOpen()&&!Rs2Inventory.isFull()){
                                Rs2Bank.closeBank();
                            }
                        } else {
                            GameObject ourRock = Rs2GameObject.getGameObject("Tin rocks");
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
                goToBankandGrabAnItem("Iron pickaxe");
            }
        }
    }

    public void fishing(){
        if(shouldFish){
            if(Rs2Inventory.contains(it->it!=null&&it.getName().contains("Small fishing net"))){

                if(chosenSpot == null){
                    WorldPoint spot1 = new WorldPoint(3241,3151,0);
                    WorldPoint spot2 = new WorldPoint(3241,3151,0);
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
                            if(Rs2Inventory.dropAllExcept("Small fishing net")){
                                sleepUntil(()-> !Rs2Inventory.isFull(), Rs2Random.between(2000,5000));
                            }
                        } else {
                            Rs2NpcModel ourFishingSpot = Rs2Npc.getNpc("Fishing spot");
                            if(ourFishingSpot!=null){
                                if(!Rs2Player.isAnimating()){
                                    if(Rs2Npc.interact(ourFishingSpot, "Net")){
                                        sleepUntil(()-> !Rs2Player.isAnimating() || ourFishingSpot == null, Rs2Random.between(20000,50000));
                                        sleep(0,500);
                                    }
                                }
                            }
                        }
                    }
                }

            } else {
                goToBankandGrabAnItem("Small fishing net");
            }
        }
    }

    //skilling

    @Override
    public void shutdown() {
        super.shutdown();
    }
}