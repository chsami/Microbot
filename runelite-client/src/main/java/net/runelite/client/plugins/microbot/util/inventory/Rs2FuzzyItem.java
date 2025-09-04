package net.runelite.client.plugins.microbot.util.inventory;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsVariationMapping;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for handling fuzzy item matching and counting.
 * Supports both ID-based variations (via InventorySetupsVariationMapping) and name-based charged items.
 * 
 * This class provides comprehensive fuzzy counting utilities for:
 * - Item variations with different IDs but same base functionality
 * - Charged items with varying charge levels (e.g., "Amulet of glory(6)", "Amulet of glory(5)", etc.)
 * - Combination of both variation types for complete fuzzy matching
 */
@Slf4j
public class Rs2FuzzyItem {
    
    // Pattern to detect charged items with numbers in parentheses, e.g., "Amulet of glory(6)"
    private static final Pattern CHARGED_ITEM_PATTERN = Pattern.compile(".*\\((\\d+)\\)$");
    
    // Maximum charges to check for charged items
    private static final int MAX_CHARGES = 8;
    
    /**
     * Data class representing a fuzzy item with charge information.
     */
    public static class FuzzyItemInfo {
        private final String name;
        private final int id;
        private final int charges;
        private final int quantity;
        private final boolean isWearing;
        
        public FuzzyItemInfo(String name, int id, int charges, int quantity, boolean isWearing) {
            this.name = name;
            this.id = id;
            this.charges = charges;
            this.quantity = quantity;
            this.isWearing = isWearing;
        }
        
        public String getName() { return name; }
        public int getId() { return id; }
        public int getCharges() { return charges; }
        public int getQuantity() { return quantity; }
        public boolean isWearing() { return isWearing; }
        
        @Override
        public String toString() {
            return String.format("FuzzyItemInfo{name='%s', id=%d, charges=%d, quantity=%d, wearing=%s}", 
                    name, id, charges, quantity, isWearing);
        }
    }
    
    /**
     * Gets the total fuzzy count of an item in inventory, including all ID variations and charged variants.
     * 
     * @param itemId the base item ID
     * @param includeUncharged whether to include uncharged variants (charge level 0 or no charges)
     * @return total count of all fuzzy matches
     */
    public static int getFuzzyInventoryCount(int itemId, boolean includeUncharged) {
        int count = 0;
        
        // Count ID-based variations
        Collection<Integer> variations = InventorySetupsVariationMapping.getVariations(itemId);
        for (int variationId : variations) {
            count += Rs2Inventory.count(variationId);
        }
        
        // Count name-based charged variants
        count += getChargedInventoryCount(itemId, includeUncharged, 0, MAX_CHARGES);
        
        return count;
    }
    public static int getFuzzyInventoryQuantity(int itemId, boolean includeUncharged) {
        int count = 0;
        
        // Count ID-based variations
        Collection<Integer> variations = InventorySetupsVariationMapping.getVariations(itemId);
        for (int variationId : variations) {
            count += Rs2Inventory.itemQuantity(variationId);
        }
        
        // Count name-based charged variants
        count += getChargedInventoryQuantity(itemId, includeUncharged, 0, MAX_CHARGES);
        
        return count;
    }
    
    /**
     * Gets the total fuzzy count of an item in bank, including all ID variations and charged variants.
     * 
     * @param itemId the base item ID
     * @param includeUncharged whether to include uncharged variants (charge level 0 or no charges)
     * @return total count of all fuzzy matches
     */
    public static int getFuzzyBankCount(int itemId, boolean includeUncharged) {
        int count = 0;
        
        // Count ID-based variations
        Collection<Integer> variations = InventorySetupsVariationMapping.getVariations(itemId);
        for (int variationId : variations) {
            count += Rs2Bank.count(variationId);
        }
        
        // Count name-based charged variants
        count += getChargedBankCount(itemId, includeUncharged, 0, MAX_CHARGES);
        
        return count;
    }
    
