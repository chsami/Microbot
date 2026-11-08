package net.runelite.client.plugins.microbot.ui;

import net.runelite.client.plugins.config.SearchablePlugin;
import net.runelite.client.plugins.microbot.ui.MicrobotPluginListOrder.EmptyState;
import net.runelite.client.plugins.microbot.ui.MicrobotPluginListOrder.Filter;
import net.runelite.client.plugins.microbot.ui.MicrobotPluginListOrder.Result;
import net.runelite.client.plugins.microbot.ui.MicrobotPluginListOrder.Sort;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MicrobotPluginListOrderTest
{
    private static final class Row implements SearchablePlugin
    {
        final String name;
        boolean pinned;
        boolean on;
        boolean builtIn;
        boolean fix;
        long installed;

        Row(String name)
        {
            this.name = name;
        }

        Row pinned()
        {
            pinned = true;
            return this;
        }

        Row on()
        {
            on = true;
            return this;
        }

        Row builtIn()
        {
            builtIn = true;
            return this;
        }

        Row fix()
        {
            fix = true;
            return this;
        }

        Row installed(long time)
        {
            installed = time;
            return this;
        }

        @Override
        public String getSearchableName()
        {
            return name;
        }

        @Override
        public List<String> getKeywords()
        {
            return null;
        }

        @Override
        public boolean isPinned()
        {
            return pinned;
        }
    }

    private static final MicrobotPluginListOrder.Rows<Row> ACCESS = new MicrobotPluginListOrder.Rows<Row>()
    {
        @Override
        public boolean isOn(Row row)
        {
            return row.on;
        }

        @Override
        public boolean isBuiltIn(Row row)
        {
            return row.builtIn;
        }

        @Override
        public boolean needsFix(Row row)
        {
            return row.fix;
        }

        @Override
        public long sortTime(Row row, Sort sort)
        {
            return sort == Sort.RECENTLY_INSTALLED ? row.installed : 0;
        }
    };

    private static Result<Row> order(String query, Sort sort, Filter filter, Row... rows)
    {
        return MicrobotPluginListOrder.order(Arrays.asList(rows), query, sort, filter, ACCESS, Collections.emptySet());
    }

    private static List<String> names(List<Row> rows)
    {
        return rows.stream().map(row -> row.name).collect(Collectors.toList());
    }

    @Test
    public void byAuthorGroupsByTagKeepsAToZInsideAndPutsUntaggedLast()
    {
        Result<Row> result = order("", Sort.AUTHOR, Filter.ALL,
            new Row("[M] Auto Mining"), new Row("[irk] Account Starter"), new Row("BlastoiseFurnace"),
            new Row("[irk] Herbi Hunter"), new Row("[D] BreakHandler"), new Row("[m] Agility"));

        assertEquals(Arrays.asList("[D] BreakHandler", "[irk] Account Starter", "[irk] Herbi Hunter",
            "[m] Agility", "[M] Auto Mining", "BlastoiseFurnace"), names(result.getNormal()));
    }

    @Test
    public void byAuthorStillRanksByRelevanceWhileSearching()
    {
        Result<Row> result = order("hunter", Sort.AUTHOR, Filter.ALL,
            new Row("[irk] Herbi Hunter"), new Row("[D] Hunter"));

        assertEquals("[D] Hunter", names(result.getNormal()).get(0));
    }

    @Test
    public void groupsPinnedNormalAndBuiltInSeparately()
    {
        Result<Row> result = order("", Sort.AZ, Filter.ALL,
            new Row("Zulrah"), new Row("Agility"), new Row("Woodcutting").pinned(),
            new Row("Antiban").builtIn(), new Row("Web Walker").builtIn().pinned());

        assertEquals(Arrays.asList("Web Walker", "Woodcutting"), names(result.getPinned()));
        assertEquals(Arrays.asList("Agility", "Zulrah"), names(result.getNormal()));
        assertEquals(Collections.singletonList("Antiban"), names(result.getBuiltIn()));
        assertEquals(5, result.getShown());
        assertEquals(5, result.getTotal());
    }

    @Test
    public void azIgnoresTagsAndCase()
    {
        Result<Row> result = order("", Sort.AZ, Filter.ALL,
            new Row("<html>[<font color=#fff>M</font>] Zulrah"), new Row("aio fighter"), new Row(" Bank"));

        assertEquals(Arrays.asList("aio fighter", " Bank", "<html>[<font color=#fff>M</font>] Zulrah"), names(result.getNormal()));
    }

    @Test
    public void recentlyInstalledIsNewestFirstThenUnknownLastInAzOrder()
    {
        Result<Row> result = order("", Sort.RECENTLY_INSTALLED, Filter.ALL,
            new Row("Zulrah"), new Row("Old").installed(100), new Row("Agility"), new Row("New").installed(200));

        assertEquals(Arrays.asList("New", "Old", "Agility", "Zulrah"), names(result.getNormal()));
    }

    @Test
    public void pinsStayOnTopUnderEverySort()
    {
        Row pinned = new Row("Zulrah").pinned();
        Row newest = new Row("Agility").installed(500);

        Result<Row> result = order("", Sort.RECENTLY_INSTALLED, Filter.ALL, newest, pinned);

        assertEquals(Collections.singletonList(pinned), result.getPinned());
        assertEquals(Collections.singletonList(newest), result.getNormal());
    }

    @Test
    public void searchKeepsRelevanceOrderInsideEachGroup()
    {
        Result<Row> result = order("fish", Sort.RECENTLY_INSTALLED, Filter.ALL,
            new Row("Aerial Fisher").installed(900), new Row("Fishing Trawler").installed(1));

        assertEquals(Arrays.asList("Fishing Trawler", "Aerial Fisher"), names(result.getNormal()));
    }

    @Test
    public void onFilterExcludesBuiltInAndCountsOnlyWhatCanBeSwitched()
    {
        Result<Row> result = order("", Sort.AZ, Filter.ON,
            new Row("Agility").on(), new Row("Bank"), new Row("Antiban").on().builtIn());

        assertEquals(Collections.singletonList("Agility"), names(result.getNormal()));
        assertTrue(result.getBuiltIn().isEmpty());
        assertEquals(1, result.getShown());
        assertEquals(3, result.getTotal());
        assertEquals(1, result.getOnCount());
    }

    @Test
    public void pinnedBuiltInIsNotShownUnderOn()
    {
        Result<Row> result = order("", Sort.AZ, Filter.ON, new Row("Antiban").on().builtIn().pinned());

        assertTrue(result.getPinned().isEmpty());
        assertEquals(0, result.getShown());
    }

    @Test
    public void toFixFilterShowsRowsNeedingAttentionIncludingBuiltIn()
    {
        Result<Row> result = order("", Sort.AZ, Filter.TO_FIX,
            new Row("Agility").fix(), new Row("Bank").on(), new Row("Antiban").builtIn().fix());

        assertEquals(Collections.singletonList("Agility"), names(result.getNormal()));
        assertEquals(Collections.singletonList("Antiban"), names(result.getBuiltIn()));
        assertEquals(2, result.getToFixCount());
    }

    @Test
    public void countsStayLiveWhileASearchNarrowsTheList()
    {
        Result<Row> result = order("agil", Sort.AZ, Filter.ALL,
            new Row("Agility").on(), new Row("Bank").on().fix());

        assertEquals(1, result.getShown());
        assertEquals(2, result.getOnCount());
        assertEquals(1, result.getToFixCount());
    }

    @Test
    public void rowKeptUnderOnStaysWhileOff()
    {
        Row off = new Row("Agility");
        Row other = new Row("Bank");

        Result<Row> result = MicrobotPluginListOrder.order(Arrays.asList(off, other), "", Sort.AZ, Filter.ON,
            ACCESS, Collections.singleton(off));

        assertEquals(Collections.singletonList(off), result.getNormal());
        assertEquals(0, result.getOnCount());
    }

    @Test
    public void shownRowThatSwitchesOffIsKeptAndLeavesWhenBackOn()
    {
        Row row = new Row("Agility");
        Set<Row> kept = new HashSet<>();

        MicrobotPluginListOrder.keepSwitchedOff(kept, Collections.singletonList(row), r -> r.on);
        assertEquals(Collections.singleton(row), kept);

        row.on();
        MicrobotPluginListOrder.keepSwitchedOff(kept, Collections.singletonList(row), r -> r.on);
        assertTrue(kept.isEmpty());
    }

    @Test
    public void namesThatAreOnlyMarkupOrEmptyDoNotThrow()
    {
        Result<Row> result = order("", Sort.RECENTLY_INSTALLED, Filter.ALL,
            new Row(""), new Row(null), new Row("<html></html>"), new Row("[M]"));

        assertEquals(4, result.getShown());
    }

    @Test
    public void nullQueryIsBlank()
    {
        assertEquals(1, order(null, Sort.AZ, Filter.ALL, new Row("Agility")).getShown());
        assertFalse(MicrobotPluginListOrder.isFiltering(null, Filter.ALL));
    }

    @Test
    public void filteringMeansSearchOrChip()
    {
        assertFalse(MicrobotPluginListOrder.isFiltering("   ", Filter.ALL));
        assertTrue(MicrobotPluginListOrder.isFiltering("a", Filter.ALL));
        assertTrue(MicrobotPluginListOrder.isFiltering("", Filter.ON));
    }

    @Test
    public void builtInSectionFollowsSavedStateUnlessSearchOrToFixForcesItOpen()
    {
        assertFalse(MicrobotPluginListOrder.builtInOpen(false, "", Filter.ALL, false));
        assertFalse(MicrobotPluginListOrder.builtInOpen(false, "", Filter.ON, false));
        assertTrue(MicrobotPluginListOrder.builtInOpen(true, "", Filter.ALL, false));
        assertTrue(MicrobotPluginListOrder.builtInOpen(false, "anti", Filter.ALL, false));
        assertTrue(MicrobotPluginListOrder.builtInOpen(false, "", Filter.TO_FIX, false));
        assertTrue("A failed built-in opens it", MicrobotPluginListOrder.builtInOpen(false, "", Filter.ALL, true));
    }

    @Test
    public void emptyStates()
    {
        assertEquals(EmptyState.NONE, MicrobotPluginListOrder.emptyState(3, true, true, false));
        assertEquals(EmptyState.NONE, MicrobotPluginListOrder.emptyState(3, false, true, false));
        assertEquals(EmptyState.NO_MATCHES, MicrobotPluginListOrder.emptyState(0, true, true, false));
        assertEquals(EmptyState.NO_MATCHES, MicrobotPluginListOrder.emptyState(0, true, false, true));
        assertEquals(EmptyState.NO_PLUGINS, MicrobotPluginListOrder.emptyState(4, false, false, false));
        assertEquals(EmptyState.NONE, MicrobotPluginListOrder.emptyState(4, true, false, false));
        assertEquals(EmptyState.SAFE_MODE, MicrobotPluginListOrder.emptyState(4, false, false, true));
    }

    @Test
    public void stepMovesThroughTargetsAndStopsAtTheEnd()
    {
        List<String> targets = Arrays.asList("a", "b");

        assertEquals("a", MicrobotPluginListOrder.step(targets, null, 1));
        assertEquals("b", MicrobotPluginListOrder.step(targets, "a", 1));
        assertEquals("b", MicrobotPluginListOrder.step(targets, "b", 1));
        assertEquals("a", MicrobotPluginListOrder.step(targets, "b", -1));
        assertNull(MicrobotPluginListOrder.step(targets, "a", -1));
        assertNull(MicrobotPluginListOrder.step(Collections.<String>emptyList(), null, 1));
    }

    @Test
    public void savedSortParsesAndUnknownFallsBackToAz()
    {
        assertEquals(Sort.RECENTLY_INSTALLED, Sort.parse("RECENTLY_INSTALLED"));
        assertEquals(Sort.AZ, Sort.parse(null));
        assertEquals(Sort.AZ, Sort.parse("nonsense"));
    }
}
