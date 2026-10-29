package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;
import org.junit.Test;

public class QuestKillDropsTest
{
	@Test
	public void theDropIsReadFromAKillForAnItemStep()
	{
		assertEquals("key", QuestKillDrops.dropName(List.of("Kill the marked zombie rat for a key.")));
		assertEquals("magenta key", QuestKillDrops.dropName(List.of("Kill Melzar the Mad for a magenta key.")));
		assertEquals("key", QuestKillDrops.dropName(List.of("Kill the marked skeleton for a key")));
	}

	@Test
	public void stepsThatAreNotAKillForAnItemGiveNothing()
	{
		assertNull(QuestKillDrops.dropName(List.of("Kill the lesser demon. You can safe spot it from the spot east of the door.")));
		assertNull(QuestKillDrops.dropName(List.of("Talk to the Guildmaster in the Champions' Guild.")));
		assertNull(QuestKillDrops.dropName(null));
	}

	@Test
	public void theDropIsFoundMidSentence()
	{
		assertEquals("key", QuestKillDrops.dropName(List.of("Kill the marked ghost for a key. Then go upstairs.")));
	}
}
