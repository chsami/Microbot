package net.runelite.client.ui;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.io.InputStream;
import javax.swing.JEditorPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import lombok.extern.slf4j.Slf4j;

/**
 * Side-panel text in the font the launcher's Themes tab picked (Montserrat by default). Montserrat and Inter are
 * created, never registered with the GraphicsEnvironment, so the JVM's font family list stays exactly as before.
 * In-game overlays keep using {@link FontManager}'s RuneScape fonts.
 */
@Slf4j
public final class PanelFonts
{
	private static final ClientTheme.PanelFont CHOICE = ClientTheme.current().font;
	// The RuneScape fonts are drawn for 16px; scale them so the panels keep the same text size.
	private static final float SCALE = CHOICE == ClientTheme.PanelFont.RUNESCAPE ? 16f / 12.5f : 1f;
	private static final Font REGULAR = load(false);
	private static final Font SEMIBOLD = load(true);

	private PanelFonts()
	{
	}

	public static Font of(int style, float size)
	{
		return ((style & Font.BOLD) != 0 ? SEMIBOLD : REGULAR).deriveFont(style & Font.ITALIC, size * SCALE);
	}

	public static Font regular()
	{
		return of(Font.PLAIN, 12.5f);
	}

	public static Font bold()
	{
		return of(Font.BOLD, 12.5f);
	}

	public static Font small()
	{
		return of(Font.PLAIN, 11.5f);
	}

	/**
	 * Read-only HTML in the panel font. Swing's HTML finds fonts by family name, and these fonts are deliberately
	 * not registered, so an HTML JLabel falls back to Dialog; this pane maps every run back to the panel font.
	 */
	public static JEditorPane html(String html, Color foreground)
	{
		JEditorPane pane = new JEditorPane()
		{
			@Override
			public Dimension getPreferredSize()
			{
				// wrap to the parent's width instead of reporting one long line
				Container parent = getParent();
				if (parent != null && parent.getWidth() > 0)
				{
					int width = parent.getWidth() - parent.getInsets().left - parent.getInsets().right;
					setSize(width, Short.MAX_VALUE);
					return new Dimension(width, super.getPreferredSize().height);
				}
				return super.getPreferredSize();
			}
		};
		pane.setEditorKit(new HTMLEditorKit()
		{
			private final StyleSheet sheet = new StyleSheet()
			{
				@Override
				public Font getFont(AttributeSet a)
				{
					Font named = super.getFont(a);
					// Swing's default HTML body size is 14
					return of(named.getStyle(), 12.5f * named.getSize2D() / 14f);
				}
			};

			{
				sheet.addStyleSheet(super.getStyleSheet());
				sheet.addRule(String.format("body { color: #%06x; margin: 0; }", foreground.getRGB() & 0xFFFFFF));
			}

			@Override
			public StyleSheet getStyleSheet()
			{
				return sheet;
			}

			@Override
			public Document createDefaultDocument()
			{
				// the default wraps the kit's sheet in a plain StyleSheet, whose getFont() would not be ours
				HTMLDocument doc = new HTMLDocument(sheet);
				doc.setParser(getParser());
				doc.setAsynchronousLoadPriority(-1);
				return doc;
			}
		});
		pane.setEditable(false);
		pane.setFocusable(false);
		pane.setOpaque(false);
		pane.setBorder(null);
		// keep the view at the top: setting text would otherwise scroll to the caret at the end
		((javax.swing.text.DefaultCaret) pane.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
		pane.setText(html);
		return pane;
	}

	private static Font load(boolean bold)
	{
		switch (CHOICE)
		{
			case RUNESCAPE:
				return bold ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeFont();
			case INTER:
				return load(bold ? "Inter-SemiBold.ttf" : "Inter-Regular.ttf", bold);
			default:
				return load(bold ? "Montserrat-SemiBold.ttf" : "Montserrat-Regular.ttf", bold);
		}
	}

	private static Font load(String name, boolean bold)
	{
		try (InputStream in = PanelFonts.class.getResourceAsStream("fonts/" + name))
		{
			if (in != null)
			{
				return Font.createFont(Font.TRUETYPE_FONT, in);
			}
			log.warn("Panel font {} is missing; using Dialog", name);
		}
		catch (Exception e)
		{
			log.warn("Panel font {} could not be loaded; using Dialog", name, e);
		}
		return new Font(Font.DIALOG, bold ? Font.BOLD : Font.PLAIN, 12);
	}
}
