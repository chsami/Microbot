package net.runelite.client.plugins.microbot.ui.search;

import net.runelite.client.util.Text;
import org.apache.commons.text.StringEscapeUtils;

import java.util.Locale;
import java.util.regex.Pattern;

public final class MicrobotPluginNames
{
    private static final Pattern AUTHOR_TAG = Pattern.compile("^\\[[^\\]]*\\]");

    private MicrobotPluginNames()
    {
    }

    public static String plainName(String raw)
    {
        return AUTHOR_TAG.matcher(withoutMarkup(raw)).replaceFirst("").trim();
    }

    /** The author tag without its brackets, such as {@code irk} for {@code [irk] Herbi Hunter}; empty when there is none. */
    public static String authorTag(String raw)
    {
        java.util.regex.Matcher tag = AUTHOR_TAG.matcher(withoutMarkup(raw));
        return tag.find() ? tag.group().substring(1, tag.group().length() - 1).trim() : "";
    }

    public static String sortKey(String raw)
    {
        return plainName(raw).toLowerCase(Locale.ROOT);
    }

    /** For keywords and descriptions, where a leading bracket is a word and not an author tag. */
    public static String searchText(String raw)
    {
        return plainText(raw).toLowerCase(Locale.ROOT);
    }

    /** Text to show, such as a description in a tooltip: tags and entities removed, case kept. */
    public static String plainText(String raw)
    {
        return withoutMarkup(raw);
    }

    private static String withoutMarkup(String raw)
    {
        if (raw == null)
        {
            return "";
        }

        return StringEscapeUtils.unescapeHtml4(Text.removeTags(raw)).replace(' ', ' ').trim();
    }
}
