package net.runelite.client.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
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
	public void onAccentPicksTheHigherContrastColour()
	{
		for (ClientTheme.Palette p : ClientTheme.Palette.values())
		{
			if (p == ClientTheme.Palette.CLASSIC)
			{
				continue;
			}
			java.awt.Color picked = ClientTheme.onAccent(p);
			java.awt.Color other = java.awt.Color.BLACK.equals(picked) ? java.awt.Color.WHITE : java.awt.Color.BLACK;
			assertTrue(p + " on-accent", ClientTheme.contrast(picked, p.accent) >= ClientTheme.contrast(other, p.accent));
		}
		// a dark accent gets white text, a light one black; Classic keeps the stock black
		assertEquals(java.awt.Color.WHITE, ClientTheme.onAccent(new java.awt.Color(0x202020)));
		assertEquals(java.awt.Color.BLACK, ClientTheme.onAccent(new java.awt.Color(0xffd700)));
		assertEquals(java.awt.Color.BLACK, ClientTheme.onAccent(ClientTheme.Palette.CLASSIC));
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
	public void classicGetsNoExtraLafDefaults()
	{
		assertTrue(ClientTheme.themedLafExtras(ClientTheme.Palette.CLASSIC).isEmpty());
	}

	@Test
	public void themedPalettesGetOnlyTheFixedLafDefaults()
	{
		java.util.Set<String> allowedKeys = new java.util.HashSet<>(java.util.Arrays.asList(
			"TitlePane.background", "MenuBar.background", "ToolBar.background",
			"ScrollBar.hoverThumbColor", "ScrollBar.pressedThumbColor",
			"Button.foreground"));
		for (ClientTheme.Palette p : ClientTheme.Palette.values())
		{
			if (p == ClientTheme.Palette.CLASSIC)
			{
				continue;
			}
			java.util.Map<String, String> extras = ClientTheme.themedLafExtras(p);
			assertEquals(allowedKeys, extras.keySet());
			for (java.util.Map.Entry<String, String> entry : extras.entrySet())
			{
				String v = entry.getValue();
				assertTrue(entry.getKey() + " = " + v, v.startsWith("@") || v.matches("lighten\\(@\\w+,\\s*\\d+%\\)"));
			}
		}
	}

	@Test
	public void everyPaletteKeepsTextLinesAndHoverVisible()
	{
		for (ClientTheme.Palette p : ClientTheme.Palette.values())
		{
			assertAtLeast(p + " text", 4.5, ClientTheme.contrast(p.text, p.dark));
			assertAtLeast(p + " muted text", 4.5, ClientTheme.contrast(p.light, p.darker));
			assertAtLeast(p + " accent", 4.5, ClientTheme.contrast(p.accent, p.dark));
			// lines and frames drawn in MEDIUM_GRAY_COLOR, and row hover, against the card colour
			assertAtLeast(p + " lines", 1.7, ClientTheme.contrast(p.medium, p.darker));
			assertAtLeast(p + " hover", 1.4, ClientTheme.contrast(p.darkerHover, p.darker));
		}
	}

	private static void assertAtLeast(String what, double min, double actual)
	{
		org.junit.Assert.assertTrue(what + " contrast " + actual + " < " + min, actual >= min);
	}
}
