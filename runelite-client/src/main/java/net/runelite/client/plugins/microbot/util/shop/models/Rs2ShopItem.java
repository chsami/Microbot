package net.runelite.client.plugins.microbot.util.shop.models;

import java.time.Duration;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.runelite.api.ItemComposition;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;

@Getter
@EqualsAndHashCode(callSuper = true)
public class Rs2ShopItem extends Rs2ShopSource{            
    final private Duration restockTime; // -1 means unknown    
    @Getter    
    final private int baseStock; // -1 means unknown, for grand exchange -1, becasue there are as many items as are sold,
    @Getter
    final int itemId; // item name -> source item name
    private ItemComposition itemComposition;
    public ItemComposition getItemComposition() {
        if (itemComposition == null) {
            itemComposition =  Microbot.getClientThread().runOnClientThreadOptional(() -> 
            Microbot.getItemManager().getItemComposition(itemId)).orElse(null);
        }
        return itemComposition;
    }   
    public Rs2ShopItem(int id, 
        String shopNpcName,   
        WorldArea locationArea,    
        Rs2ShopType shopType,
        double percentageSoldAt,
        double percentageBoughtAt, 
        double changePercent,
        Map <Quest, QuestState> quests,
        boolean members,
        String notes,
        Duration restockTime,
        int baseStock
        ) {        
        super(
                shopNpcName,  
                locationArea,               
                shopType, 
                percentageSoldAt,
                percentageBoughtAt,
                changePercent,
                quests,
                Map.of(), // varbitReq is not used in Rs2ShopItem
                Map.of(), // varPlayerReq is not used in Rs2ShopItem
                members,
                notes
                );        
        this.itemId = id;
        this.restockTime = restockTime;
        this.baseStock = baseStock;
    }
    public static Rs2ShopItem createGEItem(int itemId, double adjustedSell, double adjustedBuy) {
        WorldPoint locationArea = BankLocation.GRAND_EXCHANGE.getWorldPoint();
        WorldPoint southWestPointGE = new WorldPoint(locationArea.getX() - 5, locationArea.getY() - 5, locationArea.getPlane());
        WorldArea area = new WorldArea(southWestPointGE.getX(), southWestPointGE.getY(), 10, 10, locationArea.getPlane());
        return new Rs2ShopItem(itemId, "Grand Exchange Clerk", area, Rs2ShopType.GRAND_EXCHANGE,adjustedSell,adjustedBuy, 0.0, 
            Map.of(), false, "", Duration.ZERO, -1);
        
    }
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Rs2ShopItem{");
        sb.append("id=").append(getItemId());
        sb.append(", name='").append(getItemName()).append('\'');
        sb.append(", shopName='").append(getShopNpcName()).append('\'');
        sb.append(", numberInStock=").append(getBaseStock());
        sb.append(", restockTime=").append(getRestockTime());
        sb.append(", initial sell price=").append(getInitialPriceSellAt());
        sb.append(", initial buy price=").append(getInitialPriceBuyAt());
        sb.append(", changePercent=").append(getChangePercent());
        sb.append(", percentageSoldAt=").append(getPercentageSoldAt());
        sb.append(", percentageBoughtAt=").append(getPercentageBoughtAt());
        sb.append(", notes='").append(getNotes()).append('\'');
        sb.append(", members=").append(isMembers());            
        return sb.toString();
    }
    public int getCost( int amount) {
      return getCostForBuyingX(amount, getBaseStock());
    }
    public int getReturn(int amount) {
        return getReturnForSellingX(amount,getBaseStock());
    }

  
    public String getItemName() {
        return getItemComposition() != null ? getItemComposition().getName() : "Unknown Item";
    }
    public double getInitialPriceSellAt() {
        if ( getShopType() == Rs2ShopType.GRAND_EXCHANGE) {
            
            return Math.ceil(Rs2GrandExchange.getSellPrice(itemId)*this.getPercentageSoldAt());
        }
        int basePrice = itemStoreValue();       
        return Math.ceil(this.getPercentageSoldAt() * basePrice);
    }

