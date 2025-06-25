package net.runelite.client.plugins.microbot.MKE.wintertodt.startup.location;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;

import java.util.Arrays;
import java.util.List;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;
import static net.runelite.client.plugins.microbot.util.Global.sleepGaussian;

/**
 * Handles smart navigation to Wintertodt from anywhere in the game.
 * 
 * Navigation Strategy:
 * 1. Check if already at Wintertodt
 * 2. Check for direct teleportation options to Wintertodt
 * 3. If far from bank, use teleports to get closer to a bank first
 * 4. Bank unnecessary items
 * 5. Get Games necklace or other teleport items if needed
 * 6. Navigate to Wintertodt using optimal transportation
 * 
 * @author MakeCD
 * @version 1.0.0
 */
public class WintertodtLocationManager {
    
    // Key locations
    private static final WorldPoint WINTERTODT_BANK = new WorldPoint(1640, 3944, 0);
    private static final int GAME_ROOM_Y_THRESHOLD = 3967; // Y-coordinate north of doors = inside game room
    private static final WorldPoint WINTERTODT_BOSS_ROOM = new WorldPoint(1630, 3982, 0);
    
    // Distance thresholds
    private static final int WINTERTODT_AREA_RADIUS = 15;
    private static final int BANK_PROXIMITY_RADIUS = 3;
    private static final int FAR_FROM_BANK_THRESHOLD = 100;
    
    // Games necklace items
    private static final List<Integer> GAMES_NECKLACE_ITEMS = Arrays.asList(
        ItemID.GAMES_NECKLACE1, ItemID.GAMES_NECKLACE2, ItemID.GAMES_NECKLACE3,
        ItemID.GAMES_NECKLACE4, ItemID.GAMES_NECKLACE5, ItemID.GAMES_NECKLACE6,
        ItemID.GAMES_NECKLACE7, ItemID.GAMES_NECKLACE8
    );
    
    // Emergency teleport items to withdraw from bank
    private static final List<Integer> EMERGENCY_TELEPORT_ITEMS = Arrays.asList(
        ItemID.GAMES_NECKLACE8,
        ItemID.RING_OF_DUELING8,
        ItemID.AMULET_OF_GLORY4,
        ItemID.VARROCK_TELEPORT,
        ItemID.DRAMEN_STAFF,
        ItemID.LUNAR_STAFF
    );
    
    private NavigationState currentState = NavigationState.ANALYZING;
    private String statusMessage = "Analyzing current location...";
    
    public enum NavigationState {
        ANALYZING("Analyzing current location"),
        ALREADY_AT_WINTERTODT("Already at Wintertodt"),
        CHECKING_DIRECT_TELEPORTS("Checking for direct teleports"),
        MOVING_TO_BANK("Moving to nearest bank"),
        BANKING_ITEMS("Banking unnecessary items"),
        GETTING_TELEPORT_ITEMS("Getting teleport items"),
        TELEPORTING_TO_WINTERTODT("Teleporting to Wintertodt"),
        WALKING_TO_WINTERTODT("Walking to Wintertodt"),
        COMPLETED("Navigation completed");
        
        private final String description;
        
