package net.runelite.client.plugins.microbot.ui;

import com.google.common.base.Enums;
import com.google.common.base.Splitter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.plugins.config.SearchablePlugin;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginHealth;
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

    @Getter
    @RequiredArgsConstructor
    enum Sort
    {
        INSTALLED_FIRST("Installed first"),
        NEWEST("Newest"),
        AZ("A-Z");

        private final String label;

        static Sort parse(String saved)
        {
            return saved == null ? INSTALLED_FIRST : Enums.getIfPresent(Sort.class, saved).or(INSTALLED_FIRST);
        }
    }

    @Getter
    @RequiredArgsConstructor
    enum State
    {
        NONE(""),
        INSTALLED("Installed"),
        UPDATE("Update available"),
        // Worded by the health itself: disabled upstream or a too-old client is not "broken".
        BLOCKED("");

        private final String label;

        static State of(boolean installed, boolean blocking, boolean outdated)
        {
            return !installed ? NONE : blocking ? BLOCKED : outdated ? UPDATE : INSTALLED;
        }

        /**
         * What the card says: the health's own label when blocked, plus a pointer to the fix when a working
         * update exists, so a player updates instead of removing the plugin.
         */
        String text(MicrobotPluginHealth health, boolean workingUpdate)
        {
            if (this != BLOCKED)
            {
                return label;
            }
            return workingUpdate ? health.getLabel() + ", update to fix" : health.getLabel();
        }
    }

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
        Sort sort,
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

        Comparator<T> byName = Comparator.<T, String>comparing(plugin -> MicrobotPluginNames.sortKey(manifest.apply(plugin).getDisplayName()))
            .thenComparing(plugin -> manifest.apply(plugin).getInternalName(), Comparator.nullsLast(Comparator.naturalOrder()));
        Comparator<T> comparator = sort == Sort.NEWEST ? newestFirst(manifest)
            : sort == Sort.AZ ? byName
            // Then most used, as before the sort menu; the Hub sends no counts today, so this is A-Z until it does.
            : Comparator.<T, Boolean>comparing(installed::test).reversed()
                .thenComparing(Comparator.comparingInt(userCount).reversed())
                .thenComparing(byName);
        return plugins.stream()
            .sorted(comparator)
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
}
