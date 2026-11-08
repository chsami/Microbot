package net.runelite.client.plugins.microbot.ui;

import com.google.common.base.Splitter;
import net.runelite.client.plugins.config.SearchablePlugin;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManifest;
import net.runelite.client.plugins.microbot.ui.search.MicrobotPluginNames;
import net.runelite.client.plugins.microbot.ui.search.MicrobotPluginSearch;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class MicrobotPluginHubOrder
{
    static final String NEWLY_ADDED_FILTER_QUERY = "New";
    private static final Splitter WORDS = Splitter.on(' ').omitEmptyStrings();

    private MicrobotPluginHubOrder()
    {
    }

    static boolean isNewlyAddedFilterQuery(String query)
    {
        String trimmed = query == null ? "" : query.trim();
        return NEWLY_ADDED_FILTER_QUERY.equalsIgnoreCase(trimmed)
            || "Newly Added".equalsIgnoreCase(trimmed);
    }

    static <T extends SearchablePlugin> List<T> order(
        Collection<T> plugins,
        String query,
        Function<T, MicrobotPluginManifest> manifest,
        Predicate<T> installed,
        ToIntFunction<T> userCount)
    {
        if (isNewlyAddedFilterQuery(query))
        {
            return plugins.stream()
                .filter(plugin -> manifest.apply(plugin).isNewlyAdded())
                .sorted(newestFirst(manifest))
                .collect(Collectors.toList());
        }

        if (query != null && !query.trim().isEmpty())
        {
            return MicrobotPluginSearch.search(plugins, query);
        }

        return plugins.stream()
            .sorted(popularity(manifest, installed, userCount))
            .collect(Collectors.toList());
    }

    /** Search words for a manifest. Hub manifests may leave out authors, tags or the description. */
    static List<String> keywords(MicrobotPluginManifest manifest)
    {
        List<String> keywords = new ArrayList<>();
        WORDS.split(MicrobotPluginNames.plainName(manifest.getDisplayName())).forEach(keywords::add);
        WORDS.split(MicrobotPluginNames.searchText(manifest.getDescription())).forEach(keywords::add);
        Stream.of(manifest.getAuthors(), manifest.getTags())
            .filter(Objects::nonNull)
            .flatMap(Arrays::stream)
            .filter(Objects::nonNull)
            .forEach(keywords::add);
        return keywords;
    }

    /** Hub plugins worth offering for {@code query}: not installed, not disabled, not health-blocked. */
    static List<MicrobotPluginManifest> hubMatches(Collection<MicrobotPluginManifest> manifests, String query,
        Predicate<String> installed, Predicate<MicrobotPluginManifest> blocked, int limit)
    {
        if (query == null || query.trim().isEmpty())
        {
            return Collections.emptyList();
        }
        List<HubMatch> offered = manifests.stream()
            .filter(m -> m.getInternalName() != null && !m.isDisable() && !installed.test(m.getInternalName()) && !blocked.test(m))
            .map(HubMatch::new)
            .collect(Collectors.toList());
        return MicrobotPluginSearch.search(offered, query).stream()
            .limit(limit)
            .map(match -> match.manifest)
            .collect(Collectors.toList());
    }

    private static final class HubMatch implements SearchablePlugin
    {
        private final MicrobotPluginManifest manifest;
        private final List<String> keywords;

        HubMatch(MicrobotPluginManifest manifest)
        {
            this.manifest = manifest;
            this.keywords = keywords(manifest);
        }

        @Override
        public String getSearchableName()
        {
            return manifest.getDisplayName();
        }

        @Override
        public List<String> getKeywords()
        {
            return keywords;
        }
    }

    static <T> Comparator<T> newestFirst(Function<T, MicrobotPluginManifest> manifest)
    {
        return Comparator.<T, Instant>comparing(
                plugin -> manifest.apply(plugin).getAddedInstant().orElse(null),
                Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(
                plugin -> manifest.apply(plugin).getInternalName(),
                Comparator.nullsLast(Comparator.naturalOrder()));
    }

    static <T> Comparator<T> popularity(
        Function<T, MicrobotPluginManifest> manifest,
        Predicate<T> installed,
        ToIntFunction<T> userCount)
    {
        return Comparator.<T, Boolean>comparing(installed::test)
            .thenComparingInt(userCount)
            .reversed()
            .thenComparing(plugin -> manifest.apply(plugin).getInternalName());
    }
}
