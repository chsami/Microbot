package net.runelite.client.plugins.microbot.util.grandexchange.models;


// Cached price data structure
public class WikiPrice {
	public final int buyPrice;
	public final int sellPrice;
	public final int volume;
	public final long timestamp;
	
	public WikiPrice(int buyPrice, int sellPrice, int volume) {
		this.buyPrice = buyPrice;
		this.sellPrice = sellPrice;
		this.volume = volume;
		this.timestamp = System.currentTimeMillis();
	}
	
	public boolean isExpired(long PRICE_CACHE_DURATION) {
		return System.currentTimeMillis() - timestamp > PRICE_CACHE_DURATION;
	}
}
