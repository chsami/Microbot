package net.runelite.client.plugins.microbot.bga.autofishing.managers;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.bga.autofishing.enums.Fish;
import net.runelite.client.plugins.microbot.bga.autofishing.enums.FishingMethod;
import net.runelite.client.plugins.microbot.bga.autofishing.enums.HarpoonType;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages equipment validation, withdrawal, and equipping for fishing operations
 */
@Singleton
public class EquipmentManager {

    /**
     * Validates if player has all required fishing equipment
     */
    public boolean validateFishingGear(Fish fish, HarpoonType harpoonType) {
        return hasFishingTools(fish.getMethod()) && hasHarpoonIfNeeded(harpoonType) && hasBaitIfNeeded(fish.getMethod());
    }

    /**
     * Gets list of required items for the fishing setup
     */
    public List<String> getRequiredItems(Fish fish, HarpoonType harpoonType) {
        List<String> required = new ArrayList<>();
        
        // Add fishing method requirements
        FishingMethod method = fish.getMethod();
        switch (method) {
            case NET:
                required.add("Small fishing net");
                break;
            case BAIT:
                required.add("Fishing rod");
                required.add("Fishing bait");
                break;
            case BIG_NET:
                required.add("Big fishing net");
                break;
            case LURE:
                required.add("Fly fishing rod");
                required.add("Feather");
                break;
            case HARPOON:
                if (harpoonType != HarpoonType.NONE) {
                    required.add(harpoonType.getName());
                } else {
                    required.add("Harpoon");
                }
                break;
            case CAGE:
                required.add("Lobster pot");
                break;
            case OILY_ROD:
                required.add("Oily fishing rod");
                required.add("Fishing bait");
                break;
            case SANDWORMS:
                required.add("Fishing rod");
                required.add("Sandworms");
                break;
            case KARAMBWAN_VESSEL:
                required.add("Karambwan vessel");
                required.add("Raw karambwanji");
                break;
            case BARBARIAN_ROD:
                required.add("Barbarian rod");
                required.add("Fishing bait");
                break;
        }
        
        return required;
    }

    /**
     * Withdraws required gear from bank
     */
    public boolean withdrawGear(Fish fish, HarpoonType harpoonType) {
        try {
            if (!Rs2Bank.isOpen()) {
                Microbot.log("Bank must be open to withdraw gear");
                return false;
            }

            List<String> requiredItems = getRequiredItems(fish, harpoonType);
            
            for (String item : requiredItems) {
                if (!Rs2Inventory.hasItem(item) && !Rs2Equipment.isWearing(item)) {
                    if (Rs2Bank.hasItem(item)) {
                        int withdrawAmount = getWithdrawAmount(item);
                        if (!Rs2Bank.withdrawX(item, withdrawAmount)) {
                            Microbot.log("Failed to withdraw: " + item);
                            return false;
                        }
                        Rs2Inventory.waitForInventoryChanges(1500);
                    } else {
                        Microbot.log("Required item not found in bank: " + item);
                        return false;
                    }
                }
            }

            return true;
        } catch (Exception e) {
            Microbot.log("Error withdrawing gear: " + e.getMessage());
            return false;
        }
    }

