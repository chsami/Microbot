package net.runelite.client.plugins.microbot.VoxPlugins.util.models.sources;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.ItemStats;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Slf4j
public class ShopSource {
    @Getter
    final String name; // item name -> source item name
    @Getter    
    final private String shopName;
    @Getter    
    final WorldArea locationArea;
    @Getter    
    final private boolean members;
    @Getter
    @Setter
    private int numberInStock = -1; // -1 means unknown
    @Getter
    @Setter
    private Duration restockTime = Duration.ofSeconds(-1); // -1 means unknown
    @Getter
    @Setter
    private int priceSoldAt = -1; // -1 means unknown
    @Getter
    @Setter
    private int priceBoughtAt = -1; // -1 means unknown
    @Getter
    @Setter
    private double changePercent = -1; // -1 means unknown    
    @Getter
    @Setter
    private String notes = ""; // notes for the shop source
    @Getter
    private double percentageSoldAt = -1; // -1 means unknown
    @Getter
    private double percentageBoughtAt = -1; // -1 means unknown

    public void setPercentageSellAt(int itemId) {
        int basePrice = getItemValueOnClientThread(itemId);
        if (basePrice == 0) {
            this.percentageSoldAt = 0;
            return;
        }
        this.percentageSoldAt = this.priceSoldAt / basePrice;
    }

    public void setPercentageBoughtAt(int itemId) {
        int basePrice = getItemValueOnClientThread(itemId);
        if (basePrice == 0) {
            this.percentageBoughtAt = 0;
            return;
        }
        this.percentageBoughtAt = this.priceBoughtAt / basePrice;

    }
    public ShopSource(String itemName,
        String shopName,            
        WorldArea locationArea,
        boolean members ) {
        this.name = itemName;
        this.shopName = shopName;
        this.locationArea = locationArea;
        this.members = members;        
    }
    public ShopSource(String itemName,
            String shopName,            
            WorldArea locationArea,
            int numberInStock,
            Duration restockTime,
            int priceSoldAt,
            int priceBoughtAt, 
            double changePercent, boolean members, String notes) {
        this.name = itemName;
        this.shopName = shopName;
        this.locationArea = locationArea;
        this.members = members;
        this.numberInStock = numberInStock;
        this.restockTime = restockTime;
        this.priceSoldAt = priceSoldAt;
        this.priceBoughtAt = priceBoughtAt;
        this.changePercent = changePercent;        
        this.notes = notes;
    }

    @Override
    public String toString() {
        return "ShopSource{" +
                "shopName='" + shopName + '\'' +
                ", locationArea='" + locationArea + '\'' +
                ", numberInStock=" + numberInStock +
                ", restockTime='" + restockTime + '\'' +
                ", priceSoldAt=" + priceSoldAt +
                ", priceBoughtAt=" + priceBoughtAt +
                ", changePercent=" + changePercent +
                ", members=" + members +
                ", notes='" + notes + '\'' +
                '}';
    }
    
    /**
     * Returns a multi-line display string with detailed shop information.
     * Uses StringBuilder with tabs for proper formatting.
     * 
     * @return A formatted string containing shop source details
     */
    public String displayString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Shop Source Details ===\n");
        sb.append("Item Name:\t\t").append(name).append("\n");
        sb.append("Shop Name:\t\t").append(shopName).append("\n");
        sb.append("Location:\t\t").append(locationArea != null ? locationArea.toString() : "Unknown").append("\n");
        sb.append("Members Only:\t\t").append(members ? "Yes" : "No").append("\n");
        
        if (numberInStock != -1) {
            sb.append("Stock:\t\t\t").append(numberInStock).append(" items\n");
        }
        
        if (restockTime != null && !restockTime.equals(Duration.ofSeconds(-1))) {
            sb.append("Restock Time:\t\t").append(restockTime.toSeconds()).append(" seconds\n");
        }
        
        if (priceSoldAt != -1) {
            sb.append("Sell Price:\t\t").append(priceSoldAt).append(" gp\n");
        }
        
        if (priceBoughtAt != -1) {
            sb.append("Buy Price:\t\t").append(priceBoughtAt).append(" gp\n");
        }
        
        if (percentageSoldAt != -1) {
            sb.append("Sell % of GE:\t\t").append(String.format("%.1f%%", percentageSoldAt * 100)).append("\n");
        }
        
        if (percentageBoughtAt != -1) {
            sb.append("Buy % of GE:\t\t").append(String.format("%.1f%%", percentageBoughtAt * 100)).append("\n");
        }
        
