package net.runelite.client.plugins.microbot.cooking.scripts;

import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.cooking.AutoCookingConfig;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;
import static net.runelite.client.plugins.microbot.util.npc.Rs2Npc.getNpcs;
import static net.runelite.client.plugins.microbot.util.player.Rs2Player.toggleRunEnergy;


public class BurnBakingScript extends Script {
    private boolean cookingBread;

    public boolean run(AutoCookingConfig config) {
        Microbot.enableAutoRunOn = false;
        int targetCookingLevel = config.cookingLevel(); // Retrieve user-configured cooking level
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyCookingSetup();
        Rs2Antiban.setActivity(Activity.GENERAL_COOKING);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                    int currentCookingLevel = Microbot.getClient().getRealSkillLevel(Skill.COOKING);

                    // If the target cooking level is reached, stop the script
                    if (currentCookingLevel >= targetCookingLevel) {
                        walkToBanker();
                        if (!Rs2Bank.isOpen()) {
                            openNearestBank();
                        }
                        sleepUntil(Rs2Bank::isOpen, 30000);

                        Rs2Bank.depositAll();
                        sleep(1200);
                        Rs2Bank.setWithdrawAsNote();
                        sleep(1200);

                        if (Rs2Bank.hasWithdrawAsNote()) {
                            withdrawAndCheck("Pot");
                            withdrawAndCheck("Bucket");
                            withdrawAndCheck("Cake tin");
                            withdrawAndCheck("Uncooked cake");
                            withdrawAndCheck("Uncooked stew");
                            withdrawAndCheck("Bread dough");
                            withdrawAndCheck("Cake");
                            withdrawAndCheck("Bread");
                            withdrawAndCheck("Stew");
                            withdrawAndCheck("Burnt bread");
                            withdrawAndCheck("Burnt cake");
                            withdrawAndCheck("Burnt stew");
                        }

                        sleep(1200);
                        Rs2Player.logout();
                        sleep(5000);
                        shutdown();
                        System.out.println(targetCookingLevel + " cooking, opened bank, withdrew all items and stopped");
                        return;
                    }


                    // If cooking level is between 25 and 40, decide to cook stew
                    if (currentCookingLevel < 41 && currentCookingLevel >= 25 && shouldCookStew() && Rs2Inventory.hasItem("Uncooked stew")) {
                        System.out.println("WeShouldCookStew");
                        Microbot.log("You've tried to prepare cake between level 25 and 40 so maybe you wanna make stew. Making stew now.");
                        manageRunEnergy();
                        cookItem("Uncooked stew");
                        Microbot.naturalMouse.moveOffScreen();
                        sleepUntilIngredientHasRunOut("Uncooked stew");
                        return;// Placeholder for your cooking stew logic
                    }

                    // If cooking level is below 25, decide to cook bread
                    if (currentCookingLevel < 25) {
                        prepareBread();
                        return;
                    }

                    // If cooking level is exactly 25, prepare stew
                    if (currentCookingLevel == 25) {
                        System.out.println("WeShouldPrepareStew");
                        prepareStew();  // Placeholder for your stew preparation logic
                        return;
                    }

                    // Check if bank is open and we are in the intermediate level range (40 to target level)
                    if (Rs2Bank.isOpen() && currentCookingLevel >= 40 && Rs2Bank.hasItem(1944)) {
                        System.out.println("Cooking level is between 40 and " + targetCookingLevel + " and Egg is in the bank");
                        if (Rs2Inventory.contains("Bucket", "Pot") || Rs2Inventory.isEmpty()) {
                            prepareCake();
                        }
                    } else if (!Rs2Bank.isOpen()) {
                        // Check if inventory has required items
                        if (Rs2Inventory.contains(1944) && Rs2Inventory.contains("Bucket of milk") &&
                                Rs2Inventory.contains("Pot of flour") && Rs2Inventory.contains("Cake tin")) {
                            prepareCake();
                        } else if (Rs2Inventory.contains("Cake") && Rs2Inventory.contains("Burnt cake") || !Rs2Inventory.hasItem("Uncooked cake")) {
                            System.out.println("Cake and burnt cake found and no uncooked cake, opening bank");
                            walkToBanker();
                            if (!Rs2Bank.isOpen()) {
                                openNearestBank();
                            }
                            sleepUntil(Rs2Bank::isOpen, 30000);
                            if (Rs2Bank.isOpen()) {
                                Rs2Bank.depositAll();
                                sleepUntil(Rs2Inventory::isEmpty, 5000);
                                if (Rs2Bank.hasItem("Uncooked cake") && !Rs2Bank.hasItem("Egg")) {
                                    Rs2Bank.withdrawX("Uncooked cake", 14);
                                    sleepUntil(() -> Rs2Inventory.hasItem("Uncooked cake"), 5000);

                                    sleep(400, 900);
                                    Rs2Bank.closeBank();
                                    manageRunEnergy();
                                    if (Rs2Inventory.hasItem("Uncooked cake")) {
                                        cookItem("Uncooked cake");
                                    }  // Placeholder for your cake cooking logic
                                }
                            } else if (Rs2Bank.hasItem("Egg") && Rs2Bank.hasItem("Cake tin") && Rs2Bank.hasItem("Bucket of milk") && Rs2Bank.hasItem("Pot of flour"))
                            {prepareCake();}

                            if (Rs2Inventory.itemQuantity("Uncooked cake") == 7 && currentCookingLevel >= 40) {
                                prepareCake();
                            }
                        }
                        if (Rs2Inventory.itemQuantity("Uncooked cake") == 7 && currentCookingLevel >= 40) {
                            prepareCake();
                        }

                        if (Rs2Inventory.itemQuantity("Uncooked cake") == 14 && currentCookingLevel >= 40) {
                            cookItem("Uncooked cake");
                            sleep(10000);
                        }
                    }

                    // Additional check if the bank is open
                    if (Rs2Bank.isOpen() && currentCookingLevel >= 40 && Rs2Bank.hasItem(1944)) {
                        System.out.println("Egg found after opening bank.");
                        prepareCake();
                    } else if (Rs2Bank.isOpen() && currentCookingLevel >= 40){
                        if (Rs2Bank.hasItem("Uncooked cake")) {
                            System.out.println("bank has uncooked cake, withdrawing to cook");
                            Rs2Bank.depositAll();
                            Rs2Bank.withdrawX("Uncooked cake", 14);
                            sleep(400,900);
                            Rs2Bank.closeBank();
                            manageRunEnergy();
                            cookItem("Uncooked cake");
                        }
                    }

                    // If cooking level is between 25 and 40, and inventory doesn't have uncooked stew, prepare stew
                    if (currentCookingLevel < 40 && !Rs2Inventory.contains("Uncooked stew")) {
                        System.out.println("entering prepare stew");
                        if (!Rs2Player.isAnimating()) { walkToBanker();
                            Rs2Npc.getBankerNPC();
                            if (!Rs2Bank.isOpen()) {openNearestBank();}
                            sleepUntil(Rs2Bank::isOpen, 30000);}
                        sleep(1200, 1600);
                        prepareStew();  // Call the prepareStew method if the conditions are met
                    }

                    System.out.println("End of loop");


                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void withdrawAndCheck(String itemName) {
        Rs2Bank.withdrawAll(itemName);
        sleep(1200, 1800);
        if (!Rs2Inventory.contains(itemName)) {
            System.out.println(itemName + " was not withdrawn, retrying...");
            Rs2Bank.withdrawAll(itemName);
            sleep(1200, 1800);
        }
    }

    public void walkToBanker() {
        // Get a list of all bankers in the area
        List<Rs2NpcModel> bankers = getBankerNPCs();  // Assuming this method retrieves all bankers
        Random random = new Random();

        if (bankers.isEmpty()) {
            Microbot.log("No bankers found.");
            System.out.println("Initiating interact with range");
            findAndInteractWithNearestRange();
            return;
        }

        // Select a random banker from the list
        NPC banker = bankers.get(random.nextInt(bankers.size()));
        LocalPoint bankerLocation = banker.getLocalLocation();

        if (bankerLocation == null) {
            Microbot.log("Failed to get the banker's location.");
            return;
        }

        WorldPoint bankerWorldLocation = WorldPoint.fromLocal(Microbot.getClient(), bankerLocation);
        WorldPoint playerWorldLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();

        // Check if the player is within 4 tiles of the banker
        if (playerWorldLocation.distanceTo(bankerWorldLocation) <= 4) {
            // If within 4 tiles, just open the bank
            if (!Rs2Bank.isOpen()) {
                openNearestBank();
            }
            return;
        }

        // Turn the camera towards the banker smoothly
        //Rs2Camera.turnTo(banker);
        //could of used:
        //Rs2Tile.isTileReachable();
        //Rs2Tile.areSurroundingTilesWalkable();
        // Generate a random tile 2-3 tiles south and 0-3 tiles east of the banker
        int southOffset = 2 + random.nextInt(2);  // 2 to 3 tiles south
        int eastOffset = random.nextInt(4);       // 0 to 3 tiles east

        // Generate the new target location using LocalPoint
        LocalPoint targetLocation = new LocalPoint(
                bankerLocation.getX() + eastOffset * 128,  // Adjust by eastOffset (walk east)
                bankerLocation.getY() - southOffset * 128  // Adjust by southOffset (walk south)
        );

        WorldPoint worldLocation = WorldPoint.fromLocal(Microbot.getClient(), targetLocation);

        // Walk to the generated target location using Rs2Walker
        Rs2Walker.walkMiniMap(worldLocation);
        Microbot.naturalMouse.moveOffScreen();

        sleep(1200);

        // Wait for the player to finish walking
        waitForWalking();

        if (!Rs2Bank.isOpen()) {
            openNearestBank();
        }
    }

    public static Rs2NpcModel getBankerNPC() {
        return Rs2Npc.getNpcs()
                .filter(npc -> npc.getComposition() != null && npc.getComposition().getActions() != null &&
                        Arrays.asList(npc.getComposition().getActions()).contains("Bank"))
                .min(Comparator.comparingInt(npc -> npc.getWorldLocation().distanceTo(Microbot.getClient().getLocalPlayer().getWorldLocation())))
                .orElse(null);
    }

    public void openNearestBank() {
        if (!Rs2Bank.isOpen()) {
            Rs2NpcModel nearestBanker = getBankerNPC();  // Find the closest NPC that has "Bank" action
            if (nearestBanker != null) {
                Rs2Npc.interact(nearestBanker, "Bank");
                sleepUntil(Rs2Bank::isOpen, 5000);
            }
        }
    }

    private void waitForWalking() {
        System.out.println("Waiting for player to start walking...");

        // First, wait for the player to start walking
        long startTime = System.currentTimeMillis();
        while (!Rs2Player.isMoving()) {
            if (System.currentTimeMillis() - startTime > 30000) {  // Timeout after 30 seconds
                System.out.println("Timed out: Player did not start walking within 30 seconds.");
                return;
            }
            sleep(100);  // Check every 100ms
        }

        System.out.println("Player has started walking, now waiting for them to stop...");

        // Now wait for the player to stop walking
        while (Rs2Player.isMoving()) {
            //System.out.println("Is walking: true");  // Log the walking state
            sleep(100);  // Check every 100ms
        }

        System.out.println("Player has stopped walking.");
    }



    public static List<Rs2NpcModel> getBankerNPCs() {
        return getNpcs()
                .filter(value -> (value.getComposition() != null && value.getComposition().getActions() != null &&
                        Arrays.asList(value.getComposition().getActions()).contains("Bank")))
                .limit(4) // Collect only up to 4 bankers
                .collect(Collectors.toList());
    }


    private void prepareBread() {
        int currentCookingLevel = Microbot.getClient().getRealSkillLevel(Skill.COOKING);

        if (currentCookingLevel < 25) {
            System.out.println("WeShouldCookBread");
            Microbot.log("You've tried to prepare cake below level 25, so maybe you wanna make bread. Making bread now.");
            // holy crap fix this, state?  cooking? when cooking?
            // Step 2: Check if we need to open the bank and find ingredients
            if (!Rs2Inventory.hasItem("Pot of flour", true) || !Rs2Inventory.hasItem("Bucket of water", true)) {
                if (!Rs2Bank.isOpen()) {
                    if(Rs2Inventory.hasItem("Bread", true) && !Rs2Inventory.hasItem("Bread dough", true)){
                        System.out.println("going to the bank to restock bread dough");
                        walkToBanker();
                        if (!Rs2Bank.isOpen()) {openNearestBank();}
                        sleepUntil(Rs2Bank::isOpen, 25000);
                    }
                    if (Rs2Inventory.hasItem("Bread", true) &&
                        Rs2Inventory.hasItem("Burnt bread", true)) {
                        System.out.println("sleeping until no bread dough");
                        sleepUntilIngredientHasRunOut("Bread dough");
                    }

                    if(!cookingBread) {
                        System.out.println("going to the bank to combine bread");
                        walkToBanker();
                        if (!Rs2Bank.isOpen()) {openNearestBank();}
                        sleepUntil(Rs2Bank::isOpen, 25000);
                    }

                }


                // If the bank is open and we don't have flour/water, check the bank
                if (Rs2Bank.isOpen()) {
                    // Step 3: Check if bank has bread dough
                    if (!Rs2Bank.hasBankItem("Bucket of water", true)
                            && Rs2Bank.hasBankItem("Bread dough", true)) {
                        cookingBread = true;
                        Rs2Bank.depositAll();
                        sleep(500, 1000);
                        Rs2Bank.withdrawX("Bread dough", 28);
                        Rs2Bank.closeBank();
                        manageRunEnergy();
                        cookItem("Bread dough");
                    }
                    // Step 4: If we have flour and water in the bank, withdraw them
                    else if (Rs2Bank.hasBankItem("Pot of flour", true) && Rs2Bank.hasBankItem("Bucket of water", true)) {
                        Rs2Bank.depositAll();
                        sleep(400, 800);
                        Rs2Bank.withdrawX("Pot of flour", 9);
                        sleep(400, 700);
                        Rs2Bank.withdrawX("Bucket of water", 9);
                        sleep(400, 700);
                        Rs2Bank.closeBank();
                    } else {
                        Rs2Bank.depositAll(); //if inventory is not empty deposit all
                        System.out.println("Missing ingredients in the bank for bread-making.");
                        Microbot.log("Could not find flour or water in the bank for bread-making.");
                    }
                }
            }
            // Step 5: If we already have flour and water in the inventory
            else if (Rs2Inventory.hasItem("Pot of flour", true) && Rs2Inventory.hasItem("Bucket of water", true)) {
                // Combine flour and water to make bread dough

                Rs2Inventory.combineClosest("Pot of flour", "Bucket of water");

                sleep(900, 1300);

                // Assuming you want to interact with the 'Make' option on 'Bread dough'
                Rs2Widget.clickWidget(17694734);
                Microbot.naturalMouse.moveOffScreen();

                // Step 6: Sleep until the amount of an ingredient runs out
                sleepUntilIngredientHasRunOut("Bucket of water");
            }
        }
    }

    public boolean clickWidget(int id) {
        Widget widget = Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getWidget(id)).orElse(null);
        if (widget == null) return false;
        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        sleep(100, 300);
        Rs2Keyboard.keyRelease(KeyEvent.VK_SPACE);
        return true;
    }

