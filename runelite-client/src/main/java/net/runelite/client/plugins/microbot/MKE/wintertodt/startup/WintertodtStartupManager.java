package net.runelite.client.plugins.microbot.MKE.wintertodt.startup;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.MKE.wintertodt.MKE_WintertodtConfig;
import net.runelite.client.plugins.microbot.MKE.wintertodt.startup.location.WintertodtLocationManager;
import net.runelite.client.plugins.microbot.MKE.wintertodt.startup.gear.WintertodtGearManager;
import net.runelite.client.plugins.microbot.MKE.wintertodt.startup.inventory.WintertodtInventoryManager;
import net.runelite.client.plugins.microbot.MKE.wintertodt.startup.gear.WintertodtAxeManager;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

/**
 * Smart startup manager for the Wintertodt script.
 * Handles the complete initialization sequence from any location in the game:
 * 
 * 1. Navigate to Wintertodt starting location
 * 2. Analyze and equip optimal gear
 * 3. Set up proper inventory
 * 4. Initialize main script
 * 
 * This system is designed to work from anywhere in the game and make intelligent
 * decisions about teleportation, banking, and gear optimization.
 * 
 * @author MakeCD
 * @version 1.0.0
 */
public class WintertodtStartupManager {
    
    // Wintertodt key locations
    public static final WorldPoint WINTERTODT_BANK = new WorldPoint(1640, 3944, 0);
    public static final WorldPoint WINTERTODT_ENTRANCE = new WorldPoint(1630, 3963, 0);
    public static final WorldPoint WINTERTODT_BOSS_ROOM = new WorldPoint(1630, 3982, 0);
    public static final WorldPoint WINTERTODT_DOOR = new WorldPoint(1627, 3968, 0);
    
    // Distance thresholds
    private static final int WINTERTODT_AREA_RADIUS = 50; // tiles
    private static final int BANK_PROXIMITY_RADIUS = 20;  // tiles
    private static final int FAR_FROM_BANK_THRESHOLD = 100; // tiles
    
    private final MKE_WintertodtConfig config;
    private final WintertodtLocationManager locationManager;
    private final WintertodtGearManager gearManager;
    private final WintertodtInventoryManager inventoryManager;
    
    // Startup state tracking
    private StartupPhase currentPhase = StartupPhase.CHECKING_GAME_ROOM;
    private boolean startupCompleted = false;
    private String statusMessage = "Initializing startup sequence...";
    
    public enum StartupPhase {
        CHECKING_GAME_ROOM("Checking if already in game room"),
        LOCATION_SETUP("Moving to Wintertodt area"),
        GEAR_ANALYSIS("Analyzing optimal gear"),
        GEAR_SETUP("Equipping optimal gear"),
        INVENTORY_SETUP("Setting up inventory"),
        FINAL_POSITIONING("Final positioning"),
        COMPLETED("Startup completed");
        
        private final String description;
        
