package net.runelite.client.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;

/**
 * The look picked in the launcher's Themes tab, read once at startup from ~/.microbot/theme.json
 * ({"theme": "gold|slate|classic", "font": "montserrat|inter|runescape"}). Missing or bad values fall back to Classic + RuneScape,
 * the stock look, so players who never open the Themes tab see no change.
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

	public enum PanelFont
	{
		MONTSERRAT, INTER, RUNESCAPE
	}

	static final Path FILE = Paths.get(System.getProperty("user.home"), ".microbot", "theme.json");

	private static final ClientTheme CURRENT = read(FILE);

	public final Palette palette;
	public final PanelFont font;

	ClientTheme(Palette palette, PanelFont font)
	{
		this.palette = palette;
		this.font = font;
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
		return new ClientTheme(pick(Palette.class, o, "theme", Palette.CLASSIC), pick(PanelFont.class, o, "font", PanelFont.RUNESCAPE));
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