    private void prepareStew() {
        Microbot.log("Entering prepareStew method");

        // If the inventory contains Bowl of water and Potato, prepare the stew
        if (Rs2Inventory.hasItem("Bowl of water", true) && Rs2Inventory.hasItem("Potato", true)) {
            interactWithBowlAndPotato();
        } else {
            // Open bank if it's not open
            if (!Rs2Bank.isOpen()) {
                walkToBanker();
                if (!Rs2Bank.isOpen()) {openNearestBank();}
                sleepUntil(Rs2Bank::isOpen, 30000);
            }

            // the first if statements checks basically if there is no ingredients to make incomplete stew
            if (!Rs2Bank.hasBankItem("Bowl of water", true) || !Rs2Bank.hasBankItem("Potato", true)) {
                if (Rs2Bank.hasBankItem("Cooked meat", true)) {
                    depositAndWithdrawItems("Cooked meat", true);
                    interactWithBowlAndMeat("Cooked meat"); //making uncooked stew from incomplete stew
                } else if (Rs2Bank.hasBankItem("Cooked chicken", true)) {
                    depositAndWithdrawItems("Cooked chicken", true);
                    interactWithBowlAndMeat("Cooked chicken"); //making uncooked stew from incomplete stew
                } else if (Rs2Bank.hasBankItem("Uncooked stew", !Rs2Bank.hasBankItem("Incomplete stew"))) {
                    Rs2Bank.depositAll();

                    // Sleep until the inventory is empty
                    sleepUntil(Rs2Inventory::isEmpty, 5000);  // Using method reference

                    Rs2Bank.withdrawAll("Uncooked stew");

                    // Sleep until "Uncooked stew" is in the inventory
                    sleepUntil(() -> Rs2Inventory.hasItem("Uncooked stew"), 5000);  // Using lambda expression

                    Rs2Bank.closeBank();
                }
            } else {
                depositAndWithdrawItems("Potato", false);
                interactWithBowlAndPotato(); //making incomplete stew from bowl of water and potato
            }
        }

        Microbot.log("Completed prepareStew method");
    }