    /**
     * Gets the total fuzzy count of an item equipped, including all ID variations and charged variants.
     * 
     * @param itemId the base item ID
     * @param includeUncharged whether to include uncharged variants (charge level 0 or no charges)
     * @return 1 if any fuzzy match is equipped, 0 otherwise
     */
    public static int getFuzzyEquippedCount(int itemId, boolean includeUncharged) {
        // Check ID-based variations
        Collection<Integer> variations = InventorySetupsVariationMapping.getVariations(itemId);
        for (int variationId : variations) {
            if (Rs2Equipment.isWearing(variationId)) {
                return 1;
            }
        }
        
        // Check name-based charged variants
        return getChargedEquippedCount(itemId, includeUncharged, 0, MAX_CHARGES) > 0 ? 1 : 0;
    }
    
    /**
     * Gets the count of charged items in inventory within a specific charge range.
     * 
     * @param itemId the base item ID
     * @param includeUncharged whether to include uncharged variants
     * @param minCharges minimum charge level (inclusive)
     * @param maxCharges maximum charge level (inclusive)
     * @return total count of charged items in the specified range
     */
    public static int getChargedInventoryCount(int itemId, boolean includeUncharged, int minCharges, int maxCharges) {
        String baseName = getBaseItemName(itemId);
        if (baseName == null || baseName.isEmpty()) {
            return 0;
        }
        
        int count = 0;
        List<Rs2ItemModel> inventoryItems = Rs2Inventory.all();
        
        for (Rs2ItemModel item : inventoryItems) {
            String itemName = item.getName();
            if (isChargedVariant(itemName, baseName, includeUncharged)) {
                int charges = getChargeFromName(itemName);
                if (charges >= minCharges && charges <= maxCharges) {
                    count += 1;
                }
            }
        }
        
        return count;
    }

    public static int getChargedInventoryQuantity(int itemId, boolean includeUncharged, int minCharges, int maxCharges) {
        String baseName = getBaseItemName(itemId);
        if (baseName == null || baseName.isEmpty()) {
            return 0;
        }
        
        int count = 0;
        List<Rs2ItemModel> inventoryItems = Rs2Inventory.all();
        
        for (Rs2ItemModel item : inventoryItems) {
            String itemName = item.getName();
            if (isChargedVariant(itemName, baseName, includeUncharged)) {
                int charges = getChargeFromName(itemName);
                if (charges >= minCharges && charges <= maxCharges) {
                    count += item.getQuantity();
                }
            }
        }
        
        return count;
    }
    
    /**
     * Gets the count of charged items in bank within a specific charge range.
     * 
     * @param itemId the base item ID
     * @param includeUncharged whether to include uncharged variants
     * @param minCharges minimum charge level (inclusive)
     * @param maxCharges maximum charge level (inclusive)
     * @return total count of charged items in the specified range
     */
    public static int getChargedBankCount(int itemId, boolean includeUncharged, int minCharges, int maxCharges) {
        String baseName = getBaseItemName(itemId);
        if (baseName == null || baseName.isEmpty()) {
            return 0;
        }
        
        int count = 0;
        List<Rs2ItemModel> bankItems = Rs2Bank.bankItems();
        
        for (Rs2ItemModel item : bankItems) {
            String itemName = item.getName();
            if (isChargedVariant(itemName, baseName, includeUncharged)) {
                int charges = getChargeFromName(itemName);
                if (charges >= minCharges && charges <= maxCharges) {
                    count += Rs2Bank.count(itemName);
                }
            }
        }
        
        return count;
    }
    
    /**
     * Gets the count of charged items equipped within a specific charge range.
     * 
     * @param itemId the base item ID
     * @param includeUncharged whether to include uncharged variants
     * @param minCharges minimum charge level (inclusive)
     * @param maxCharges maximum charge level (inclusive)
     * @return 1 if any charged item in range is equipped, 0 otherwise
     */
    public static int getChargedEquippedCount(int itemId, boolean includeUncharged, int minCharges, int maxCharges) {
        String baseName = getBaseItemName(itemId);
        if (baseName == null || baseName.isEmpty()) {
            return 0;
        }
        
        // Check each possible charge level
        for (int charges = minCharges; charges <= maxCharges; charges++) {
            String chargedName = baseName + "(" + charges + ")";
            if (Rs2Equipment.isWearing(chargedName)) {
                return 1;
            }
        }
        
        // Check uncharged variant if requested
        if (includeUncharged) {
            if (Rs2Equipment.isWearing(baseName) || Rs2Equipment.isWearing(baseName + "(0)")) {
                return 1;
            }
        }
        
        return 0;
    }
    
