package net.runelite.client.ui;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ClientThemeTest
{
	@Test
	public void customColoursOverrideTheBasePerKey()
	{
		ClientTheme t = ClientTheme.parse("{\"theme\":\"custom\",\"custom\":{\"base\":\"slate\",\"colors\":{\"accent\":\"#7AA2F7\",\"dark\":\"#11131a\"}}}");
		assertTrue(t.custom);
		assertEquals(ClientTheme.Palette.SLATE, t.palette);
		assertEquals(new Color(0x7aa2f7), t.colors.accent);
		assertEquals(new Color(0x11131a), t.colors.dark);
		assertEquals(ClientTheme.Palette.SLATE.text, t.colors.text);
	}

	@Test
	public void badCustomValuesFallBackWithoutThrowing()
	{
		ClientTheme t = ClientTheme.parse("{\"theme\":\"custom\",\"custom\":{\"base\":\"pink\",\"colors\":{\"accent\":\"#12345\",\"text\":\"red;}\",\"dark\":7}}}");
		assertEquals(ClientTheme.Palette.CLASSIC, t.palette);
		assertEquals(ClientTheme.Palette.CLASSIC.accent, t.colors.accent);
		assertEquals(ClientTheme.Palette.CLASSIC.text, t.colors.text);
		assertEquals(ClientTheme.Palette.CLASSIC, ClientTheme.parse("{\"theme\":\"custom\"}").palette);
		assertEquals(ClientTheme.Palette.CLASSIC, ClientTheme.parse("{\"theme\":\"custom\",\"custom\":[1]}").palette);
	}

	@Test
	public void customIsNeverTreatedAsStock()
	{
		ClientTheme t = ClientTheme.parse("{\"theme\":\"custom\",\"custom\":{\"base\":\"classic\"}}");
		assertEquals(Color.RED, ClientTheme.classicOr(t, new Color(40, 40, 40), Color.RED));
	}

	@Test
	public void classicBasedCustomGetsTheThemedLafExtras()
	{
		ClientTheme t = ClientTheme.parse("{\"theme\":\"custom\",\"custom\":{\"base\":\"classic\"}}");
		assertFalse(ClientTheme.themedLafExtras(t).isEmpty());
		assertEquals(ClientTheme.themedLafExtras(ClientTheme.Palette.GOLD), ClientTheme.themedLafExtras(t));
	}

	@Test
	public void customAccentPicksOnAccentByContrast()
	{
		ClientTheme dark = ClientTheme.parse("{\"theme\":\"custom\",\"custom\":{\"base\":\"classic\",\"colors\":{\"accent\":\"#202020\"}}}");
		assertEquals(Color.WHITE, ClientTheme.onAccent(dark));
		ClientTheme light = ClientTheme.parse("{\"theme\":\"custom\",\"custom\":{\"base\":\"gold\",\"colors\":{\"accent\":\"#ffd700\"}}}");
		assertEquals(Color.BLACK, ClientTheme.onAccent(light));
	}

	@Test
	public void styleIsTypedAndClamped()
	{
		ClientTheme t = ClientTheme.parse("{\"theme\":\"custom\",\"custom\":{\"style\":{\"corners\":99,\"roundScrollbars\":true,\"scrollbarWidth\":1}}}");
		assertEquals(12, t.corners);
		assertEquals(Boolean.TRUE, t.roundScrollbars);
		assertEquals(4, t.scrollbarWidth);
		assertEquals(17, ClientTheme.parse("{\"theme\":\"custom\",\"custom\":{\"style\":{\"scrollbarWidth\":99}}}").scrollbarWidth);
		ClientTheme s = ClientTheme.parse("{\"theme\":\"custom\",\"custom\":{\"style\":{\"corners\":\"6\",\"roundScrollbars\":\"yes\"}}}");
		assertEquals(-1, s.corners);
		assertNull(s.roundScrollbars);
	}

	@Test
	public void builtInThemesHaveNoCustomStyle()
	{
		ClientTheme t = ClientTheme.parse("{\"theme\":\"gold\",\"custom\":{\"style\":{\"corners\":6}}}");
		assertFalse(t.custom);
		assertEquals(-1, t.corners);
		assertEquals(ClientTheme.Palette.GOLD.accent, t.colors.accent);
	}

	@Test
	public void hugeOrDeepFileFallsBackToStock() throws Exception
	{
		Path f = Files.createTempFile("theme", ".json");
		try
		{
			byte[] pad = new byte[70 * 1024];
			java.util.Arrays.fill(pad, (byte) ' ');
			Files.write(f, ("{\"theme\":\"gold\"}" + new String(pad, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));
			assertEquals(ClientTheme.Palette.CLASSIC, ClientTheme.read(f).palette);
			byte[] fits = new byte[64 * 1024];
			java.util.Arrays.fill(fits, (byte) ' ');
			byte[] json = "{\"theme\":\"gold\"}".getBytes(StandardCharsets.UTF_8);
			System.arraycopy(json, 0, fits, 0, json.length);
			Files.write(f, fits);
			assertEquals(ClientTheme.Palette.GOLD, ClientTheme.read(f).palette);
		}
		finally
		{
			Files.delete(f);
		}
		StringBuilder deep = new StringBuilder();
		for (int i = 0; i < 20000; i++)
		{
			deep.append('[');
		}
		assertEquals(ClientTheme.Palette.CLASSIC, ClientTheme.parse(deep.toString()).palette);
	}

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
	public void redesignIsOffOnlyForStock()
	{
		for (ClientTheme.Palette p : ClientTheme.Palette.values())
		{
			assertEquals(p != ClientTheme.Palette.CLASSIC, ClientTheme.redesign(new ClientTheme(p)));
		}
		assertTrue(ClientTheme.redesign(ClientTheme.parse("{\"theme\":\"custom\",\"custom\":{\"base\":\"classic\"}}")));
	}

	@Test
	public void redesignLafOverridesShip()
	{
		org.junit.Assert.assertNotNull(net.runelite.client.ui.laf.RuneLiteLAF.class.getResource("RuneLiteLAF-redesign.properties"));
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

	@Test
	public void sidebarColorFollowsTheStockRule()
	{
		ClientTheme custom = ClientTheme.parse("{\"theme\":\"custom\",\"custom\":{\"base\":\"classic\"}}");
		assertEquals(ColorScheme.DARKER_GRAY_COLOR, ClientTheme.classicOr(new ClientTheme(ClientTheme.Palette.CLASSIC), ColorScheme.DARKER_GRAY_COLOR, ColorScheme.DARK_GRAY_COLOR));
		assertEquals(ColorScheme.DARK_GRAY_COLOR, ClientTheme.classicOr(custom, ColorScheme.DARKER_GRAY_COLOR, ColorScheme.DARK_GRAY_COLOR));
	}

	private static void assertAtLeast(String what, double min, double actual)
	{
		org.junit.Assert.assertTrue(what + " contrast " + actual + " < " + min, actual >= min);
	}

	@Test
	public void lafExtrasHoldOnlyWhitelistedNumbers()
	{
		Map<String, String> x = ClientTheme.parse("{\"theme\":\"custom\",\"custom\":{\"style\":{\"corners\":6,\"roundScrollbars\":true,\"scrollbarWidth\":9}}}").lafExtras();
		assertEquals("6", x.get("Button.arc"));
		assertEquals("4", x.get("CheckBox.arc"));
		assertEquals("999", x.get("ScrollBar.thumbArc"));
		assertEquals("9", x.get("ScrollBar.width"));
		for (Map.Entry<String, String> e : x.entrySet())
		{
			assertTrue(e.getKey(), e.getKey().matches("[A-Za-z]+\\.[A-Za-z]+"));
			assertTrue(e.getValue(), e.getValue().matches("[0-9,]+"));
		}
	}

	@Test
	public void squareScrollbarsGetNoArcOrInsets()
	{
		Map<String, String> x = ClientTheme.parse("{\"theme\":\"custom\",\"custom\":{\"style\":{\"roundScrollbars\":false}}}").lafExtras();
		assertEquals("0", x.get("ScrollBar.thumbArc"));
		assertFalse(x.containsKey("ScrollBar.thumbInsets"));
	}

	@Test
	public void builtInAndUnsetStyleAddNothing()
	{
		assertTrue(ClientTheme.parse("{\"theme\":\"slate\"}").lafExtras().isEmpty());
		assertTrue(ClientTheme.parse("{\"theme\":\"custom\",\"custom\":{}}").lafExtras().isEmpty());
	}
}
