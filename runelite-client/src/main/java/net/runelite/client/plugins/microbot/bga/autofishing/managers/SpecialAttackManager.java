package net.runelite.client.plugins.microbot.bga.autofishing.managers;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.bga.autofishing.enums.HarpoonType;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;

import javax.inject.Singleton;

/**
 * Manages special attack activation for harpoon spec weapons during fishing
 */
@Singleton
public class SpecialAttackManager {
    
    private long lastSpecActivation = 0;
    private static final long SPEC_COOLDOWN = 5000; // 5 second cooldown between spec attempts
    
    /**
     * Checks if special attack should be activated
     */
    public boolean shouldActivateSpec(HarpoonType harpoonType) {
        if (harpoonType == HarpoonType.NONE) {
            return false;
        }
        
        boolean hasWeapon = hasSpecWeaponEquipped(harpoonType);
        boolean hasFullEnergy = hasFullSpecEnergy();
        boolean notOnCooldown = !isOnCooldown();
        boolean isSpecWeapon = isSpecWeapon(harpoonType);
        
        Microbot.log("Spec check - Weapon: " + hasWeapon + ", Energy: " + hasFullEnergy + 
                    ", Cooldown: " + notOnCooldown + ", IsSpecWeapon: " + isSpecWeapon);
        
        return hasWeapon && hasFullEnergy && notOnCooldown && isSpecWeapon;
    }
    
    /**
     * Checks if player has 100% special attack energy
     */
    public boolean hasFullSpecEnergy() {
        try {
            int currentSpecEnergy = Rs2Combat.getSpecEnergy();
            int percentageEnergy = currentSpecEnergy / 10; // Convert from 0-1000 to 0-100
            Microbot.log("Current spec energy: " + percentageEnergy + "% (raw: " + currentSpecEnergy + ")");
            
            // Special attack energy needs to be at 1000 (100%) to be fully charged
            boolean hasFullEnergy = currentSpecEnergy >= 1000;
            if (!hasFullEnergy) {
                Microbot.log("Spec energy not full (" + percentageEnergy + "%), skipping special attack");
            }
            return hasFullEnergy;
        } catch (Exception e) {
            Microbot.log("Error checking spec energy: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Activates special attack if conditions are met
     */
    public boolean activateSpecialAttack(HarpoonType harpoonType) {
        try {
            if (!shouldActivateSpec(harpoonType)) {
                return false;
            }
            
            Microbot.log("Activating special attack for: " + harpoonType.toString());
            
            boolean activated = Rs2Combat.setSpecState(true);
            
            if (activated) {
                lastSpecActivation = System.currentTimeMillis();
                Microbot.log("Special attack activated successfully");
            } else {
                Microbot.log("Failed to activate special attack");
            }
            
            return activated;
        } catch (Exception e) {
            Microbot.log("Error activating special attack: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Checks if spec weapon is currently equipped
     */
    public boolean hasSpecWeaponEquipped(HarpoonType harpoonType) {
        if (harpoonType == HarpoonType.NONE) {
            return false;
        }
        
        try {
            return Rs2Equipment.isWearing(harpoonType.getItemId());
        } catch (Exception e) {
            Microbot.log("Error checking equipped spec weapon: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Checks if the harpoon type has a special attack
     */
    public boolean isSpecWeapon(HarpoonType harpoonType) {
        switch (harpoonType) {
            case DRAGON_HARPOON:
            case CRYSTAL_HARPOON:
            case INFERNAL_HARPOON:
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Checks if special attack is on cooldown
     */
    private boolean isOnCooldown() {
        return System.currentTimeMillis() - lastSpecActivation < SPEC_COOLDOWN;
    }
}