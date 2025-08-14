package net.runelite.client.plugins.microbot.bga.autofishing.managers;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.bga.autofishing.AutoFishingConfig;
import net.runelite.client.plugins.microbot.bga.autofishing.enums.Fish;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import javax.inject.Singleton;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages inventory operations including smart banking, deposits, and item management
 */
@Singleton
public class InventoryManager {
    
    
    /**
     * Deposits all raw fish found in inventory (not just the current fishing type)
     */
    public boolean depositFish() {
        try {
            boolean success = true;
            
            // Get all possible raw fish names from all fish types
            String[] allRawFish = getAllPossibleRawFish();
            
            // Deposit each raw fish type if present in inventory
            for (String rawFish : allRawFish) {
                if (Rs2Inventory.hasItem(rawFish)) {
                    boolean deposited = false;
                    
                    if (Rs2Bank.isOpen()) {
                        deposited = Rs2Bank.depositAll(rawFish);
                    } else if (Rs2DepositBox.isOpen()) {
                        deposited = Rs2DepositBox.depositAll(rawFish);
                    }
                    
                    if (deposited) {
                        Rs2Inventory.waitForInventoryChanges(500);
                        Microbot.log("Deposited: " + rawFish);
                    }
                    
                    success &= deposited;
                }
            }
            
            return success;
        } catch (Exception e) {
            Microbot.log("Error depositing fish: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Deposits treasure items (clue bottles, caskets, scroll boxes)
     */
    public boolean depositTreasures(AutoFishingConfig config) {
        boolean success = true;
        
        try {
            if (config.shouldBankClueBottles()) {
                success &= depositItemByName("clue bottle");
            }
            
            if (config.shouldBankCaskets()) {
                success &= depositItemByName("casket");
            }
            
            if (config.shouldBankScrollBoxes()) {
                // Deposit all scroll box variants
                success &= depositItemByName("scroll box (easy)");
                success &= depositItemByName("scroll box (medium)");
                success &= depositItemByName("scroll box (hard)");
                success &= depositItemByName("scroll box (elite)");
                success &= depositItemByName("scroll box (master)");
                // Generic fallback for any other scroll box variants
                success &= depositItemByName("scroll box");
            }
            
            // Empty fish barrel if banking
            if (Rs2Bank.isOpen()) {
                Rs2Bank.emptyFishBarrel();
            }
            
            return success;
        } catch (Exception e) {
            Microbot.log("Error depositing treasures: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Drops all raw fish items when banking is disabled
     */
    public boolean dropFish() {
        try {
            boolean success = true;
            
            // Get all possible raw fish names
            String[] allRawFish = getAllPossibleRawFish();
            
            // Drop each raw fish type if present in inventory
            for (String rawFish : allRawFish) {
                if (Rs2Inventory.hasItem(rawFish)) {
                    boolean dropped = Rs2Inventory.dropAll(rawFish);
                    if (dropped) {
                        Rs2Inventory.waitForInventoryChanges(300);
                        Microbot.log("Dropped: " + rawFish);
                    }
                    success &= dropped;
                }
            }
            
            return success;
        } catch (Exception e) {
            Microbot.log("Error dropping fish: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Checks if inventory has space for more fish
     */
    public boolean hasInventorySpace() {
        return !Rs2Inventory.isFull();
    }
    
    /**
     * Checks if inventory is full
     */
    public boolean isInventoryFull() {
        return Rs2Inventory.isFull();
    }
    
    /**
     * Gets count of fish in inventory
     */
    public int getFishCount(Fish fish) {
        try {
            int count = 0;
            for (String rawFish : fish.getRawNames()) {
                count += Rs2Inventory.count(rawFish);
            }
            return count;
        } catch (Exception e) {
            Microbot.log("Error counting fish: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Validates successful deposit operation
     */
    public boolean validateDepositSuccess() {
        // Check that total fish count decreased significantly
        int totalRemainingFish = getTotalFishCount();
        return totalRemainingFish <= 2; // Allow for a few stragglers
    }
    
    /**
     * Gets total count of all fish in inventory
     */
    public int getTotalFishCount() {
        try {
            int totalCount = 0;
            String[] allRawFish = getAllPossibleRawFish();
            
            for (String rawFish : allRawFish) {
                totalCount += Rs2Inventory.count(rawFish);
            }
            
            return totalCount;
        } catch (Exception e) {
            Microbot.log("Error counting total fish: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Helper method to deposit items by name
     */
    private boolean depositItemByName(String itemName) {
        try {
            boolean deposited = false;
            
            if (Rs2Bank.isOpen()) {
                deposited = Rs2Bank.depositAll(itemName);
            } else if (Rs2DepositBox.isOpen()) {
                deposited = Rs2DepositBox.depositAll(itemName);
            }
            
            if (deposited) {
                Rs2Inventory.waitForInventoryChanges(1000);
            }
            
            return deposited;
        } catch (Exception e) {
            Microbot.log("Error depositing " + itemName + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets all possible raw fish names from all fish types
     */
    private String[] getAllPossibleRawFish() {
        Set<String> allRawFish = new HashSet<>();
        
        // Add raw fish names from all fish types
        for (Fish fish : Fish.values()) {
            allRawFish.addAll(fish.getRawNames());
        }
        
        return allRawFish.toArray(new String[0]);
    }
}