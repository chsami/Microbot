package net.runelite.client.ui;

import java.awt.Font;

/**
 * Side-panel text fonts: the RuneScape fonts from {@link FontManager}, sized for the panel.
 */
public final class PanelFonts
{
	private PanelFonts()
	{
	}

	public static Font of(int style, float size)
	{
		// FontManager builds the RuneScape bold font with Font.BOLD, so the style is kept when resizing it
		return ((style & Font.BOLD) != 0 ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeFont()).deriveFont(style, size(size));
	}

	public static Font regular()
	{
		return FontManager.getRunescapeFont();
	}

	public static Font bold()
	{
		return FontManager.getRunescapeBoldFont();
	}

	public static Font small()
	{
		return FontManager.getRunescapeSmallFont();
	}

	/** The RuneScape fonts are drawn for 16px, so scale them to match and keep whole pixels: pixel fonts blur in between. */
	static float size(float size)
	{
		return Math.round(size * 16f / 12.5f);
	}
}
