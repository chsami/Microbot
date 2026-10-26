package net.runelite.client.plugins.microbot.util.dialogues;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Pure helpers for the quest-start confirmation menu OSRS shows when you start a quest
 * (chat menu in widget group 219, child 1: title "Start Doric's Quest?", then "Yes." / "No.").
 * No client access here so it can be unit tested.
 */
public final class QuestStartMenu {
    public static final Pattern TITLE = Pattern.compile("^Start .+[?]$");

    private QuestStartMenu() {
    }

    /** Strips colour/other tags and surrounding whitespace. */
    public static String clean(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("<[^>]*>", "").replace((char) 160, ' ').trim();
    }

    public static boolean isTitle(String raw) {
        String t = clean(raw);
        return !t.isEmpty() && TITLE.matcher(t).matches();
    }

    /**
     * Option texts that follow the title: everything after index 0, cleaned, blanks dropped.
     * Order is preserved so list position + 1 is the option number.
     */
    public static List<String> parseOptions(List<String> childTexts) {
        List<String> out = new ArrayList<>();
        if (childTexts == null) return out;
        for (int i = 1; i < childTexts.size(); i++) {
            String t = clean(childTexts.get(i));
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /** Index of the affirmative option ("Yes", "Yes."), or -1. */
    public static int yesIndex(List<String> options) {
        for (int i = 0; i < options.size(); i++) {
            String o = clean(options.get(i)).toLowerCase();
            if (o.startsWith("yes")) return i;
        }
        return -1;
    }
}
