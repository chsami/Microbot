package net.runelite.client.ui;

import java.awt.Font;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class PanelFontsTest
{
	@Test
	public void sizesAreWholePixels()
	{
		assertEquals(16f, PanelFonts.size(12.5f), 0f);
		assertEquals(15f, PanelFonts.size(11.5f), 0f);
		assertEquals(13f, PanelFonts.size(10.5f), 0f);
	}

	@Test
	public void defaultSizesAreTheStockFonts()
	{
		assertEquals(FontManager.getRunescapeFont(), PanelFonts.of(Font.PLAIN, 12.5f));
		assertEquals(FontManager.getRunescapeBoldFont(), PanelFonts.of(Font.BOLD, 12.5f));
	}
}
