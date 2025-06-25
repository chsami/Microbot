package net.runelite.client.plugins.microbot.MKE.wintertodt.startup.gear;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.MKE.wintertodt.MKE_WintertodtConfig;

import java.util.*;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

/**
 * Comprehensive gear manager for Wintertodt optimization.
 * Analyzes available gear and automatically equips the best setup for maximum efficiency.
 * 
 * Priority System:
 * 1. Pyromancer gear (1000+ priority) - Best in slot for Wintertodt
 * 2. Warm clothing (600-799) - Provides cold resistance 
 * 3. Graceful outfit (400-599) - Weight reduction for faster movement
 * 4. High-level combat gear (200-399) - Good stats but not Wintertodt-specific
 * 5. Basic gear (0-199) - Fallback options
 * 
 * @author MakeCD
 * @version 1.0.0
 */
public class WintertodtGearManager {
    
    private final MKE_WintertodtConfig config;
    private final WintertodtGearDatabase gearDatabase;
    private Map<EquipmentInventorySlot, Integer> optimalGear;
    private List<String> gearAnalysisLog;
    
    public WintertodtGearManager(MKE_WintertodtConfig config) {
        this.config = config;
        this.gearDatabase = new WintertodtGearDatabase();
        this.optimalGear = new HashMap<>();
        this.gearAnalysisLog = new ArrayList<>();
    }
    
