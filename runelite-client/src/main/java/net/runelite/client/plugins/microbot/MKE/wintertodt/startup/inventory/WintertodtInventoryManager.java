package net.runelite.client.plugins.microbot.MKE.wintertodt.startup.inventory;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.MKE.wintertodt.MKE_WintertodtConfig;
import net.runelite.client.plugins.microbot.MKE.wintertodt.startup.gear.WintertodtAxeManager;

import java.util.*;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
import static net.runelite.client.plugins.microbot.util.Global.sleepGaussian;

/**
 * Handles inventory setup for Wintertodt with optimal tool arrangement.
 * 
 * Inventory Layout Strategy:
 * - Tools arranged on the right side of inventory (slots 20-27)
 * - Knife always in slot 27 (bottom-right) for easy access
 * - Other tools arranged in convenient positions
 * - Leaves left side free for logs, kindling, and food
 * 
 * Tool Priority:
 * 1. Knife (if fletching enabled) - Slot 27
 * 2. Hammer (if fixing enabled) - Slot 26  
 * 3. Tinderbox (if no Bruma torch) - Slot 25
 * 4. Axe (if needed and not equipped) - Slot 24
 * 
 * @author MakeCD
 * @version 1.0.0
 */
public class WintertodtInventoryManager {
    
    private final MKE_WintertodtConfig config;
    private List<String> inventorySetupLog;
    
    // Preferred tool slots (right side of inventory)
    private static final int KNIFE_SLOT = 27;      // Bottom-right corner
    private static final int HAMMER_SLOT = 26;     // Next to knife
    private static final int TINDERBOX_SLOT = 25;  // Above hammer
    private static final int AXE_SLOT = 24;        // Above tinderbox
    
    // Axe priority list (best to worst)
    private static final List<Integer> AXE_PRIORITY = Arrays.asList(
        ItemID.INFERNAL_AXE,
        ItemID.DRAGON_AXE,
        ItemID.RUNE_AXE,
        ItemID.ADAMANT_AXE,
        ItemID.MITHRIL_AXE,
        ItemID.STEEL_AXE,
        ItemID.IRON_AXE,
        ItemID.BRONZE_AXE
    );
    
    public WintertodtInventoryManager(MKE_WintertodtConfig config) {
        this.config = config;
        this.inventorySetupLog = new ArrayList<>();
    }
    