    /**
     * Gets the total charges available for an item across inventory, bank, and equipment.
     * 
     * @param itemId the base item ID
     * @return total charge count across all locations
     */
    public static int getTotalCharges(int itemId) {
        String baseName = getBaseItemName(itemId);
        if (baseName == null || baseName.isEmpty()) {
            return 0;
        }
        
        int totalCharges = 0;
        
        // Count charges in inventory
        List<Rs2ItemModel> inventoryItems = Rs2Inventory.all();
        for (Rs2ItemModel item : inventoryItems) {
            String itemName = item.getName();
            if (isChargedVariant(itemName, baseName, false)) {
                int charges = getChargeFromName(itemName);
                totalCharges += charges * item.getQuantity();
            }
        }
        
        // Count charges in bank
        List<Rs2ItemModel> bankItems = Rs2Bank.bankItems();
        for (Rs2ItemModel item : bankItems) {
            String itemName = item.getName();
            if (isChargedVariant(itemName, baseName, false)) {
                int charges = getChargeFromName(itemName);
                totalCharges += charges * Rs2Bank.count(itemName);
            }
        }
        
        // Count charges in equipment
        for (int charges = 1; charges <= MAX_CHARGES; charges++) {
            String chargedName = baseName + "(" + charges + ")";
            if (Rs2Equipment.isWearing(chargedName)) {
                totalCharges += charges;
                break; // Only one item can be equipped
            }
        }
        
        return totalCharges;
    }
    
    /**
     * Gets all fuzzy item variations in inventory with detailed information.
     * 
     * @param itemId the base item ID
     * @param includeUncharged whether to include uncharged variants
     * @return list of FuzzyItemInfo objects sorted by charges (highest first)
     */
    public static List<FuzzyItemInfo> getAllFuzzyItemsInInventory(int itemId, boolean includeUncharged) {
        List<FuzzyItemInfo> fuzzyItems = new ArrayList<>();
        
        // Add ID-based variations
        Collection<Integer> variations = InventorySetupsVariationMapping.getVariations(itemId);
        for (int variationId : variations) {
            int count = Rs2Inventory.count(variationId);
            if (count > 0) {
                String name = getItemName(variationId);
                fuzzyItems.add(new FuzzyItemInfo(name, variationId, -1, count, false));
            }
        }
        
        // Add name-based charged variants
        String baseName = getBaseItemName(itemId);
        if (baseName != null && !baseName.isEmpty()) {
            List<Rs2ItemModel> inventoryItems = Rs2Inventory.all();
            for (Rs2ItemModel item : inventoryItems) {
                String itemName = item.getName();
                if (isChargedVariant(itemName, baseName, includeUncharged)) {
                    int charges = getChargeFromName(itemName);
                    fuzzyItems.add(new FuzzyItemInfo(itemName, item.getId(), charges, item.getQuantity(), false));
                }
            }
        }
        
        // Sort by charges (highest first), with special handling for uncharged (-1) items
        fuzzyItems.sort((a, b) -> {
            if (a.getCharges() == -1 && b.getCharges() == -1) return 0;
            if (a.getCharges() == -1) return 1; // Put variation items at the end
            if (b.getCharges() == -1) return -1;
            return Integer.compare(b.getCharges(), a.getCharges());
        });
        
        return fuzzyItems;
    }
    
