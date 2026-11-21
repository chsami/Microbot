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
		assertEquals(ClientTheme.PanelFont.RUNESCAPE, ClientTheme.parse(null).font);
	}
}