        NavigationState(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Tries to leave the Wintertodt arena safely using CUSTOM door interaction.
     * Never relies on webwalker - always uses manual door clicking and dialog handling.
     * 
     * Sequential flow:
     * 1. Walk close to the door
     * 2. Handle any existing dialog first
     * 3. Click the door to trigger dialog
     * 4. Confirm dialog by pressing 1
     * 5. Verify we're outside
     *
     * @return true once the player is outside (south of the doors)
     */
    public static boolean attemptLeaveWintertodt()
    {
        /* Already outside? → nothing else to do */
        if (!isInsideGameRoom())
        {
            return true;
        }

        /* Step 1: Walk close to the door first */
        WorldPoint doorTile = new WorldPoint(1630, 3970, 0);
        if (Rs2Player.getWorldLocation().distanceTo(doorTile) > 4) {
            Microbot.log("Walking closer to Wintertodt exit door...");
            Rs2Walker.walkTo(doorTile, 3);
            return false; // Continue next tick after walking
        }

        /* NEW: Check points before leaving - wait for rewards if we have enough */
        int currentPoints = getWintertodtPoints();
        if (currentPoints >= 500) {
            Microbot.log("Have " + currentPoints + " points - waiting near door for round to end naturally");
            // Just wait here - don't interact with door yet
            return false;
        }

        /* Step 2: Handle any existing dialog first */
        if (Rs2Widget.hasWidget("Leave and lose")
                || Rs2Widget.hasWidget("Wintertodt is still alive")) {
            Microbot.log("Confirming leave dialog...");
            Rs2Keyboard.typeString("1");
            sleepGaussian(1200, 200);
            return false; // Wait for dialog to process
        }

        /* Step 3: Click the door to trigger the dialog */
        GameObject exitDoor = Rs2GameObject.getGameObject(ObjectID.DOORS_OF_DINH, 6);
        if (exitDoor != null) {
            Microbot.log("Clicking Wintertodt exit door...");
            if (Rs2GameObject.interact(exitDoor, "Leave")
                    || Rs2GameObject.interact(exitDoor, "Exit")
                    || Rs2GameObject.interact(exitDoor, "Open")) {
                sleepGaussian(800, 200); // Wait for dialog to appear
                return false; // Continue next tick to handle dialog
            }
        } else {
            Microbot.log("Could not find Wintertodt exit door, trying to walk closer...");
            Rs2Walker.walkTo(new WorldPoint(1627, 3968, 0), 1); // Try original door coordinates
            return false;
        }

        /* Step 4: If we get here and still inside, something went wrong - try again */
        Microbot.log("Still inside Wintertodt, retrying leave sequence...");
        return false;
    }

    /**
     * Gets the current Wintertodt points from the game interface.
     * 
     * @return current points, or 0 if not available
     */
    private static int getWintertodtPoints() {
        try {
            String pointsText = Rs2Widget.getChildWidgetText(396, 6);
            if (pointsText == null || pointsText.isEmpty()) {
                return 0;
            }
            
            // Parse text like "Points<br>25" or just "25"
            String[] parts = pointsText.split("<br>");
            String numberPart = parts.length > 1 ? parts[1] : parts[0];
            
            // Extract just the numbers
            String pointsStr = numberPart.replaceAll("\\D+", "");
            
            if (!pointsStr.isEmpty()) {
                return Integer.parseInt(pointsStr);
            }
            
            return 0;
            
        } catch (Exception e) {
            System.err.println("Error getting Wintertodt points: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Main navigation method - handles getting to Wintertodt from anywhere.
     * This method checks for direct teleports first, then handles banking for teleport items,
     * then lets the webwalker handle all actual transportation using the optimal route.
     */
    public boolean navigateToWintertodt() {
        try {
            // Step 1: Check if already at Wintertodt
            if (isAtWintertodt()) {
                currentState = NavigationState.ALREADY_AT_WINTERTODT;
                statusMessage = "Already at Wintertodt area";
                Microbot.log("Player is already at Wintertodt - no navigation needed.");
                return true;
            }

            boolean walkToWintertodtBank = false;

            if (isInsideGameRoom()) {
                currentState = NavigationState.ALREADY_AT_WINTERTODT;
                statusMessage = "Inside Wintertodt game room - leaving through doors";
                Microbot.log("Player is inside Wintertodt game room, attempting custom door exit...");
                
                // Use custom door leaving logic - never webwalker
                if (attemptLeaveWintertodt()) {
                    Microbot.log("Successfully left Wintertodt game room");
                    walkToWintertodtBank = true;
                } else {
                    Microbot.log("Still attempting to leave Wintertodt game room...");
                    return false; // Keep trying to leave
                }
            }

            // Step 2: Check if we are close to Wintertodt
            int tilesToWintertodBank = Rs2Walker.getTotalTiles(WINTERTODT_BANK);
            if (tilesToWintertodBank < FAR_FROM_BANK_THRESHOLD) {  
                Microbot.log("Already close to Wintertodt - only walking to Wintertodt required");
                walkToWintertodtBank = true;
            }

            if (walkToWintertodtBank) {
                Rs2Walker.walkTo(WINTERTODT_BANK, BANK_PROXIMITY_RADIUS);
                if (sleepUntilTrue(this::isAtWintertodt, 1000, 180000)) { // 3 minutes for walking
                    currentState = NavigationState.COMPLETED;
                    statusMessage = "Successfully walked to Wintertodt";
                    Microbot.log("Successfully walked to Wintertodt");
                    return true;
                } else {
                    Microbot.log("Timeout while walking to Wintertodt - may still be travelling");
                }
            }
            
            // Step 3: Check if we have Games necklace for direct teleport
            if (hasGamesNecklace()) {
                currentState = NavigationState.CHECKING_DIRECT_TELEPORTS;
                statusMessage = "Found Games necklace - using direct teleport";
                Microbot.log("Found Games necklace - using direct teleport to Wintertodt");
                
                // Let webwalker handle the teleport automatically
                Rs2Walker.walkTo(WINTERTODT_BANK, BANK_PROXIMITY_RADIUS);
                
                if (sleepUntilTrue(this::isAtWintertodt, 1000, 60000)) { // 1 minute for teleport
                    currentState = NavigationState.COMPLETED;
                    statusMessage = "Successfully teleported to Wintertodt";
                    Microbot.log("Successfully teleported to Wintertodt");
                    return true;
                }
            }
            
            // Step 4: Get to bank and acquire useful teleport items
            if (!handleBankingPhase(tilesToWintertodBank)) {
                Microbot.log("Failed to complete banking phase");
                // Continue anyway - webwalker might still work
            }

            // Step 4: Let webwalker handle the navigation automatically
            currentState = NavigationState.WALKING_TO_WINTERTODT;
            statusMessage = "Using webwalker to navigate to Wintertodt...";
            
            Microbot.log("Using Rs2Walker to navigate to Wintertodt - will automatically use best route and teleports");
            
            // Rs2Walker will automatically handle the best route including all teleports
            Rs2Walker.walkTo(WINTERTODT_BANK, BANK_PROXIMITY_RADIUS);
            
            // Wait for arrival with generous timeout for long routes
            if (sleepUntilTrue(this::isAtWintertodt, 1000, 300000)) { // 5 minutes timeout
                currentState = NavigationState.COMPLETED;
                statusMessage = "Successfully navigated to Wintertodt";
                Microbot.log("Successfully navigated to Wintertodt using webwalker");
                return true;
            } else {
                Microbot.log("Timeout while navigating to Wintertodt - may still be travelling");
                return false;
            }
            
        } catch (Exception e) {
            Microbot.log("Error during navigation: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Checks if player is already in Wintertodt area.
     */
    private boolean isAtWintertodt() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        return playerLocation.distanceTo(WINTERTODT_BANK) <= WINTERTODT_AREA_RADIUS && playerLocation.getY() <= 3967;
    }

    /**
     * Checks if player is inside the Wintertodt game room (north of doors).
     */
    public static boolean isInsideGameRoom() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        return playerLocation.getY() > GAME_ROOM_Y_THRESHOLD && 
               playerLocation.distanceTo(WINTERTODT_BOSS_ROOM) <= 40;
    }
    
    
    /**
     * Checks if the player has a Games necklace for direct teleportation.
     */
    private boolean hasGamesNecklace() {
        try {
            // Check inventory for any Games necklace
            for (int gamesNecklaceId : GAMES_NECKLACE_ITEMS) {
                if (Rs2Inventory.hasItem(gamesNecklaceId)) {
                    Microbot.log("Found Games necklace (ID: " + gamesNecklaceId + ") in inventory");
                    return true;
                }
            }
            
            // Check equipped for any Games necklace
            for (int gamesNecklaceId : GAMES_NECKLACE_ITEMS) {
                if (Rs2Equipment.isWearing(gamesNecklaceId)) {
                    Microbot.log("Found Games necklace (ID: " + gamesNecklaceId + ") equipped");
                    return true;
                }
            }
            
            return false;
        } catch (Exception e) {
            Microbot.log("Error checking for Games necklace: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Handles the banking phase - walk to bank, bank items, get teleport items.
     */
    private boolean handleBankingPhase(int tilesToWintertodBank) {
        try {
            // Navigate to nearest bank
            currentState = NavigationState.MOVING_TO_BANK;
            statusMessage = "Walking to nearest bank...";

            WorldPoint nearestBank = Rs2Bank.getNearestBank().getWorldPoint();
            int tilesToClosestBank = Rs2Walker.getTotalTiles(nearestBank);

            if (tilesToWintertodBank < tilesToClosestBank + 50) {
                Microbot.log("Its faster to walk to Wintertodt than to walk other bank.");
                return true;
            }
            
            if (!Rs2Bank.walkToBank()) {
                Microbot.log("Failed to reach bank");
                return false;
            }

            // Prevent bug that causes bot to not being able to wear items in bank by adding inventory open command first
            if (!Rs2Inventory.isOpen()) {
                Rs2Inventory.open();
            }

            while (!Rs2Bank.isOpen() && Rs2Bank.isNearBank(15)) {
                Rs2Bank.openBank();
                sleepGaussian(1000, 200);
            }
            
            // Bank unnecessary items (keep essentials)
            currentState = NavigationState.BANKING_ITEMS;
            statusMessage = "Banking unnecessary items...";
            
            if (!bankUnnecessaryItems()) {
                Microbot.log("Failed to bank items");
                return false;
            }
            
            // Get teleport items if needed
            currentState = NavigationState.GETTING_TELEPORT_ITEMS;
            statusMessage = "Getting teleport items...";
            
            if (!ensureTeleportItems()) {
                Microbot.log("Failed to get teleport items");
                return false;
            }
            
            // Close bank
            Rs2Bank.closeBank();
            
            return true;
            
        } catch (Exception e) {
            Microbot.log("Error in banking phase: " + e.getMessage());
            return false;
        }
    }
    
    
    /**
     * Banks all items except essentials we want to keep.
     */
    private boolean bankUnnecessaryItems() {
        try {
            if (!Rs2Bank.isOpen()) {
                return false;
            }
            
            // Keep these items (don't bank them)
            String[] keepItems = {
                "Games necklace", "Ring of dueling", "Amulet of glory",
                "Coins", "teleport", "rune", "staff", "Bruma torch"
            };
            
            // Deposit everything except the items we want to keep
            Rs2Bank.depositAllExcept(keepItems);
            sleepGaussian(1000, 200);
            
            return true;
            
        } catch (Exception e) {
            Microbot.log("Error banking unnecessary items: " + e.getMessage());
            return false;
        }
    }
    
    
    /**
     * Ensures we have the necessary teleport items for getting to Wintertodt.
     */
    private boolean ensureTeleportItems() {
        try {
            if (!Rs2Bank.isOpen()) {
                return false;
            }
            
            // Check if we already have Games necklace
            boolean hasGamesNecklace = false;
            for (int gamesNecklaceId : GAMES_NECKLACE_ITEMS) {
                if (Rs2Inventory.hasItem(gamesNecklaceId)) {
                    hasGamesNecklace = true;
                    break;
                }
            }
            
            // If no Games necklace, try to get one
            if (!hasGamesNecklace) {
                for (int gamesNecklaceId : GAMES_NECKLACE_ITEMS) {
                    if (Rs2Bank.hasItem(gamesNecklaceId)) {
                        Rs2Bank.withdrawX(gamesNecklaceId, 1);
                        sleepGaussian(800, 200);
                        hasGamesNecklace = true;
                        break;
                    }
                }
            }
            
            // If still no Games necklace, get other emergency teleports
            if (!hasGamesNecklace) {
                Microbot.log("No Games necklace available - getting emergency teleports");
                
                // Get basic teleports and some coins
                if (Rs2Bank.hasItem("Coins")) {
                    Rs2Bank.withdrawX("Coins", 10000);
                    sleepGaussian(600, 150);
                }
                
                // Get a basic teleport
                for (int teleportId : EMERGENCY_TELEPORT_ITEMS) {
                    if (Rs2Bank.hasItem(teleportId)) {
                        Rs2Bank.withdrawX(teleportId, 1);
                        sleepGaussian(600, 150);
                        break;
                    }
                }
            }
            
            return true;
            
        } catch (Exception e) {
            Microbot.log("Error ensuring teleport items: " + e.getMessage());
            return false;
        }
    }
    
    // Removed navigateToWintertodtFromBank method - webwalker handles navigation directly
    
    /**
     * Resets the location manager state completely.
     */
    public void reset() {
        currentState = NavigationState.ANALYZING;
        statusMessage = "Location manager reset";
        
        // Reset any navigation flags or cached locations
        Microbot.log("Location manager state reset - ready for fresh navigation");
    }
    
    // Getters for external monitoring
    public NavigationState getCurrentState() { return currentState; }
    public String getStatusMessage() { return statusMessage; }
} 