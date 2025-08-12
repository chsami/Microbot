package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums;

/**
 * Defines how OR requirements should be planned and fulfilled.
 */
public enum OrRequirementMode {
    /**
     * ANY_COMBINATION mode: The total amount can be fulfilled by any combination of items in the OR requirement.
     * For example, if 5 food items are needed, we could have 2 lobsters + 3 swordfish.
     * This is the default mode and matches the current behavior.
     */
    ANY_COMBINATION,
    
    /**
     * SINGLE_TYPE mode: Must fulfill the entire amount with exactly one type of item from the OR requirement.
     * For example, if 5 food items are needed, we must have exactly 5 lobsters OR 5 swordfish OR 5 monkfish,
     * but not a combination.
     */
    SINGLE_TYPE
}