    /**
     * Sets up the complete inventory with tools and arranges them optimally.
     * @return true if setup completed successfully
     */
    public boolean setupInventory() {
        try {
            Microbot.log("Setting up Wintertodt inventory...");
            inventorySetupLog.clear();
            
            // Ensure bank is open
            if (!Rs2Bank.isOpen()) {
                // Prevent bug that causes bot to not being able to wear items in bank by adding inventory open command first
                if (!Rs2Inventory.isOpen()) {
                    Rs2Inventory.open();
                }
                if (!Rs2Bank.openBank()) {
                    Microbot.log("Failed to open bank for inventory setup");
                    return false;
                }
            }
            
            // NEW: Deposit unnecessary items first
            depositUnnecessaryItems();

            // Step 0: Handle bruma torch conversion if needed
            WintertodtAxeManager.AxeDecision axeDecision = WintertodtAxeManager.determineOptimalAxeSetup();
            if (axeDecision.needsBrumaTorchConversion()) {
                inventorySetupLog.add("🔥 Converting bruma torch to offhand version...");
                if (!WintertodtAxeManager.performBrumaTorchConversion()) {
                    inventorySetupLog.add("❌ Failed to convert bruma torch");
                } else {
                    inventorySetupLog.add("✅ Successfully converted bruma torch to offhand version");
                }
            }
            
            // Step 1: Get all required tools
            if (!acquireRequiredTools()) {
                return false;
            }
            
            // Step 2: Arrange tools in optimal positions
            if (!arrangeToolsOptimally()) {
                return false;
            }
            
            // Step 3: Log the final setup
            logInventorySetup();
            
            Microbot.log("Inventory setup completed successfully!");
            return true;
            
        } catch (Exception e) {
            Microbot.log("Error setting up inventory: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * NEW: Deposits any non-essential items from the inventory before setup.
     * This cleans up items unequipped during the gear swap phase.
     */
    private void depositUnnecessaryItems() {
        if (!Rs2Bank.isOpen()) {
            return;
        }
        inventorySetupLog.add("🧹 Cleaning inventory of unnecessary items...");

        // Determine essential items that we want to KEEP in the inventory.
        // Everything else will be deposited.
        List<String> keepItems = new ArrayList<>();
        
        // Always keep tools if they are already present
        keepItems.add("knife");
        keepItems.add("hammer");
        keepItems.add("tinderbox");
        
        // Keep all possible axes, as we don't know which one we might have
        for (int axeId : AXE_PRIORITY) {
            keepItems.add(getAxeName(axeId).toLowerCase());
        }

        // Keep food/potions
        if (config.rejuvenationPotions()) {
            // Cant transfer potions to bank, so we dont need to keep them
        } else {
            keepItems.add(config.food().getName().toLowerCase());
        }

        // Deposit everything except the essential items
        Rs2Bank.depositAllExcept(keepItems.toArray(new String[0]));
        sleepGaussian(600, 200); // Wait for deposits to process
        inventorySetupLog.add("✅ Inventory cleaned successfully.");
    }
    
    /**
     * Acquires all required tools with automatic axe handling.
     */
    private boolean acquireRequiredTools() {
        inventorySetupLog.add("🛠️ Acquiring required tools with automatic axe detection...");
        
        // Get automatic axe decision
        WintertodtAxeManager.AxeDecision axeDecision = WintertodtAxeManager.determineOptimalAxeSetup();
        inventorySetupLog.add("🪓 Axe decision: " + axeDecision.toString());
        
        // Handle axe setup
        if (!handleAxeSetup(axeDecision)) {
            return false;
        }
        
        // Handle fire lighting tool
        if (!handleFireTool()) {
            return false;
        }
        
        // Handle knife if fletching enabled
            if (config.fletchRoots() && !Rs2Inventory.hasItem(ItemID.KNIFE)) {
            if (!Rs2Bank.withdrawX(ItemID.KNIFE, 1)) {
                inventorySetupLog.add("❌ Failed to get knife for fletching");
                return false;
            }
            inventorySetupLog.add("🔪 Acquired knife for fletching");
            }
            
        // Handle hammer if fixing enabled
            if (config.fixBrazier() && !Rs2Inventory.hasItem(ItemID.HAMMER)) {
            if (!Rs2Bank.withdrawX(ItemID.HAMMER, 1)) {
                inventorySetupLog.add("❌ Failed to get hammer for fixing");
                return false;
            }
            inventorySetupLog.add("🔨 Acquired hammer for fixing");
        }
        
        return true;
    }
    
    /**
     * Handles axe setup based on automatic decision.
     */
    private boolean handleAxeSetup(WintertodtAxeManager.AxeDecision axeDecision) {
        if (axeDecision.shouldEquipAxe()) {
            // Axe should be equipped - check if it is
            if (!Rs2Equipment.hasEquipped(axeDecision.getAxeId())) {
                if (Rs2Inventory.hasItem(axeDecision.getAxeId())) {
                    Rs2Inventory.wield(axeDecision.getAxeId());
                    inventorySetupLog.add("⚔️ Equipped axe from inventory: " + axeDecision.getAxeName());
                } else if (Rs2Bank.isOpen() && Rs2Bank.hasBankItem(axeDecision.getAxeId(),1)) {
                    Rs2Bank.withdrawAndEquip(axeDecision.getAxeId());
                    inventorySetupLog.add("⚔️ Withdrew and equipped axe: " + axeDecision.getAxeName());
                } else {
                    inventorySetupLog.add("❌ Cannot find axe to equip: " + axeDecision.getAxeName());
                    return false;
                }
            } else {
                inventorySetupLog.add("✅ Axe already equipped: " + axeDecision.getAxeName());
                }
        } else {
            // Axe should be in inventory
            if (!Rs2Inventory.hasItem(axeDecision.getAxeId())) {
                if (Rs2Equipment.hasEquipped(axeDecision.getAxeId())) {
                    Rs2Equipment.unEquip(EquipmentInventorySlot.WEAPON);
                    inventorySetupLog.add("📦 Moved axe to inventory: " + axeDecision.getAxeName());
                } else if (Rs2Bank.isOpen() && Rs2Bank.hasBankItem(axeDecision.getAxeId(),1)) {
                    Rs2Bank.withdrawX(axeDecision.getAxeId(), 1);
                    inventorySetupLog.add("📦 Withdrew axe to inventory: " + axeDecision.getAxeName());
                } else {
                    inventorySetupLog.add("❌ Cannot find axe for inventory: " + axeDecision.getAxeName());
                    return false;
                }
            } else {
                inventorySetupLog.add("✅ Axe already in inventory: " + axeDecision.getAxeName());
            }
        }
        
        return true;
    }
    
    /**
     * Handles fire lighting tool acquisition.
     */
    private boolean handleFireTool() {
        // Check if we have bruma torch equipped/available (prioritize offhand)
        if (Rs2Equipment.hasEquipped(ItemID.BRUMA_TORCH_OFFHAND) || 
            Rs2Equipment.hasEquipped(ItemID.BRUMA_TORCH)) {
            inventorySetupLog.add("🔥 Fire tool: Bruma torch equipped");
            return true;
        }
        
        // Check if we have bruma torch in inventory
        if (Rs2Inventory.hasItem(ItemID.BRUMA_TORCH_OFFHAND) ||
            Rs2Inventory.hasItem(ItemID.BRUMA_TORCH)) {
            inventorySetupLog.add("🔥 Fire tool: Bruma torch in inventory");
            return true;
        }
        
        // No bruma torch - need tinderbox
        if (!Rs2Inventory.hasItem(ItemID.TINDERBOX)) {
            if (!Rs2Bank.withdrawX(ItemID.TINDERBOX, 1)) {
                inventorySetupLog.add("❌ Failed to get tinderbox for fire lighting");
            return false;
            }
            inventorySetupLog.add("🔥 Acquired tinderbox for fire lighting");
        } else {
            inventorySetupLog.add("✅ Fire tool: Tinderbox in inventory");
        }
        
        return true;
    }
    
    /**
     * Arranges tools optimally with automatic axe handling.
     */
    private boolean arrangeToolsOptimally() {
        inventorySetupLog.add("🎯 Arranging tools optimally...");
            
        // Get current axe decision
        WintertodtAxeManager.AxeDecision axeDecision = WintertodtAxeManager.determineOptimalAxeSetup();
        
        // Move knife to optimal slot (always bottom-right)
            if (Rs2Inventory.hasItem(ItemID.KNIFE)) {
                moveItemToSlot(ItemID.KNIFE, KNIFE_SLOT, "knife to bottom-right corner");
            }
            
        // Move hammer next to knife if available
            if (Rs2Inventory.hasItem(ItemID.HAMMER)) {
                moveItemToSlot(ItemID.HAMMER, HAMMER_SLOT, "hammer next to knife");
            }
            
        // Handle axe placement if in inventory
        if (axeDecision.shouldKeepInInventory() && Rs2Inventory.hasItem(axeDecision.getAxeId())) {
            moveItemToSlot(axeDecision.getAxeId(), AXE_SLOT, axeDecision.getAxeName() + " to designated slot");
            }
            
        // Move tinderbox if needed
        if (Rs2Inventory.hasItem(ItemID.TINDERBOX)) {
            moveItemToSlot(ItemID.TINDERBOX, TINDERBOX_SLOT, "tinderbox to designated slot");
            }
            
        inventorySetupLog.add("✅ Tool arrangement completed optimally");
            return true;
    }
    
    /**
     * Checks if axe should be in inventory (automatic detection).
     */
    private boolean needsAxeInInventory() {
        WintertodtAxeManager.AxeDecision axeDecision = WintertodtAxeManager.determineOptimalAxeSetup();
        return axeDecision.shouldKeepInInventory();
    }
    
    /**
     * Moves a specific item to a target slot if not already there.
     */
    private void moveItemToSlot(int itemId, int targetSlot, String description) {
        try {
            Rs2ItemModel item = Rs2Inventory.get(itemId);
            if (item != null && item.getSlot() != targetSlot) {
                if (Rs2Inventory.moveItemToSlot(item, targetSlot)) {
                    sleepUntil(() -> {
                        Rs2ItemModel movedItem = Rs2Inventory.get(itemId);
                        return movedItem != null && movedItem.getSlot() == targetSlot;
                    }, 2000);
                    inventorySetupLog.add("✓ Moved " + description + " (slot " + targetSlot + ")");
                    sleepGaussian(300, 100); // Small delay between moves
                }
            }
        } catch (Exception e) {
            Microbot.log("Error moving item " + itemId + " to slot " + targetSlot + ": " + e.getMessage());
        }
    }
    
    /**
     * Logs the final inventory setup for debugging.
     */
    private void logInventorySetup() {
        Microbot.log("=== Inventory Setup Complete ===");
        for (String logEntry : inventorySetupLog) {
            Microbot.log(logEntry);
        }
        
        // Show tool layout
        Microbot.log("Tool Layout:");
        
        // Check knife in slot 27
        Rs2ItemModel knifeItem = Rs2Inventory.get(ItemID.KNIFE);
        if (knifeItem != null && knifeItem.getSlot() == KNIFE_SLOT) {
            Microbot.log("  Slot 27: Knife ✓");
        }
        
        // Check hammer in slot 26
        Rs2ItemModel hammerItem = Rs2Inventory.get(ItemID.HAMMER);
        if (hammerItem != null && hammerItem.getSlot() == HAMMER_SLOT) {
            Microbot.log("  Slot 26: Hammer ✓");
        }
        
        // Check tinderbox in slot 25
        Rs2ItemModel tinderboxItem = Rs2Inventory.get(ItemID.TINDERBOX);
        if (tinderboxItem != null && tinderboxItem.getSlot() == TINDERBOX_SLOT) {
            Microbot.log("  Slot 25: Tinderbox ✓");
        }
        
        // Check for axe in slot 24
        for (int axeId : AXE_PRIORITY) {
            Rs2ItemModel axeItem = Rs2Inventory.get(axeId);
            if (axeItem != null && axeItem.getSlot() == AXE_SLOT) {
                Microbot.log("  Slot 24: " + getAxeName(axeId) + " ✓");
                break;
            }
        }
        
        Microbot.log("Left side free for logs, kindling and food");
    }
    
    /**
     * Resets the inventory manager state completely.
     */
    public void reset() {
        inventorySetupLog.clear();
        
        // Clear any cached state
        Microbot.log("Inventory manager state reset - ready for fresh setup");
    }
    
    /**
     * Gets the inventory setup log for display.
     */
    public List<String> getInventorySetupLog() {
        return new ArrayList<>(inventorySetupLog);
    }
    
    /**
     * Gets the display name for an axe.
     */
    private String getAxeName(int axeId) {
        switch (axeId) {
            case ItemID.INFERNAL_AXE: return "Infernal axe";
            case ItemID.DRAGON_AXE: return "Dragon axe";
            case ItemID.RUNE_AXE: return "Rune axe";
            case ItemID.ADAMANT_AXE: return "Adamant axe";
            case ItemID.MITHRIL_AXE: return "Mithril axe";
            case ItemID.STEEL_AXE: return "Steel axe";
            case ItemID.IRON_AXE: return "Iron axe";
            case ItemID.BRONZE_AXE: return "Bronze axe";
            default: return "Unknown axe";
        }
    }
} 