        if (changePercent != -1) {
            sb.append("Price Change:\t\t").append(String.format("%.2f%%", changePercent)).append("\n");
        }
        
        if (notes != null && !notes.isEmpty()) {
            sb.append("Notes:\t\t\t").append(notes).append("\n");
        }
        
        return sb.toString();
    }

    public WorldPoint getLocation() {
        return locationArea.toWorldPoint();
    }

    public Rs2NpcModel getShopNPC() {
        WorldArea area = getLocationArea();
        List<Rs2NpcModel> npcs = Rs2Npc.getNpcs().collect(Collectors.toList());
        List<Rs2NpcModel> potentialNPCShops = npcs.stream().filter(npc -> npc.getComposition().getActions() != null
                && Arrays.asList(npc.getComposition().getActions()).stream().anyMatch("Trade"::equalsIgnoreCase)
                && area.contains(npc.getWorldLocation())).collect(Collectors.toList());
        if (potentialNPCShops.isEmpty()) {
            log.error("Could not find shop NPC for shop: " + this.shopName + " in area: " + area);
            return null;
        }
        return potentialNPCShops.get(0);
    }

    public Rs2NpcModel getPotentialShopNPCNearPlayer() {
        int searchRange = 10;
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        List<Rs2NpcModel> npcs = Rs2Npc.getNpcs().collect(Collectors.toList());
        List<Rs2NpcModel> potentialNPCShops = npcs.stream().filter(npc -> npc.getComposition().getActions() != null
                && Arrays.asList(npc.getComposition().getActions()).stream().anyMatch("Trade"::equalsIgnoreCase)
                && npc.getWorldLocation().distanceTo(playerLocation) < searchRange).collect(Collectors.toList());
        if (potentialNPCShops == null || potentialNPCShops.isEmpty()) {
            log.error("Could not find shop NPC for shop: " + this.shopName + " at near player location: "
                    + playerLocation + "with range of: " + searchRange);
            return null;
        }

        for (Rs2NpcModel npc : potentialNPCShops) {
            if (this.shopName.equalsIgnoreCase(npc.getName())) {
                return npc;
            } else if (this.shopName.toLowerCase().contains("General Store".toLowerCase())) {
                return npc;
            } else if (this.shopName.toLowerCase().contains(npc.getName().toLowerCase())) {
                return npc;
            }
        }
        log.error("Could not find shop NPC for shop: " + this.shopName);
        return null;
    }

    public static int getCostForBuyingX(ShopSource shopSource, int itemID, int amount) {

        if (shopSource == null)
            return Integer.MAX_VALUE;

        // Calculate the actual buy price based on stock level
        int baseStockPrice = shopSource.getPriceSoldAt();

        int basePrice = getItemValueOnClientThread(itemID);
        int grandExchangePrice = getWikiAveragePrice(itemID);

        int stockLevel = shopSource.getNumberInStock();
        final int baseStockLevel = stockLevel;
        final double percentageSellAt = baseStockPrice / basePrice;
        // Implementation of the OSRS shop pricing formula:
        // Buy price = Max(Value × (130 - 3 × Stock) ÷ 100, 30 × Value ÷ 100)
        int totalCost = 0;
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
        return totalCost;

    }

    public static int getCostForSellingX(ShopSource shopSource, int itemID, int amount) {

        if (shopSource == null)
            return Integer.MAX_VALUE;

        // Calculate the actual buy price based on stock level
        final int baseStockPrice = shopSource.getPriceBoughtAt();

        int basePrice = getItemValueOnClientThread(itemID);
        int grandExchangePrice = getWikiAveragePrice(itemID);
        int stockLevel = shopSource.getNumberInStock();
        final int baseStockLevel = stockLevel;
        final double percentageBoughtAt = baseStockPrice / basePrice;
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
     * Gets the shop value of an item
     * @param itemId The ID of the item
     * @return The shop value, or 0 if not available
     */
    private static int getItemValueOnClientThread(int itemId) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ItemComposition itemComposition = Microbot.getItemManager().getItemComposition(itemId);
            return itemComposition.getPrice();
        }).orElse(-1);
    }

    private static int getWikiAveragePrice(int itemId) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ItemStats itemStats = Microbot.getItemManager().getItemStats(itemId);
            ItemComposition itemComposition = Microbot.getItemManager().getItemComposition(itemId);
            int wikiPrice = itemComposition != null ? itemComposition.getPrice() : 0;                                        
            return wikiPrice;
        }).orElse(null);
    }

}