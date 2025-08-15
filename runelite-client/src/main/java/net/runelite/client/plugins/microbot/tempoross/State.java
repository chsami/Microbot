package net.runelite.client.plugins.microbot.tempoross;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;


import java.util.function.BooleanSupplier;

public enum State {
    ATTACK_TEMPOROSS(() -> TemporossScript.ENERGY >= 98, null),
    SECOND_FILL(() -> isSolo() ? getAllFish() == 0 : getCookedFish() == 0, ATTACK_TEMPOROSS),
    THIRD_COOK(() -> getCookedFish() == ((isSolo() && TemporossScript.ESSENCE > 30) ? 19 : getAllFish()) || TemporossScript.INTENSITY >= 92 || (TemporossScript.ENERGY < 50 && getAllFish() > 16 && !isSolo()), SECOND_FILL),
    THIRD_CATCH(() -> {
        // Original logic - only catch 19 fish if ESSENCE is > 31
        // This is the intended behavior as per requirements
        return getAllFish() >= ((isSolo() && TemporossScript.ESSENCE <= 26) ? getTotalAvailableFishSlots() : (isSolo() && TemporossScript.ESSENCE > 26) ? 19 : getTotalAvailableFishSlots()) || (isSolo() && TemporossScript.INTENSITY >= 91);
    }, (isSolo() && TemporossScript.INTENSITY >= 92) ? SECOND_FILL : THIRD_COOK),
    // In solo mode with energy state active, this only checks if raw fish count meets the exact energy-based target
    // rather than checking all fish in inventory. This ensures optimal fish collection based on Tempoross energy.
    // In non-solo mode or when energy state is not active, it uses the new getAllRawFishTarget method for more accuracy.
    // We don't need to wait for energy to reach 10% as we've already calculated the exact amount of fish needed
    EMERGENCY_FILL(() -> {
        // Check if we're in the process of transitioning to EMERGENCY_FILL
        // If so, don't mark the state as complete yet to prevent immediate transition back to THIRD_CATCH
        if (EnergyStateManager.isTransitioningToEmergencyFill()) {
            return false;
        }
        // Modified logic - go to THIRD_CATCH once all fish are gone from inventory
        return isSolo() ? 
            (EnergyStateManager.isEnergyStateModeActive() || EnergyStateManager.isInEmergencyCatchMode() ? 
                State.getRawFish() == 0 : 
                EnergyStateManager.getAllRawFishTarget() == 0) :
            getAllFish() == 0;
    }, THIRD_CATCH),
    INITIAL_FILL(() -> getCookedFish() == 0, THIRD_CATCH),
    SECOND_COOK (() -> getCookedFish() == (isSolo() ? 16 : getAllFish()), INITIAL_FILL),
    SECOND_CATCH(() -> getAllFish() >= (isSolo() ? 16 : getTotalAvailableFishSlots()), SECOND_COOK),
    INITIAL_COOK(() -> getRawFish() == 0, SECOND_CATCH),
    INITIAL_CATCH(() -> getRawFish() >= 8 || getAllFish() >= 10, INITIAL_COOK),
    // New state for emergency catching based on energy levels
    // This state is used when Tempoross energy is >= 11% and we need to catch a specific amount of fish
    // Transitions to EMERGENCY_FILL when the target fish count has been reached
    // Using hasReachedTargetFishCount() which directly checks if target fish count is reached without side effects
    EMERGENCY_CATCH(() -> EnergyStateManager.hasReachedTargetFishCount(), EMERGENCY_FILL);

    public final BooleanSupplier isComplete;
    public final State next;

    State(BooleanSupplier isComplete, State next) {
        this.isComplete = isComplete;
        this.next = next;
    }

    public boolean isComplete() {
        return this.isComplete.getAsBoolean();
    }
    
    /**
     * Gets the next state to transition to.
     */
    public State getNext() {
        // Special case for THIRD_CATCH to dynamically check intensity for solo mode
        if (this == THIRD_CATCH && isSolo() && TemporossScript.INTENSITY >= 92) {
            Microbot.log("THIRD_CATCH transitioning to SECOND_FILL due to high intensity: " + TemporossScript.INTENSITY + "%");
            return SECOND_FILL;
        }
        // Use the next state defined in the enum
        return this.next == null ? THIRD_CATCH : this.next;
    }
    
    public static boolean isSolo() {
        return TemporossScript.temporossConfig != null && TemporossScript.temporossConfig.solo();
    }

    public static int getRawFish() {
        return Rs2Inventory.count(ItemID.TEMPOROSS_RAW_HARPOONFISH);
    }

    public static int getAllFish() {
        return getRawFish() + getCookedFish();
    }

    public static int getCookedFish() {
        return Rs2Inventory.count(ItemID.TEMPOROSS_HARPOONFISH);
    }

    public static int getTotalAvailableFishSlots() {
        return Rs2Inventory.getEmptySlots() + getAllFish();
    }

    public String toString() {
        return name().toLowerCase().replace("_", " ");
    }
}
