package net.runelite.client.plugins.microbot.pluginscheduler.tasks.state;

import lombok.Getter;

/**
 * Represents the different steps in the requirement fulfillment process.
 * These steps are executed in order for both pre-schedule and post-schedule contexts.
 */
@Getter
public enum FulfillmentStep {
    CONDITIONAL(0, "Conditional", "Executing conditional and ordered requirements"),
    LOOT(1, "Loot", "Collecting required loot items"),
    SHOP(2, "Shop", "Purchasing required shop items"),
    ITEMS(3, "Items", "Preparing inventory and equipment"),
    SPELLBOOK(4, "Spellbook", "Switching to required spellbook"),
    LOCATION(5, "Location", "Moving to required location"),
    EXTERNAL_REQUIREMENTS(6, "External", "Fulfilling externally added requirements");
    
    private final int order;
    private final String displayName;
    private final String description;
    
    FulfillmentStep(int order, String displayName, String description) {
        this.order = order;
        this.displayName = displayName;
        this.description = description;
    }
    
    /**
     * Gets the total number of fulfillment steps.
     */
    public static int getTotalSteps() {
        return values().length;
    }
    
    /**
     * Gets the next step in the fulfillment process.
     * @return The next step, or null if this is the last step
     */
    public FulfillmentStep getNext() {
        FulfillmentStep[] values = values();
        if (ordinal() < values.length - 1) {
            return values[ordinal() + 1];
        }
        return null;
    }
    
    /**
     * Gets the previous step in the fulfillment process.
     * @return The previous step, or null if this is the first step
     */
    public FulfillmentStep getPrevious() {
        if (ordinal() > 0) {
            return values()[ordinal() - 1];
        }
        return null;
    }
    
    /**
     * Checks if this is the first step.
     */
    public boolean isFirst() {
        return ordinal() == 0;
    }
    
    /**
     * Checks if this is the last step.
     */
    public boolean isLast() {
        return ordinal() == values().length - 1;
    }
}
