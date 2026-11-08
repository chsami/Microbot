package net.runelite.client.plugins.microbot.ui.search;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MicrobotPluginNamesTest
{
    @Test
    public void plainNameRemovesMarkupAndAuthorTag()
    {
        assertEquals("Agility", MicrobotPluginNames.plainName("<html>[<font color=#b8f704M>M</font>] Agility"));
        assertEquals("Mining", MicrobotPluginNames.plainName("<html>[<font color=#FFD700><b>B</b></font>] Mining</html>"));
    }

    @Test
    public void authorTagReadsOnlyTheLeadingTag()
    {
        assertEquals("irk", MicrobotPluginNames.authorTag("[irk] Herbi Hunter"));
        assertEquals("D", MicrobotPluginNames.authorTag("<html>[<font color=green>D</font>] BreakHandler"));
        assertEquals("", MicrobotPluginNames.authorTag("Miner [Beta]"));
        assertEquals("", MicrobotPluginNames.authorTag(null));
    }

    @Test
    public void plainNameTrimsOuterSpaces()
    {
        assertEquals("Aerial Fisher", MicrobotPluginNames.plainName(" Aerial Fisher "));
    }

    @Test
    public void plainNameRemovesOnlyOneLeadingAuthorTag()
    {
        assertEquals("[Beta] Miner", MicrobotPluginNames.plainName("[M] [Beta] Miner"));
        assertEquals("Miner [Beta]", MicrobotPluginNames.plainName("Miner [Beta]"));
    }

    @Test
    public void plainNameDecodesEntities()
    {
        assertEquals("Fish & Chips", MicrobotPluginNames.plainName("Fish &amp; Chips"));
    }

    @Test
    public void plainNameOfNothingIsEmpty()
    {
        assertEquals("", MicrobotPluginNames.plainName(null));
        assertEquals("", MicrobotPluginNames.plainName(""));
        assertEquals("", MicrobotPluginNames.plainName("<html>[<font color=green>D</font>] "));
    }

    @Test
    public void nonBreakingSpaceCountsAsWhitespace()
    {
        assertEquals("Foo", MicrobotPluginNames.plainName("&nbsp;[M] Foo"));
        assertEquals("Foo", MicrobotPluginNames.plainName(" [M] Foo "));
    }

    @Test
    public void searchTextRemovesMarkupButKeepsBracketText()
    {
        assertEquals("[beta] fights giants", MicrobotPluginNames.searchText("<html>[BETA] <b>Fights</b>&nbsp;giants</html>"));
        assertEquals("[beta]", MicrobotPluginNames.searchText("[beta]"));
        assertEquals("", MicrobotPluginNames.searchText(null));
    }

    @Test
    public void sortKeyIsLowercasePlainName()
    {
        assertEquals("aio magic", MicrobotPluginNames.sortKey("<html>[<font color=green>D</font>] AIO Magic"));
        assertEquals("", MicrobotPluginNames.sortKey(null));
    }
}
