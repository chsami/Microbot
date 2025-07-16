package net.runelite.client.plugins.microbot.zerozero.bluedragons;

import lombok.Getter;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class BlueDragonsScript extends Script {

    public static BlueDragonState currentState;
    private String lastChatMessage = "";
    private BlueDragonsConfig config;
    public static final WorldPoint SAFE_SPOT = new WorldPoint(2918, 9781, 0);
    @Getter
    private Integer currentTargetId = null;

    @Inject
    private BlueDragonsOverlay overlay;
    
    private long lastLootMessageTime = 0;

    private static final int BLUE_DRAGON_ID_1 = 265;
    private static final int BLUE_DRAGON_ID_2 = 266;
    private static final int MIN_WORLD = 302;
    private static final int MAX_WORLD = 580;

    private boolean isInventoryFull() {
        boolean simpleFull = Rs2Inventory.isFull();
        if (!simpleFull) {
            return Rs2Inventory.getEmptySlots() <= 0;
        }
        
        return true;
    }

    public boolean run(BlueDragonsConfig config) {
        this.config = config;
        currentState = BlueDragonState.STARTING;
        
        if (overlay != null) {
            overlay.resetStats();
            overlay.setScript(this);
            overlay.setConfig(config);
        }
        
        final int[] consecutiveErrors = {0};
        final int MAX_CONSECUTIVE_ERRORS = 5;
        
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !Microbot.isLoggedIn()) return;

                if (isInventoryFull() && currentState != BlueDragonState.BANKING && currentState != BlueDragonState.STARTING) {
                    logOnceToChat("Global safety: Inventory is full. Switching to BANKING state.", false, config);
                    currentState = BlueDragonState.BANKING;
                }

                switch (currentState) {
                    case STARTING:
                        determineStartingState(config);
                        break;

                    case BANKING:
                        handleBanking(config);
                        break;

                    case TRAVEL_TO_DRAGONS:
                        handleTravelToDragons();
                        break;

                    case FIGHTING:
                        handleFighting(config);
                        break;
                        
                    case LOOTING:
                        handleLooting(config);
                        break;
                }
                
                consecutiveErrors[0] = 0;
                
            } catch (Exception ex) {
                consecutiveErrors[0]++;
                logOnceToChat("Error in Blue Dragons script: " + ex.getMessage(), false, config);
                
                if (config.debugLogs()) {
                    StringBuilder stackTrace = new StringBuilder();
                    for (StackTraceElement element : ex.getStackTrace()) {
                        stackTrace.append(element.toString()).append("\n");
                    }
                    logOnceToChat("Stack trace: " + stackTrace.toString(), true, config);
                }
                
                if (consecutiveErrors[0] >= MAX_CONSECUTIVE_ERRORS) {
                    logOnceToChat("Too many consecutive errors. Stopping script for safety.", false, config);
                    shutdown();
                }
                
                if (Rs2Player.isInCombat()) {
                    logOnceToChat("In combat during error - attempting to eat food", true, config);
                    Rs2Player.eatAt(config.eatAtHealthPercent());
                }
                
                sleep(1000 * Math.min(consecutiveErrors[0], 5));
            }
        }, 0, 100, TimeUnit.MILLISECONDS);

        return true;
    }

    private void handleBanking(BlueDragonsConfig config) {
        logOnceToChat("Traveling to Falador West bank for depositing looted items.", true, config);
        logOnceToChat("Current location: " + Microbot.getClient().getLocalPlayer().getWorldLocation(), true, config);

        if (Rs2Bank.walkToBankAndUseBank(BankLocation.FALADOR_WEST)) {
            logOnceToChat("Opened bank. Depositing loot.", true, config);
            Rs2Bank.depositAll("Dragon bones");
            Rs2Bank.depositAll("Dragon spear");
            Rs2Bank.depositAll("Shield left half");
            Rs2Bank.depositAll("Scaly blue dragonhide");

            if (config.lootEnsouledHead()) {
                Rs2Bank.depositAll("Ensouled dragon head");
            }

            if (config.lootDragonhide()) {
                Rs2Bank.depositAll("Blue dragonhide");
            }
            logOnceToChat("Withdrawing food for combat.", true, config);
            withdrawFood(config);
            Rs2Bank.closeBank();
            logOnceToChat("Banking complete. Transitioning to travel state.", true, config);
            currentState = BlueDragonState.TRAVEL_TO_DRAGONS;
        }
        else {
            logOnceToChat("Failed to reach the bank.", true, config);
        }
    }

    private void determineStartingState(BlueDragonsConfig config) {
        boolean hasTeleport = hasTeleportToFalador();
        boolean hasAgilityOrKey = Microbot.getClient().getRealSkillLevel(Skill.AGILITY) >= 70 || hasDustyKey();

        if (!hasTeleport) {
            logOnceToChat("Missing teleport to Falador or required runes.", false, config);
        }

        if (!hasAgilityOrKey) {
            logOnceToChat("Requires Agility level 70 or a Dusty Key.", false, config);
        }

        if (hasTeleport && hasAgilityOrKey) {
            currentState = BlueDragonState.BANKING;
        } else {
            logOnceToChat("Starting conditions not met. Stopping the plugin.", false, config);
            shutdown();
        }
    }

    private boolean hasTeleportToFalador() {
        logOnceToChat("Checking for Falador teleport or required runes.", true, config);

        if (Rs2Inventory.contains("Falador teleport")) {
            logOnceToChat("Found Falador teleport in inventory.", true, config);
            return true;
        }

        int lawRuneId = ItemID.LAWRUNE;
        int waterRuneId = ItemID.WATERRUNE;
        int dustRuneId = ItemID.DUSTRUNE;
        int airRuneId = ItemID.AIRRUNE;

        int requiredLawRunes = 1;
        int requiredAirRunes = 3;
        int requiredWaterRunes = 1;

        boolean runePouchInInventory = Rs2Inventory.contains("Rune pouch") || Rs2Inventory.contains("Divine rune pouch");
        boolean hasLawRunes = checkRuneAvailability(lawRuneId, requiredLawRunes, runePouchInInventory);
        boolean hasWaterRunes = checkRuneAvailability(waterRuneId, requiredWaterRunes, runePouchInInventory);
        boolean hasAirOrDustRunes = checkRuneAvailability(dustRuneId, requiredAirRunes, runePouchInInventory) ||
                checkRuneAvailability(airRuneId, requiredAirRunes, runePouchInInventory);

        return hasLawRunes && hasWaterRunes && hasAirOrDustRunes;
    }

    private boolean checkRuneAvailability(int runeId, int requiredAmount, boolean checkRunePouch) {
        boolean inInventory = Rs2Inventory.hasItemAmount(runeId, requiredAmount);
        boolean inRunePouch = checkRunePouch && Rs2RunePouch.contains(runeId, requiredAmount);
        return inInventory || inRunePouch;
    }

    private boolean hasDustyKey() {
        return Rs2Inventory.contains("Dusty key");
    }

    private void handleTravelToDragons() {
        logOnceToChat("Traveling to dragons.", false, config);
        logOnceToChat("Player location before travel: " + Microbot.getClient().getLocalPlayer().getWorldLocation(), true, config);

        if (isPlayerAtSafeSpot()) {
            logOnceToChat("Already at safe spot. Transitioning to FIGHTING state.", true, config);
            currentState = BlueDragonState.FIGHTING;
            return;
        }

        boolean walkAttemptSuccessful = Rs2Walker.walkTo(SAFE_SPOT);
        
        if (!walkAttemptSuccessful) {
            logOnceToChat("Failed to start walking to safe spot. Will retry next tick.", true, config);
            return;
        }
        
        boolean reachedNearSafeSpot = sleepUntil(() -> Rs2Player.distanceTo(SAFE_SPOT) <= 5, 60000);
        
        if (reachedNearSafeSpot || Rs2Player.distanceTo(SAFE_SPOT) <= 20) {
            logOnceToChat("Close to safe spot. Using precise movement for final approach.", true, config);
            moveToSafeSpot();
        } else {
            logOnceToChat("Failed to get close to safe spot within timeout.", true, config);
            
            int distance = Rs2Player.distanceTo(SAFE_SPOT);
            logOnceToChat("Current distance to safe spot: " + distance, true, config);
            
            if (distance < 50) {
                moveToSafeSpot();
            } else {
                logOnceToChat("Too far from safe spot. Returning to banking state to try again.", true, config);
                currentState = BlueDragonState.BANKING;
                return;
            }
        }

        if (hopIfPlayerAtSafeSpot()) {
            logOnceToChat("Hopped worlds due to player detection at safe spot.", true, config);
            return;
        }
        
        if (isPlayerAtSafeSpot()) {
            logOnceToChat("Reached safe spot. Transitioning to FIGHTING state.", true, config);
            currentState = BlueDragonState.FIGHTING;
        } else {
            logOnceToChat("Still not at safe spot after multiple attempts. Will continue from current position.", true, config);
            currentState = BlueDragonState.FIGHTING;
        }
    }

    private void handleFighting(BlueDragonsConfig config) {
        if (currentState != BlueDragonState.FIGHTING) {
            logOnceToChat("Not in FIGHTING state but handleFighting was called. Current state: " + currentState, true, config);
            return;
        }
        
        if (isInventoryFull()) {
            logOnceToChat("Inventory is full. Switching to BANKING state.", false, config);
            currentState = BlueDragonState.BANKING;
            return;
        }
        
        if (checkForLoot()) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastLootMessageTime > 10000) {
                logOnceToChat("Found loot on the ground. Switching to LOOTING state.", false, config);
                lastLootMessageTime = currentTime;
            }
            currentState = BlueDragonState.LOOTING;
            return;
        }
        
        if (!isPlayerAtSafeSpot()) {
            logOnceToChat("Not at safe spot. Moving back before continuing to fight.", true, config);
            moveToSafeSpot();
            return;
        }

        Rs2Player.eatAt(config.eatAtHealthPercent());

        if (!underAttack()) {
            NPC dragon = getAvailableDragon();
            if (dragon != null) {
                logOnceToChat("Found available dragon. Attacking.", true, config);
                if (attackDragon(dragon)) {
                    currentTargetId = dragon.getId();
                }
            } else {
                logOnceToChat("No dragons available to attack.", true, config);
            }
        } else {
            if (!isPlayerAtSafeSpot()) {
                moveToSafeSpot();
            }
        }
    }
    
    private boolean checkForLoot() {
        String[] lootItems = {
            "Dragon bones", "Blue dragonhide", "Ensouled dragon head",
            "Dragon spear", "Shield left half", "Scaly blue dragonhide"
        };
        
        LootingParameters params = new LootingParameters(15, 1, 1, 0, false, true, lootItems);
        
        return Rs2GroundItem.lootItemsBasedOnNames(params);
    }
    
    private void handleLooting(BlueDragonsConfig config) {
        if (currentState != BlueDragonState.LOOTING) {
            return;
        }
        
        if (isInventoryFull()) {
            logOnceToChat("Inventory is full, switching to BANKING state.", false, config);
            currentState = BlueDragonState.BANKING;
            return;
        }
        
        boolean lootedAnything = false;
        
        if (!isInventoryFull()) {
            lootedAnything |= lootItem("Dragon bones");
        } 
        if (!isInventoryFull()) {
            lootedAnything |= lootItem("Dragon spear");
        }
        if (!isInventoryFull()) {
            lootedAnything |= lootItem("Shield left half");
        }
        if (!isInventoryFull()) {
            lootedAnything |= lootItem("Scaly blue dragonhide");
        }
        
        if (config.lootDragonhide() && !isInventoryFull()) {
            lootedAnything |= lootItem("Blue dragonhide");
        }
        
        if (config.lootEnsouledHead() && !isInventoryFull()) {
            lootedAnything |= lootItem("Ensouled dragon head");
        }
        
        sleep(300, 500);
        
        if (isInventoryFull()) {
            logOnceToChat("Inventory is full after looting, switching to BANKING state.", false, config);
            currentState = BlueDragonState.BANKING;
            return;
        }
        
        if (!checkForLoot() || !lootedAnything) {
            logOnceToChat("Finished looting. Returning to combat.", true, config);
            currentState = BlueDragonState.FIGHTING;
            currentTargetId = null;
        }
    }
    
    private boolean lootItem(String itemName) {
        if (!isInventoryFull()) {
            LootingParameters params = new LootingParameters(15, 1, 1, 0, false, true, itemName);
            boolean looted = Rs2GroundItem.lootItemsBasedOnNames(params);
            if (looted) {
                logOnceToChat("Looted: " + itemName, true, config);
                
                if (itemName.equalsIgnoreCase("Dragon bones")) {
                    BlueDragonsOverlay.bonesCollected++;
                    Microbot.log("Bones looted: " + BlueDragonsOverlay.bonesCollected);
                } else if (itemName.equalsIgnoreCase("Blue dragonhide")) {
                    BlueDragonsOverlay.hidesCollected++;
                }
                
                return true;
            }
        }
        return false;
    }

    private void withdrawFood(BlueDragonsConfig config) {
        Rs2Food food = config.foodType();
        int requiredAmount = config.foodAmount();

        if (food == null || requiredAmount <= 0) {
            logOnceToChat("Invalid food type or amount in configuration.", true, config);
            return;
        }

        int currentFoodInInventory = Rs2Inventory.count(food.getName());
        int deficit = requiredAmount - currentFoodInInventory;

        if (deficit <= 0) {
            logOnceToChat("Inventory already contains the required amount of " + food.getName() + ".", true, config);
            return;
        }

        if (!Rs2Bank.isOpen()) {
            logOnceToChat("Bank is not open. Cannot withdraw food.", true, config);
            return;
        }

        boolean bankLoaded = sleepUntil(() -> !Rs2Bank.bankItems().isEmpty(), 5000);
        if (!bankLoaded) {
            logOnceToChat("Bank items did not load in time.", true, config);
            return;
        }

        if (!Rs2Bank.hasItem(food.getName())) {
            logOnceToChat(food.getName() + " not found in the bank. Stopping script.", true, config);
            shutdown();
            return;
        }

        logOnceToChat("Attempting to withdraw " + deficit + "x " + food.getName(), false, config);

        int retryCount = 0;
        final int maxRetries = 3;
        boolean success = false;

        while (retryCount < maxRetries && !success) {
            success = Rs2Bank.withdrawX(food.getName(), deficit, true);
            if (!success) {
                retryCount++;
                sleep(500);
                logOnceToChat("Retrying withdrawal of " + food.getName() + " (" + retryCount + ")", true, config);
            }
        }

        if (!success) {
            logOnceToChat("Unable to withdraw " + food.getName() + " after multiple attempts. Stopping script.", true, config);
            shutdown();
        } else {
            logOnceToChat("Successfully withdrew " + deficit + "x " + food.getName(), false, config);
        }
    }

    private NPC getAvailableDragon() {
        NPC dragon = Rs2Npc.getNpc("Blue dragon");
        logOnceToChat("Found dragon: " + (dragon != null ? "Yes (ID: " + dragon.getId() + ")" : "No"), true, config);
        
        if (dragon != null) {
            boolean correctId = (dragon.getId() == BLUE_DRAGON_ID_1 || dragon.getId() == BLUE_DRAGON_ID_2);
            logOnceToChat("Dragon has correct ID (265 or 266): " + correctId, true, config);
            
            boolean hasLineOfSight = Rs2Npc.hasLineOfSight(new Rs2NpcModel(dragon));
            logOnceToChat("Has line of sight to dragon: " + hasLineOfSight, true, config);
            
            if (correctId && hasLineOfSight) {
                return dragon;
            }
        }
        return null;
    }

    private boolean attackDragon(NPC dragon) {
        final int dragonId = dragon.getId();
        
        if (Rs2Combat.inCombat() && dragon.getInteracting() != Microbot.getClient().getLocalPlayer()) {
            logOnceToChat("Cannot attack dragon - player is in combat with different target.", true, config);
            return false;
        }
        
        if (Rs2Npc.attack(dragon)) {
            boolean dragonKilled = sleepUntil(() -> Rs2Npc.getNpc(dragonId) == null, 60000);
            
            if (dragonKilled) {
                logOnceToChat("Dragon killed. Transitioning to looting state.", true, config);
                BlueDragonsOverlay.dragonKillCount++;
                
                sleep(600, 900);
                currentState = BlueDragonState.LOOTING;
            }
            
            return true;
        }
        return false;
    }

    private boolean isPlayerAtSafeSpot() {
        return SAFE_SPOT.equals(Microbot.getClient().getLocalPlayer().getWorldLocation());
    }

    private void moveToSafeSpot() {
		Microbot.pauseAllScripts.compareAndSet(false, true);
        
        int distance = Rs2Player.distanceTo(SAFE_SPOT);
        
        logOnceToChat("Moving to safe spot. Distance: " + distance, true, config);
        
        if (distance > 15) {
            logOnceToChat("Using walkTo to approach safe spot", true, config);
            Rs2Walker.walkTo(SAFE_SPOT);
            
            sleepUntil(() -> Rs2Player.distanceTo(SAFE_SPOT) <= 5, 30000);
        }
        
        if (!isPlayerAtSafeSpot()) {
            logOnceToChat("Using walkFastCanvas for final approach to safe spot", true, config);
            Rs2Walker.walkFastCanvas(SAFE_SPOT);
            sleepUntil(this::isPlayerAtSafeSpot, 15000);
        }

        if (hopIfPlayerAtSafeSpot()) {
            return;
        }

        if (!isPlayerAtSafeSpot()) {
            logOnceToChat("Failed to reach exact safe spot. Will continue with current position.", true, config);
        } else {
            logOnceToChat("Successfully reached safe spot.", true, config);
        }

		Microbot.pauseAllScripts.compareAndSet(true, false);
    }

    private boolean hopIfPlayerAtSafeSpot() {
        boolean otherPlayersAtSafeSpot = false;
        List<Player> players = Rs2Player.getPlayers();

        for (Player player : players) {
            if (player != null &&
                !player.equals(Microbot.getClient().getLocalPlayer()) &&
                player.getWorldLocation().distanceTo(SAFE_SPOT) <= 1) {
                otherPlayersAtSafeSpot = true;
                break;
            }
        }
                
        if (otherPlayersAtSafeSpot) {
            logOnceToChat("Player detected at safe spot. Pausing script and hopping worlds.", false, config);
            Microbot.pauseAllScripts.set(true);
            
            boolean hopSuccess = Microbot.hopToWorld(findRandomWorld());
            sleep(5000);
            
            Microbot.pauseAllScripts.set(false);
            return hopSuccess;
        }
        
        return false;
    }
    
    private int findRandomWorld() {
        int currentWorld = Microbot.getClient().getWorld();
        int targetWorld;
        do {
            targetWorld = MIN_WORLD + new java.util.Random().nextInt(MAX_WORLD - MIN_WORLD);
        } while (targetWorld == currentWorld);
        return targetWorld;
    }

    void logOnceToChat(String message, boolean isDebug, BlueDragonsConfig config) {
        if (message == null || message.trim().isEmpty()) {
            message = "Unknown log message (null or empty)...";
        }
        if (isDebug && (config == null || !config.debugLogs())) {
            return;
        }
        if (!message.equals(lastChatMessage)) {
            Microbot.log(message);
            lastChatMessage = message;
        }
    }

    public void updateConfig(BlueDragonsConfig config) {
        logOnceToChat("Applying new configuration to Blue Dragons script.", true, config);
        this.config = config;
        
        if (overlay != null) {
            overlay.setConfig(config);
        }
        
        withdrawFood(config);
    }

    public void shutdown() {
        super.shutdown();
        Rs2Walker.disableTeleports = false;
        if (overlay != null) {
            overlay.resetStats();
        }
        currentState = BlueDragonState.STARTING;
        currentTargetId = null;
    }

    private boolean underAttack() {
        return Rs2Combat.inCombat();
    }
}