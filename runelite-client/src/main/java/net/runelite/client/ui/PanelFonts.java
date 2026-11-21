package net.runelite.client.ui;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;

/**
 * Side-panel text in the font the launcher's Themes tab picked. In-game overlays keep using {@link FontManager}'s
 * RuneScape fonts. Montserrat and Inter are registered and looked up through {@link FontManager#getFallbackFont}, as
 * FontManager does for the RuneScape fonts, so HTML labels find them by name and missing glyphs fall back.
 */
@Slf4j
public final class PanelFonts
{
	private static final ClientTheme.PanelFont CHOICE = ClientTheme.current().font;
	private static final boolean RUNESCAPE = CHOICE == ClientTheme.PanelFont.RUNESCAPE;
	private static final Font REGULAR = load(CHOICE, false);
	private static final Font SEMIBOLD = load(CHOICE, true);

	private PanelFonts()
	{
	}

	public static Font of(int style, float size)
	{
		// the RuneScape bold face is a composite, and deriving it as PLAIN would rebuild it plain
		return ((style & Font.BOLD) != 0 ? SEMIBOLD : REGULAR).deriveFont(RUNESCAPE ? style : style & Font.ITALIC, size(size, RUNESCAPE));
	}

	/** For screens that picked their own font before themes: the RuneScape choice keeps it, the others get the themed one. */
	public static Font stockOr(Font stock, Font themed)
	{
		return RUNESCAPE ? stock : themed;
	}

	public static Font regular()
	{
		return RUNESCAPE ? FontManager.getRunescapeFont() : of(Font.PLAIN, 12.5f);
	}

	public static Font bold()
	{
		return RUNESCAPE ? FontManager.getRunescapeBoldFont() : of(Font.BOLD, 12.5f);
	}

	public static Font small()
	{
		return RUNESCAPE ? FontManager.getRunescapeSmallFont() : of(Font.PLAIN, 11.5f);
	}

	/** The RuneScape fonts are drawn for 16px, so scale them to match and keep whole pixels: pixel fonts blur in between. */
	static float size(float size, boolean runescape)
	{
		return runescape ? Math.round(size * 16f / 12.5f) : size;
	}

	static Font load(ClientTheme.PanelFont choice, boolean bold)
	{
		switch (choice)
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
				Font font = Font.createFont(Font.TRUETYPE_FONT, in);
				GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
				// the SemiBold face is its own family, so ask for it plain rather than faking bold on top
				return FontManager.getFallbackFont(font.getFamily(), Font.PLAIN, 12);
			}
			log.warn("Panel font {} is missing; using Dialog", name);
		}
		catch (Exception e)
		{
			log.warn("Panel font {} could not be loaded; using Dialog", name, e);
		}
		return FontManager.getFallbackFont(Font.DIALOG, bold ? Font.BOLD : Font.PLAIN, 12);
	}
}
