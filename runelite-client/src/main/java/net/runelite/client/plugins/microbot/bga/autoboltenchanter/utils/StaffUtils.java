package net.runelite.client.plugins.microbot.bga.autoboltenchanter.utils;

import java.util.HashSet;
import java.util.Set;

import net.runelite.client.plugins.microbot.util.magic.Rs2Staff;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StaffUtils {
    
    /**
     * Get the currently equipped staff if any
     * @return the equipped Rs2Staff or null if no staff is equipped
     */
    public static Rs2Staff getEquippedStaff() {
        for (Rs2Staff staff : Rs2Staff.values()) {
            if (staff != Rs2Staff.NONE && Rs2Equipment.isWearing(staff.getItemID())) {
                return staff;
            }
        }
        return null;
    }

    /**
     * Get all rune IDs that are provided by the equipped staff
     * @return set of rune IDs that are unlimited due to equipped staff
     */
    public static Set<Integer> getProvidedRunes() {
        Rs2Staff equippedStaff = getEquippedStaff();
        if (equippedStaff == null) return new HashSet<>();
        
        Set<Integer> providedRuneIds = new HashSet<>();
        for (Runes rune : equippedStaff.getRunes()) {
            providedRuneIds.add(rune.getItemId());
        }
        return providedRuneIds;
    }

    /**
     * Find the best available staff for the given required runes
     * Priority: 1) Provides all required runes, 2) Provides most required runes, 3) Single rune staff
     * @param requiredRunes array of rune IDs needed
     * @return the best available Rs2Staff or null if none found
     */
    public static Rs2Staff findBestAvailableStaff(int[] requiredRunes) {
        Rs2Staff bestStaff = null;
        int bestScore = -1;

        // check inventory and bank for available staves
        for (Rs2Staff staff : Rs2Staff.values()) {
            if (staff == Rs2Staff.NONE) continue; // skip none
            
            boolean hasInInventory = Rs2Inventory.hasItem(staff.getItemID());
            boolean hasInBank = Rs2Bank.hasItem(staff.getItemID());
            
            if (!hasInInventory && !hasInBank) continue; // staff not available
            
            int score = calculateStaffScore(staff, requiredRunes);
            if (score > bestScore) {
                bestScore = score;
                bestStaff = staff;
            }
        }
        
        return bestStaff;
    }

    /**
     * Calculate a score for how useful a staff is for the given required runes
     * Higher scores are better
     * @param staff the staff to evaluate
     * @param requiredRunes array of rune IDs needed
     * @return score indicating usefulness of the staff
     */
    public static int calculateStaffScore(Rs2Staff staff, int[] requiredRunes) {
        Set<Integer> staffRuneIds = new HashSet<>();
        for (Runes rune : staff.getRunes()) {
            staffRuneIds.add(rune.getItemId());
        }
        
        int matchingRunes = 0;
        for (int requiredRune : requiredRunes) {
            if (staffRuneIds.contains(requiredRune)) {
                matchingRunes++;
            }
        }
        
        if (matchingRunes == requiredRunes.length) {
            return 1000 + staff.getRunes().size(); // perfect match gets highest score
        }
        
        if (matchingRunes > 0) {
            return matchingRunes * 100; // partial matches get medium scores
        }
        
        return 0; // no matching runes
    }

    /**
     * Try to equip the best available staff for the given required runes
     * @param requiredRunes array of rune IDs needed
     * @return true if a suitable staff was equipped, false otherwise
     */
    public static boolean equipBestAvailableStaff(int[] requiredRunes) {
        // check if we already have a good staff equipped
        Rs2Staff currentStaff = getEquippedStaff();
        if (currentStaff != null && calculateStaffScore(currentStaff, requiredRunes) > 0) {
            log.info("Already have suitable staff equipped: {}", currentStaff.name());
            return true;
        }
        
        Rs2Staff bestStaff = findBestAvailableStaff(requiredRunes);
        if (bestStaff == null) {
            log.info("No suitable staff found in inventory or bank");
            return false;
        }
        
        // try to equip from inventory first
        if (Rs2Inventory.hasItem(bestStaff.getItemID())) {
            log.info("Equipping {} from inventory", bestStaff.name());
            return Rs2Inventory.wield(bestStaff.getItemID());
        }
        
        // if bank is open, try to withdraw and equip from bank
        if (Rs2Bank.isOpen() && Rs2Bank.hasItem(bestStaff.getItemID())) {
            log.info("Withdrawing and equipping {} from bank", bestStaff.name());
            return Rs2Bank.withdrawAndEquip(bestStaff.getItemID());
        }
        
        return false;
    }
}