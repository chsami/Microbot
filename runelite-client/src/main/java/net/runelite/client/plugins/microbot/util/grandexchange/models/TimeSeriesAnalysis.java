package net.runelite.client.plugins.microbot.util.grandexchange.models;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Class to hold aggregated time-series analysis for OSRS market data.
 * Focuses purely on statistical analysis of price and volume data over time.
 */
public class TimeSeriesAnalysis {
    public final List<TimeSeriesDataPoint> dataPoints;
    public final int averageHighPrice;
    public final int averageLowPrice;
    public final int averagePrice;
    public final int totalHighPriceVolume;
    public final int totalLowPriceVolume;
    public final int totalVolume;
    public final TimeSeriesInterval interval;
    
    // Advanced analytics
    public final int minHighPrice;
    public final int maxHighPrice;
    public final int minLowPrice;
    public final int maxLowPrice;
    public final double priceVolatility;
    public final int averageHighPriceVolume;
    public final int averageLowPriceVolume;
    public final int averageTotalVolume;
    public final double volumeStability;

    
    public TimeSeriesAnalysis(List<TimeSeriesDataPoint> dataPoints, TimeSeriesInterval interval) {
        this.dataPoints = dataPoints;
        this.interval = interval;
        
        if (!dataPoints.isEmpty()) {
            // Basic calculations
            this.averageHighPrice = (int) dataPoints.stream().mapToInt(dp -> dp.highPrice).average().orElse(0);
            this.averageLowPrice = (int) dataPoints.stream().mapToInt(dp -> dp.lowPrice).average().orElse(0);
            this.averagePrice = (averageHighPrice + averageLowPrice) / 2;
            this.totalHighPriceVolume = dataPoints.stream().mapToInt(dp -> dp.highPriceVolume).sum();
            this.totalLowPriceVolume = dataPoints.stream().mapToInt(dp -> dp.lowPriceVolume).sum();
            this.totalVolume = totalHighPriceVolume + totalLowPriceVolume;
            
            // Advanced analytics
            this.minHighPrice = dataPoints.stream().mapToInt(dp -> dp.highPrice).min().orElse(0);
            this.maxHighPrice = dataPoints.stream().mapToInt(dp -> dp.highPrice).max().orElse(0);
            this.minLowPrice = dataPoints.stream().mapToInt(dp -> dp.lowPrice).min().orElse(0);
            this.maxLowPrice = dataPoints.stream().mapToInt(dp -> dp.lowPrice).max().orElse(0);
            this.averageHighPriceVolume = (int) dataPoints.stream().mapToInt(dp -> dp.highPriceVolume).average().orElse(0);
            this.averageLowPriceVolume = (int) dataPoints.stream().mapToInt(dp -> dp.lowPriceVolume).average().orElse(0);
            this.averageTotalVolume = (int) dataPoints.stream().mapToInt(dp -> dp.getTotalVolume()).average().orElse(0);
            
            // Calculate price volatility (standard deviation of average prices)
            OptionalDouble avgPriceOpt = dataPoints.stream().mapToDouble(dp -> dp.getAveragePrice()).average();
            if (avgPriceOpt.isPresent()) {
                double avgPrice = avgPriceOpt.getAsDouble();
                double variance = dataPoints.stream()
                    .mapToDouble(dp -> Math.pow(dp.getAveragePrice() - avgPrice, 2))
                    .average().orElse(0.0);
                this.priceVolatility = Math.sqrt(variance) / avgPrice; // Coefficient of variation
            } else {
                this.priceVolatility = 0.0;
            }
            
            // Calculate volume stability (inverse of volume coefficient of variation)
            OptionalDouble avgVolumeOpt = dataPoints.stream().mapToDouble(dp -> dp.getTotalVolume()).average();
            if (avgVolumeOpt.isPresent() && avgVolumeOpt.getAsDouble() > 0) {
                double avgVol = avgVolumeOpt.getAsDouble();
                double volumeVariance = dataPoints.stream()
                    .mapToDouble(dp -> Math.pow(dp.getTotalVolume() - avgVol, 2))
                    .average().orElse(0.0);
                double volumeCoefficientOfVariation = Math.sqrt(volumeVariance) / avgVol;
                this.volumeStability = Math.max(0, 1.0 - volumeCoefficientOfVariation);
            } else {
                this.volumeStability = 0.0;
            }
        } else {
            this.averageHighPrice = 0;
            this.averageLowPrice = 0;
            this.averagePrice = 0;
            this.totalHighPriceVolume = 0;
            this.totalLowPriceVolume = 0;
            this.totalVolume = 0;
            this.minHighPrice = 0;
            this.maxHighPrice = 0;
            this.minLowPrice = 0;
            this.maxLowPrice = 0;
            this.priceVolatility = 0.0;
            this.averageHighPriceVolume = 0;
            this.averageLowPriceVolume = 0;
            this.averageTotalVolume = 0;
            this.volumeStability = 0.0;
        }
    }
    