    // Refactored deposit and withdraw items based on ingredient (meat, chicken, or potato)
    private void depositAndWithdrawItems(String ingredient, boolean withdrawIncompleteStew) {
        Rs2Bank.depositAll();
        sleep(500, 1000);

        // Withdraw Bowl of water if we are not withdrawing Complete stew
        if (!withdrawIncompleteStew) {
            Rs2Bank.withdrawX("Bowl of water", 14);
            sleep(500, 1000);
        }

        // Withdraw the chosen ingredient (meat, chicken, or potato)
        Rs2Bank.withdrawX(ingredient, 14);
        sleep(500, 1000);

        // Withdraw Complete stew if the flag is true
        if (withdrawIncompleteStew) {
            Rs2Bank.withdrawX("Incomplete stew", 14);
            sleep(500, 1000);
        }

        Rs2Bank.closeBank();
    }


    private void interactWithBowlAndMeat(String meatType) {
        // Check if the bank is closed and the necessary items are available
        if (!Rs2Bank.isOpen() && Rs2Inventory.hasItem("Incomplete stew") && Rs2Inventory.hasItem(meatType)) {

            // Combine the "Incomplete stew" with the specified meatType ("Cooked meat" or "Cooked chicken")
            Rs2Inventory.combineClosest("Incomplete stew", meatType);

            // Simulate human-like delay
            sleep(1550, 2180);

            // Press space to confirm the interaction
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            sleep(100, 300);
            Rs2Keyboard.keyRelease(KeyEvent.VK_SPACE);
            Microbot.naturalMouse.moveOffScreen();


            // Simulate human-like delay after the action
            sleep(1500, 3000);

            // Wait until "Incomplete stew" is no longer in the inventory
            sleepUntilIngredientHasRunOut("Incomplete stew");
        } else {
            System.out.println("Either bank is open, or necessary items are missing.");
        }
    }



