package net.runelite.client.plugins.microbot.tempoross;

import static net.runelite.client.plugins.microbot.Microbot.log;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Manages the energy-based target fish logic for Tempoross
 * This class is responsible for determining the target fish count based on Tempoross energy
 * and checking if the target has been reached
 */
public class EnergyStateManager {
    
    
    // Static value to store the exact raw fish number needed for EMERGENCY_FILL in solo mode
    private static int emergencyFillRawFishTarget = 0;
    
    // Flag to track if we're in emergency catch mode
    private static boolean inEmergencyCatchMode = false;
    
    // Flag to track if we're in the process of transitioning to EMERGENCY_FILL
    private static boolean isTransitioningToEmergencyFill = false;
    
    // Shared timer instance for state transitions
    private static final Timer sharedTimer = new Timer("EmergencyCatchStateTimer", true);
    
    // Energy thresholds and corresponding target fish counts
    // The array is processed in order, so each entry defines a range:
    // If energy is within the range, the corresponding target fish count is used
    private static final int[][] ENERGY_FISH_THRESHOLDS = {
            {76, 19}, // Energy 76-80+, target = 19 fish
            {73, 18}, // Energy 73-75, target = 18 fish
            {70, 17}, // Energy 70-72, target = 17 fish
            {66, 16}, // Energy 66-69, target = 16 fish
            {62, 15}, // Energy 62-65, target = 15 fish
            {58, 14}, // Energy 58-61, target = 14 fish
            {55, 13}, // Energy 55-57, target = 13 fish
            {52, 12}, // Energy 52-54, target = 12 fish
            {48, 11}, // Energy 48-51, target = 11 fish
            {45, 10}, // Energy 45-47, target = 10 fish
            {41, 9},  // Energy 41-44, target = 9 fish
            {37, 8},  // Energy 37-40, target = 8 fish
            {34, 7},  // Energy 34-36, target = 7 fish
            {30, 6},  // Energy 30-33, target = 6 fish
            {26, 5},  // Energy 26-30, target = 5 fish
            {22, 4},  // Energy 22-26, target = 4 fish
            {19, 3},  // Energy 19-21, target = 3 fish
            {15, 2},  // Energy 15-18, target = 2 fish
            {11, 1}   // Energy 11-14, target = 1 fish
    };

    /**
     * Resets energy state mode usage and emergency state variables
     * This should be called at the start of a new game
     */
    public static void resetAttackTemporossTracking() {
        emergencyFillRawFishTarget = 0;
        inEmergencyCatchMode = false;
        isTransitioningToEmergencyFill = false;
        lastLoggedEnergyValue = 0;
        lastLoggedEmergencyFillEnergyValue = 0;
        
        // If we're in EMERGENCY_CATCH state, reset it to THIRD_CATCH
        // This ensures we don't get stuck in EMERGENCY_CATCH state when starting a new game
        if (TemporossScript.state == State.EMERGENCY_CATCH) {
            TemporossScript.state = State.THIRD_CATCH;
            log("Reset EMERGENCY_CATCH state to THIRD_CATCH when starting a new game");
        }
    }
    
