package net.runelite.client.plugins.microbot.util.combat.models;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.ItemComposition;
import net.runelite.client.plugins.microbot.Microbot;

@Getter
@Setter
public class Rs2DropSource {
    final private int itemId;
    final private String sourceName;    
    private int sourceLevel;    
    private int minQuantity;
    private int maxQuantity;    
    private double dropRate;
    private String notes;
    private ItemComposition itemComposition;

    private ItemComposition getItemComposition() {
        if (itemComposition == null) {
            itemComposition =  Microbot.getClientThread().runOnClientThreadOptional(() -> 
            Microbot.getItemManager().getItemComposition(itemId)).orElse(null);
        }
        return itemComposition;
    }  

    public Rs2DropSource(int itemId, String sourceName, 
                    int sourceLevel,                     
                    int minQuantity, 
                    int maxQuantity, 
                    double dropRate, 
                    String notes) {
        this.itemId = itemId;
        this.sourceName = sourceName;
        this.sourceLevel = sourceLevel;        
        this.minQuantity = minQuantity;
        this.maxQuantity = maxQuantity;
        this.dropRate = dropRate;
        this.notes = notes;
    }
    public int getKillsForXDesiredPercentage(double desiredChance) {
        
        int kills = (int) Math.ceil(
            Math.log(1 - desiredChance) / Math.log(1 - dropRate)
        );
        return kills;
    }
    public double getDropValue(int itemPrice) {
        int minQuantity = getMinQuantity();
        int maxQuantity = getMaxQuantity();
        double dropRate = getDropRate();
        return dropRate * (minQuantity + maxQuantity) / 2.0 * itemPrice;
    }
 
    @Override
    public String toString() {
        return "DropSource{" +
            "itemId=" + itemId +
            ", itemName='" + getItemComposition().getName() + '\'' +
            "sourceName='" + sourceName + '\'' +
            ", sourceLevel=" + sourceLevel +
            ", minQuantity=" + minQuantity +
            ", maxQuantity=" + maxQuantity +
            ", dropRate=" + dropRate +
            ", notes='" + notes + '\'' +
            '}';
    }
}