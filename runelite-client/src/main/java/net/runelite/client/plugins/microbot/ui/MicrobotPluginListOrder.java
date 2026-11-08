package net.runelite.client.plugins.microbot.ui;

import com.google.common.base.Enums;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import net.runelite.client.plugins.config.SearchablePlugin;
import net.runelite.client.plugins.microbot.ui.search.MicrobotPluginNames;
import net.runelite.client.plugins.microbot.ui.search.MicrobotPluginSearch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Decides which installed rows show and in what order. The panel only draws the answer. */
final class MicrobotPluginListOrder
{
    @Getter
    @RequiredArgsConstructor
    enum Sort
    {
        AZ("A-Z"),
        RECENTLY_INSTALLED("Recently installed"),
        RECENTLY_USED("Recently used"),
        AUTHOR("By author");

        private final String label;

        static Sort parse(String saved)
        {
            return saved == null ? AZ : Enums.getIfPresent(Sort.class, saved).or(AZ);
        }
    }

    enum Filter
    {
        ALL, ON, TO_FIX
    }

    enum EmptyState
    {
        NONE, NO_MATCHES, NO_PLUGINS, SAFE_MODE
    }

    interface Rows<T>
    {
        boolean isOn(T row);

        boolean isBuiltIn(T row);

        boolean needsFix(T row);

        /** Newest first under {@code sort}. 0 means unknown and sorts last. */
        long sortTime(T row, Sort sort);
    }

    @Value
    static class Result<T>
    {
        List<T> pinned;
        List<T> normal;
        List<T> builtIn;
        int shown;
        int total;
        int onCount;
        int toFixCount;
    }

    private MicrobotPluginListOrder()
    {
    }

    /** @param kept rows that stay under {@link Filter#ON} after being switched off */
    static <T extends SearchablePlugin> Result<T> order(List<T> rows, String query, Sort sort, Filter filter, Rows<T> access, Set<T> kept)
    {
        Predicate<T> switchedOn = row -> access.isOn(row) && !access.isBuiltIn(row);

        // Search already gives pinned-then-A-Z for a blank query and relevance otherwise.
        List<T> matches = MicrobotPluginSearch.search(rows, query).stream()
            .filter(row -> matchesFilter(row, filter, switchedOn, access, kept))
            .collect(Collectors.toCollection(ArrayList::new));
        if (isBlank(query) && sort == Sort.AUTHOR)
        {
            // Stable, so each author's rows stay A-Z; untagged rows go last.
            Map<T, String> tags = new HashMap<>();
            matches.forEach(row -> tags.put(row, authorKey(row)));
            matches.sort(Comparator.comparing(tags::get));
        }
        else if (isBlank(query))
        {
            // Stable, so equal and unknown times stay in A-Z order.
            matches.sort(Comparator.<T>comparingLong(row -> access.sortTime(row, sort)).reversed());
        }

        List<T> pinned = new ArrayList<>();
        List<T> normal = new ArrayList<>();
        List<T> builtIn = new ArrayList<>();
        for (T row : matches)
        {
            (row.isPinned() ? pinned : access.isBuiltIn(row) ? builtIn : normal).add(row);
        }

        return new Result<>(pinned, normal, builtIn, matches.size(), rows.size(),
            (int) rows.stream().filter(switchedOn).count(),
            (int) rows.stream().filter(access::needsFix).count());
    }

    /** The group a row falls under in {@link Sort#AUTHOR}: its tag in lower case, or a key that sorts after every tag. */
    static String authorKey(SearchablePlugin row)
    {
        String tag = MicrobotPluginNames.authorTag(row.getSearchableName());
        return tag.isEmpty() ? "￿" : tag.toLowerCase(Locale.ROOT);
    }

    private static <T> boolean matchesFilter(T row, Filter filter, Predicate<T> switchedOn, Rows<T> access, Set<T> kept)
    {
        switch (filter)
        {
            case ON:
                return switchedOn.test(row) || kept.contains(row);
            case TO_FIX:
                return access.needsFix(row);
            default:
                return true;
        }
    }

    /** Call with the rows on screen under {@link Filter#ON}, so one that goes off stays put until the filter changes. */
    static <T> void keepSwitchedOff(Set<T> kept, Collection<T> shown, Predicate<T> isOn)
    {
        kept.removeIf(isOn);
        shown.stream().filter(isOn.negate()).forEach(kept::add);
    }

    static boolean isFiltering(String query, Filter filter)
    {
        return !isBlank(query) || filter != Filter.ALL;
    }

    static boolean builtInOpen(boolean savedOpen, String query, Filter filter, boolean builtInFailed)
    {
        return savedOpen || builtInFailed || !isBlank(query) || filter == Filter.TO_FIX;
    }

    static EmptyState emptyState(int shown, boolean filtering, boolean pluginsInstalled, boolean safeMode)
    {
        if (filtering)
        {
            return shown == 0 ? EmptyState.NO_MATCHES : EmptyState.NONE;
        }
        if (pluginsInstalled && shown > 0)
        {
            return EmptyState.NONE;
        }
        return safeMode ? EmptyState.SAFE_MODE : EmptyState.NO_PLUGINS;
    }

    /** The next focus target, or null when moving up past the first one. */
    static <T> T step(List<T> targets, T current, int delta)
    {
        int index = targets.indexOf(current) + delta;
        if (index < 0 || targets.isEmpty())
        {
            return null;
        }
        return targets.get(Math.min(index, targets.size() - 1));
    }

    private static boolean isBlank(String query)
    {
        return query == null || query.trim().isEmpty();
    }
}
