package net.runelite.client.plugins.microbot.questhelper;

import net.runelite.client.plugins.microbot.questhelper.logic.PiratesTreasure;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PiratesTreasureCrateTest
{
	private static final List<String> STEP = Collections.singletonList(
		"Right-click fill the rest of the crate with bananas, then talk to Luthas.");

	@Test
	public void rightClickVerbIsParsed()
	{
		assertEquals("Fill", QuestObjectActions.rightClickAction(STEP));
		assertNull(QuestObjectActions.rightClickAction(Collections.singletonList("Open the chest.")));
	}

	@Test
	public void crateUsesFillEvenWhenOtherActionsComeFirst()
	{
		assertEquals("Fill", QuestObjectActions.choose(STEP, new String[]{"Search", null, "Fill", "Examine"}));
	}

	@Test
	public void noNamedActionFallsBackToDefault()
	{
		assertEquals("Search", QuestObjectActions.choose(Collections.singletonList("Do the thing."),
			new String[]{null, "Search", "Fill"}));
	}

	@Test
	public void actionLookupIgnoresCase()
	{
		assertTrue(QuestObjectActions.hasAction(new String[]{"fill"}, "Fill"));
		assertFalse(QuestObjectActions.hasAction(new String[]{"Search"}, "Fill"));
	}

	@Test
	public void tenBananasStopsTheBananaTripWalk()
	{
		assertTrue(PiratesTreasure.needsMoreBananas(9));
		assertFalse(PiratesTreasure.needsMoreBananas(10));
	}

	@Test
	public void commitmentKeepsSubStepUntilTriedThenReleases()
	{
		StepCommitment<String> c = new StepCommitment<>(2);
		String fill = "fill";
		String talk = "talk";
		assertSame(fill, c.select(fill));
		assertSame(fill, c.select(talk));
		c.markTried();
		assertSame(talk, c.select(talk));
		assertSame(talk, c.select(fill));
		assertSame(talk, c.select(fill));
		assertSame(fill, c.select(fill));
	}

	@Test
	public void commitmentGivesUpAfterMaxTicks()
	{
		StepCommitment<String> c = new StepCommitment<>(2);
		assertSame("a", c.select("a"));
		assertSame("a", c.select("b"));
		assertSame("a", c.select("b"));
		assertSame("b", c.select("b"));
	}
}
