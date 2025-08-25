package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.shop.models;

import lombok.Getter;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;

/**
 * Tracks the state of a cancelled Grand Exchange offer for potential recovery.
 * This class stores all the necessary information to restore a cancelled offer
 * at a later time, including item details, quantities, pricing, and timing.
 * 
 * <p>Only tracks offers that were actively BUYING or SELLING when cancelled,
 * ensuring we only restore meaningful offer states.</p>
 * 
 * <p>The class is designed to be immutable to ensure data integrity and
 * thread safety when tracking offer states across operations.</p>
 * 
 * @author Enhanced GE Slot Management System
 * @version 1.1
 */
@Getter
public class CancelledOfferState {
    
    /** Maximum age for an offer to be considered still relevant (5 minutes) */
    public static final long DEFAULT_MAX_AGE_MS = 300_000L;
    
    private final int itemId;
    private final String itemName;
    private final int totalQuantity;
    private final int remainingQuantity;
    private final int price;
    private final boolean isBuyOffer;
    private final GrandExchangeSlots originalSlot;
    private final long cancelledTime;
    
    /**
     * Creates a new cancelled offer state with all required details.
     * 
     * @param itemId The RuneScape item ID
     * @param itemName The display name of the item
     * @param totalQuantity The original total quantity in the offer
     * @param remainingQuantity The quantity that was not yet bought/sold when cancelled
     * @param price The price per item in the offer
     * @param isBuyOffer True if this was a buy offer, false for sell offer
     * @param originalSlot The Grand Exchange slot this offer was in
     */
    public CancelledOfferState(int itemId, String itemName, int totalQuantity, 
                             int remainingQuantity, int price, boolean isBuyOffer, 
                             GrandExchangeSlots originalSlot) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.totalQuantity = totalQuantity;
        this.remainingQuantity = remainingQuantity;
        this.price = price;
        this.isBuyOffer = isBuyOffer;
        this.originalSlot = originalSlot;
        this.cancelledTime = System.currentTimeMillis();
    }
    
    /**
     * Checks if this cancelled offer is still relevant for restoration.
     * Uses the default maximum age of 5 minutes.
     * 
     * @return true if the offer is still recent enough to be relevant
     */
    public boolean isStillRelevant() {
        return isStillRelevant(DEFAULT_MAX_AGE_MS);
    }
    
    /**
     * Checks if this cancelled offer is still relevant for restoration.
     * 
     * @param maxAgeMs Maximum age in milliseconds for the offer to be considered relevant
     * @return true if the offer is still within the acceptable age limit
     */
    public boolean isStillRelevant(long maxAgeMs) {
        return System.currentTimeMillis() - cancelledTime < maxAgeMs;
    }
    
    /**
     * Validates if this offer is worth restoring based on game logic.
     * Checks for valid item ID, remaining quantity, and reasonable price.
     * 
     * @return true if this offer should be restored
     */
    public boolean isWorthRestoring() {
        return itemId > 0 && 
               remainingQuantity > 0 && 
               price > 0 && 
               isStillRelevant() &&
               originalSlot != null;
    }
    
    /**
     * Gets the age of this cancelled offer in milliseconds.
     * 
     * @return The time elapsed since the offer was cancelled
     */
    public long getAge() {
        return System.currentTimeMillis() - cancelledTime;
    }
    
    /**
     * Checks if this offer has any remaining quantity worth restoring.
     * 
     * @return true if there are items remaining to be bought/sold
     */
    public boolean hasRemainingQuantity() {
        return remainingQuantity > 0;
    }
    
    /**
     * Calculates the progress percentage of the original offer.
     * 
     * @return A value between 0.0 and 1.0 representing completion percentage
     */
    public double getProgressPercentage() {
        if (totalQuantity <= 0) {
            return 0.0;
        }
        int completedQuantity = totalQuantity - remainingQuantity;
        return (double) completedQuantity / totalQuantity;
    }
    
    /**
     * Gets the operation type as a readable string.
     * 
     * @return "BUY" or "SELL" depending on the offer type
     */
    public String getOperationType() {
        return isBuyOffer ? "BUY" : "SELL";
    }
    
    /**
     * Creates a summary string suitable for logging and debugging.
     * 
     * @return A formatted string containing key offer details
     */
    public String getSummary() {
        return String.format("%s %d/%d %s at %d gp (slot %s, age: %.1fs)", 
                           getOperationType(), remainingQuantity, totalQuantity, 
                           itemName, price, originalSlot, getAge() / 1000.0);
    }
    
    @Override
    public String toString() {
        return getSummary();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        CancelledOfferState that = (CancelledOfferState) obj;
        return itemId == that.itemId &&
               price == that.price &&
               isBuyOffer == that.isBuyOffer &&
               originalSlot == that.originalSlot &&
               cancelledTime == that.cancelledTime;
    }
    
    @Override
    public int hashCode() {
        int result = itemId;
        result = 31 * result + price;
        result = 31 * result + (isBuyOffer ? 1 : 0);
        result = 31 * result + (originalSlot != null ? originalSlot.hashCode() : 0);
        result = 31 * result + (int) (cancelledTime ^ (cancelledTime >>> 32));
        return result;
    }
}