    /**
     * Gets all fuzzy item variations in bank with detailed information.
     * 
     * @param itemId the base item ID
     * @param includeUncharged whether to include uncharged variants
     * @return list of FuzzyItemInfo objects sorted by charges (highest first)
     */
    public static List<FuzzyItemInfo> getAllFuzzyItemsInBank(int itemId, boolean includeUncharged) {
        List<FuzzyItemInfo> fuzzyItems = new ArrayList<>();
        
        // Add ID-based variations
        Collection<Integer> variations = InventorySetupsVariationMapping.getVariations(itemId);
        for (int variationId : variations) {
            int count = Rs2Bank.count(variationId);
            if (count > 0) {
                String name = getItemName(variationId);
                fuzzyItems.add(new FuzzyItemInfo(name, variationId, -1, count, false));
            }
        }
        
        // Add name-based charged variants
        String baseName = getBaseItemName(itemId);
        if (baseName != null && !baseName.isEmpty()) {
            List<Rs2ItemModel> bankItems = Rs2Bank.bankItems();
            for (Rs2ItemModel item : bankItems) {
                String itemName = item.getName();
                if (isChargedVariant(itemName, baseName, includeUncharged)) {
                    int charges = getChargeFromName(itemName);
                    int count = Rs2Bank.count(itemName);
                    if (count > 0) {
                        fuzzyItems.add(new FuzzyItemInfo(itemName, item.getId(), charges, count, false));
                    }
                }
            }
        }
        
        // Sort by charges (highest first), with special handling for uncharged (-1) items
        fuzzyItems.sort((a, b) -> {
            if (a.getCharges() == -1 && b.getCharges() == -1) return 0;
            if (a.getCharges() == -1) return 1; // Put variation items at the end
            if (b.getCharges() == -1) return -1;
            return Integer.compare(b.getCharges(), a.getCharges());
        });
        
        return fuzzyItems;
    }
    
    /**
     * Gets the best (highest charged) fuzzy item match in inventory.
     * 
     * @param itemId the base item ID
     * @param includeUncharged whether to include uncharged variants
     * @return the best FuzzyItemInfo match, or null if none found
     */
    public static FuzzyItemInfo getBestFuzzyItemInInventory(int itemId, boolean includeUncharged) {
        List<FuzzyItemInfo> items = getAllFuzzyItemsInInventory(itemId, includeUncharged);
        return items.isEmpty() ? null : items.get(0);
    }
    
    /**
     * Gets the best (highest charged) fuzzy item match in bank.
     * 
     * @param itemId the base item ID
     * @param includeUncharged whether to include uncharged variants
     * @return the best FuzzyItemInfo match, or null if none found
     */
    public static FuzzyItemInfo getBestFuzzyItemInBank(int itemId, boolean includeUncharged) {
        List<FuzzyItemInfo> items = getAllFuzzyItemsInBank(itemId, includeUncharged);
        return items.isEmpty() ? null : items.get(0);
    }
    
