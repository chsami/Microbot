package net.runelite.client.plugins.microbot.MKE.wintertodt.startup.gear;

import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.Arrays;
import java.util.List;

import static net.runelite.client.plugins.microbot.util.Global.sleepGaussian;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

/**
 * Intelligent axe management system that automatically determines optimal axe usage.
 * Considers player levels, available gear, and inventory optimization.
 * Also handles bruma torch conversion to offhand version when optimal.
 */
public class WintertodtAxeManager {
    
    // Axe priority list (best to worst)
    private static final List<AxeInfo> AXE_PRIORITY = Arrays.asList(
        new AxeInfo(ItemID.INFERNAL_AXE, "Infernal axe", 61, 61),
        new AxeInfo(ItemID.DRAGON_AXE, "Dragon axe", 61, 60),
        new AxeInfo(ItemID.CRYSTAL_AXE, "Crystal axe", 71, 70),
        new AxeInfo(ItemID.RUNE_AXE, "Rune axe", 41, 40),
        new AxeInfo(ItemID.ADAMANT_AXE, "Adamant axe", 31, 30),
        new AxeInfo(ItemID.MITHRIL_AXE, "Mithril axe", 21, 20),
        new AxeInfo(ItemID.BLACK_AXE, "Black axe", 11, 10),
        new AxeInfo(ItemID.STEEL_AXE, "Steel axe", 6, 5),
        new AxeInfo(ItemID.IRON_AXE, "Iron axe", 1, 1),
        new AxeInfo(ItemID.BRONZE_AXE, "Bronze axe", 1, 1)
    );
    
    // Bruma torch variants
    private static final List<Integer> BRUMA_TORCH_ITEMS = Arrays.asList(
        ItemID.BRUMA_TORCH,
        ItemID.BRUMA_TORCH_OFFHAND
    );
    
    private static class AxeInfo {
        final int itemId;
        final String name;
        final int wcLevel;
        final int attackLevel;
        
        AxeInfo(int itemId, String name, int wcLevel, int attackLevel) {
            this.itemId = itemId;
            this.name = name;
            this.wcLevel = wcLevel;
            this.attackLevel = attackLevel;
        }
    }
    
    /**
     * Determines the optimal axe configuration automatically.
     * @return AxeDecision containing the best setup
     */
    public static AxeDecision determineOptimalAxeSetup() {
        try {
            Microbot.log("Analyzing optimal axe configuration...");
            
            int wcLevel = Rs2Player.getRealSkillLevel(Skill.WOODCUTTING);
            int attackLevel = Rs2Player.getRealSkillLevel(Skill.ATTACK);
            
            // Find the best available axe
            AxeInfo bestAxe = findBestAvailableAxe(wcLevel, attackLevel);
            if (bestAxe == null) {
                Microbot.log("No suitable axe found!");
                return new AxeDecision(false, 0, "No axe available", false, false);
            }
            
            // Check bruma torch situation and handle conversion if needed
            BrumaTorchAnalysis torchAnalysis = analyzeBrumaTorchSituation();
            
            boolean canWieldAxe = attackLevel >= bestAxe.attackLevel;
            
            // Decision logic
            boolean shouldEquipAxe = determineIfShouldEquipAxe(bestAxe, canWieldAxe, torchAnalysis);
            
            String reasoning = buildReasoningString(bestAxe, canWieldAxe, torchAnalysis, shouldEquipAxe);
            
            Microbot.log("Axe decision: " + reasoning);
            
            return new AxeDecision(shouldEquipAxe, bestAxe.itemId, bestAxe.name, canWieldAxe, torchAnalysis.needsConversion);
            
        } catch (Exception e) {
            Microbot.log("Error determining axe setup: " + e.getMessage());
            e.printStackTrace();
            return new AxeDecision(false, ItemID.IRON_AXE, "Iron axe (fallback)", false, false);
        }
    }
    