        StartupPhase(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public WintertodtStartupManager(MKE_WintertodtConfig config) {
        this.config = config;
        this.locationManager = new WintertodtLocationManager();
        this.gearManager = new WintertodtGearManager(config);
        this.inventoryManager = new WintertodtInventoryManager(config);
        
        // IMPORTANT: Reset all sub-managers immediately on creation
        this.locationManager.reset();
        this.gearManager.reset();
        this.inventoryManager.reset();
        
        Microbot.log("Startup manager created with fresh sub-manager states");
    }
    
    /**
     * Executes the complete startup sequence.
     * This is the main entry point for the startup system.
     * 
     * @return true if startup completed successfully, false if failed
     */
    public boolean executeStartupSequence() {
        try {
            Microbot.log("=== Starting Wintertodt Smart Startup Sequence ===");
            
            // Phase 0: Check if already in game room and ready to play
            if (!executeGameRoomCheck()) {
                Microbot.log("Failed to complete game room check phase");
                return false;
            }
            
            // If we're already ready to play, skip the rest
            if (startupCompleted) {
                Microbot.log("=== Player already in game room and ready to play! ===");
                return true;
            }
            
            // Phase 1: Location Setup
            if (!executeLocationSetup()) {
                Microbot.log("Failed to complete location setup phase");
                return false;
            }
            
            // Phase 2: Gear Analysis and Setup
            if (!executeGearSetup()) {
                Microbot.log("Failed to complete gear setup phase");
                return false;
            }
            
            // Phase 3: Inventory Setup
            if (!executeInventorySetup()) {
                Microbot.log("Failed to complete inventory setup phase");
                return false;
            }
            
            // Phase 4: Final Positioning
            if (!executeFinalPositioning()) {
                Microbot.log("Failed to complete final positioning phase");
                return false;
            }
            
            // Mark startup as completed
            currentPhase = StartupPhase.COMPLETED;
            startupCompleted = true;
            statusMessage = "Startup sequence completed successfully!";
            
            Microbot.log("=== Wintertodt Startup Sequence Completed Successfully ===");
            return true;
            
        } catch (Exception e) {
            Microbot.log("Error during startup sequence: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Phase 1: Location Setup
     * Handles moving to the Wintertodt area from anywhere in the game.
     */
    private boolean executeLocationSetup() {
        try {
            currentPhase = StartupPhase.LOCATION_SETUP;
            statusMessage = "Analyzing current location and planning route to Wintertodt...";
            
            return locationManager.navigateToWintertodt();
            
        } catch (Exception e) {
            Microbot.log("Error in location setup phase: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Phase 2: Gear Setup
     * Analyzes available gear and equips the optimal setup.
     */
    private boolean executeGearSetup() {
        try {
            currentPhase = StartupPhase.GEAR_ANALYSIS;
            statusMessage = "Analyzing available gear for optimal warmth and efficiency...";
            
            Microbot.log("GEAR PHASE: Starting comprehensive gear analysis and setup");
            
            if (gearManager.setupOptimalGear()) {
                statusMessage = "Optimal gear equipped - ready for Wintertodt!";
                Microbot.log("GEAR PHASE: Completed successfully");
                return true;
            } else {
                statusMessage = "Failed to setup optimal gear - check bank and levels";
                Microbot.log("GEAR PHASE: Failed to complete gear setup");
                return false;
            }
            
        } catch (Exception e) {
            statusMessage = "Error during gear setup: " + e.getMessage();
            Microbot.log("GEAR PHASE: Error - " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Phase 3: Inventory Setup
     * Sets up the optimal inventory for Wintertodt.
     */
    private boolean executeInventorySetup() {
        try {
            currentPhase = StartupPhase.INVENTORY_SETUP;
            statusMessage = "Setting up inventory with optimal tool arrangement...";
            
            Microbot.log("INVENTORY PHASE: Starting comprehensive inventory setup");
            
            if (inventoryManager.setupInventory()) {
                statusMessage = "Inventory setup completed - tools optimally arranged!";
                Microbot.log("INVENTORY PHASE: Completed successfully");
                return true;
            } else {
                statusMessage = "Failed to setup inventory - check logs for details";
                Microbot.log("INVENTORY PHASE: Failed to complete inventory setup");
                return false;
            }
            
        } catch (Exception e) {
            statusMessage = "Error during inventory setup: " + e.getMessage();
            Microbot.log("INVENTORY PHASE: Error - " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Phase 4: Final Positioning
     * Moves to the optimal starting position within Wintertodt area.
     */
    private boolean executeFinalPositioning() {
        try {
            currentPhase = StartupPhase.FINAL_POSITIONING;
            statusMessage = "Moving to optimal starting position...";
            
            // Move to the bank area as the final starting position
            if (Rs2Player.getWorldLocation().distanceTo(WINTERTODT_BANK) > 10) {
                Microbot.log("Moving to Wintertodt bank for final positioning...");
                Rs2Walker.walkTo(WINTERTODT_BANK, 5);
                
                if (!sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(WINTERTODT_BANK) <= 10, 
                                   100, 30000)) {
                    Microbot.log("Failed to reach final position");
                    return false;
                }
            }
            
            Microbot.log("Final positioning completed - ready to start main script");
            return true;
            
        } catch (Exception e) {
            Microbot.log("Error in final positioning phase: " + e.getMessage());
            return false;
        }
    }
    
    // Getters for external monitoring
    public StartupPhase getCurrentPhase() { return currentPhase; }
    public boolean isStartupCompleted() { return startupCompleted; }
    public String getStatusMessage() { return statusMessage; }
    
    /**
     * Resets the startup manager for a fresh run.
     */
    public void reset() {
        currentPhase = StartupPhase.CHECKING_GAME_ROOM;
        startupCompleted = false;
        statusMessage = "Startup sequence reset";
        
        // Reset sub-managers - CRITICAL for fresh starts
        locationManager.reset();
        gearManager.reset();
        inventoryManager.reset();
        
        Microbot.log("Complete startup manager reset - all phases ready for fresh execution");
    }
    
    /**
     * Phase 0: Game Room Check
     * Checks if player is already inside the game room and ready to play.
     */
    private boolean executeGameRoomCheck() {
        try {
            currentPhase = StartupPhase.CHECKING_GAME_ROOM;
            statusMessage = "Checking if already in game room with necessary items...";
            
            // Check if player is inside the game room (north of doors)
            if (!WintertodtLocationManager.isInsideGameRoom()) {
                Microbot.log("Player not in game room, proceeding with full startup sequence");
                return true; // Continue with normal startup
            }
            
            Microbot.log("Player is inside Wintertodt game room! Checking readiness...");
            
            // Check if we have necessary items and warm gear
            if (isReadyToPlayInGameRoom()) {
                Microbot.log("Player is ready to play! Skipping banking and setup phases");
                currentPhase = StartupPhase.COMPLETED;
                startupCompleted = true;
                statusMessage = "Ready to play - skipped startup sequence!";
                return true;
            } else {
                Microbot.log("Player not ready to play, missing items or gear. Need to go bank first.");
                statusMessage = "Missing items or gear - need to bank first";
                return true; // Continue with normal startup to bank
            }
            
        } catch (Exception e) {
            Microbot.log("Error in game room check phase: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Checks if player has necessary items and at least 4 warm items to play.
     */
    private boolean isReadyToPlayInGameRoom() {
        try {
            Microbot.log("Analyzing current gear and inventory for readiness...");
            
            // Check for required tools
            if (!hasRequiredTools()) {
                Microbot.log("Missing required tools");
                return false;
            }
            
            // NEW: Different food checking for rejuvenation potions vs regular food
            int foodCount = getFoodCount();
            int minRequired = config.rejuvenationPotions() ? 0 : config.minFood(); // Allow 0 potions since we can make them
            
            if (config.rejuvenationPotions()) {
                // With rejuvenation potions, we don't need any in inventory to start
                // We can make them from the crate and sprouting roots
                Microbot.log("Using rejuvenation potions - no minimum food requirement (can make potions on demand)");
            } else {
                // Regular food requirement
                if (foodCount < minRequired) {
                    Microbot.log("Not enough food: " + foodCount + "/" + minRequired + " minimum required");
                    return false;
                }
            }
            
            // Check for warm gear (at least 4 pieces of gear that provide warmth)
            int warmGearCount = getWarmGearCount();
            if (warmGearCount < 4) {
                Microbot.log("Not enough warm gear equipped: " + warmGearCount + "/4 minimum required");
                return false;
            }
            
            if (config.rejuvenationPotions()) {
                Microbot.log("Has required tools, using rejuvenation potions (can make on demand), and " + warmGearCount + " warm gear pieces");
            } else {
                Microbot.log("Has required tools, " + foodCount + " food items, and " + warmGearCount + " warm gear pieces");
            }
            return true;
            
        } catch (Exception e) {
            Microbot.log("Error checking readiness: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Checks if player has required tools for Wintertodt with automatic axe detection.
     */
    private boolean hasRequiredTools() {
        // Get automatic axe decision
        WintertodtAxeManager.AxeDecision axeDecision = WintertodtAxeManager.determineOptimalAxeSetup();
        
        boolean hasAxe;
        if (axeDecision.shouldEquipAxe()) {
            hasAxe = Rs2Equipment.hasEquipped(axeDecision.getAxeId());
        } else {
            hasAxe = Rs2Inventory.hasItem(axeDecision.getAxeId());
        }
            
        boolean hasFireTool = Rs2Equipment.isWearing(ItemID.BRUMA_TORCH) ||
                             Rs2Equipment.isWearing(ItemID.BRUMA_TORCH_OFFHAND) ||
                             Rs2Inventory.hasItem(ItemID.TINDERBOX);
                             
        boolean hasKnife = !config.fletchRoots() || Rs2Inventory.hasItem(ItemID.KNIFE);
        boolean hasHammer = !config.fixBrazier() || Rs2Inventory.hasItem(ItemID.HAMMER);
        
        Microbot.log("Tools check - Axe (" + axeDecision.getAxeName() + "): " + hasAxe + 
                    ", Fire tool: " + hasFireTool + ", Knife: " + hasKnife + ", Hammer: " + hasHammer);
        
        return hasAxe && hasFireTool && hasKnife && hasHammer;
    }
    
    /**
     * Gets the count of food items in inventory.
     */
    private int getFoodCount() {
        if (config.rejuvenationPotions()) {
            return Rs2Inventory.count("Rejuvenation potion");
        } else {
            return Rs2Inventory.count(config.food().getName());
        }
    }
    
    /**
     * Counts equipped gear that provides warmth protection using the warmth flag.
     * Simply checks if each equipped item has the providesWarmth() flag set to true.
     */
    private int getWarmGearCount() {
        try {
            int warmCount = 0;
            
            // Use the gear database to check warmth flags
            net.runelite.client.plugins.microbot.MKE.wintertodt.startup.gear.WintertodtGearDatabase gearDatabase = 
                new net.runelite.client.plugins.microbot.MKE.wintertodt.startup.gear.WintertodtGearDatabase();
            
            Microbot.log("Analyzing equipped gear for warmth using providesWarmth() flags...");
            
            // Check each equipment slot for warm gear
            for (EquipmentInventorySlot slot : EquipmentInventorySlot.values()) {
                Rs2ItemModel equipped = Rs2Equipment.get(slot);
                if (equipped != null) {
                    int itemId = equipped.getId();
                    
                    // Look up the gear item in our comprehensive database
                    net.runelite.client.plugins.microbot.MKE.wintertodt.startup.gear.WintertodtGearItem gearItem = 
                        gearDatabase.findGearItemById(itemId);
                    
                    if (gearItem != null && gearItem.providesWarmth()) {
                        warmCount++;
                        Microbot.log("Warm gear: " + gearItem.getItemName() + " (provides warmth)");
                    } else if (gearItem != null) {
                        Microbot.log("Cold gear: " + gearItem.getItemName() + " (no warmth)");
                    } else {
                        Microbot.log("Unknown gear: " + equipped.getName() + " (not in database)");
                    }
                }
            }
            
            Microbot.log("Total warm gear count: " + warmCount + "/4 pieces providing warmth");
            return warmCount;
            
        } catch (Exception e) {
            Microbot.log("Error during warm gear analysis: " + e.getMessage());
            e.printStackTrace();
            // Return 0 on error to be safe - forces banking if there's an issue
            return 0;
        }
    }
    
    /**
     * Quick check if we're already in a good position to start Wintertodt.
     */
    public boolean isAlreadyAtWintertodt() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        return playerLocation.distanceTo(WINTERTODT_BANK) <= WINTERTODT_AREA_RADIUS;
    }
} 