    // Interaction with Bowl of Water and Potato
    private void interactWithBowlAndPotato() {

        if (!Rs2Bank.isOpen()) {
            Rs2Inventory.combineClosest("Bowl of water","Potato");
            sleep(1550, 2180);
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            sleep(100, 300);
            Rs2Keyboard.keyRelease(KeyEvent.VK_SPACE);
            Microbot.naturalMouse.moveOffScreen();
            sleep(1500,3000);
            sleepUntilIngredientHasRunOut("Potato");

        }
    }

    private void sleepUntilIngredientHasRunOut(String itemToTrack) {
        // sleep until the inventory no longer contains the specified item
        sleepUntilTrue(() -> !Rs2Inventory.hasItem(itemToTrack), 1200, 30000);
    }

    private boolean shouldCookStew() {
        Microbot.log("Entering shouldCookStew method");

        if (Rs2Bank.isOpen()) {
            // Bank is open, check if it has the right items
            boolean hasUncookedStew = Rs2Bank.hasBankItem("Uncooked stew", true);
            boolean missingIngredients = !Rs2Bank.hasBankItem("Bowl of water", true) &&
                    !Rs2Bank.hasBankItem("Potato", true) &&
                    !Rs2Bank.hasBankItem("Incomplete stew", true);

            return hasUncookedStew && missingIngredients;
        } else {
            // Bank is not open, check the inventory
            boolean inventoryHasStew = Rs2Inventory.hasItemAmount("Uncooked Stew", 28) ||
                    Rs2Inventory.hasItem("Stew", true) ||
                    Rs2Inventory.hasItem("Burnt stew", true);

            return Rs2Inventory.isEmpty() || inventoryHasStew;
        }
    }


