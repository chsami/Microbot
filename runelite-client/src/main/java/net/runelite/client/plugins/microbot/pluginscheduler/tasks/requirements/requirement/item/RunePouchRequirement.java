package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.item;

import lombok.Getter;
import lombok.EqualsAndHashCode;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementPriority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.TaskContext;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import net.runelite.client.plugins.microbot.util.inventory.RunePouchType;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

/**
 * Represents a rune pouch requirement that extends ItemRequirement to handle both
 * the pouch item itself and its required rune contents.
 * 
 * This requirement ensures:
 * 1. The player has a rune pouch in their inventory
 * 2. The rune pouch contains the specified runes with minimum quantities
 * 3. The required runes are available (inventory + bank + already in pouch)
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class RunePouchRequirement extends ItemRequirement {
    
    /**
     * Map of required runes and their minimum quantities.
     */
    private final Map<Runes, Integer> requiredRunes;
    
    /**
     * Whether to allow combination runes to satisfy basic rune requirements.
     * For example, dust runes can satisfy both air and earth rune requirements.
     */
    private final boolean allowCombinationRunes;
    
    /**
     * Helper method to check if player has any valid rune pouch in inventory.
     * @return true if any rune pouch variant is found
     */
    private boolean hasAnyRunePouch() {
        for (RunePouchType pouchType : RunePouchType.values()) {
            if (Rs2Inventory.hasItem(pouchType.getItemId())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Full constructor for rune pouch requirement.
     * 
     * @param requiredRunes Map of runes to their minimum required quantities
     * @param allowCombinationRunes Whether combination runes can satisfy basic rune requirements
     * @param priority Priority level for this requirement
     * @param rating Rating/importance (1-10)
     * @param description Human-readable description
     * @param TaskContext When this requirement applies (PRE_SCHEDULE, POST_SCHEDULE, BOTH)
     */
    public RunePouchRequirement(
            Map<Runes, Integer> requiredRunes,
            boolean allowCombinationRunes,
            RequirementPriority priority,
            int rating,
            String description,
            TaskContext taskContext) {
        
        // Call parent constructor with first rune pouch ID - we'll handle multiple IDs in our override methods
        super(RunePouchType.values()[0].getItemId(), // Use first pouch ID as primary
              1, // amount - we need exactly 1 rune pouch
              -1, // inventorySlot - any available slot
              priority,
              rating,
              description,
              taskContext);
        
        this.requiredRunes = requiredRunes;
        this.allowCombinationRunes = allowCombinationRunes;
    }
    
    /**
     * Simplified constructor with default settings.
     * 
     * @param requiredRunes Map of runes to their minimum required quantities
     * @param priority Priority level for this requirement
     * @param rating Rating/importance (1-10)
     * @param description Human-readable description
     * @param TaskContext When this requirement applies
     */
    public RunePouchRequirement(
            Map<Runes, Integer> requiredRunes,
            RequirementPriority priority,
            int rating,
            String description,
            TaskContext taskContext) {
        this(requiredRunes, false, priority, rating, description, taskContext);
    }
    
    @Override
    public String getName() {
        String runesDescription = requiredRunes.entrySet().stream()
                .map(entry -> entry.getValue() + "x " + entry.getKey().name())
                .collect(Collectors.joining(", "));
        return "Rune Pouch (" + runesDescription + ")";
    }
    
    /**
     * Checks if this rune pouch requirement is currently fulfilled.
     * A requirement is fulfilled if:
     * 1. Player has a rune pouch in inventory
     * 2. The rune pouch contains all required runes with sufficient quantities
     * 
     * @return true if requirement is fulfilled, false otherwise
     */
    @Override
    public boolean isFulfilled() {
        // First check if we have any rune pouch
        if (!hasAnyRunePouch()) {
            return false;
        }
        
        // Then check if the rune pouch has the required runes
        return Rs2RunePouch.contains(requiredRunes, allowCombinationRunes);
    }
    
    /**
     * Attempts to fulfill this rune pouch requirement.
     * 
     * @param executorService The ScheduledExecutorService on which fulfillment is running
     * @return true if successfully fulfilled, false otherwise
     */
    @Override
    public boolean fulfillRequirement(CompletableFuture<Boolean> scheduledFuture) {
        try {
            // Check if we already have any rune pouch
            if (!hasAnyRunePouch()) {
                // Try to get a rune pouch using parent logic (will try to get the first type)
                if (!super.fulfillRequirement(scheduledFuture)) {
                    Microbot.log("Failed to obtain rune pouch");
                    return false;
                }
            }
            
            // Check if rune pouch already has required runes
            if (Rs2RunePouch.contains(requiredRunes, allowCombinationRunes)) {
                Microbot.log("Rune pouch already contains required runes");
                return true;
            }
            
            // Check if we have all required runes available (inventory + bank + current pouch)
            if (!areRequiredRunesAvailable()) {
                Microbot.log("Required runes are not available");
                return false;
            }
            
            // Load the required runes into the pouch
            return loadRunesIntoPouch();
            
        } catch (Exception e) {
            Microbot.log("Error fulfilling rune pouch requirement: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Checks if this rune pouch requirement meets all conditions.
     * This includes having the pouch available AND having all required runes available.
     * 
     * @return true if all requirements are met, false otherwise
     */
    @Override
    public boolean meetsAllRequirements() {
        // First check if the pouch itself is available
        if (!super.meetsAllRequirements()) {
            return false;
        }
        
        // Then check if all required runes are available
        return areRequiredRunesAvailable();
    }
    
    /**
     * Checks if all required runes are available across inventory, bank, and current pouch contents.
     * 
     * @return true if all runes are available in sufficient quantities
     */
    private boolean areRequiredRunesAvailable() {
        for (Map.Entry<Runes, Integer> entry : requiredRunes.entrySet()) {
            Runes rune = entry.getKey();
            int required = entry.getValue();
            
            // Count runes from all sources
            int inInventory = Rs2Inventory.count(rune.getItemId());
            int inBank = Rs2Bank.count(rune.getItemId());
            int inPouch = Rs2RunePouch.getQuantity(rune);
            
            int totalAvailable = inInventory + inBank + inPouch;
            
            if (allowCombinationRunes) {
                // Add combination runes that can provide this base rune
                for (Runes combinationRune : Runes.values()) {
                    
                    // Convert base runes array to a list for easier contains check
                    List<Runes> baseRunesList =Arrays.asList(combinationRune.getBaseRunes());
                    if (baseRunesList.contains(rune)) {
                        totalAvailable += Rs2Inventory.count(combinationRune.getItemId());
                        totalAvailable += Rs2Bank.count(combinationRune.getItemId());
                        totalAvailable += Rs2RunePouch.getQuantity(combinationRune);
                    }
                }
            }
            
            if (totalAvailable < required) {
                Microbot.log("Insufficient " + rune.name() + " runes: need " + required + ", have " + totalAvailable);
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Loads the required runes into the rune pouch using Rs2RunePouch utility.
     * 
     * @return true if runes were successfully loaded
     */
    private boolean loadRunesIntoPouch() {
        try {
            // Ensure bank is open for rune pouch configuration
            if (!Rs2Bank.isOpen()) {
                if (!Rs2Bank.openBank()) {
                    Microbot.log("Failed to open bank for rune pouch configuration");
                    return false;
                }
            }
            
            // Use Rs2RunePouch.load() method to configure the pouch
            boolean success = Rs2RunePouch.load(requiredRunes);
            
            if (success) {
                Microbot.log("Successfully loaded runes into pouch: " + formatRuneMap(requiredRunes));
            } else {
                Microbot.log("Failed to load runes into pouch");
            }
            
            return success;
            
        } catch (Exception e) {
            Microbot.log("Error loading runes into pouch: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Formats a rune map for display purposes.
     * 
     * @param runes Map of runes to quantities
     * @return Formatted string representation
     */
    private String formatRuneMap(Map<Runes, Integer> runes) {
        return runes.entrySet().stream()
                .map(entry -> entry.getValue() + "x " + entry.getKey().name())
                .collect(Collectors.joining(", "));
    }
    
    /**
     * Returns a detailed display string with rune pouch requirement information.
     * 
     * @return Formatted string containing requirement details
     */
    @Override
    public String displayString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Rune Pouch Requirement Details ===\n");
        sb.append("Name:\t\t\t").append(getName()).append("\n");
        sb.append("Type:\t\t\t").append(getRequirementType().name()).append("\n");
        sb.append("Priority:\t\t").append(getPriority().name()).append("\n");
        sb.append("Rating:\t\t\t").append(getRating()).append("/10\n");
        sb.append("Schedule Context:\t").append(getTaskContext().name()).append("\n");
        sb.append("Allow Combination Runes:\t").append(allowCombinationRunes).append("\n");
        sb.append("Description:\t\t").append(getDescription() != null ? getDescription() : "No description").append("\n");
        
        sb.append("\n--- Required Runes ---\n");
        for (Map.Entry<Runes, Integer> entry : requiredRunes.entrySet()) {
            Runes rune = entry.getKey();
            int quantity = entry.getValue();
            int available = Rs2RunePouch.getQuantity(rune) + 
                           Rs2Inventory.count(rune.getItemId()) + 
                           Rs2Bank.count(rune.getItemId());
            
            sb.append(rune.name()).append(":\t\t")
              .append("Required: ").append(quantity)
              .append(", Available: ").append(available)
              .append(available >= quantity ? " ✓" : " ✗")
              .append("\n");
        }
        
        sb.append("\n--- Current Status ---\n");
        sb.append("Has Rune Pouch:\t\t").append(Rs2Inventory.hasRunePouch() ? "Yes" : "No").append("\n");
        sb.append("Runes Available:\t\t").append(areRequiredRunesAvailable() ? "Yes" : "No").append("\n");
        sb.append("Requirement Met:\t\t").append(isFulfilled() ? "Yes" : "No").append("\n");
        
        return sb.toString();
    }
    
    /**
     * Creates a unique identity string for this requirement.
     * Used for debugging and logging purposes.
     * 
     * @return Unique identity string
     */
    public String getUniqueIdentity() {
        String runesSignature = requiredRunes.entrySet().stream()
                .map(entry -> entry.getKey().name() + ":" + entry.getValue())
                .sorted()
                .collect(Collectors.joining("|"));
        
        return "RunePouch[" + runesSignature + "|combo:" + allowCombinationRunes + "]";
    }
}