    /**
     * Analyzes bruma torch situation and determines if conversion is needed.
     */
    private static BrumaTorchAnalysis analyzeBrumaTorchSituation() {
        int firLevel = Rs2Player.getRealSkillLevel(Skill.FIREMAKING);
        
        // Check if we can use bruma torch (level 50 firemaking required)
        if (firLevel < 50) {
            return new BrumaTorchAnalysis(false, false, false, "Insufficient Firemaking level (need 50)");
        }
        
        // Check if we already have offhand bruma torch
        if (hasAccess(ItemID.BRUMA_TORCH_OFFHAND)) {
            return new BrumaTorchAnalysis(true, false, true, "Already have offhand bruma torch");
        }
        
        // Check if we have regular bruma torch that could be converted
        if (hasAccess(ItemID.BRUMA_TORCH)) {
            return new BrumaTorchAnalysis(true, true, false, "Have regular bruma torch - should convert to offhand");
        }
        
        return new BrumaTorchAnalysis(false, false, false, "No bruma torch available");
    }
    
    /**
     * Performs bruma torch conversion from regular to offhand.
     * @return true if conversion was successful or not needed
     */
    public static boolean performBrumaTorchConversion() {
        try {
            // Check if we need to convert
            BrumaTorchAnalysis analysis = analyzeBrumaTorchSituation();
            if (!analysis.needsConversion) {
                Microbot.log("No bruma torch conversion needed");
                return true;
            }
            
            Microbot.log("🔥 Starting bruma torch conversion to offhand version...");
            
            boolean bankWasOpen = Rs2Bank.isOpen();
            
            // Ensure regular bruma torch is in inventory
            if (!ensureBrumaTorchInInventory()) {
                return false;
            }
            
            // MUST close bank before swapping items
            if (Rs2Bank.isOpen()) {
                Microbot.log("Closing bank to perform bruma torch swap...");
                Rs2Bank.closeBank();
                sleepUntilTrue(() -> !Rs2Bank.isOpen(), 100, 3000);
                sleepGaussian(500, 200); // Additional delay to ensure bank is fully closed
            }
            
            // Perform the conversion
            if (Rs2Inventory.hasItem(ItemID.BRUMA_TORCH)) {
                Microbot.log("Converting bruma torch to offhand version...");
                
                // Right click and select "Swap"
                if (Rs2Inventory.interact(ItemID.BRUMA_TORCH, "Swap")) {
                    // Wait for conversion to complete
                    boolean converted = sleepUntilTrue(() -> 
                        Rs2Inventory.hasItem(ItemID.BRUMA_TORCH_OFFHAND), 100, 5000);
                    
                    if (converted) {
                        Microbot.log("Successfully converted bruma torch to offhand version!");
                        
                        // Reopen bank if it was originally open
                        if (bankWasOpen) {
                            Microbot.log("Reopening bank after successful conversion...");
                            if (!Rs2Inventory.isOpen()) {
                                Rs2Inventory.open();
                            }
                            Rs2Bank.useBank();
                            sleepUntilTrue(() -> Rs2Bank.isOpen(), 100, 3000);
                        }
                        
                        return true;
                    } else {
                        Microbot.log("Failed to convert bruma torch - timeout");
                        
                        // Reopen bank even if conversion failed
                        if (bankWasOpen) {
                            Microbot.log("Reopening bank after failed conversion...");
                            if (!Rs2Inventory.isOpen()) {
                                Rs2Inventory.open();
                            }
                            Rs2Bank.useBank();
                            sleepUntilTrue(() -> Rs2Bank.isOpen(), 100, 3000);
                        }
                        
                        return false;
                    }
                } else {
                    Microbot.log("Failed to interact with bruma torch for conversion");
                    
                    // Reopen bank if interaction failed
                    if (bankWasOpen) {
                        Microbot.log("Reopening bank after failed interaction...");
                        if (!Rs2Inventory.isOpen()) {
                            Rs2Inventory.open();
                        }
                        Rs2Bank.useBank();
                        sleepUntilTrue(() -> Rs2Bank.isOpen(), 100, 3000);
                    }
                    
                    return false;
                }
            } else {
                Microbot.log("Bruma torch not found in inventory for conversion");
                
                // Reopen bank if torch not found
                if (bankWasOpen) {
                    Microbot.log("Reopening bank after torch not found...");
                    if (!Rs2Inventory.isOpen()) {
                        Rs2Inventory.open();
                    }
                    Rs2Bank.useBank();
                    sleepUntilTrue(() -> Rs2Bank.isOpen(), 100, 3000);
                }
                
                return false;
            }
            
        } catch (Exception e) {
            Microbot.log("Error during bruma torch conversion: " + e.getMessage());
            e.printStackTrace();
            
            // Try to reopen bank in case of exception
            try {
                if (!Rs2Bank.isOpen()) {
                    if (!Rs2Inventory.isOpen()) {
                        Rs2Inventory.open();
                    }
                    Rs2Bank.useBank();
                    sleepUntilTrue(() -> Rs2Bank.isOpen(), 100, 3000);
                }
            } catch (Exception reopenException) {
                Microbot.log("Failed to reopen bank after conversion error: " + reopenException.getMessage());
            }
            
            return false;
        }
    }
    
