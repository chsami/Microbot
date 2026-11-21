package net.runelite.client.ui;

import java.awt.Color;
import java.awt.Font;
import javax.swing.JEditorPane;
import javax.swing.text.html.HTMLDocument;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class PanelFontsTest
{
	@Test
	public void htmlTextUsesThePanelFont()
	{
		JEditorPane pane = PanelFonts.html("<html>plain <b>bold</b></html>", Color.WHITE);
		HTMLDocument doc = (HTMLDocument) pane.getDocument();
		String text = pane.getText();

		Font plain = doc.getFont(doc.getCharacterElement(1).getAttributes());
		Font bold = doc.getFont(doc.getCharacterElement(8).getAttributes());

		assertEquals(PanelFonts.regular().getFontName(), plain.getFontName());
		assertEquals(PanelFonts.bold().getFontName(), bold.getFontName());
	}
}
