package net.runelite.client.plugins.microbot.util.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.runelite.client.util.Text;

/**
 * RuneLite/Jagex text cleanup helpers.
 *
 * Scope: removes RuneLite-style markup tags (e.g. {@code <col=...>}) and decodes the small set of entities/escapes we
 * actively see in chat/widget strings. Not a general-purpose HTML sanitizer.
 */
public final class Rs2TextSanitizer
{
	private Rs2TextSanitizer()
	{
	}

	/** Allows empty {@code <>} as well as non-empty tags (chat sometimes emits zero-length markup). */
	private static final Pattern TAG_STRIP = Pattern.compile("<[^>]*>");
	private static final Pattern DEC_ENTITY = Pattern.compile("&#(\\d{1,7});");
	private static final Pattern HEX_ENTITY = Pattern.compile("&#(?i)x([0-9a-fA-F]{1,6});");
	// Extract base name and numeric suffix, e.g. "Super attack (4)" -> "Super attack", 4
	private static final Pattern ITEM_NAME_SUFFIX_PATTERN = Pattern.compile("^(.*?)(?:\\s*\\((\\d+)\\))?$");

	/** Strip markup tags, repeatedly, and drop dangling {@code <} with no {@code >}. */
	public static String stripTags(String raw)
	{
		if (raw == null || raw.isEmpty())
		{
			return "";
		}
		String s = raw;
		String prev;
		do
		{
			prev = s;
			s = TAG_STRIP.matcher(s).replaceAll("");
		}
		while (!s.equals(prev));

		for (;;)
		{
			int lt = s.indexOf('<');
			if (lt < 0)
			{
				break;
			}
			int gt = s.indexOf('>', lt);
			if (gt >= 0)
			{
				break;
			}
			s = s.substring(0, lt) + s.substring(lt + 1);
		}
		return s;
	}

	/** Strip tags and replace them with a single space (useful for tokenization/matching). */
	public static String stripTagsToSpace(String raw)
	{
		String s = stripTags(raw);
		return s.isEmpty() ? "" : s.replaceAll("\\s+", " ").trim();
	}

	/**
	 * Widget-friendly cleanup: handles {@code <br>} and compresses spaces, then applies our known entity decode and
	 * normalization. Intended for parsing widget text, not HTML.
	 */
	public static String sanitizeWidgetMultilineText(String raw)
	{
		if (raw == null || raw.isEmpty())
		{
			return "";
		}
		// Text.sanitizeMultilineText removes tags and handles <br> -> space + space compression.
		String s = Text.sanitizeMultilineText(raw);
		// No extra stripTags: Text.sanitizeMultilineText already removed tags.
		return normalizeApostrophes(decodeKnownEntities(normalizeGameText(s))).trim();
	}

	/**
	 * Decode entities observed in game strings.
	 * - numeric decimal/hex entities
	 * - a small named entity set we actively see
	 */
	public static String decodeKnownEntities(String raw)
	{
		if (raw == null || raw.isEmpty())
		{
			return "";
		}
		String s = raw;
		for (int pass = 0; pass < 4; pass++)
		{
			String next = replaceNumericEntities(replaceNumericEntitiesHex(s))
					.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
					.replace("&quot;", "\"").replace("&#34;", "\"")
					.replace("&apos;", "'").replace("&#39;", "'")
					.replace("&nbsp;", " ").replace("&#160;", " ")
					.replace("&shy;", "").replace("&#173;", "")
					.replace("&ndash;", "\u2013").replace("&mdash;", "\u2014")
					.replace("&hellip;", "\u2026").replace("&lsquo;", "\u2018").replace("&rsquo;", "\u2019")
					.replace("&ldquo;", "\u201c").replace("&rdquo;", "\u201d")
					.replace("&frac12;", "\u00BD").replace("&frac14;", "\u00BC").replace("&frac34;", "\u00BE")
					.replace("&eacute;", "\u00E9").replace("&Eacute;", "\u00C9")
					.replace("&aacute;", "\u00E1").replace("&Aacute;", "\u00C1")
					.replace("&iacute;", "\u00ED").replace("&Iacute;", "\u00CD")
					.replace("&oacute;", "\u00F3").replace("&Oacute;", "\u00D3")
					.replace("&uacute;", "\u00FA").replace("&Uacute;", "\u00DA")
					.replace("&agrave;", "\u00E0").replace("&egrave;", "\u00E8").replace("&ograve;", "\u00F2")
					.replace("&acirc;", "\u00E2").replace("&ecirc;", "\u00EA").replace("&ocirc;", "\u00F4")
					.replace("&ntilde;", "\u00F1").replace("&ccedil;", "\u00E7").replace("&Ccedil;", "\u00C7");
			if (next.equals(s))
			{
				break;
			}
			s = next;
		}
		return s;
	}

	public static String normalizeApostrophes(String raw)
	{
		if (raw == null || raw.isEmpty())
		{
			return "";
		}
		return raw
				.replace('\u2019', '\'')
				.replace('\u2018', '\'')
				.replace('\u02BC', '\'');
	}

