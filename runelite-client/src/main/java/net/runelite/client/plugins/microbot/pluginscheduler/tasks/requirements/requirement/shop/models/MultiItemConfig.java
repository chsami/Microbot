package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.shop.models;

import lombok.Getter;
import net.runelite.client.plugins.microbot.Microbot;
/**
 * Enhanced configuration class for individual items in a multi-item requirement.
 * Updated to use the unified stock management system.
 */
public class MultiItemConfig {
    public final int amount;
    public final int stockTolerance; // **UNIFIED SYSTEM**: Replaces minimumStock + maxQuantityPerVisit
    
    /**
     * Creates a new MultiItemConfig with unified stock management.
     * 
     * @param amount Total amount needed for this item
     * @param stockTolerance Stock tolerance around baseStock (affects both min stock and max per visit)
     */
    public MultiItemConfig(int amount, int stockTolerance) {
        this.amount = amount;
        this.stockTolerance = stockTolerance;
    }
    
    /**
     * Creates a new MultiItemConfig with default stock tolerance.
     * 
     * @param amount Total amount needed for this item
     */
    public MultiItemConfig(int amount) {
        this(amount, 10); // Default tolerance of 10
    }
    
    /**
     * Legacy constructor for backward compatibility.
     * Converts old minimumStock/maxQuantityPerVisit to unified stockTolerance.
     * 
     * @param amount Total amount needed
     * @param minimumStock Legacy minimum stock (ignored in new system)
     * @param maxQuantityPerVisit Legacy max per visit (used as stockTolerance)
     */
    @Deprecated
    public MultiItemConfig(int amount, int minimumStock, int maxQuantityPerVisit) {
        this.amount = amount;
        this.stockTolerance = maxQuantityPerVisit; // Use maxQuantityPerVisit as tolerance
        
        // Log the conversion for debugging
        if (minimumStock != 5) { // 5 was the old default
            Microbot.log("MultiItemConfig: Converting legacy minimumStock=" + minimumStock + 
                        " to unified stockTolerance=" + this.stockTolerance);
        }
    }
    
    @Override
    public String toString() {
        return String.format("MultiItemConfig{amount=%d, stockTolerance=%d}", amount, stockTolerance);
    }
}