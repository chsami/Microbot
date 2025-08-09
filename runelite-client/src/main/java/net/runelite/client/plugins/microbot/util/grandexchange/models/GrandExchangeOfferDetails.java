package net.runelite.client.plugins.microbot.util.grandexchange.models;

import net.runelite.api.GrandExchangeOfferState;
import net.runelite.client.plugins.microbot.Microbot;

/**
	 * Class to hold detailed information about a Grand Exchange offer.
	 */
public class GrandExchangeOfferDetails {
    private final int itemId;
    private final int quantitySold;
    private final int totalQuantity;
    private final int price;
    private final int spent;
    private final GrandExchangeOfferState state;
    private final boolean isSelling;
    
    public GrandExchangeOfferDetails(int itemId, int quantitySold, int totalQuantity, int price, int spent, 
            GrandExchangeOfferState state, boolean isSelling) {
        this.itemId = itemId;
        this.quantitySold = quantitySold;
        this.totalQuantity = totalQuantity;
        this.price = price;
        this.spent = spent;
        this.state = state;
        this.isSelling = isSelling;
    }
    
    /**
     * Calculates the progress percentage of this offer.
     *
     * @return A value between 0 and 100 representing the percentage completion
     */
    public int getProgressPercentage() {
        if (totalQuantity <= 0) {
            return 0;
        }
        return (int) ((quantitySold * 100.0) / totalQuantity);
    }
    
    /**
     * Checks if this offer is completed (either finished or cancelled).
     *
     * @return true if the offer is in a terminal state, false otherwise
     */
    public boolean isCompleted() {
        return state == GrandExchangeOfferState.BOUGHT ||
                state == GrandExchangeOfferState.SOLD ||
                state == GrandExchangeOfferState.CANCELLED_BUY ||
                state == GrandExchangeOfferState.CANCELLED_SELL;
    }
    
    /**
     * Checks if this offer is still in progress.
     *
     * @return true if the offer is still processing, false otherwise
     */
    public boolean isInProgress() {
        return state == GrandExchangeOfferState.BUYING ||
                state == GrandExchangeOfferState.SELLING;
    }
    
    /**
     * Gets the item name for this offer.
     *
     * @return The name of the item being bought or sold
     */
    public String getItemName() {
        return Microbot.getRs2ItemManager().getItemComposition(itemId).getName();
    }

    public int getItemId() {
        return itemId;
    }

    public int getQuantitySold() {
        return quantitySold;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public int getPrice() {
        return price;
    }

    public int getSpent() {
        return spent;
    }

    public GrandExchangeOfferState getState() {
        return state;
    }

    public boolean isSelling() {
        return isSelling;
    }
}