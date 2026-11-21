package net.runelite.client.ui;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ClientThemeTest
{
	@Test
	public void readsThemeAndIgnoresLegacyFontKey()
	{
		// a "font" key from an older launcher is ignored
		ClientTheme t = ClientTheme.parse("{\"theme\":\" Gold \",\"font\":\"inter\"}");
		assertEquals(ClientTheme.Palette.GOLD, t.palette);
	}

	@Test
	public void unknownOrMissingValuesFallBackToDefaults()
	{
		ClientTheme t = ClientTheme.parse("{\"theme\":\"pink\"}");
		assertEquals(ClientTheme.Palette.CLASSIC, t.palette);
	}

	@Test
	public void classicKeepsTheStockColour()
	{
		java.awt.Color stock = new java.awt.Color(40, 40, 40);
		assertEquals(stock, ClientTheme.classicOr(ClientTheme.Palette.CLASSIC, stock, java.awt.Color.RED));
		assertEquals(java.awt.Color.RED, ClientTheme.classicOr(ClientTheme.Palette.GOLD, stock, java.awt.Color.RED));
	}

	@Test
	public void badJsonOrNoFileUsesDefaults()
	{
		assertEquals(ClientTheme.Palette.CLASSIC, ClientTheme.parse("not json {").palette);
		assertEquals(ClientTheme.Palette.CLASSIC, ClientTheme.parse("[1]").palette);
		assertEquals(ClientTheme.Palette.CLASSIC, ClientTheme.parse(null).palette);
	}

	@Test
	public void classicIsTheStockPalette()
	{
		// ColorScheme's values before themes; Classic must stay exactly these so nobody's client changes by default
		ClientTheme.Palette p = ClientTheme.Palette.CLASSIC;
		assertEquals(new java.awt.Color(220, 138, 0), p.accent);
		assertEquals(new java.awt.Color(30, 30, 30), p.darker);
		assertEquals(new java.awt.Color(40, 40, 40), p.dark);
		assertEquals(new java.awt.Color(77, 77, 77), p.medium);
		assertEquals(new java.awt.Color(165, 165, 165), p.light);
		assertEquals(new java.awt.Color(198, 198, 198), p.text);
		assertEquals(new java.awt.Color(30, 30, 30), p.control);
		assertEquals(new java.awt.Color(23, 23, 23), p.border);
		assertEquals(new java.awt.Color(60, 60, 60), p.darkerHover);
		assertEquals(new java.awt.Color(35, 35, 35), p.darkHover);
		assertEquals(new java.awt.Color(25, 25, 25), p.scrollTrack);
	}

	@Test
	public void everyPaletteKeepsTextLinesAndHoverVisible()
	{
		for (ClientTheme.Palette p : ClientTheme.Palette.values())
		{
			assertAtLeast(p + " text", 4.5, contrast(p.text, p.dark));
			assertAtLeast(p + " muted text", 4.5, contrast(p.light, p.darker));
			assertAtLeast(p + " accent", 4.5, contrast(p.accent, p.dark));
			// lines and frames drawn in MEDIUM_GRAY_COLOR, and row hover, against the card colour
			assertAtLeast(p + " lines", 1.7, contrast(p.medium, p.darker));
			assertAtLeast(p + " hover", 1.4, contrast(p.darkerHover, p.darker));
		}
	}

	private static void assertAtLeast(String what, double min, double actual)
	{
		org.junit.Assert.assertTrue(what + " contrast " + actual + " < " + min, actual >= min);
	}

	// WCAG relative-luminance contrast ratio
	private static double contrast(java.awt.Color a, java.awt.Color b)
	{
		double x = luminance(a);
		double y = luminance(b);
		return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
	}

	private static double luminance(java.awt.Color c)
	{
		return 0.2126 * channel(c.getRed()) + 0.7152 * channel(c.getGreen()) + 0.0722 * channel(c.getBlue());
	}

	private static double channel(int v)
	{
		double s = v / 255.0;
		return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
	}
}