    public double getInitialPriceBuyAt() {
        if ( getShopType() == Rs2ShopType.GRAND_EXCHANGE) {
            return Math.floor(Rs2GrandExchange.getPrice(itemId)*this.getPercentageBoughtAt());
        }
        int basePrice = itemStoreValue();       
        return Math.floor(this.getPercentageBoughtAt() * basePrice);

    }
    public int itemStoreValue() {
        ItemComposition itemComposition = getItemComposition();
        if (itemComposition == null) {
            return 0;
        }
        return itemComposition.getPrice();
    }


    public int getCostForBuyingX(int  amount, int stockLevel) {



        // Calculate the actual buy price based on stock level
        Rs2ShopType shopType = this.getShopType();
        int basePrice = (int)(getInitialPriceBuyAt());
        

        // Implementation of the OSRS shop pricing formula:
        // Buy price = Max(Value × (130 - 3 × Stock) ÷ 100, 30 × Value ÷ 100)
        int totalCost = 0;
        if (shopType == Rs2ShopType.GRAND_EXCHANGE) {
            // For Grand Exchange, we use the current price and the "percentageBoughtAt" to calculate the cost, base price == Rs2GrandExchange.getPrice(itemId)* this.percentageBoughtAt;
            totalCost = basePrice* amount;
        } else {
            // If stock level is 0,  we cant buy anything, cost is 0
            while (stockLevel > 0 && amount > 0) {
                int priceMultiplier = Math.max(
                        (130 - (3 * stockLevel)) / 100, // Dynamic price based on stock
                        30 / 100 // Minimum 30% of base value
                );

                int calculatedPrice = (int) Math.ceil(basePrice * priceMultiplier);
                totalCost += calculatedPrice;
                amount--;
                stockLevel--;
            }
    
        }
        return totalCost;
       

    }

    public int getReturnForSellingX(int  amount, int stockLevel) {        

        int basePrice = (int)getInitialPriceSellAt();
        
        
        // Implementation of the OSRS shop pricing formula:
        // Buy price = Max(Value × (130 - 3 × Stock) ÷ 100, 30 × Value ÷ 100)
        int totalSellValue = 0;
        while (amount > 0) {

            int priceMultiplier = (40 - (3 * Math.min(stockLevel, 10)) / 100);

            int calculatedPrice = (int) Math.ceil(basePrice * priceMultiplier);
            totalSellValue += calculatedPrice;
            amount--;
            stockLevel++;
        }
        return totalSellValue;

    }
       /**
     * Checks if an item is noteable
     * @param itemId The ID of the item
     * @return true if the item is noteable, false otherwise
     */
    public boolean isNoteable() {
        try {

            
            ItemComposition itemComposition = getItemComposition();
            if (itemComposition == null) {
                return false;
            }                
            int linkedId = itemComposition.getLinkedNoteId();
            return linkedId != -1;
            
        } catch (Exception e) {            
            return false;
        }
    }
    public int getNoteId() {
        // Check for noted version if item is noteable
        ItemComposition itemComposition = getItemComposition();
        if (itemComposition == null) {
            return -1;
        }
        int linkItemId = itemComposition.getLinkedNoteId();
        if (linkItemId != -1) {
            return itemComposition.isStackable() ? getItemId() : linkItemId; // if item is stackable, we return the item id, otherwise we return the linked note id
        } else {
            return itemComposition.getId();
        }
    }
    public int getUnNotedId() {
        // Check for unnoted version if item is noteable
        ItemComposition itemComposition = getItemComposition();
        if (itemComposition == null) {
            return -1;
        }
        int linkItemId = itemComposition.getLinkedNoteId();
        if (linkItemId != -1) {
            return itemComposition.isStackable() ? linkItemId: getItemId(); // if item is stackable, we return the linked note id, otherwise we return the item id
        } else {
            return itemComposition.getId();
        }
    }
}