    private void cookItem(String uncookedItemName) {
        System.out.println("Entering cookItem");

        // Step 1: Find and interact with the nearest range (or fallback to a preset location)
        findAndInteractWithNearestRange();
        Microbot.naturalMouse.moveOffScreen();

        // Step 2: Set the bot status to "Cooking <uncookedItemName>"
        Microbot.status = "Cooking " + uncookedItemName;

        // Step 3: Perform antiban actions and take micro breaks if necessary
        Rs2Antiban.actionCooldown();
        Rs2Antiban.takeMicroBreakByChance();

        // Step 4: Wait until the player starts cooking (based on animation or interface)
        sleepUntil(() -> Rs2Player.getAnimation() != AnimationID.IDLE);

        // Step 5: Wait until all uncooked items are gone, or the player stops cooking
        sleepUntilTrue(() -> !hasRawItem(uncookedItemName) && !Rs2Player.isAnimating(3500)
                || Rs2Dialogue.isInDialogue() || Rs2Player.isMoving(), 500, 30000);
    }

    public static void manageRunEnergy() {
        int energy = Microbot.getClient().getEnergy();  // Get current run energy level
        boolean isRunEnabled = Microbot.getVarbitPlayerValue(173) == 1;  // Check if run is enabled

        if (energy > 25 && !isRunEnabled) {
            toggleRunEnergy(true);  // Enable run if energy is above 60% and run is not enabled
        } else if (energy <= 25 && isRunEnabled) {
            toggleRunEnergy(false);  // Disable run if energy is 60% or lower and run is enabled
        }
    }



