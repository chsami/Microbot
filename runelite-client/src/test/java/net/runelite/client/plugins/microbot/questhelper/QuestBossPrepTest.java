package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class QuestBossPrepTest
{
	@Test
	public void theBossLevelIsReadFromAPrepareStep()
	{
		assertEquals(83, QuestBossPrep.bossLevel(List.of(
			"Prepare to fight Elvarg (level 83). When you're ready, go board your boat in Port Sarim and talk to Ned.")));
		assertEquals(83, QuestBossPrep.bossLevel(List.of("Prepare to fight Elvarg again (level 83).")));
	}

	@Test
	public void otherStepsAreNotBossPrep()
	{
		assertEquals(-1, QuestBossPrep.bossLevel(List.of("Kill the lesser demon (level 82).")));
		assertEquals(-1, QuestBossPrep.bossLevel(List.of("Talk to Ned.")));
		assertEquals(-1, QuestBossPrep.bossLevel(null));
	}

	@Test
	public void foodScalesWithTheBossAndThePlayer()
	{
		assertEquals(15, QuestBossPrep.foodWanted(83, 46));
		assertEquals(12, QuestBossPrep.foodWanted(83, 90));
		assertEquals(10, QuestBossPrep.foodWanted(60, 40));
		assertTrue(QuestBossPrep.foodWanted(20, 99) >= 5);
	}
}