    /**
     * Ensures the regular bruma torch is in inventory for conversion.
     */
    private static boolean ensureBrumaTorchInInventory() {
        try {
            // Already in inventory
            if (Rs2Inventory.hasItem(ItemID.BRUMA_TORCH)) {
                return true;
            }
            
            // Check if equipped and unequip it
            if (Rs2Equipment.isWearing(ItemID.BRUMA_TORCH)) {
                Microbot.log("Unequipping bruma torch for conversion...");
                Rs2Equipment.unEquip(net.runelite.api.EquipmentInventorySlot.WEAPON);
                sleepUntilTrue(() -> Rs2Inventory.hasItem(ItemID.BRUMA_TORCH), 100, 3000);
                return Rs2Inventory.hasItem(ItemID.BRUMA_TORCH);
            }
            
            // Check if in bank and withdraw it
            if (Rs2Bank.isOpen() && Rs2Bank.hasBankItem(ItemID.BRUMA_TORCH, 1)) {
                Microbot.log("Withdrawing bruma torch from bank for conversion...");
                Rs2Bank.withdrawX(ItemID.BRUMA_TORCH, 1);
                sleepUntilTrue(() -> Rs2Inventory.hasItem(ItemID.BRUMA_TORCH), 100, 3000);
                return Rs2Inventory.hasItem(ItemID.BRUMA_TORCH);
            }
            
            Microbot.log("Cannot find bruma torch to convert");
            return false;
            
        } catch (Exception e) {
            Microbot.log("Error ensuring bruma torch in inventory: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Finds the best axe we can use and have access to.
     */
    private static AxeInfo findBestAvailableAxe(int wcLevel, int attackLevel) {
        for (AxeInfo axe : AXE_PRIORITY) {
            // Must meet woodcutting requirement
            if (wcLevel < axe.wcLevel) {
                continue;
            }
            
            // Check if we have access to this axe
            if (hasAccess(axe.itemId)) {
                Microbot.log("Found available axe: " + axe.name + " (WC: " + axe.wcLevel + ", ATK: " + axe.attackLevel + ")");
                return axe;
            }
        }
        
        return null;
    }
    
    /**
     * Checks if we have access to an item (equipped, inventory, or bank).
     */
    private static boolean hasAccess(int itemId) {
        // Check if equipped
        if (Rs2Equipment.isWearing(itemId)) {
            return true;
        }
        
        // Check inventory
        if (Rs2Inventory.hasItem(itemId)) {
            return true;
        }
        
        // Check bank if open
        if (Rs2Bank.isOpen() && Rs2Bank.hasBankItem(itemId, 1)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Core decision logic for whether to equip or carry the axe.
     */
    private static boolean determineIfShouldEquipAxe(AxeInfo bestAxe, boolean canWieldAxe, BrumaTorchAnalysis torchAnalysis) {
        // Can't equip if we don't meet attack requirements
        if (!canWieldAxe) {
            Microbot.log("Cannot wield " + bestAxe.name + " (need " + bestAxe.attackLevel + " Attack) - keeping in inventory");
            return false;
        }
        
        // If we have or will have bruma torch (offhand), definitely equip the axe
        if (torchAnalysis.hasBrumaTorch) {
            if (torchAnalysis.needsConversion) {
                Microbot.log("Will convert bruma torch to offhand, then equip axe for optimal setup");
            } else {
                Microbot.log("Have bruma torch available - equipping axe for optimal setup");
            }
            return true;
        }
        
        // No bruma torch - decision based on inventory space efficiency
        // For most cases, equipped axe + tinderbox is better than axe in inventory + tinderbox
        // Both take up space, but equipped axe frees up inventory slot
        Microbot.log("No bruma torch - equipping axe and carrying tinderbox for optimal inventory space");
        return true;
    }
    
    /**
     * Builds a human-readable explanation of the decision.
     */
    private static String buildReasoningString(AxeInfo bestAxe, boolean canWieldAxe, BrumaTorchAnalysis torchAnalysis, boolean shouldEquipAxe) {
        StringBuilder sb = new StringBuilder();
        sb.append(bestAxe.name);
        
        if (!canWieldAxe) {
            sb.append(" (inventory only - insufficient Attack level)");
        } else if (shouldEquipAxe) {
            sb.append(" (equipped");
            if (torchAnalysis.hasOffhand) {
                sb.append(" + bruma torch offhand");
            } else if (torchAnalysis.needsConversion) {
                sb.append(" + bruma torch offhand after conversion");
            } else if (torchAnalysis.hasBrumaTorch) {
                sb.append(" + bruma torch");
            } else {
                sb.append(" + tinderbox in inventory");
            }
            sb.append(")");
        } else {
            sb.append(" (inventory + tinderbox)");
        }
        
        return sb.toString();
    }
    
    /**
     * Class to hold bruma torch analysis results.
     */
    private static class BrumaTorchAnalysis {
        final boolean hasBrumaTorch;
        final boolean needsConversion;
        final boolean hasOffhand;
        final String reason;
        
        BrumaTorchAnalysis(boolean hasBrumaTorch, boolean needsConversion, boolean hasOffhand, String reason) {
            this.hasBrumaTorch = hasBrumaTorch;
            this.needsConversion = needsConversion;
            this.hasOffhand = hasOffhand;
            this.reason = reason;
        }
        
        @Override
        public String toString() {
            return String.format("BrumaTorch{has=%s, convert=%s, offhand=%s, reason='%s'}", 
                               hasBrumaTorch, needsConversion, hasOffhand, reason);
        }
    }
    
    /**
     * Result class containing the axe decision.
     */
    public static class AxeDecision {
        private final boolean shouldEquip;
        private final int axeId;
        private final String axeName;
        private final boolean canWield;
        private final boolean needsTorchConversion;
        
        public AxeDecision(boolean shouldEquip, int axeId, String axeName, boolean canWield, boolean needsTorchConversion) {
            this.shouldEquip = shouldEquip;
            this.axeId = axeId;
            this.axeName = axeName;
            this.canWield = canWield;
            this.needsTorchConversion = needsTorchConversion;
        }
        
        public boolean shouldEquipAxe() { return shouldEquip; }
        public boolean shouldKeepInInventory() { return !shouldEquip; }
        public int getAxeId() { return axeId; }
        public String getAxeName() { return axeName; }
        public boolean canWieldAxe() { return canWield; }
        public boolean needsBrumaTorchConversion() { return needsTorchConversion; }
        
        @Override
        public String toString() {
            return String.format("AxeDecision{axe=%s, equip=%s, canWield=%s, convertTorch=%s}", 
                               axeName, shouldEquip, canWield, needsTorchConversion);
        }
    }
} 