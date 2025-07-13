package net.runelite.client.plugins.microbot.shortestpath;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum TileStyle {
	TILES("Tiles"),
	LINES("Lines");

	private final String type;

	public static TileStyle fromType(String type) {
		for (TileStyle tileStyle : values()) {
			if (tileStyle.type.equals(type)) {
				return tileStyle;
			}
		}
		return null;
	}
}