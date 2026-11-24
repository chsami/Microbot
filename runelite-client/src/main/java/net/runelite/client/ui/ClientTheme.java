package net.runelite.client.ui;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Color;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * The look picked in the launcher's Themes tab, read once at startup from ~/.microbot/theme.json
 * ({"theme": "gold|slate|classic|custom"}). A missing or bad value falls back to Classic, the stock look, so players who
 * never open the Themes tab see no change. Other keys in the file are ignored.
 * <p>
 * With "custom", a "custom" object is read: {"base": "gold|slate|classic", "colors": {"accent": "#rrggbb", ...any of the
 * Palette keys}, "style": {"corners": 0-12, "roundScrollbars": bool, "scrollbarWidth": 4-17}}. Colours not given come
 * from the base palette; anything malformed is dropped. A Classic-based custom theme is not stock: the classicOr
 * places use theme colours.
 * <p>
 * Stock is one rule everywhere ({@link #isStock}): not custom and the Classic palette. classicOr, onAccent and
 * themedLafExtras and redesign all use it.
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

	/** The 11 colours in use: the palette's, with any custom overrides applied. */
	public static final class Colors
	{
		public final Color accent, darker, dark, medium, light, text, control, border, darkerHover, darkHover, scrollTrack;

		Colors(Palette p, JsonObject o)
		{
			accent = c(o, "accent", p.accent);
			darker = c(o, "darker", p.darker);
			dark = c(o, "dark", p.dark);
			medium = c(o, "medium", p.medium);
			light = c(o, "light", p.light);
			text = c(o, "text", p.text);
			control = c(o, "control", p.control);
			border = c(o, "border", p.border);
			darkerHover = c(o, "darkerHover", p.darkerHover);
			darkHover = c(o, "darkHover", p.darkHover);
			scrollTrack = c(o, "scrollTrack", p.scrollTrack);
		}

		private static Color c(JsonObject o, String key, Color fallback)
		{
			try
			{
				JsonElement e = o == null ? null : o.get(key);
				if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString())
				{
					String s = e.getAsString();
					if (HEX.matcher(s).matches())
					{
						return new Color(Integer.parseInt(s.substring(1), 16));
					}
				}
			}
			catch (RuntimeException ex)
			{
				// fall through to the base colour
			}
			return fallback;
		}
	}

	private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");
	private static final long MAX_BYTES = 64 * 1024;

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

	/** The base palette (the whole theme unless custom). */
	public final Palette palette;
	public final Colors colors;
	public final boolean custom;
	/** -1, null, -1: not set by the theme. */
	public final int corners;
	public final Boolean roundScrollbars;
	public final int scrollbarWidth;

	ClientTheme(Palette palette)
	{
		this(palette, new Colors(palette, null), false, -1, null, -1);
	}

	private ClientTheme(Palette palette, Colors colors, boolean custom, int corners, Boolean roundScrollbars, int scrollbarWidth)
	{
		this.palette = palette;
		this.colors = colors;
		this.custom = custom;
		this.corners = corners;
		this.roundScrollbars = roundScrollbars;
		this.scrollbarWidth = scrollbarWidth;
	}

	public static ClientTheme current()
	{
		return CURRENT;
	}

	/** The stock look: plain Classic, not a custom theme (even one based on Classic). */
	static boolean isStock(ClientTheme t)
	{
		return !t.custom && t.palette == Palette.CLASSIC;
	}

	/**
	 * For screens that drew their own colours before themes: the stock look keeps that exact colour, every other
	 * theme gets the themed one.
	 */
	public static Color classicOr(Color classic, Color themed)
	{
		return classicOr(CURRENT, classic, themed);
	}

	static Color classicOr(ClientTheme t, Color classic, Color themed)
	{
		return isStock(t) ? classic : themed;
	}

	/**
	 * The side-panel redesign is for every theme but stock; Classic, the default and fallback, keeps the stock layout.
	 */
	public static boolean redesign()
	{
		return redesign(CURRENT);
	}

	static boolean redesign(ClientTheme t)
	{
		return !isStock(t);
	}

	static Color classicOr(Palette palette, Color classic, Color themed)
	{
		return classicOr(new ClientTheme(palette), classic, themed);
	}

	/** The nav strip and sidebar take the page colour on themed looks; the stock look keeps DARKER_GRAY. */
	public static Color sidebarColor()
	{
		return classicOr(ColorScheme.DARKER_GRAY_COLOR, ColorScheme.DARK_GRAY_COLOR);
	}

	/** Text on an accent-coloured fill: the stock look keeps its black, every other theme picks black or white by contrast. */
	public static Color onAccent()
	{
		return onAccent(CURRENT);
	}

	static Color onAccent(ClientTheme t)
	{
		return isStock(t) ? Color.BLACK : onAccent(t.colors.accent);
	}

	static Color onAccent(Palette palette)
	{
		return onAccent(new ClientTheme(palette));
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
	 * FlatLaf defaults added on top of RuneLiteLAF.properties for every theme but the stock look, so the frame chrome,
	 * scrollbars and inputs follow the launcher. Fixed in code; no user strings reach FlatLaf.
	 */
	public static Map<String, String> themedLafExtras()
	{
		return themedLafExtras(CURRENT);
	}

	static Map<String, String> themedLafExtras(ClientTheme t)
	{
		return isStock(t) ? Collections.emptyMap() : THEMED_LAF_EXTRAS;
	}

	static Map<String, String> themedLafExtras(Palette palette)
	{
		return themedLafExtras(new ClientTheme(palette));
	}

	static ClientTheme read(Path file)
	{
		if (!Files.isRegularFile(file))
		{
			return parse(null);
		}
		try
		{
			byte[] bytes;
			// bounded read: the size can change after a check, so never read more than MAX_BYTES + 1
			try (InputStream in = Files.newInputStream(file))
			{
				bytes = ByteStreams.toByteArray(ByteStreams.limit(in, MAX_BYTES + 1));
			}
			if (bytes.length > MAX_BYTES)
			{
				log.warn("theme.json is larger than 64 KB; using the default theme");
				return parse(null);
			}
			return parse(new String(bytes, StandardCharsets.UTF_8));
		}
		catch (Exception e)
		{
			log.warn("Could not read theme.json ({}); using the default theme", e.getClass().getSimpleName());
			return parse(null);
		}
	}

	static ClientTheme parse(String json)
	{
		try
		{
			return parse0(json);
		}
		catch (Exception | StackOverflowError e)
		{
			log.warn("Could not use theme.json; using the default theme");
			return new ClientTheme(Palette.CLASSIC);
		}
	}

	private static ClientTheme parse0(String json)
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
		catch (Exception | StackOverflowError e)
		{
			log.warn("theme.json is not valid JSON; using the default theme");
		}
		Palette theme = pick(Palette.class, o, "theme", Palette.CLASSIC);
		JsonObject cu = o != null && o.has("custom") && o.get("custom").isJsonObject() ? o.getAsJsonObject("custom") : null;
		if (cu == null || !isCustom(o))
		{
			return new ClientTheme(theme);
		}
		Palette base = pick(Palette.class, cu, "base", Palette.CLASSIC);
		JsonElement colors = cu.get("colors");
		JsonObject style = cu.get("style") != null && cu.get("style").isJsonObject() ? cu.getAsJsonObject("style") : null;
		return new ClientTheme(base, new Colors(base, colors != null && colors.isJsonObject() ? colors.getAsJsonObject() : null), true,
			num(style, "corners", 0, 12), bool(style, "roundScrollbars"), num(style, "scrollbarWidth", 4, PluginPanel.SCROLLBAR_WIDTH));
	}

	private static boolean isCustom(JsonObject o)
	{
		try
		{
			return "custom".equals(o.get("theme").getAsString().trim().toLowerCase(Locale.ROOT));
		}
		catch (RuntimeException e)
		{
			return false;
		}
	}

	private static int num(JsonObject o, String key, int min, int max)
	{
		JsonElement e = o == null ? null : o.get(key);
		if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber())
		{
			return (int) Math.max(min, Math.min(max, e.getAsDouble()));
		}
		return -1;
	}

	private static Boolean bool(JsonObject o, String key)
	{
		JsonElement e = o == null ? null : o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : null;
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

	/** FlatLaf extra defaults for a custom theme's style: built only from the typed fields, never from user strings. */
	public Map<String, String> lafExtras()
	{
		Map<String, String> x = new LinkedHashMap<>();
		if (!custom)
		{
			return x;
		}
		if (corners >= 0)
		{
			for (String k : new String[]{"Button.arc", "Component.arc", "ProgressBar.arc", "TextComponent.arc", "TabbedPane.buttonArc"})
			{
				x.put(k, String.valueOf(corners));
			}
			x.put("CheckBox.arc", String.valueOf(Math.min(corners, 4)));
		}
		if (roundScrollbars != null)
		{
			x.put("ScrollBar.thumbArc", roundScrollbars ? "999" : "0");
			if (roundScrollbars)
			{
				x.put("ScrollBar.thumbInsets", "1,1,1,1");
			}
		}
		if (scrollbarWidth >= 0)
		{
			x.put("ScrollBar.width", String.valueOf(scrollbarWidth));
		}
		return x;
	}
}