    /**
     * Checks if we should transition to EMERGENCY_CATCH state
     * This is determined by Tempoross energy level and other conditions
     * @return true if we should transition to EMERGENCY_CATCH, false otherwise
     */
    public static boolean shouldTransitionToEmergencyCatch() {
        // Log current state and conditions for debugging
        log("EMERGENCY_CATCH check - State: " + TemporossScript.state + 
            ", Energy: " + TemporossScript.ENERGY + "%, Solo: " + State.isSolo() + 
            ", CurrentMode: " + (inEmergencyCatchMode ? "EMERGENCY" : "NORMAL"));
        
        // Check if we're still in the wave recovery grace period
        if (TemporossPlugin.waveRecoveryTime > 0) {
            long currentTime = System.currentTimeMillis();
            long timeSinceRecovery = currentTime - TemporossPlugin.waveRecoveryTime;
            
            if (timeSinceRecovery < TemporossPlugin.WAVE_RECOVERY_GRACE_PERIOD_MS) {
                log("Wave recovery grace period active (" + timeSinceRecovery + "ms/" + TemporossPlugin.WAVE_RECOVERY_GRACE_PERIOD_MS + "ms), skipping emergency state transition");
                return false;
            } else {
                // Grace period has expired, reset the flag
                log("Wave recovery grace period expired, emergency state transitions allowed again");
                TemporossPlugin.waveRecoveryTime = 0;
            }
        }
        
        // Only applicable in solo mode
        if (!State.isSolo()) {
            log("Not using EMERGENCY_CATCH as not in solo mode");
            return false;
        }
        
        
        // Ensure EMERGENCY_CATCH only activates after INITIAL_FILL has been completed
        // and never during ATTACK_TEMPOROSS state
        if (TemporossScript.state == State.INITIAL_CATCH || 
            TemporossScript.state == State.INITIAL_COOK || 
            TemporossScript.state == State.SECOND_CATCH || 
            TemporossScript.state == State.SECOND_COOK || 
            TemporossScript.state == State.INITIAL_FILL ||
            TemporossScript.state == State.SECOND_FILL ||
            TemporossScript.state == State.ATTACK_TEMPOROSS) {
            log("Not using EMERGENCY_CATCH during state: " + TemporossScript.state);
            return false;
        }
        
        // Don't transition to EMERGENCY_CATCH if we're already in EMERGENCY_FILL state
        // or if we're in the process of transitioning to EMERGENCY_FILL
        if (TemporossScript.state == State.EMERGENCY_FILL || isTransitioningToEmergencyFill) {
            log("Not using EMERGENCY_CATCH as we're already in EMERGENCY_FILL state or transitioning to it");
            return false;
        }
        
        // Check if conditions are met to activate emergency catch mode
        boolean hasEnoughEnergy = TemporossScript.ENERGY >= 11;
        boolean delayPassed = true;
        
        // Log detailed condition checks
        log("Emergency conditions check: Energy >= 11: " + hasEnoughEnergy + 
            ", NotInEmergencyMode: " + !inEmergencyCatchMode);
        
        // Check if we should activate emergency mode (only if not already in emergency mode)
        boolean shouldActivateEmergencyMode = hasEnoughEnergy && delayPassed && !inEmergencyCatchMode;
        
        // Check if we should transition to EMERGENCY_CATCH state (regardless of whether we're already in emergency mode)
        boolean shouldTransitionToEmergencyCatchState = hasEnoughEnergy && delayPassed && TemporossScript.state == State.THIRD_CATCH;
        
        // Activate emergency mode if needed
        if (shouldActivateEmergencyMode) {
            log("Activating emergency catch mode at energy level: " + TemporossScript.ENERGY + "%");
            inEmergencyCatchMode = true;
        } else if (inEmergencyCatchMode) {
            log("Already in emergency catch mode, no need to activate again");
        } else if (!hasEnoughEnergy) {
            log("Energy too low for emergency state activation: " + TemporossScript.ENERGY + "% (needs to be >= 11%)");
        } else if (!delayPassed) {
            log("Energy update delay hasn't passed yet, waiting before activating emergency mode");
        }
        
        // Transition to EMERGENCY_CATCH state if needed (regardless of whether we just activated emergency mode)
        if (shouldTransitionToEmergencyCatchState) {
            // Only transition if we're in THIRD_CATCH state
            if (TemporossScript.state == State.THIRD_CATCH) {
                log("Setting state to EMERGENCY_CATCH from THIRD_CATCH");
                TemporossScript.state = State.EMERGENCY_CATCH;
                
                // Handle the state conversion for compatibility with the switch statement
                handleEmergencyCatchState();
            } else {
                log("Will transition to EMERGENCY_CATCH when reaching THIRD_CATCH state (current state: " + TemporossScript.state + ")");
            }
        }
        
        return shouldActivateEmergencyMode || shouldTransitionToEmergencyCatchState;
    }
    