    /**
     * Gets the fuzzy item currently equipped.
     * 
     * @param itemId the base item ID
     * @param includeUncharged whether to include uncharged variants
     * @return the equipped FuzzyItemInfo, or null if none found
     */
    public static FuzzyItemInfo getFuzzyItemEquipped(int itemId, boolean includeUncharged) {
        // Check ID-based variations first
        Collection<Integer> variations = InventorySetupsVariationMapping.getVariations(itemId);
        for (int variationId : variations) {
            if (Rs2Equipment.isWearing(variationId)) {
                String name = getItemName(variationId);
                return new FuzzyItemInfo(name, variationId, -1, 1, true);
            }
        }
        
        // Check name-based charged variants
        String baseName = getBaseItemName(itemId);
        if (baseName != null && !baseName.isEmpty()) {
            // Check charged variants (highest first)
            for (int charges = MAX_CHARGES; charges >= 1; charges--) {
                String chargedName = baseName + "(" + charges + ")";
                if (Rs2Equipment.isWearing(chargedName)) {
                    Rs2ItemModel equippedItem = Rs2Equipment.get(chargedName);
                    if (equippedItem != null) {
                        return new FuzzyItemInfo(chargedName, equippedItem.getId(), charges, 1, true);
                    }
                }
            }
            
            // Check uncharged variants if requested
            if (includeUncharged) {
                if (Rs2Equipment.isWearing(baseName)) {
                    Rs2ItemModel equippedItem = Rs2Equipment.get(baseName);
                    if (equippedItem != null) {
                        return new FuzzyItemInfo(baseName, equippedItem.getId(), 0, 1, true);
                    }
                }
                
                String unchargedName = baseName + "(0)";
                if (Rs2Equipment.isWearing(unchargedName)) {
                    Rs2ItemModel equippedItem = Rs2Equipment.get(unchargedName);
                    if (equippedItem != null) {
                        return new FuzzyItemInfo(unchargedName, equippedItem.getId(), 0, 1, true);
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Checks if an item has any fuzzy variants available (inventory, bank, or equipped).
     * 
     * @param itemId the base item ID
     * @param includeUncharged whether to include uncharged variants
     * @return true if any fuzzy variant is available, false otherwise
     */
    public static boolean hasFuzzyItemAvailable(int itemId, boolean includeUncharged) {
        return getFuzzyInventoryCount(itemId, includeUncharged) > 0 ||
               getFuzzyBankCount(itemId, includeUncharged) > 0 ||
               getFuzzyEquippedCount(itemId, includeUncharged) > 0;
    }
    
    /**
     * Gets all item names that match the fuzzy criteria for the given item ID.
     * 
     * @param itemId the base item ID
     * @param includeUncharged whether to include uncharged variants
     * @return set of all matching item names
     */
    public static Set<String> getFuzzyItemNames(int itemId, boolean includeUncharged) {
        Set<String> names = new HashSet<>();
        
        // Add ID-based variation names
        Collection<Integer> variations = InventorySetupsVariationMapping.getVariations(itemId);
        for (int variationId : variations) {
            String name = getItemName(variationId);
            if (name != null && !name.isEmpty()) {
                names.add(name);
            }
        }
        
        // Add name-based charged variant names
        String baseName = getBaseItemName(itemId);
        if (baseName != null && !baseName.isEmpty()) {
            // Add charged variants
            for (int charges = 1; charges <= MAX_CHARGES; charges++) {
                names.add(baseName + "(" + charges + ")");
            }
            
            // Add uncharged variants if requested
            if (includeUncharged) {
                names.add(baseName);
                names.add(baseName + "(0)");
            }
        }
        
        return names;
    }

    /**
     * Returns the base item name (without charges) from a fuzzy item name or already cleaned name.
     * For example, "Amulet of glory(6)" -> "Amulet of glory", "Amulet of glory" -> "Amulet of glory"
     *
     * @param itemName the fuzzy or clean item name
     * @return the base item name without charges
     */
    public static String getBaseItemNameFromString(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return "";
        }
        // Remove charge information in parentheses at the end, e.g., "(6)"
        return itemName.replaceAll("\\(\\d+\\)$", "").trim();
    }
    
    // Helper methods
    
    /**
     * Extracts the base item name (without charge information) from an item ID.
     */
    private static String getBaseItemName(int itemId) {
        String itemName = getItemName(itemId);
        if (itemName == null || itemName.isEmpty()) {
            return null;
        }
        
        // Remove charge information from the name
        return itemName.replaceAll("\\(.*?\\) ?", "").trim();
    }
    
    /**
     * Gets the full item name for a given item ID.
     */
    private static String getItemName(int itemId) {
        ItemManager itemManager = Microbot.getItemManager();
        if (itemManager == null) {
            log.error("ItemManager is null");
            return "";
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() -> 
            Microbot.getItemManager().getItemComposition(itemId).getName()   
        ).orElse("");
    }
    
    /**
     * Extracts the charge level from an item name.
     */
    private static int getChargeFromName(String itemName) {
        if (itemName != null) {
            Matcher matcher = CHARGED_ITEM_PATTERN.matcher(itemName);
            if (matcher.matches()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }
    
    /**
     * Checks if an item name is a charged variant of the base name.
     */
    private static boolean isChargedVariant(String itemName, String baseName, boolean includeUncharged) {
        if (itemName == null || baseName == null) {
            return false;
        }
        
        // Check for exact base name match (uncharged)
        if (includeUncharged && itemName.equals(baseName)) {
            return true;
        }
        
        // Check for uncharged variant with (0)
        if (includeUncharged && itemName.equals(baseName + "(0)")) {
            return true;
        }
        
        // Check for charged variants
        for (int charges = 1; charges <= MAX_CHARGES; charges++) {
            if (itemName.equals(baseName + "(" + charges + ")")) {
                return true;
            }
        }
        
        return false;
    }
    /**
     * Checks if an item ID represents a charged item.
     * 
     * @param itemId the item ID to check
     * @return true if the item is a charged variant, false otherwise
     */
    public static boolean isChargedItem(int itemId) {
        String itemName = getItemName(itemId);
        return isChargedItem(itemName);
    }
    /**
     * Checks if an item name represents a charged item.
     */
    public static boolean isChargedItem(String itemName) {
        return itemName != null && CHARGED_ITEM_PATTERN.matcher(itemName).matches();
    }
}
