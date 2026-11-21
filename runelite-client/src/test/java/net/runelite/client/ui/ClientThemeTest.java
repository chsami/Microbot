package net.runelite.client.ui;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ClientThemeTest
{
	@Test
	public void readsThemeAndFont()
	{
		ClientTheme t = ClientTheme.parse("{\"theme\":\"gold\",\"font\":\"Inter\"}");
		assertEquals(ClientTheme.Palette.GOLD, t.palette);
		assertEquals(ClientTheme.PanelFont.INTER, t.font);
	}

	@Test
	public void unknownOrMissingValuesFallBackToDefaults()
	{
		ClientTheme t = ClientTheme.parse("{\"theme\":\"pink\"}");
		assertEquals(ClientTheme.Palette.CLASSIC, t.palette);
		assertEquals(ClientTheme.PanelFont.RUNESCAPE, t.font);
	}

	@Test
	public void badJsonOrNoFileUsesDefaults()
	{
		assertEquals(ClientTheme.Palette.CLASSIC, ClientTheme.parse("not json {").palette);
		assertEquals(ClientTheme.Palette.CLASSIC, ClientTheme.parse("[1]").palette);
		assertEquals(ClientTheme.PanelFont.RUNESCAPE, ClientTheme.parse(null).font);
	}
}