    /**
     * Checks if we're currently in emergency catch mode
     * @return true if in emergency catch mode, false otherwise
     */
    public static boolean isInEmergencyCatchMode() {
        return inEmergencyCatchMode;
    }
    
    
    /**
     * This method is called automatically when needed to ensure EMERGENCY_CATCH works with existing code
     * It temporarily converts EMERGENCY_CATCH to THIRD_CATCH for compatibility with the switch statement
     * and then converts it back after processing
     */
    private static void handleEmergencyCatchState() {
        // If we're in EMERGENCY_CATCH state, temporarily set it to THIRD_CATCH for the switch statement
        if (TemporossScript.state == State.EMERGENCY_CATCH) {
            // Temporarily change the state to THIRD_CATCH so it works with the existing switch statement
            TemporossScript.state = State.THIRD_CATCH;
            log("Temporarily using THIRD_CATCH for EMERGENCY_CATCH state");
            
            // Reset the transitioning flag before scheduling the timer
            // This ensures the flag is reset even if the timer task doesn't execute
            isTransitioningToEmergencyFill = false;
            
            // Use the shared timer to schedule the state restoration
            sharedTimer.schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            // Check if we're in THIRD_CATCH state
                            if (TemporossScript.state == State.THIRD_CATCH) {
                                // Check if we're in emergency catch mode
                                if (inEmergencyCatchMode) {
                                    // Reset the transitioning flag at the start of each check
                                    isTransitioningToEmergencyFill = false;
                                    
                                    // If we're repairing a totem or filling, log it but don't restore yet
                                    if (TemporossScript.isRepairingTotem || TemporossScript.isFilling) {
                                        log("Not restoring EMERGENCY_CATCH because we're " + 
                                            (TemporossScript.isRepairingTotem ? "repairing a totem" : "filling"));
                                        return;
                                    }
                                    
                                    // Check if we're already in EMERGENCY_FILL state
                                    if (TemporossScript.state == State.EMERGENCY_FILL) {
                                        isTransitioningToEmergencyFill = true;
                                        log("Not restoring EMERGENCY_CATCH because we're already in EMERGENCY_FILL state");
                                    }
                                    // If energy is >= 11% and we have enough fish for EMERGENCY_FILL, 
                                    // we're transitioning to EMERGENCY_FILL
                                    else if (TemporossScript.ENERGY >= 11) {
                                        int targetFish = getTargetFishCount();
                                        int currentRawFish = State.getRawFish();
                                        
                                        if (currentRawFish >= targetFish) {
                                            isTransitioningToEmergencyFill = true;
                                            log("Transitioning to EMERGENCY_FILL with energy: " + 
                                                TemporossScript.ENERGY + "%, fish: " + currentRawFish + "/" + targetFish);
                                            
                                            // Actively set the state to EMERGENCY_FILL instead of just not restoring to EMERGENCY_CATCH
                                            TemporossScript.state = State.EMERGENCY_FILL;
                                            TemporossScript.isFilling = true;
                                        }
                                    }
                                    
                                    // Only restore if we're not transitioning to EMERGENCY_FILL
                                    if (!isTransitioningToEmergencyFill) {
                                        TemporossScript.state = State.EMERGENCY_CATCH;
                                        log("Restored EMERGENCY_CATCH state after processing");
                                    }
                                } else {
                                    log("Not restoring EMERGENCY_CATCH because we're not in emergency catch mode");
                                }
                            } else {
                                log("Not restoring EMERGENCY_CATCH because we're not in THIRD_CATCH state (current state: " + TemporossScript.state + ")");
                            }
                        } catch (Exception e) {
                            // Catch any exceptions to prevent timer task from failing
                            log("Error in timer task: " + e.getMessage());
                        }
                    }
                },
                100 // 100ms delay
            );
        }
    }

    
    // Track the last energy value we logged
    private static int lastLoggedEnergyValue = 0;
    
    // Track the last energy value we logged for emergency fill transition
    private static int lastLoggedEmergencyFillEnergyValue = 0;
    
    /**
     * Determines if the energy state mode should be active
     * @return true if energy state mode should be active, false otherwise
     */
    public static boolean isEnergyStateModeActive() {
        // If we're already in EMERGENCY_CATCH state, return true
        // This ensures we don't lose energy state mode when temporarily switching to THIRD_CATCH
        if (TemporossScript.state == State.EMERGENCY_CATCH || inEmergencyCatchMode) {
            return true;
        }
        
        // Check if conditions are met to activate
        boolean isSolo = State.isSolo();
        // Ensure energy is strictly above 10% to avoid rounding issues and prevent unnecessary EMERGENCY_FILL at 10%
        // As per requirements: "10% or under is third_catch"
        // The condition is now >= 11 which ensures energy is above 10%
        boolean hasEnoughEnergy = TemporossScript.ENERGY >= 11;
        boolean delayPassed = true;
        
        boolean shouldActivate = isSolo && hasEnoughEnergy;
        
        // Only log if energy has changed since the last log
        if (TemporossScript.ENERGY != lastLoggedEnergyValue) {
            // Simplified logging
            log("Energy: " + TemporossScript.ENERGY + "%, Activation: Solo=" + isSolo + 
                ", Energy>=11=" + hasEnoughEnergy + 
                ", DelayPassed=" + delayPassed);
            
            // Simplified activation status logging
            if (shouldActivate) {
                log("Energy state mode activated at " + TemporossScript.ENERGY + "%");
            } else if (TemporossScript.ENERGY < 10) {
                log("Energy state mode inactive: energy too low (" + TemporossScript.ENERGY + "%)");
            }
            
            // Update the last logged energy value
            lastLoggedEnergyValue = TemporossScript.ENERGY;
        }
        
        return shouldActivate;
    }
    
    /**
     * Gets the target fish count based on Tempoross energy
     * Uses a lookup table instead of a lengthy if-else chain
     * @return the target fish count
     */
    public static int getTargetFishCount() {
        int currentEnergy = TemporossScript.ENERGY;
        
        // Iterate through the thresholds and return the first matching target
        for (int[] threshold : ENERGY_FISH_THRESHOLDS) {
            if (currentEnergy >= threshold[0]) {
                return threshold[1];
            }
        }
        
        // Default return if energy is below all thresholds
        return 0;
    }
    
    /**
     * Gets the exact raw fish number needed for EMERGENCY_FILL in solo mode
     * @return the exact raw fish number needed
     */
    public static int getEmergencyFillRawFishTarget() {
        return emergencyFillRawFishTarget;
    }
    
    /**
     * Gets the raw fish count target for non-energy state mode in solo mode
     * This is used by EMERGENCY_FILL state when energy state mode is not active
     * @return the raw fish count target to check against
     */
    public static int getAllRawFishTarget() {
        // For non-energy state mode, we want to check if there are any raw fish
        // This makes the EMERGENCY_FILL state more accurate
        return State.getRawFish();
    }
    
    /**
     * Simple method to check if the current raw fish count meets or exceeds the target based on energy level
     * This method has no side effects and doesn't change any state
     * @return true if the current raw fish count meets or exceeds the target, false otherwise
     */
    public static boolean hasReachedTargetFishCount() {
        // Skip if energy is 10% or under (below 11%)
        // As per requirements: "10% or under is third_catch"
        if (TemporossScript.ENERGY < 11) {
            return false;
        }
        
        int targetFish = getTargetFishCount();
        int currentRawFish = State.getRawFish();
        
        return currentRawFish >= targetFish;
    }

    
    /**
     * Resets the isTransitioningToEmergencyFill flag to false
     * This should be called when transitioning from EMERGENCY_FILL to another state
     */
    public static void resetEmergencyFillTransition() {
        isTransitioningToEmergencyFill = false;
        log("Reset emergency fill transition flag");
    }
    
    /**
     * Checks if we're in the process of transitioning to EMERGENCY_FILL
     * This is used by the EMERGENCY_FILL state to prevent immediate transition back to THIRD_CATCH
     * @return true if we're transitioning to EMERGENCY_FILL, false otherwise
     */
    public static boolean isTransitioningToEmergencyFill() {
        return isTransitioningToEmergencyFill;
    }
    
    /**
     * Helper method to handle the common logic for transitioning to EMERGENCY_FILL
     * This is used by handleEmergencyFillTransition to handle state transitions
     * 
     * @param onlyCheckEmergencyCatch if true, only checks EMERGENCY_CATCH state (for handleEmergencyFillTransition)
     * @return true if target fish is reached, false otherwise
     */
    private static boolean handleEmergencyFillTransitionLogic(boolean onlyCheckEmergencyCatch) {
        // Skip if energy is 10% or under (below 11%)
        // As per requirements: "10% or under is third_catch"
        if (TemporossScript.ENERGY < 11) {
            return false;
        }
        
        // Skip if repairing totem
        if (TemporossScript.isRepairingTotem) {
            log("Target fish check skipped because totem repair is in progress");
            return false;
        }
        
        int targetFish = getTargetFishCount();
        int currentRawFish = State.getRawFish();
        
        // Store the target fish count for EMERGENCY_FILL to use
        emergencyFillRawFishTarget = targetFish;
        
        boolean targetReached = currentRawFish >= targetFish;
        boolean isValidState = TemporossScript.state == State.EMERGENCY_CATCH || 
                              (!onlyCheckEmergencyCatch && TemporossScript.state == State.THIRD_CATCH);
        
        // If target is reached and we're in a valid state, transition to EMERGENCY_FILL
        if (targetReached && isValidState) {
            log("Target fish reached in solo mode, going for emergency fill at energy level: " + TemporossScript.ENERGY + "%");
            
            // If we're in EMERGENCY_CATCH state, we need to handle the state conversion
            if (TemporossScript.state == State.EMERGENCY_CATCH) {
                // First convert back to THIRD_CATCH to ensure compatibility with the switch statement
                TemporossScript.state = State.THIRD_CATCH;
                log("Temporarily converting EMERGENCY_CATCH to THIRD_CATCH before transitioning to EMERGENCY_FILL");
            }
            
            // Set the flag to indicate we're transitioning to EMERGENCY_FILL
            isTransitioningToEmergencyFill = true;
            TemporossScript.state = State.EMERGENCY_FILL;
            TemporossScript.isFilling = true;
        }
        
        return targetReached;
    }

    /**
     * Handles the transition to EMERGENCY_FILL when target fish is reached
     * This method should be called in handleMainLoop before the switch statement
     * Will not transition if totem repair is in progress
     * 
     * Special case: If energy is below 11% and we're in EMERGENCY_CATCH state,
     * forces a transition to EMERGENCY_FILL and then to THIRD_CATCH.
     * This prevents getting stuck in EMERGENCY_CATCH when energy is too low.
     */
    public static void handleEmergencyFillTransition() {
        // Don't modify states during wave recovery grace period
        if (TemporossPlugin.waveRecoveryTime > 0) {
            long timeSinceRecovery = System.currentTimeMillis() - TemporossPlugin.waveRecoveryTime;
            if (timeSinceRecovery < TemporossPlugin.WAVE_RECOVERY_GRACE_PERIOD_MS) {
                log("Skipping emergency fill transition during wave recovery grace period (" + timeSinceRecovery + "ms/" + TemporossPlugin.WAVE_RECOVERY_GRACE_PERIOD_MS + "ms)");
                return;
            }
        }
        
        // Log the current state for debugging
        if (TemporossScript.state == State.EMERGENCY_CATCH) {
            log("handleEmergencyFillTransition called with EMERGENCY_CATCH state");
        }
        
        // Skip state transition if currently repairing a totem
        if (TemporossScript.isRepairingTotem) {
            log("Skipping emergency fill transition because totem repair is in progress");
            return;
        }
        
        // Special case: If energy is 10% or under (below 11%), force transition to THIRD_CATCH
        // This handles both EMERGENCY_CATCH and EMERGENCY_FILL states
        // As per requirements: "10% or under is third_catch"
        if ((TemporossScript.state == State.EMERGENCY_CATCH || TemporossScript.state == State.EMERGENCY_FILL) && TemporossScript.ENERGY < 11) {
            log("Energy at 10% or under (" + TemporossScript.ENERGY + "%), forcing transition from " + TemporossScript.state + " to THIRD_CATCH");
            
            // Convert to THIRD_CATCH to ensure compatibility with the switch statement
            TemporossScript.state = State.THIRD_CATCH;
            log("Converting to THIRD_CATCH as per requirements for energy at 10% or under");
            
            // Do not transition to EMERGENCY_FILL
            return;
        }
        
        // Check if energy is 10% or under (below 11%)
        // As per requirements: "10% or under is third_catch"
        if (TemporossScript.ENERGY < 11) {
            // Only log this if we're in the right state to avoid spam
            // AND only if the energy value has changed since the last log
            if ((TemporossScript.state == State.THIRD_CATCH || TemporossScript.state == State.EMERGENCY_CATCH) &&
                TemporossScript.ENERGY != lastLoggedEmergencyFillEnergyValue) {
                log("Emergency fill transition skipped because energy is at 10% or under: " + TemporossScript.ENERGY + "%");
                // Update the last logged energy value for emergency fill transition
                lastLoggedEmergencyFillEnergyValue = TemporossScript.ENERGY;
            }
            return;
        }
        
        // Only check EMERGENCY_CATCH state (true parameter)
        handleEmergencyFillTransitionLogic(true);
    }
}