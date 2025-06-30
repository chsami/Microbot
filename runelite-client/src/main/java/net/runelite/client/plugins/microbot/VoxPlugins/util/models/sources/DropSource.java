package net.runelite.client.plugins.microbot.VoxPlugins.util.models.sources;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DropSource {
    final String itemName;
    private String sourceName;
    private int sourceLevel;    
    private int minQuantity;
    private int maxQuantity;    
    private double dropRate;
    private String notes;
    public DropSource(String itemName, String sourceName, 
                    int sourceLevel,                     
                    int minQuantity, 
                    int maxQuantity, 
                    double dropRate, 
                    String notes) {
        this.itemName = itemName;
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
            "sourceName='" + sourceName + '\'' +
            ", sourceLevel=" + sourceLevel +
            ", minQuantity=" + minQuantity +
            ", maxQuantity=" + maxQuantity +
            ", dropRate=" + dropRate +
            ", notes='" + notes + '\'' +
            '}';
    }
}