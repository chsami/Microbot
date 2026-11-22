package net.runelite.client.ui;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * The look picked in the launcher's Themes tab, read once at startup from ~/.microbot/theme.json
 * ({"theme": "gold|slate|classic"}). A missing or bad value falls back to Classic, the stock look, so players who never
 * open the Themes tab see no change. Other keys in the file are ignored.
 */
@Slf4j
public final class ClientTheme
{
	public enum Palette
	{
		// accent, darker (cards), dark (panels), medium, light (muted text), text, control, border, darker hover, dark hover, scroll track
		GOLD(0xFFB13B, 0x1B1612, 0x15110D, 0x514030, 0x9B9384, 0xE6DFD2, 0x1B1612, 0x2E261C, 0x3F3226, 0x19140F, 0x15110D),
		SLATE(0xF5A524, 0x1C1F26, 0x15171C, 0x41485B, 0x8B93A3, 0xE3E6EC, 0x1C1F26, 0x262A33, 0x343A49, 0x1A1D23, 0x15171C),
		CLASSIC(0xDC8A00, 0x1E1E1E, 0x282828, 0x4D4D4D, 0xA5A5A5, 0xC6C6C6, 0x1E1E1E, 0x171717, 0x3C3C3C, 0x232323, 0x191919);

		final Color accent, darker, dark, medium, light, text, control, border, darkerHover, darkHover, scrollTrack;

		Palette(int accent, int darker, int dark, int medium, int light, int text, int control, int border, int darkerHover, int darkHover, int scrollTrack)
		{
			this.accent = new Color(accent);
			this.darker = new Color(darker);
			this.dark = new Color(dark);
			this.medium = new Color(medium);
			this.light = new Color(light);
			this.text = new Color(text);
			this.control = new Color(control);
			this.border = new Color(border);
			this.darkerHover = new Color(darkerHover);
			this.darkHover = new Color(darkHover);
			this.scrollTrack = new Color(scrollTrack);
		}
	}

	static final Path FILE = Paths.get(System.getProperty("user.home"), ".microbot", "theme.json");

	private static final Map<String, String> THEMED_LAF_EXTRAS = ImmutableMap.<String, String>builder()
		.put("TitlePane.background", "@background")
		.put("MenuBar.background", "@background")
		.put("ToolBar.background", "@background")
		.put("ScrollBar.hoverThumbColor", "lighten(@MEDIUM_GRAY,10%)")
		.put("ScrollBar.pressedThumbColor", "@accentColor")
		.put("Button.foreground", "@foreground")
		.build();

	private static final ClientTheme CURRENT = read(FILE);

	public final Palette palette;

	ClientTheme(Palette palette)
	{
		this.palette = palette;
	}

	public static ClientTheme current()
	{
		return CURRENT;
	}

	/**
	 * For screens that drew their own colours before themes: Classic keeps that exact colour, the other palettes get
	 * the themed one.
	 */
	public static Color classicOr(Color classic, Color themed)
	{
		return classicOr(CURRENT.palette, classic, themed);
	}

	static Color classicOr(Palette palette, Color classic, Color themed)
	{
		return palette == Palette.CLASSIC ? classic : themed;
	}

	/** The nav strip and sidebar take the page colour on themed palettes; Classic keeps DARKER_GRAY. */
	public static Color sidebarColor()
	{
		return classicOr(ColorScheme.DARKER_GRAY_COLOR, ColorScheme.DARK_GRAY_COLOR);
	}

	/** Text on an accent-coloured fill: Classic keeps its stock black, the others pick black or white by contrast. */
	public static Color onAccent()
	{
		return onAccent(CURRENT.palette);
	}

	static Color onAccent(Palette palette)
	{
		return palette == Palette.CLASSIC ? Color.BLACK : onAccent(palette.accent);
	}

	static Color onAccent(Color accent)
	{
		return contrast(Color.BLACK, accent) >= contrast(Color.WHITE, accent) ? Color.BLACK : Color.WHITE;
	}

	// WCAG relative-luminance contrast ratio
	static double contrast(Color a, Color b)
	{
		double x = luminance(a);
		double y = luminance(b);
		return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
	}

	private static double luminance(Color c)
	{
		return 0.2126 * channel(c.getRed()) + 0.7152 * channel(c.getGreen()) + 0.0722 * channel(c.getBlue());
	}

	private static double channel(int v)
	{
		double s = v / 255.0;
		return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
	}

	/**
	 * FlatLaf defaults added on top of RuneLiteLAF.properties for every palette but Classic, so the frame chrome,
	 * scrollbars and inputs follow the launcher. Fixed in code; no user strings reach FlatLaf.
	 */
	public static Map<String, String> themedLafExtras()
	{
		return themedLafExtras(CURRENT.palette);
	}

	static Map<String, String> themedLafExtras(Palette palette)
	{
		return palette == Palette.CLASSIC ? Collections.emptyMap() : THEMED_LAF_EXTRAS;
	}

	static ClientTheme read(Path file)
	{
		if (!Files.isRegularFile(file))
		{
			return parse(null);
		}
		try
		{
			return parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
		}
		catch (Exception e)
		{
			log.warn("Could not read {}; using the default theme", file, e);
			return parse(null);
		}
	}

	static ClientTheme parse(String json)
	{
		JsonObject o = null;
		try
		{
			JsonElement root = json == null ? null : new JsonParser().parse(json);
			if (root != null && root.isJsonObject())
			{
				o = root.getAsJsonObject();
			}
		}
		catch (Exception e)
		{
			log.warn("theme.json is not valid JSON; using the default theme");
		}
		return new ClientTheme(pick(Palette.class, o, "theme", Palette.CLASSIC));
	}

	private static <E extends Enum<E>> E pick(Class<E> type, JsonObject o, String key, E fallback)
	{
		try
		{
			return o != null && o.has(key) ? Enum.valueOf(type, o.get(key).getAsString().trim().toUpperCase(Locale.ROOT)) : fallback;
		}
		catch (Exception e)
		{
			return fallback;
		}
	}
}