    /**
     * Gets the recommended buy price based on time-series analysis.
     * Uses average low price with slight premium for reliability.
     */
    public int getRecommendedBuyPrice() {
        return (int) (averageLowPrice * 1.02); // 2% above average low
    }
    
    /**
     * Gets the recommended sell price based on time-series analysis.
     * Uses average high price with slight discount for quick sales.
     */
    public int getRecommendedSellPrice() {
        return (int) (averageHighPrice * 0.98); // 2% below average high
    }
    
    /**
     * Calculates volume per 4-hour period based on the interval.
     * This helps estimate market activity over a standardized time period.
     */
    public int calculateVolumePer4Hours() {
        if (dataPoints.isEmpty()) return 0;
        
        switch (interval) {
            case FIVE_MINUTES:
                // 48 intervals per 4 hours
                return averageTotalVolume * 48;
            case ONE_HOUR:
                // 4 intervals per 4 hours
                return averageTotalVolume * 4;
            case SIX_HOURS:
                // ~0.67 intervals per 4 hours
                return (int) (averageTotalVolume * 0.67);
            case TWENTY_FOUR_HOURS:
                // ~0.17 intervals per 4 hours
                return (int) (averageTotalVolume * 0.17);
            default:
                return averageTotalVolume;
        }
    }
    
    /**
     * Estimates wait time for an offer based on price positioning and volume.
     * 
     * @param price The offer price
     * @param isBuyOffer true for buy offers, false for sell offers
     * @return Estimated wait time in milliseconds
     */
    public long estimateWaitTime(int price, boolean isBuyOffer) {
        if (averageTotalVolume == 0) return Long.MAX_VALUE;
        
        // Base wait time in hours based on volume
        double baseWaitHours = Math.max(0.1, 100.0 / averageTotalVolume);
        
        // Adjust based on price positioning
        double pricePosition;
        if (isBuyOffer) {
            // Higher buy price = faster execution
            pricePosition = averageHighPrice > 0 ? (double) price / averageHighPrice : 1.0;
        } else {
            // Lower sell price = faster execution
            pricePosition = price > 0 ? (double) averageLowPrice / price : 1.0;
        }
        
        // Apply price positioning factor (1.0 = average, 2.0 = very aggressive)
        double adjustedWaitHours = baseWaitHours / Math.max(0.5, pricePosition);
        
        return (long) (adjustedWaitHours * 60 * 60 * 1000); // Convert to milliseconds
    }
    
    /**
     * Gets the price spread as a percentage.
     * Higher spreads indicate more profit potential but also higher risk.
     */
    public double getPriceSpreadPercent() {
        if (averageLowPrice == 0) return 0.0;
        return ((double) (averageHighPrice - averageLowPrice) / averageLowPrice) * 100;
    }
    
    /**
     * Checks if the market data shows healthy trading activity.
     */
    public boolean hasHealthyTradingActivity() {
        return !dataPoints.isEmpty() && 
               averageTotalVolume > 10 && 
               averageHighPrice > 0 && 
               averageLowPrice > 0 &&
               priceVolatility < 0.5; // Not extremely volatile
    }
}