package net.runelite.client.plugins.microbot.gemMining;

import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class GemScript extends Script {
    public boolean emptyGemBag = false;
    public static boolean test = false;
    private long delay = Rs2Random.between(250,600);
    public boolean run(GemConfig config) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                mineGems();

                BankGems();

                delay = Rs2Random.between(250,600);

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, delay, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
    public int generateRandomNumber(int min, int max) {
        return Rs2Random.nextInt(min, max, 1000, true);
    }
public void mineGems(){
        if(!Rs2Inventory.isFull()){
            emptyGemBag = false;
            int randomDistance = Rs2Random.between(5,13);
            GameObject gem = Rs2GameObject.getGameObject("Gem rocks", randomDistance);

            if(gem == null){return;}

            WorldPoint gemrock =gem.getWorldLocation();

            if(Rs2GameObject.interact(gem)){

                sleepUntil(() -> Rs2Player.isAnimating() && !Rs2Player.isMoving(), generateRandomNumber(5000, 15000));

                if(Rs2Player.isAnimating() && !Rs2Player.isMoving()) {
                    sleepUntil(() -> Rs2GameObject.getGameObject(gemrock).getId() == 11390 || Rs2GameObject.getGameObject(gemrock).getId() == 11391 || !Rs2Player.isAnimating(), generateRandomNumber(5000, 15000));
                }
            }
        }
    }
    public void BankGems(){
        if(Rs2Inventory.isFull()){
            if(!Rs2DepositBox.isOpen()){
                //if it's not open, open it.
                if(Rs2DepositBox.openDepositBox()){
                    sleepUntil(()-> Rs2DepositBox.isOpen(), generateRandomNumber(5000,15000));
                }
            } else {
                // if it is open.
                //while inventory contains gems.
                int howtobank = generateRandomNumber(0, 100);
                // must loop
                if (howtobank <= 50) {
                    if (Rs2Inventory.contains(it -> it != null && it.getName().contains("Uncut"))) {
                        List<String> listOfGems = new ArrayList<>();
                        listOfGems.add("Uncut opal");
                        listOfGems.add("Uncut jade");
                        listOfGems.add("Uncut red topaz");
                        Collections.shuffle(listOfGems);
                        for (String gem : listOfGems) {
                            if (Rs2Inventory.contains(gem)) {
                                if(Rs2DepositBox.depositAll(gem)) {
                                    sleepUntil(() -> !Rs2Inventory.contains(gem), generateRandomNumber(5000, 15000));
                                }
                            }
                        }
                    }
                    howtobank = generateRandomNumber(0, 100);
                }
                //Empty Gem bag
                if (howtobank <= 50) {
                    if (Rs2Inventory.contains("Open gem bag")) {
                        if (emptyGemBag == false) {
                            // gem bag option while bank open is Empty
                            if (Rs2Inventory.interact("Open gem bag", "Empty")) {
                                emptyGemBag = true;
                                sleep(500, 1000);
                            }
                        }
                    }
                    howtobank = generateRandomNumber(0, 100);
                }
            }
        }
    }
}