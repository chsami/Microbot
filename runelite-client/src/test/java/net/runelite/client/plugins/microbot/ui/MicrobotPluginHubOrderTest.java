package net.runelite.client.plugins.microbot.ui;

import com.google.gson.Gson;
import net.runelite.client.plugins.config.SearchablePlugin;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginHealth;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManifest;
import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MicrobotPluginHubOrderTest
{
    private static final class Item implements SearchablePlugin
    {
        final MicrobotPluginManifest manifest;
        final boolean installed;
        final int users;
        final List<String> keywords = new ArrayList<>();

        Item(String name, String addedAt, boolean installed, int users)
        {
            manifest = new MicrobotPluginManifest();
            manifest.setInternalName(name);
            manifest.setDisplayName(name);
            manifest.setAddedAt(addedAt);
            this.installed = installed;
            this.users = users;
            keywords.add(name);
            if (manifest.isNewlyAdded())
            {
                keywords.add("new");
                keywords.add("newly added");
            }
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

    private static String daysAgo(long days)
    {
        return Instant.now().minus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
    }

    private static List<String> order(List<Item> items, String query)
    {
        return order(items, query, MicrobotPluginHubOrder.Sort.INSTALLED_FIRST);
    }

    private static List<String> order(List<Item> items, String query, MicrobotPluginHubOrder.Sort sort)
    {
        return names(MicrobotPluginHubOrder.order(items, query, sort, i -> i.manifest, i -> i.installed, i -> i.users));
    }

    private static List<String> names(List<Item> items)
    {
        return items.stream().map(i -> i.manifest.getInternalName()).collect(Collectors.toList());
    }

    @Test
    public void newModeSortsByAddedDateDescendingIgnoringInstallAndPopularity()
    {
        List<Item> items = Arrays.asList(
            new Item("Oldest", daysAgo(20), true, 5000),
            new Item("Newest", daysAgo(1), false, 0),
            new Item("Middle", daysAgo(10), false, 900),
            new Item("Legacy", null, true, 9000),
            new Item("Stale", daysAgo(400), true, 8000));

        assertEquals(Arrays.asList("Newest", "Middle", "Oldest"), order(items, "New"));
    }

    @Test
    public void newModeAcceptsAliasesCaseAndWhitespace()
    {
        List<Item> items = Arrays.asList(
            new Item("A", daysAgo(5), false, 1),
            new Item("B", daysAgo(2), false, 100));

        List<String> expected = Arrays.asList("B", "A");
        assertEquals(expected, order(items, "new"));
        assertEquals(expected, order(items, "  NEW "));
        assertEquals(expected, order(items, "Newly Added"));
        assertEquals(expected, order(items, "newly added"));
    }

    @Test
    public void tiesBreakByInternalNameRegardlessOfInputOrder()
    {
        String same = daysAgo(3);
        List<Item> items = new ArrayList<>(Arrays.asList(
            new Item("Charlie", same, true, 10),
            new Item("Alpha", same, false, 0),
            new Item("Bravo", same, false, 999)));

        assertEquals(Arrays.asList("Alpha", "Bravo", "Charlie"), order(items, "New"));
        Collections.reverse(items);
        assertEquals(Arrays.asList("Alpha", "Bravo", "Charlie"), order(items, "New"));
    }

    @Test
    public void comparatorPutsMissingAndUnparseableDatesLastByName()
    {
        List<Item> items = new ArrayList<>(Arrays.asList(
            new Item("Zed", null, true, 100),
            new Item("Bad", "not-a-date", true, 100),
            new Item("Old", "2025-08-01", false, 0),
            new Item("Recent", "2026-09-01T10:00:00Z", false, 0),
            new Item("Blank", "", false, 0)));

        items.sort(MicrobotPluginHubOrder.newestFirst(i -> i.manifest));

        assertEquals(Arrays.asList("Recent", "Old", "Bad", "Blank", "Zed"), names(items));
    }

    @Test
    public void searchQueryKeepsSearchOrderingAndIsNotNewMode()
    {
        List<Item> items = Arrays.asList(
            new Item("MiningB", daysAgo(1), false, 0),
            new Item("MiningA", daysAgo(400), true, 100),
            new Item("Fishing", daysAgo(2), false, 0));

        assertEquals(Arrays.asList("MiningA", "MiningB"), order(items, "mining"));
        assertEquals(Arrays.asList("Fishing", "MiningB"), order(items, "newly"));
        assertFalse(MicrobotPluginHubOrder.isNewlyAddedFilterQuery("new mining"));
        assertTrue(MicrobotPluginHubOrder.isNewlyAddedFilterQuery(" New"));
    }

    // User counts are gone from the default order: getPluginCounts() is an empty stub.
    @Test
    public void defaultSortIsInstalledFirstThenMostUsedThenAToZByCleanName()
    {
        List<Item> items = Arrays.asList(
            new Item("Delta", daysAgo(1), false, 50),
            new Item("Alpha", null, false, 50),
            new Item("Charlie", daysAgo(300), true, 1),
            new Item("Bravo", daysAgo(2), false, 900),
            new Item("Echo", null, true, 70),
            new Item("<html>[<font color=red>A</font>] Zed", null, false, 0));

        List<String> expected = Arrays.asList("Echo", "Charlie", "Bravo", "Alpha", "Delta", "<html>[<font color=red>A</font>] Zed");
        assertEquals(expected, order(items, ""));
        assertEquals(expected, order(items, null));
        assertEquals(expected, order(items, "   "));
    }

    @Test
    public void newestAndAToZSortsIgnoreInstalledState()
    {
        List<Item> items = Arrays.asList(
            new Item("Bravo", daysAgo(1), false, 0),
            new Item("Alpha", daysAgo(9), false, 0),
            new Item("Charlie", null, true, 0));

        assertEquals(Arrays.asList("Bravo", "Alpha", "Charlie"), order(items, "", MicrobotPluginHubOrder.Sort.NEWEST));
        assertEquals(Arrays.asList("Alpha", "Bravo", "Charlie"), order(items, "", MicrobotPluginHubOrder.Sort.AZ));
        assertEquals("A search keeps its own order", Arrays.asList("Charlie"), order(items, "char", MicrobotPluginHubOrder.Sort.NEWEST));
    }

    @Test
    public void savedSortParsesWithInstalledFirstAsDefault()
    {
        assertEquals(MicrobotPluginHubOrder.Sort.INSTALLED_FIRST, MicrobotPluginHubOrder.Sort.parse(null));
        assertEquals(MicrobotPluginHubOrder.Sort.INSTALLED_FIRST, MicrobotPluginHubOrder.Sort.parse("POPULAR"));
        assertEquals(MicrobotPluginHubOrder.Sort.NEWEST, MicrobotPluginHubOrder.Sort.parse("NEWEST"));
        assertEquals(Arrays.asList("Installed first", "Newest", "A-Z"), Arrays.stream(MicrobotPluginHubOrder.Sort.values())
            .map(MicrobotPluginHubOrder.Sort::getLabel).collect(Collectors.toList()));
    }

    private static MicrobotPluginManifest manifest(String internalName, String displayName)
    {
        MicrobotPluginManifest manifest = new MicrobotPluginManifest();
        manifest.setInternalName(internalName);
        manifest.setDisplayName(displayName);
        manifest.setVersion("1.0.0");
        return manifest;
    }

    private static List<String> hubMatches(List<MicrobotPluginManifest> manifests, String query, String... installed)
    {
        return MicrobotPluginHubOrder.hubMatches(manifests, query, Arrays.asList(installed)::contains,
                m -> "Blocked".equals(m.getInternalName()), 5).stream()
            .map(MicrobotPluginManifest::getInternalName).collect(Collectors.toList());
    }

    @Test
    public void hubMatchesOfferOnlyNotInstalledInstallablePluginsRankedBySearch()
    {
        MicrobotPluginManifest disabled = manifest("Disabled", "Zulrah Old");
        disabled.setDisable(true);
        List<MicrobotPluginManifest> manifests = Arrays.asList(
            manifest("Killer", "Big Zulrah Killer"),
            manifest("Helper", "<html>[<font color=red>M</font>] Zulrah Helper"),
            manifest("Mine", "Zulrah Mine"),
            manifest("Blocked", "Zulrah Blocked"),
            disabled,
            manifest("Fisher", "Fisher"));

        assertEquals(Arrays.asList("Helper", "Killer"), hubMatches(manifests, "zul", "Mine"));
        assertTrue(hubMatches(manifests, "").isEmpty());
        assertTrue(hubMatches(manifests, "   ").isEmpty());
        assertTrue(hubMatches(Collections.emptyList(), "zul").isEmpty());
    }

    @Test
    public void hubMatchesStopAtTheLimit()
    {
        List<MicrobotPluginManifest> manifests = new ArrayList<>();
        for (int i = 0; i < 8; i++)
        {
            manifests.add(manifest("P" + i, "Plugin " + i));
        }

        assertEquals(Arrays.asList("P0", "P1", "P2", "P3", "P4"), hubMatches(manifests, "plugin"));
    }

    @Test
    public void manifestWithNullAuthorsTagsAndDescriptionStillSearches()
    {
        MicrobotPluginManifest bare = manifest("Bare", null);
        MicrobotPluginManifest full = manifest("Full", "Full");
        full.setAuthors(new String[]{"Zoe", null});
        full.setTags(new String[]{null, "skilling"});
        full.setDescription("<b>Cuts</b> trees");

        assertTrue(MicrobotPluginHubOrder.keywords(bare).isEmpty());
        assertEquals(Arrays.asList("Full", "cuts", "trees", "Zoe", "skilling"), MicrobotPluginHubOrder.keywords(full));
        assertEquals(Collections.singletonList("Full"), hubMatches(Arrays.asList(bare, full), "zoe"));
        assertEquals(Collections.singletonList("Full"), hubMatches(Arrays.asList(bare, full), "skilling"));
    }

    @Test
    public void rowStateInWords()
    {
        assertEquals(MicrobotPluginHubOrder.State.NONE, MicrobotPluginHubOrder.State.of(false, true, true));
        assertEquals(MicrobotPluginHubOrder.State.BLOCKED, MicrobotPluginHubOrder.State.of(true, true, true));
        assertEquals(MicrobotPluginHubOrder.State.UPDATE, MicrobotPluginHubOrder.State.of(true, false, true));
        assertEquals(MicrobotPluginHubOrder.State.INSTALLED, MicrobotPluginHubOrder.State.of(true, false, false));
        assertEquals(Arrays.asList("", "Installed", "Update available", ""), Arrays.stream(MicrobotPluginHubOrder.State.values())
            .map(MicrobotPluginHubOrder.State::getLabel).collect(Collectors.toList()));
        MicrobotPluginManifest disabled = new MicrobotPluginManifest();
        disabled.setInternalName("Old");
        disabled.setVersion("1.0.0");
        disabled.setDisable(true);
        assertEquals("A blocked card says why, not always broken", MicrobotPluginHealth.State.DISABLED.getLabel(),
            MicrobotPluginHubOrder.State.BLOCKED.text(MicrobotPluginHealth.evaluate(disabled, "1.0.0", true), false));
        assertEquals("A working update is the fix, so the card says so", MicrobotPluginHealth.State.DISABLED.getLabel() + ", update to fix",
            MicrobotPluginHubOrder.State.BLOCKED.text(MicrobotPluginHealth.evaluate(disabled, "1.0.0", true), true));
        assertEquals("Only a blocked card adds it", "Update available",
            MicrobotPluginHubOrder.State.UPDATE.text(MicrobotPluginHealth.evaluate(disabled, "1.0.0", true), true));
    }

    @Test
    public void legacyManifestWithoutAddedAtParsesAndSortsLast()
    {
        MicrobotPluginManifest[] parsed = new Gson().fromJson(
            "[{\"internalName\":\"LegacyPlugin\",\"name\":\"Legacy\",\"version\":\"1.0.0\"},"
                + "{\"internalName\":\"FreshPlugin\",\"name\":\"Fresh\",\"version\":\"1.0.0\",\"addedAt\":\"" + daysAgo(1) + "\"}]",
            MicrobotPluginManifest[].class);

        assertFalse(parsed[0].getAddedInstant().isPresent());
        assertTrue(parsed[1].isNewlyAdded());

        List<MicrobotPluginManifest> manifests = Arrays.asList(parsed);
        List<MicrobotPluginManifest> sorted = new ArrayList<>(manifests);
        sorted.sort(MicrobotPluginHubOrder.newestFirst(m -> m));
        assertEquals("FreshPlugin", sorted.get(0).getInternalName());
        assertEquals("LegacyPlugin", sorted.get(1).getInternalName());
    }
}
