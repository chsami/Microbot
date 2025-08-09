package net.runelite.client.plugins.microbot.util.grandexchange.models;

import java.time.Duration;

/**
 * Enum for different time-series intervals supported by OSRS Wiki API.
 */
public enum TimeSeriesInterval {
    FIVE_MINUTES("5m", "5 Minutes"),
    ONE_HOUR("1h", "1 Hour"),
    SIX_HOURS("6h", "6 Hours"),
    TWENTY_FOUR_HOURS("24h", "24 Hours");
    
    private final String apiValue;
    private final String displayName;
    
    TimeSeriesInterval(String apiValue, String displayName) {
        this.apiValue = apiValue;
        this.displayName = displayName;
    }
    
    public String getApiValue() { return apiValue; }
    public String getDisplayName() { return displayName; }
    public Duration toDuration() {
        switch (this) {
            case FIVE_MINUTES: return Duration.ofMinutes(5);
            case ONE_HOUR: return Duration.ofHours(1);
            case SIX_HOURS: return Duration.ofHours(6);
            case TWENTY_FOUR_HOURS: return Duration.ofDays(1);
            default: throw new IllegalArgumentException("Unknown TimeSeriesInterval: " + this);
        }
    }
}