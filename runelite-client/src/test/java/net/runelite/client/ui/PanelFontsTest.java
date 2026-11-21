package net.runelite.client.ui;

import java.awt.Font;
import javax.swing.text.StyleContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PanelFontsTest
{
	@Test
	public void htmlFindsTheBundledFontsByName()
	{
		for (ClientTheme.PanelFont choice : new ClientTheme.PanelFont[]{ClientTheme.PanelFont.MONTSERRAT, ClientTheme.PanelFont.INTER})
		{
			for (boolean bold : new boolean[]{false, true})
			{
				Font font = PanelFonts.load(choice, bold);
				// Swing HTML resolves font-family through the default StyleContext
				Font byName = StyleContext.getDefaultStyleContext().getFont(font.getFamily(), Font.PLAIN, 12);
				assertEquals(font.getFontName(), byName.getFontName());
				assertTrue(font.getFontName(), font.getFontName().startsWith(choice == ClientTheme.PanelFont.INTER ? "Inter" : "Montserrat"));
			}
		}
	}

	@Test
	public void missingGlyphsFallBack()
	{
		Font font = PanelFonts.load(ClientTheme.PanelFont.MONTSERRAT, false);
		assertTrue(font.canDisplay((char) 0x0142)); // Polish l-stroke, in Montserrat itself
		assertTrue(font.canDisplay((char) 0x0416)); // Cyrillic Zhe
		assertTrue(font.canDisplay((char) 0x2605)); // a star, from the fallback fonts
	}

	@Test
	public void runescapeSizesAreWholePixels()
	{
		assertEquals(16f, PanelFonts.size(12.5f, true), 0f);
		assertEquals(15f, PanelFonts.size(11.5f, true), 0f);
		assertEquals(13f, PanelFonts.size(10.5f, true), 0f);
		assertEquals(11.5f, PanelFonts.size(11.5f, false), 0f);
	}

	@Test
	public void resizedRuneScapeBoldStaysTheStockBoldFont()
	{
		Font stock = FontManager.getRunescapeBoldFont();
		assertEquals(stock, PanelFonts.derive(PanelFonts.load(ClientTheme.PanelFont.RUNESCAPE, true), Font.BOLD, 12.5f, true));
	}

	@Test
	public void semiBoldIsNotFakeBolded()
	{
		Font semi = PanelFonts.load(ClientTheme.PanelFont.MONTSERRAT, true);
		assertEquals(Font.PLAIN, PanelFonts.derive(semi, Font.BOLD, 12.5f, false).getStyle());
	}
}