    /**
     * Analyzes and sets up the optimal gear configuration for Wintertodt.
     * This is the main method called by the startup manager.
     * @return true if gear setup completed successfully
     */
    public boolean setupOptimalGear() {
        try {
            Microbot.log("Analyzing optimal gear for Wintertodt...");
            
            // Ensure bank is open for gear analysis
            if (!Rs2Bank.isOpen()) {
                // Prevent bug that causes bot to not being able to wear items in bank by adding inventory open command first
                if (!Rs2Inventory.isOpen()) {
                    Rs2Inventory.open();
                }
                if (!Rs2Bank.openBank()) {
                    Microbot.log("Failed to open bank for gear analysis");
                    return false;
                }
            }
            
            // STEP 0: Handle bruma torch conversion if needed
            WintertodtAxeManager.AxeDecision axeDecision = WintertodtAxeManager.determineOptimalAxeSetup();
            if (axeDecision.needsBrumaTorchConversion()) {
                gearAnalysisLog.add("Converting bruma torch to offhand version...");
                if (!WintertodtAxeManager.performBrumaTorchConversion()) {
                    gearAnalysisLog.add("Failed to convert bruma torch - continuing without it");
                } else {
                    gearAnalysisLog.add("Successfully converted bruma torch to offhand version");
                }
            }
            
            // First analyze what gear is optimal
            if (!analyzeOptimalGear()) {
                return false;
            }
            
            // Then equip the optimal gear
            return equipOptimalGear();
            
        } catch (Exception e) {
            Microbot.log("Error setting up optimal gear: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Analyzes available gear and determines optimal setup.
     * Checks bank and inventory for best items player can use.
     */
    private boolean analyzeOptimalGear() {
        try {
            Microbot.log("Analyzing optimal gear for Wintertodt...");
            gearAnalysisLog.clear();
            optimalGear.clear();
            
            // Analyze each equipment slot
            analyzeHeadSlot();
            analyzeBodySlot();
            analyzeLegsSlot();
            analyzeFeetSlot();
            analyzeHandsSlot();
            analyzeWeaponSlot();
            analyzeShieldSlot();
            analyzeNeckSlot();
            analyzeRingSlot();
            analyzeCapeSlot();
            
            // Log analysis results
            logGearAnalysis();
            
            return true;
            
        } catch (Exception e) {
            Microbot.log("Error analyzing gear: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Equips the optimal gear setup.
     * Banks current non-optimal gear and withdraws/equips best items.
     */
    public boolean equipOptimalGear() {
        try {
            Microbot.log("Equipping optimal gear setup...");
            
            // Ensure bank is open
            if (!Rs2Bank.isOpen()) {
                // Prevent bug that causes bot to not being able to wear items in bank by adding inventory open command first
                if (!Rs2Inventory.isOpen()) {
                    Rs2Inventory.open();
                }
                if (!Rs2Bank.openBank()) {
                    Microbot.log("Failed to open bank for gear setup");
                    return false;
                }
            }
            
            // Bank current non-optimal equipment
            bankCurrentGear();
            
            // Withdraw and equip optimal gear
            for (Map.Entry<EquipmentInventorySlot, Integer> entry : optimalGear.entrySet()) {
                equipSlotItem(entry.getKey(), entry.getValue());
            }
            
            // Handle special inventory items
            setupInventoryTools();
            
            Microbot.log("Gear setup completed successfully!");
            return true;
            
        } catch (Exception e) {
            Microbot.log("Error equipping gear: " + e.getMessage());
            return false;
        }
    }
    
    private void analyzeHeadSlot() {
        int bestItem = 0;
        String reason = "No suitable head gear found";
        
        // Pyromancer hood (best)
        if (hasAccess(ItemID.PYROMANCER_HOOD)) {
            bestItem = ItemID.PYROMANCER_HOOD;
            reason = "Pyromancer hood - Best for Wintertodt";
        }
        // Clue hunter gear (warm clothing)
        else if (hasAccess(ItemID.CLUE_HUNTER_GARB)) {
            bestItem = ItemID.CLUE_HUNTER_GARB;
            reason = "Clue hunter garb - Provides warmth";
        }
        // Graceful hood
        else if (hasAccess(ItemID.GRACEFUL_HOOD) && Rs2Player.getSkillRequirement(Skill.AGILITY, 30)) {
            bestItem = ItemID.GRACEFUL_HOOD;
            reason = "Graceful hood - Weight reduction";
        }
        // Fashionscape options
        else if (hasAccess(ItemID.SANTA_HAT)) {
            bestItem = ItemID.SANTA_HAT;
            reason = "Santa hat - Festive and warm";
        }
        else if (hasAccess(ItemID.ANTISANTA_MASK)) {
            bestItem = ItemID.ANTISANTA_MASK;
            reason = "Anti-santa mask - Provides warmth";
        }
        
        if (bestItem != 0) {
            optimalGear.put(EquipmentInventorySlot.HEAD, bestItem);
            gearAnalysisLog.add("HEAD: " + reason);
        }
    }
    
    private void analyzeBodySlot() {
        int bestItem = 0;
        String reason = "No suitable body gear found";
        
        // Pyromancer garb (best)
        if (hasAccess(ItemID.PYROMANCER_GARB)) {
            bestItem = ItemID.PYROMANCER_GARB;
            reason = "Pyromancer garb - Best for Wintertodt";
        }
        // Graceful top
        else if (hasAccess(ItemID.GRACEFUL_TOP) && Rs2Player.getSkillRequirement(Skill.AGILITY, 35)) {
            bestItem = ItemID.GRACEFUL_TOP;
            reason = "Graceful top - Weight reduction";
        }
        
        if (bestItem != 0) {
            optimalGear.put(EquipmentInventorySlot.BODY, bestItem);
            gearAnalysisLog.add("BODY: " + reason);
        }
    }
    
    private void analyzeLegsSlot() {
        int bestItem = 0;
        String reason = "No suitable leg gear found";
        
        // Pyromancer robe (best)
        if (hasAccess(ItemID.PYROMANCER_ROBE)) {
            bestItem = ItemID.PYROMANCER_ROBE;
            reason = "Pyromancer robe - Best for Wintertodt";
        }
        // Clue hunter gear (warm clothing)
        else if (hasAccess(ItemID.CLUE_HUNTER_TROUSERS)) {
            bestItem = ItemID.CLUE_HUNTER_TROUSERS;
            reason = "Clue hunter trousers - Provides warmth";
        }
        // Graceful legs
        else if (hasAccess(ItemID.GRACEFUL_LEGS) && Rs2Player.getSkillRequirement(Skill.AGILITY, 40)) {
            bestItem = ItemID.GRACEFUL_LEGS;
            reason = "Graceful legs - Weight reduction";
        }
        // Ham robe legs
        else if (hasAccess(ItemID.HAM_ROBE)) {
            bestItem = ItemID.HAM_ROBE;
            reason = "Ham robe - Light and warm";
        }
        
        if (bestItem != 0) {
            optimalGear.put(EquipmentInventorySlot.LEGS, bestItem);
            gearAnalysisLog.add("LEGS: " + reason);
        }
    }
    
    private void analyzeFeetSlot() {
        int bestItem = 0;
        String reason = "No suitable boot gear found";
        
        // Pyromancer boots (best)
        if (hasAccess(ItemID.PYROMANCER_BOOTS)) {
            bestItem = ItemID.PYROMANCER_BOOTS;
            reason = "Pyromancer boots - Best for Wintertodt";
        }
        // Clue hunter gear (warm clothing)
        else if (hasAccess(ItemID.CLUE_HUNTER_BOOTS)) {
            bestItem = ItemID.CLUE_HUNTER_BOOTS;
            reason = "Clue hunter boots - Provides warmth";
        }
        // Graceful boots
        else if (hasAccess(ItemID.GRACEFUL_BOOTS) && Rs2Player.getSkillRequirement(Skill.AGILITY, 25)) {
            bestItem = ItemID.GRACEFUL_BOOTS;
            reason = "Graceful boots - Weight reduction";
        }
        // Ham boots
        else if (hasAccess(ItemID.HAM_BOOTS)) {
            bestItem = ItemID.HAM_BOOTS;
            reason = "Ham boots - Light and warm";
        }
        
        if (bestItem != 0) {
            optimalGear.put(EquipmentInventorySlot.BOOTS, bestItem);
            gearAnalysisLog.add("FEET: " + reason);
        }
    }
    
    private void analyzeHandsSlot() {
        int bestItem = 0;
        String reason = "No suitable glove gear found";
        
        // Warm gloves (best)
        if (hasAccess(ItemID.WARM_GLOVES)) {
            bestItem = ItemID.WARM_GLOVES;
            reason = "Warm gloves - Best for Wintertodt";
        }
        // Clue hunter gear (warm clothing)
        else if (hasAccess(ItemID.CLUE_HUNTER_GLOVES)) {
            bestItem = ItemID.CLUE_HUNTER_GLOVES;
            reason = "Clue hunter gloves - Provides warmth";
        }
        // Graceful gloves
        else if (hasAccess(ItemID.GRACEFUL_GLOVES) && Rs2Player.getSkillRequirement(Skill.AGILITY, 20)) {
            bestItem = ItemID.GRACEFUL_GLOVES;
            reason = "Graceful gloves - Weight reduction";
        }
        
        if (bestItem != 0) {
            optimalGear.put(EquipmentInventorySlot.GLOVES, bestItem);
            gearAnalysisLog.add("HANDS: " + reason);
        }
    }
    
    /**
     * Analyzes the weapon slot with automatic axe management.
     */
    private void analyzeWeaponSlot() {
        gearAnalysisLog.add("=== WEAPON SLOT ANALYSIS ===");
        
        // Get automatic axe decision
        WintertodtAxeManager.AxeDecision axeDecision = WintertodtAxeManager.determineOptimalAxeSetup();
        gearAnalysisLog.add("Automatic axe analysis: " + axeDecision.toString());
        
        if (axeDecision.shouldEquipAxe()) {
            // Equip the axe
            if (hasAccess(axeDecision.getAxeId())) {
                optimalGear.put(EquipmentInventorySlot.WEAPON, axeDecision.getAxeId());
                gearAnalysisLog.add("Will equip: " + axeDecision.getAxeName());
            } else {
                gearAnalysisLog.add("Cannot access optimal axe: " + axeDecision.getAxeName());
                // Fallback to any available axe that can be equipped
                findFallbackEquippableAxe();
            }
        } else {
            gearAnalysisLog.add("Will keep axe in inventory: " + axeDecision.getAxeName());
            // Don't equip any weapon - axe will be handled by inventory manager
            
            // Check for other useful weapons to equip instead
            analyzeAlternativeWeapons();
        }
    }
    
    /**
     * Finds a fallback axe if the optimal one isn't available.
     */
    private void findFallbackEquippableAxe() {
        List<WintertodtGearItem> axes = gearDatabase.getGearForSlot(EquipmentInventorySlot.WEAPON)
            .stream()
            .filter(item -> item.getItemName().toLowerCase().contains("axe"))
            .filter(item -> hasAccess(item.getItemId()))
            .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
            .collect(java.util.stream.Collectors.toList());
            
        if (!axes.isEmpty()) {
            WintertodtGearItem fallbackAxe = axes.get(0);
            optimalGear.put(EquipmentInventorySlot.WEAPON, fallbackAxe.getItemId());
            gearAnalysisLog.add("Fallback axe: " + fallbackAxe.getItemName());
        }
    }
    
    /**
     * Analyzes alternative weapons when axe is kept in inventory.
     */
    private void analyzeAlternativeWeapons() {
        // Look for utility weapons like bruma torch
        List<WintertodtGearItem> weapons = gearDatabase.getGearForSlot(EquipmentInventorySlot.WEAPON)
            .stream()
            .filter(item -> !item.getItemName().toLowerCase().contains("axe"))
            .filter(item -> hasAccess(item.getItemId()))
            .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
            .collect(java.util.stream.Collectors.toList());
            
        if (!weapons.isEmpty()) {
            WintertodtGearItem weapon = weapons.get(0);
            optimalGear.put(EquipmentInventorySlot.WEAPON, weapon.getItemId());
            gearAnalysisLog.add("Alternative weapon: " + weapon.getItemName());
        }
    }
    
    private void analyzeShieldSlot() {
        int bestItem = 0;
        String reason = "No suitable shield found";
        
        // Bruma torch offhand (best for Wintertodt - acts as tinderbox)
        if (hasAccess(ItemID.BRUMA_TORCH_OFFHAND) && Rs2Player.getSkillRequirement(Skill.FIREMAKING, 50)) {
            bestItem = ItemID.BRUMA_TORCH_OFFHAND;
            reason = "Bruma torch offhand - Acts as tinderbox and frees inventory space";
        }
        // Regular bruma torch (should be converted to offhand if possible)
        else if (hasAccess(ItemID.BRUMA_TORCH) && Rs2Player.getSkillRequirement(Skill.FIREMAKING, 50)) {
            // This will be handled by bruma torch conversion logic
            gearAnalysisLog.add("SHIELD: Regular bruma torch found - conversion to offhand will be handled");
        }
        // Tome of fire
        else if (hasAccess(ItemID.TOME_OF_FIRE)) {
            bestItem = ItemID.TOME_OF_FIRE;
            reason = "Tome of fire - Firemaking bonus";
        }
        // Fashionscape shields
        else if (hasAccess(ItemID.BOOK_OF_KNOWLEDGE)) {
            bestItem = ItemID.BOOK_OF_KNOWLEDGE;
            reason = "Book of knowledge - Fashionscape";
        }
        
        if (bestItem != 0) {
            optimalGear.put(EquipmentInventorySlot.SHIELD, bestItem);
            gearAnalysisLog.add("SHIELD: " + reason);
        }
    }
    
    private void analyzeNeckSlot() {
        int bestItem = 0;
        String reason = "No suitable amulet found";
        
        // Teleport jewelry (for utility)
        if (hasAccess(ItemID.GAMES_NECKLACE8)) {
            bestItem = ItemID.GAMES_NECKLACE8;
            reason = "Games necklace - Wintertodt teleport";
        }
        else if (hasAccess(ItemID.AMULET_OF_GLORY4)) {
            bestItem = ItemID.AMULET_OF_GLORY4;
            reason = "Amulet of glory - Useful teleports";
        }
        // Fashionscape options
        else if (hasAccess(ItemID.GHOSTSPEAK_AMULET)) {
            bestItem = ItemID.GHOSTSPEAK_AMULET;
            reason = "Ghostspeak amulet - Fashionscape";
        }
        
        if (bestItem != 0) {
            optimalGear.put(EquipmentInventorySlot.AMULET, bestItem);
            gearAnalysisLog.add("NECK: " + reason);
        }
    }
    
    private void analyzeRingSlot() {
        int bestItem = 0;
        String reason = "No suitable ring found";
        
        // Utility rings
        if (hasAccess(ItemID.RING_OF_DUELING8)) {
            bestItem = ItemID.RING_OF_DUELING8;
            reason = "Ring of dueling - Useful teleports";
        }
        else if (hasAccess(ItemID.RING_OF_WEALTH_5)) {
            bestItem = ItemID.RING_OF_WEALTH_5;
            reason = "Ring of wealth - Grand Exchange teleport";
        }
        // Fashionscape rings
        else if (hasAccess(ItemID.GOLD_RING)) {
            bestItem = ItemID.GOLD_RING;
            reason = "Gold ring - Fashionscape";
        }
        
        if (bestItem != 0) {
            optimalGear.put(EquipmentInventorySlot.RING, bestItem);
            gearAnalysisLog.add("RING: " + reason);
        }
    }
    
    private void analyzeCapeSlot() {
        int bestItem = 0;
        String reason = "No suitable cape found";
        
        // Skill capes (best for Wintertodt)
        if (hasAccess(ItemID.FIREMAKING_CAPE)) {
            bestItem = ItemID.FIREMAKING_CAPE;
            reason = "Firemaking cape - Perfect for Wintertodt";
        }
        else if (hasAccess(ItemID.FLETCHING_CAPE)) {
            bestItem = ItemID.FLETCHING_CAPE;
            reason = "Fletching cape - Useful for fletching at Wintertodt";
        }
        // Clue hunter gear (warm clothing)
        else if (hasAccess(ItemID.CLUE_HUNTER_CLOAK)) {
            bestItem = ItemID.CLUE_HUNTER_CLOAK;
            reason = "Clue hunter cloak - Provides warmth";
        }
        // Graceful cape
        else if (hasAccess(ItemID.GRACEFUL_CAPE) && Rs2Player.getSkillRequirement(Skill.AGILITY, 15)) {
            bestItem = ItemID.GRACEFUL_CAPE;
            reason = "Graceful cape - Weight reduction";
        }
        // Warm capes
        else if (hasAccess(ItemID.TEAM_CAPE_ZERO)) {
            bestItem = ItemID.TEAM_CAPE_ZERO;
            reason = "Team cape - Basic fashionscape";
        }
        
        if (bestItem != 0) {
            optimalGear.put(EquipmentInventorySlot.CAPE, bestItem);
            gearAnalysisLog.add("CAPE: " + reason);
        }
    }
    
    private boolean hasAccess(int itemId) {
        return Rs2Inventory.hasItem(itemId) || Rs2Equipment.isWearing(itemId) || Rs2Bank.hasItem(itemId);
    }
    
    /**
     * Banks current gear but preserves tools that are optimally arranged.
     */
    private void bankCurrentGear() {
        try {
        Microbot.log("Banking current gear...");
        
            // Get current axe decision to know if axe should be preserved
            WintertodtAxeManager.AxeDecision axeDecision = WintertodtAxeManager.determineOptimalAxeSetup();
            
            // Bank all inventory items except optimally placed tools
            for (int slotIndex = 0; slotIndex < 28; slotIndex++) {
                final int slot = slotIndex; // Make effectively final for lambda usage
                Rs2ItemModel item = Rs2Inventory.get(slot);
                if (item != null) {
                    int itemId = item.getId();
                    
                    // Skip banking tools that are in optimal positions
                    if (shouldPreserveTool(itemId, slot, axeDecision)) {
                        Microbot.log("Preserving optimally placed tool: " + item.getName() + " in slot " + slot);
                        continue;
                    }
                    
                    // Bank everything else
                    Rs2Bank.depositOne(itemId);
                    sleepUntilTrue(() -> Rs2Inventory.get(slot) == null || 
                                         Rs2Inventory.get(slot).getId() != itemId, 100, 2000);
                }
            }
            
        } catch (Exception e) {
            Microbot.log("Error banking current gear: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Determines if a tool should be preserved based on its optimal placement.
     */
    private boolean shouldPreserveTool(int itemId, int slot, WintertodtAxeManager.AxeDecision axeDecision) {
        // Preserve knife in slot 27
        if (itemId == ItemID.KNIFE && slot == 27 && config.fletchRoots()) {
            return true;
            }
        
        // Preserve hammer in slot 26
        if (itemId == ItemID.HAMMER && slot == 26 && config.fixBrazier()) {
            return true;
        }
        
        // Preserve axe in slot 24 if it should be in inventory
        if (itemId == axeDecision.getAxeId() && slot == 24 && !axeDecision.shouldEquipAxe()) {
            return true;
        }
        
        // Preserve tinderbox in slot 25 if no bruma torch equipped
        if (itemId == ItemID.TINDERBOX && slot == 25 && 
            !Rs2Equipment.isWearing(ItemID.BRUMA_TORCH) && 
            !Rs2Equipment.isWearing(ItemID.BRUMA_TORCH_OFFHAND)) {
            return true;
        }
        
        return false;
    }
    
    private void equipSlotItem(EquipmentInventorySlot slot, int itemId) {
        // Skip if already equipped
        if (Rs2Equipment.isWearing(itemId)) {
            return;
        }
        
        // Withdraw if not in inventory
        if (!Rs2Inventory.hasItem(itemId)) {
            if (Rs2Bank.hasItem(itemId)) {
                Rs2Bank.withdrawOne(itemId);
                sleepUntil(() -> Rs2Inventory.hasItem(itemId), 3000);
            } else {
                Microbot.log("Warning: Item " + itemId + " not found in bank");
                return;
            }
        }
        
        // Equip the item
        Rs2Inventory.wield(itemId);
        sleepUntil(() -> Rs2Equipment.isWearing(itemId), 3000);
    }
    
    /**
     * Sets up inventory tools with automatic axe detection.
     * Only withdraws tools if they're missing - doesn't rearrange if already optimal.
     */
    private void setupInventoryTools() {
        try {
            Microbot.log("Setting up inventory tools...");
        
            // Get optimal axe decision
            WintertodtAxeManager.AxeDecision axeDecision = WintertodtAxeManager.determineOptimalAxeSetup();
            gearAnalysisLog.add("Axe decision: " + axeDecision.toString());
        
            // Check if axe is needed in inventory and if it's already there
            if (!axeDecision.shouldEquipAxe()) {
                if (!Rs2Inventory.hasItem(axeDecision.getAxeId())) {
                    // Withdraw axe only if missing
                    if (Rs2Bank.hasItem(axeDecision.getAxeId())) {
                        Rs2Bank.withdrawOne(axeDecision.getAxeId());
                        sleepUntilTrue(() -> Rs2Inventory.hasItem(axeDecision.getAxeId()), 100, 3000);
                        Microbot.log("Withdrew axe for inventory: " + axeDecision.getAxeName());
                    }
                } else {
                    Microbot.log("Axe already in inventory: " + axeDecision.getAxeName());
            }
        }
        
            // Check for knife (only if fletching enabled)
        if (config.fletchRoots() && !Rs2Inventory.hasItem(ItemID.KNIFE)) {
            if (Rs2Bank.hasItem(ItemID.KNIFE)) {
                Rs2Bank.withdrawOne(ItemID.KNIFE);
                    sleepUntilTrue(() -> Rs2Inventory.hasItem(ItemID.KNIFE), 100, 3000);
                    Microbot.log("Withdrew knife for fletching");
            }
        }
        
            // Check for hammer (only if fixing enabled)
        if (config.fixBrazier() && !Rs2Inventory.hasItem(ItemID.HAMMER)) {
            if (Rs2Bank.hasItem(ItemID.HAMMER)) {
                Rs2Bank.withdrawOne(ItemID.HAMMER);
                    sleepUntilTrue(() -> Rs2Inventory.hasItem(ItemID.HAMMER), 100, 3000);
                    Microbot.log("Withdrew hammer for brazier repairs");
                }
            }
            
            // Check for tinderbox (only if no bruma torch equipment)
            if (!Rs2Equipment.isWearing(ItemID.BRUMA_TORCH) && 
                !Rs2Equipment.isWearing(ItemID.BRUMA_TORCH_OFFHAND) &&
                !Rs2Inventory.hasItem(ItemID.TINDERBOX)) {
                if (Rs2Bank.hasItem(ItemID.TINDERBOX)) {
                    Rs2Bank.withdrawOne(ItemID.TINDERBOX);
                    sleepUntilTrue(() -> Rs2Inventory.hasItem(ItemID.TINDERBOX), 100, 3000);
                    Microbot.log("Withdrew tinderbox for lighting");
                }
            }
            
            Microbot.log("Tool setup completed - only missing tools withdrawn");
            
        } catch (Exception e) {
            Microbot.log("Error setting up inventory tools: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void logGearAnalysis() {
        Microbot.log("=== Optimal Gear Analysis ===");
        for (String logEntry : gearAnalysisLog) {
            Microbot.log(logEntry);
        }
        Microbot.log("Total gear pieces: " + optimalGear.size());
    }
    
    /**
     * Resets the gear manager state completely.
     */
    public void reset() {
        optimalGear.clear();
        gearAnalysisLog.clear();
        
        // Reset any cached state that might interfere with fresh analysis
        Microbot.log("Gear manager state reset - ready for fresh analysis");
    }
    
    /**
     * Gets the gear analysis log for display.
     */
    public List<String> getGearAnalysisLog() {
        return new ArrayList<>(gearAnalysisLog);
    }
} 