    /**
     * Equips fishing tools that need to be equipped
     */
    public boolean equipTools(HarpoonType harpoonType) {
        try {
            // Equip harpoon if needed and available
            if (harpoonType != HarpoonType.NONE) {
                if (Rs2Inventory.hasItem(harpoonType.getItemId())) {
                    if (!Rs2Equipment.isWearing(harpoonType.getItemId())) {
                        if (Rs2Inventory.wield(harpoonType.getItemId())) {
                            Rs2Inventory.waitForInventoryChanges(1000);
                        }
                    }
                }
            }

            // Auto-equip other tools if beneficial
            equipIfAvailable(String.valueOf(ItemID.DRAGON_HARPOON));
            equipIfAvailable(String.valueOf(ItemID.CRYSTAL_HARPOON));
            equipIfAvailable(String.valueOf(ItemID.INFERNAL_HARPOON));

            return true;
        } catch (Exception e) {
            Microbot.log("Error equipping tools: " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if player has the fishing tools for the method
     */
    private boolean hasFishingTools(FishingMethod method) {
        switch (method) {
            case NET:
                return Rs2Inventory.hasItem(ItemID.NET);
            case BAIT:
            case SANDWORMS:
                return Rs2Inventory.hasItem(ItemID.FISHING_ROD);
            case BIG_NET:
                return Rs2Inventory.hasItem(ItemID.BIG_NET);
            case LURE:
                return Rs2Inventory.hasItem(ItemID.FLY_FISHING_ROD);
            case HARPOON:
                return Rs2Inventory.hasItem(ItemID.HARPOON) ||
                       Rs2Equipment.isWearing(ItemID.DRAGON_HARPOON) || Rs2Equipment.isWearing(ItemID.CRYSTAL_HARPOON) ||
                       Rs2Equipment.isWearing(ItemID.INFERNAL_HARPOON);
            case CAGE:
                return Rs2Inventory.hasItem(ItemID.LOBSTER_POT);
            case OILY_ROD:
                return Rs2Inventory.hasItem(ItemID.OILY_FISHING_ROD);
            case KARAMBWAN_VESSEL:
                return Rs2Inventory.hasItem(ItemID.TBWT_KARAMBWAN_VESSEL) || 
                       Rs2Inventory.hasItem(ItemID.TBWT_KARAMBWAN_VESSEL_LOADED_WITH_KARAMBWANJI);
            case BARBARIAN_ROD:
                return Rs2Inventory.hasItem(ItemID.BRUT_FISHING_ROD);
            default:
                return false;
        }
    }

    /**
     * Checks if player has harpoon when needed
     */
    private boolean hasHarpoonIfNeeded(HarpoonType harpoonType) {
        if (harpoonType == HarpoonType.NONE) {
            return true;
        }
        return Rs2Inventory.hasItem(harpoonType.getItemId()) || Rs2Equipment.isWearing(harpoonType.getItemId());
    }

    /**
     * Checks if player has bait/consumables for the method
     */
    private boolean hasBaitIfNeeded(FishingMethod method) {
        switch (method) {
            case BAIT:
            case OILY_ROD:
                return Rs2Inventory.hasItem(ItemID.FISHING_BAIT);
            case LURE:
                return Rs2Inventory.hasItem(ItemID.FEATHER);
            case SANDWORMS:
                return Rs2Inventory.hasItem(ItemID.PISCARILIUS_SANDWORMS);
            case KARAMBWAN_VESSEL:
                return Rs2Inventory.hasItem(ItemID.TBWT_RAW_KARAMBWANJI);
            case BARBARIAN_ROD:
                return Rs2Inventory.hasItem(ItemID.FISHING_BAIT) || 
                       Rs2Inventory.hasItem(ItemID.FEATHER) || 
                       Rs2Inventory.hasItem(ItemID.BRUT_FISH_CUTS);
            default:
                return true; // No bait needed
        }
    }

    /**
     * Gets appropriate withdraw amount for item
     */
    private int getWithdrawAmount(String item) {
        // Withdraw more bait/consumables, single tools
        switch (item.toLowerCase()) {
            case "fishing bait":
            case "feather":
            case "sandworms":
                return 100; // Withdraw plenty of consumables
            case "raw karambwanji":
                return 50;
            default:
                return 1; // Single tool
        }
    }

    /**
     * Equips item if available in inventory
     */
    private void equipIfAvailable(String itemName) {
        if (Rs2Inventory.hasItem(itemName) && !Rs2Equipment.isWearing(itemName)) {
            Rs2Inventory.wield(itemName);
        }
    }

    /**
     * Checks if the current equipment is optimal for the fishing method
     */
    public boolean hasOptimalGear(Fish fish, HarpoonType harpoonType) {
        // Basic validation first
        if (!validateFishingGear(fish, harpoonType)) {
            return false;
        }

        // Check for optimal harpoon if using harpoon fishing
        if (fish.getMethod() == FishingMethod.HARPOON && harpoonType != HarpoonType.NONE) {
            return Rs2Equipment.isWearing(harpoonType.getItemId());
        }

        return true;
    }
}