    private boolean hasRawItem(String uncookedItemName) {
        // Check the bank or inventory for the uncooked item
        if (Rs2Bank.isOpen()) {
            return Rs2Bank.hasBankItem(uncookedItemName, true); // Check the bank for the item
        }
        return Rs2Inventory.hasItem(uncookedItemName, true); // Check the inventory for the item
    }


    public void findAndInteractWithNearestRange() {
        System.out.println("Entering find nearest range");

        // Step 1: Get the player's current position
        WorldPoint playerPosition = Rs2Player.getWorldLocation();
        if (playerPosition == null) {
            System.out.println("Player position could not be determined.");
            return;
        }
        System.out.println("Player location: " + playerPosition);

        // Step 2: Retrieve all game objects within 15 tiles of the player
        List<GameObject> nearbyObjects = Rs2GameObject.getGameObjects();
        if (nearbyObjects == null || nearbyObjects.isEmpty()) {
            System.out.println("Found 0 nearby objects.");
            return;
        }

        // Step 3: Fetch the list of range object IDs from the getRangeObjectIds method
        int[] rangeObjectIds = getRangeObjectIds();

        // Step 4: Iterate over nearby objects to find any matching range or stove
        boolean rangeFound = false;
        for (GameObject obj : nearbyObjects) {
            int objId = obj.getId();

            // Step 5: Check if the object ID matches any of the range object IDs
            if (Arrays.stream(rangeObjectIds).anyMatch(id -> id == objId)) {

                    Rs2Camera.turnTo(obj.getLocalLocation());


                // If a match is found, interact with the object using the "Cook" action
                boolean interactionSuccess = Rs2GameObject.interact(obj, "Cook");
                if (interactionSuccess) {
                    System.out.println("Successfully interacted with object ID: " + objId + " using 'Cook'");
                } else {
                    System.out.println("Failed to interact with object ID: " + objId);
                }

                // Step 6: Wait until the player stops moving and the cooking widget appears
                boolean widgetAppeared = sleepUntil(() -> !Rs2Player.isMoving() &&
                        Rs2Widget.findWidget("How many would you like to cook?", null, false) != null, 35000);
                if (widgetAppeared) {
                    System.out.println("Cooking widget appeared, pressing space to confirm.");
                    Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                } else {
                    System.out.println("Cooking widget did not appear.");
                }

                rangeFound = true;
                break;
            } else {
                System.out.println("No match for object ID: " + objId);
            }
        }

        if (!rangeFound) {
            System.out.println("No range found within 15 tiles.");
        }
    }



