package net.runelite.client.plugins.microbot.util.grandexchange.models;

/**
 * Class to hold time-series price data point.
 */
public class TimeSeriesDataPoint {
    public final long timestamp;
    public final int highPrice;
    public final int lowPrice;
    public final int highPriceVolume;
    public final int lowPriceVolume;
    
    public TimeSeriesDataPoint(long timestamp, int highPrice, int lowPrice, int highPriceVolume, int lowPriceVolume) {
        this.timestamp = timestamp;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.highPriceVolume = highPriceVolume;
        this.lowPriceVolume = lowPriceVolume;
    }
    
    public int getAveragePrice() {
        return (highPrice + lowPrice) / 2;
    }
    
    /**
     * Gets the total volume (buy + sell) for this data point.
     */
    public int getTotalVolume() {
        return highPriceVolume + lowPriceVolume;
    }
    
    /**
     * Gets the buy volume (high price volume represents buying activity).
     */
    public int getBuyVolume() {
        return highPriceVolume;
    }
    
    /**
     * Gets the sell volume (low price volume represents selling activity).
     */
    public int getSellVolume() {
        return lowPriceVolume;
    }
}
