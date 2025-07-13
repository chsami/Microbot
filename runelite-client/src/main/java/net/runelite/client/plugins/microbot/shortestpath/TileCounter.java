package net.runelite.client.plugins.microbot.shortestpath;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum TileCounter {
	DISABLED("Disabled"),
	TRAVELLED("Travelled"),
	REMAINING("Remaining");

	private final String type;

	public static TileCounter fromType(String type) {
		for (TileCounter tileCounter : values()) {
			if (tileCounter.type.equals(type)) {
				return tileCounter;
			}
		}
		return null;
	}
}