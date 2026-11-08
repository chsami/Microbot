package net.runelite.client.plugins.microbot.ui.search;

import net.runelite.client.plugins.config.SearchablePlugin;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MicrobotPluginSearchTest
{
    private static final String AUTHOR = "<html>[<font color=#b8f704M>M</font>] ";

    private static final class Item implements SearchablePlugin
    {
        final String name;
        final boolean pinned;
        final List<String> keywords;

        Item(String name, boolean pinned, String... keywords)
        {
            this.name = name;
            this.pinned = pinned;
            this.keywords = Arrays.asList(keywords);
        }

        Item(String name, List<String> keywords)
        {
            this.name = name;
            this.pinned = false;
            this.keywords = keywords;
        }

        @Override
        public String getSearchableName()
        {
            return name;
        }

        @Override
        public List<String> getKeywords()
        {
            return keywords;
        }

        @Override
        public boolean isPinned()
        {
            return pinned;
        }
    }

    private static List<String> search(String query, Item... items)
    {
        return MicrobotPluginSearch.search(Arrays.asList(items), query).stream()
            .map(item -> item.name)
            .collect(Collectors.toList());
    }

    @Test
    public void blankQuerySortsPinnedFirstThenCleanNameAToZ()
    {
        Item agility = new Item(AUTHOR + "Agility", false);
        Item fisher = new Item(" Aerial Fisher", false);
        Item magic = new Item("AIO Magic", false);
        Item fighter = new Item("aio fighter", false);
        Item pinned = new Item("Zulrah", true);

        assertEquals(Arrays.asList("Zulrah", " Aerial Fisher", AUTHOR + "Agility", "aio fighter", "AIO Magic"),
            search("  ", agility, fisher, magic, fighter, pinned));
        assertEquals(5, search(null, agility, fisher, magic, fighter, pinned).size());
    }

    @Test
    public void markupNeverMatches()
    {
        Item agility = new Item(AUTHOR + "Agility", false, "<b>rooftop</b>");

        assertTrue(search("font", agility).isEmpty());
        assertTrue(search("color", agility).isEmpty());
        assertTrue(search("co", agility).isEmpty());
        assertEquals(1, search("rooftop", agility).size());
    }

    @Test
    public void everyTokenMustMatch()
    {
        Item magic = new Item("AIO Magic", false, "alching");
        Item fighter = new Item("AIO Fighter", false);

        assertEquals(Collections.singletonList("AIO Magic"), search("aio magic", magic, fighter));
        assertEquals(Collections.singletonList("AIO Magic"), search("  alch   aio ", magic, fighter));
        assertTrue(search("aio cooking", magic, fighter).isEmpty());
    }

    @Test
    public void ranksNamePrefixThenNameWordThenKeyword()
    {
        Item keyword = new Item("Alpha", false, "mining");
        Item word = new Item("Gold Miner", false);
        Item prefix = new Item("Motherlode", false);
        Item prefixMatch = new Item("Mining Guild", false);

        assertEquals(Arrays.asList("Mining Guild", "Gold Miner", "Alpha"), search("min", keyword, word, prefix, prefixMatch));
    }

    @Test
    public void pinsBreakRankTiesButDoNotBeatRank()
    {
        Item plainPrefix = new Item("Miner", false);
        Item pinnedPrefix = new Item("Mining", true);
        Item pinnedWord = new Item("Gold Mine", true);

        assertEquals(Arrays.asList("Mining", "Miner", "Gold Mine"), search("min", plainPrefix, pinnedPrefix, pinnedWord));
    }

    @Test
    public void bracketTextInKeywordsStaysSearchable()
    {
        Item tagged = new Item("Alpha", false, "[beta]");
        Item described = new Item("Bravo", false, "[BETA]", "Fights", "giants");

        assertEquals(Arrays.asList("Alpha", "Bravo"), search("beta", tagged, described));
    }

    @Test
    public void eachRowIsCleanedOncePerSearch()
    {
        AtomicInteger nameReads = new AtomicInteger();
        AtomicInteger keywordReads = new AtomicInteger();
        List<SearchablePlugin> rows = new ArrayList<>();
        for (String name : Arrays.asList("Miner", "Mining", "Gold Mine", "Alpha"))
        {
            rows.add(new SearchablePlugin()
            {
                @Override
                public String getSearchableName()
                {
                    nameReads.incrementAndGet();
                    return name;
                }

                @Override
                public List<String> getKeywords()
                {
                    keywordReads.incrementAndGet();
                    return Collections.singletonList("mine");
                }
            });
        }

        assertEquals(4, MicrobotPluginSearch.search(rows, "min").size());
        assertEquals(4, nameReads.get());
        assertEquals(4, keywordReads.get());
    }

    @Test
    public void unusualNamesAndKeywordsDoNotThrow()
    {
        Item nullKeywords = new Item("Null Keywords", (List<String>) null);
        Item nullKeyword = new Item("Null Keyword", Arrays.asList("x", null));
        Item nullName = new Item(null, Collections.singletonList("nameless"));
        Item empty = new Item("", false);
        Item tagOnly = new Item("<html>[<font color=green>D</font>] ", false);
        Item entity = new Item("Fish &amp; Chips", false);

        assertEquals(6, search("", nullKeywords, nullKeyword, nullName, empty, tagOnly, entity).size());
        assertEquals(2, search("null", nullKeywords, nullKeyword, nullName, empty, tagOnly, entity).size());
        assertEquals(Collections.singletonList("Fish &amp; Chips"), search("&", nullKeywords, nullKeyword, nullName, empty, tagOnly, entity));
        assertTrue(search("amp", entity).isEmpty());
    }
}