	/** NFKC + NBSP/NNBSP to space + soft hyphen removal. */
	public static String normalizeGameText(String raw)
	{
		if (raw == null || raw.isEmpty())
		{
			return "";
		}
		return Normalizer.normalize(raw, Normalizer.Form.NFKC)
				.replace('\u00A0', ' ')
				.replace('\u202F', ' ')
				.replace("\u00AD", "");
	}

	public static String normalizeGameTextLower(String raw)
	{
		return normalizeGameText(raw).toLowerCase(Locale.ROOT);
	}

	/**
	 * Cleanup for parsing: decode entities, strip tags, normalize apostrophes, trim, and lowercase.
	 * Intended for comparisons / matching, not for UI display. Null returns empty string.
	 */
	public static String sanitizeForParsing(String raw)
	{
		return sanitizeCore(raw).toLowerCase(Locale.ROOT);
	}

	/**
	 * Cleanup for region name matching (Leagues locked chat): decode entities + strip tags + normalize apostrophes + trim.
	 * Caller decides casing.
	 */
	public static String sanitizeLeaguesLockedRegionName(String raw)
	{
		return sanitizeCore(raw);
	}

	/** @return trimmed, non-empty {@code group(1)} from first {@link Matcher#find()} match; otherwise empty. */
	public static Optional<String> captureFirstGroup(Pattern pattern, String input)
	{
		if (pattern == null || input == null || input.isEmpty())
		{
			return Optional.empty();
		}
		Matcher m = pattern.matcher(input);
		if (!m.find())
		{
			return Optional.empty();
		}
		String g1 = m.group(1);
		if (g1 == null)
		{
			return Optional.empty();
		}
		String trimmed = g1.trim();
		return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
	}

	private static String sanitizeCore(String raw)
	{
		if (raw == null)
		{
			return "";
		}
		return normalizeApostrophes(stripTags(decodeKnownEntities(normalizeGameText(raw)))).trim();
	}

	public static final class ItemNameWithSuffix
	{
		private final String baseName;
		private final OptionalInt suffix;

		private ItemNameWithSuffix(String baseName, OptionalInt suffix)
		{
			this.baseName = Objects.requireNonNull(baseName, "baseName");
			this.suffix = Objects.requireNonNull(suffix, "suffix");
		}

		public String getBaseName()
		{
			return baseName;
		}

		/** @return numeric suffix in "(N)" if present. */
		public OptionalInt getSuffix()
		{
			return suffix;
		}
	}

	/**
	 * Parses a name optionally suffixed with "(N)" (e.g. potion doses).
	 * Strips tags and trims before matching.
	 */
	public static Optional<ItemNameWithSuffix> parseItemNameSuffix(String raw)
	{
		if (raw == null)
		{
			return Optional.empty();
		}
		String s = stripTags(raw).trim();
		if (s.isEmpty())
		{
			return Optional.empty();
		}
		Matcher m = ITEM_NAME_SUFFIX_PATTERN.matcher(s);
		if (!m.matches())
		{
			return Optional.empty();
		}
		String base = m.group(1) != null ? m.group(1).trim() : "";
		if (base.isEmpty())
		{
			return Optional.empty();
		}
		OptionalInt suffix = OptionalInt.empty();
		String suffixRaw = m.group(2);
		if (suffixRaw != null && !suffixRaw.isEmpty())
		{
			try
			{
				suffix = OptionalInt.of(Integer.parseInt(suffixRaw));
			}
			catch (NumberFormatException ignored)
			{
				return Optional.empty();
			}
		}
		return Optional.of(new ItemNameWithSuffix(base, suffix));
	}

	private static String replaceNumericEntities(String s)
	{
		Matcher m = DEC_ENTITY.matcher(s);
		StringBuilder out = new StringBuilder();
		int last = 0;
		while (m.find())
		{
			out.append(s, last, m.start());
			try
			{
				out.append(codePointToString(Integer.parseInt(m.group(1))));
			}
			catch (IllegalArgumentException ex)
			{
				out.append(m.group());
			}
			last = m.end();
		}
		out.append(s, last, s.length());
		return out.toString();
	}

	private static String replaceNumericEntitiesHex(String s)
	{
		Matcher m = HEX_ENTITY.matcher(s);
		StringBuilder out = new StringBuilder();
		int last = 0;
		while (m.find())
		{
			out.append(s, last, m.start());
			try
			{
				out.append(codePointToString(Integer.parseInt(m.group(1), 16)));
			}
			catch (IllegalArgumentException ex)
			{
				out.append(m.group());
			}
			last = m.end();
		}
		out.append(s, last, s.length());
		return out.toString();
	}

	private static String codePointToString(int cp)
	{
		if (cp < 0 || cp > Character.MAX_CODE_POINT)
		{
			throw new IllegalArgumentException("cp");
		}
		return cp < Character.MIN_SUPPLEMENTARY_CODE_POINT
				? String.valueOf((char) cp)
				: new String(Character.toChars(cp));
	}
}