    // Method to fetch range object IDs from the ObjectID class
    private int[] getRangeObjectIds() {
        return new int[] {
                ObjectID.STOVE, ObjectID.STOVE_9086, ObjectID.STOVE_9087, ObjectID.STOVE_12269,
                ObjectID.GOBLIN_STOVE, ObjectID.GOBLIN_STOVE_25441, ObjectID.SIMPLE_STOVE,
                ObjectID.COOKING_STOVE, ObjectID.STOVE_51540, ObjectID.COOKING_RANGE,
                ObjectID.RANGE, ObjectID.COOKING_RANGE_4172, ObjectID.RANGE_7183,
                ObjectID.RANGE_7184, ObjectID.COOKING_RANGE_8750, ObjectID.RANGE_9682,
                ObjectID.RANGE_9736, ObjectID.RANGE_12102, ObjectID.RANGE_12611,
                ObjectID.STEEL_RANGE, ObjectID.STEEL_RANGE_13540, ObjectID.STEEL_RANGE_13541,
                ObjectID.FANCY_RANGE, ObjectID.FANCY_RANGE_13543, ObjectID.FANCY_RANGE_13544,
                ObjectID.COOKING_RANGE_16641, ObjectID.COOKING_RANGE_16893, ObjectID.RANGE_21792,
                ObjectID.COOKING_RANGE_22154, ObjectID.RANGE_22713, ObjectID.RANGE_22714,
                ObjectID.RANGE_25730, ObjectID.RANGE_26181, ObjectID.RANGE_26182,
                ObjectID.RANGE_26183, ObjectID.RANGE_26184, ObjectID.RANGE_27516,
                ObjectID.RANGE_27517, ObjectID.RANGE_27724, ObjectID.RANGE_31631,
                ObjectID.COOKING_RANGE_35877, ObjectID.RANGE_35980, ObjectID.RANGE_36077,
                ObjectID.RANGE_36699, ObjectID.RANGE_37728, ObjectID.RANGE_39391,
                ObjectID.RANGE_39458, ObjectID.RANGE_39490, ObjectID.RANGE_40068,
                ObjectID.RANGE_40149, ObjectID.COOKING_RANGE_41423, ObjectID.RANGE_47392,
                ObjectID.RANGE_47925, ObjectID.RANGE_48169, ObjectID.RANGE_50563,
                ObjectID.RANGE_54049, ObjectID.OVEN, ObjectID.SMALL_OVEN,
                ObjectID.SMALL_OVEN_13534, ObjectID.SMALL_OVEN_13535, ObjectID.LARGE_OVEN,
                ObjectID.LARGE_OVEN_13537, ObjectID.LARGE_OVEN_13538, ObjectID.OVEN_52647,
                ObjectID.OVEN_52648
        };
    }

    private void prepareCake() {
        System.out.println("Entered prepare cake method");
        if (!Rs2Bank.isOpen() && Rs2Inventory.contains(1944) && Rs2Inventory.contains("Bucket of milk") && Rs2Inventory.contains("Pot of flour") && Rs2Inventory.contains("Cake tin")) {
            interactWithCakeTin();
            depositAndWithdrawItems();
        } else {
            if (Rs2Bank.isOpen() && Rs2Bank.hasItem("Bucket of milk") && Rs2Bank.hasItem("Pot of flour") && Rs2Bank.hasItem("Cake tin") && Rs2Bank.hasItem(1944)) {
                System.out.println("Inventory was empty of useful items");
                depositAndWithdrawItems();
                interactWithCakeTin();
            }
            if (!Rs2Bank.isOpen()) {
                walkToBanker();
                if (!Rs2Bank.isOpen()) {openNearestBank();}
                System.out.println("Attempted to open the bank");
                sleepUntil(Rs2Bank::isOpen, 30000);
            }

            if (!Rs2Bank.hasItem(1944)) {
                Microbot.log("No more egg found in the bank");
            }
        }
    }

    private void interactWithCakeTin() {
        // Check if the necessary items are in the inventory
        if (Rs2Inventory.hasItem("Cake tin") &&
                Rs2Inventory.hasItem("Egg") &&
                Rs2Inventory.hasItem("Bucket of Milk") &&
                Rs2Inventory.hasItem("Pot of flour")) {

            sleep(550, 880);  // Mimic human-like delay

            // Use Rs2Inventory.combineClosest to combine the Cake tin with one of the ingredients
            Rs2Inventory.combineClosest("Cake tin", "Egg");

            // Press space to confirm the action after combining
            clickWidgetWithRetry(17694734, 5);

            // Move the mouse outside the screen (mimic human action)
            Microbot.naturalMouse.moveOffScreen();

            sleepUntilIngredientHasRunOut("Egg");
        } else {
            System.out.println("Missing ingredients for Cake.");
        }
    }

    public boolean clickWidgetWithRetry(int id, int maxRetries) {
        int attempts = 0;

        while (attempts < maxRetries) {
            if (clickWidget(id)) {
                return true;  // If successful, exit early
            }

            // Sleep for a random time between 400 and 800 milliseconds
            sleep(400, 800);

            attempts++;
        }

        return false;  // Return false if all attempts fail
    }

    private void depositAndWithdrawItems() {
        // Deposit all items in the inventory
        Rs2Bank.depositAll();
        sleep(400, 800);

        // Withdraw required items if bank is open and inventory is empty
        if (Rs2Bank.isOpen() && Rs2Inventory.isEmpty()) {
            Rs2Bank.withdrawX("Cake tin", 7);
            sleep(500, 800);

            Rs2Bank.withdrawX("Egg", 7);
            sleep(500, 800);

            Rs2Bank.withdrawX("Bucket of Milk", 7);
            sleep(500, 800);

            Rs2Bank.withdrawX("Pot of flour", 7);
            sleep(500, 800);

            // Close the bank after completing withdrawals
            Rs2Bank.closeBank();
        }
    }


    @Override
    public void shutdown() {
        super.shutdown();
    }
}
