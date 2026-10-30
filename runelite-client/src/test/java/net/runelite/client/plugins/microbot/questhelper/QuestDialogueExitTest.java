package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/** A Porcine of Interest, Spria's optional info menu after the step had moved on: the fallback looped on two options. */
public class QuestDialogueExitTest
{
	private static final List<String> SPRIA = Arrays.asList(
		"Why are you helping me?",
		"How did I end up back in Draynor Village?",
		"You said you were a slayer master. What's that?",
		"Can I have another pair of Reinforced Goggles?",
		"That'll be all for now.");
	private static final List<String> STEP = Collections.singletonList(
		"Climb down into the Strange Hole east of Draynor Manor.");
	private static final List<String> QUEST = Collections.singletonList("A Porcine of Interest");
	private static final List<String> NONE = Collections.emptyList();

	private static String pick(List<String> options, Set<String> chosen, boolean preferExit, List<String> missing)
	{
		return QuestDialogueRules.pickOptionOrExit(options, STEP, QUEST, missing, 0, null, chosen, preferExit);
	}

	@Test
	public void onlyPoliteLinesAreExits()
	{
		assertTrue(QuestDialogueRules.isExitOption("That'll be all for now."));
		assertTrue(QuestDialogueRules.isExitOption("That's all."));
		assertTrue(QuestDialogueRules.isExitOption("Goodbye."));
		assertTrue(QuestDialogueRules.isExitOption("Bye"));
		assertTrue(QuestDialogueRules.isExitOption("I'll be going."));
		assertTrue(QuestDialogueRules.isExitOption("Never mind."));
		assertFalse("a refusal answers a question, it does not leave", QuestDialogueRules.isExitOption("No."));
		assertFalse(QuestDialogueRules.isExitOption("Nothing"));
		assertFalse(QuestDialogueRules.isExitOption("Cancel"));
		assertFalse(QuestDialogueRules.isExitOption("No thanks."));
		assertFalse(QuestDialogueRules.isExitOption("How do I get there for now?"));
		assertEquals("That'll be all for now.", QuestDialogueRules.firstExitOption(SPRIA));
		assertNull(QuestDialogueRules.firstExitOption(Arrays.asList("Yes", "No.", "Nothing", "Cancel")));
	}

	@Test
	public void theSpriaTranscriptAsksEachInformationalOptionOnceThenExits()
	{
		Set<String> chosen = new HashSet<>();
		String last = null;
		for (int guard = 0; guard < 10; guard++)
		{
			String option = pick(SPRIA, chosen, false, NONE);
			if (QuestDialogueRules.isExitOption(option))
			{
				last = option;
				break;
			}
			assertTrue("repeated " + option, chosen.add(QuestDialogueRules.normalize(option)));
		}
		assertEquals("That'll be all for now.", last);
		assertEquals(3, chosen.size());
		assertFalse(chosen.contains(QuestDialogueRules.normalize("Can I have another pair of Reinforced Goggles?")));
	}

	@Test
	public void aTalkStepLeavesAtOnceOnceSomethingWasAsked()
	{
		Set<String> chosen = new HashSet<>();
		chosen.add(QuestDialogueRules.normalize("Why are you helping me?"));
		assertEquals("That'll be all for now.", pick(SPRIA, chosen, true, NONE));
	}

	@Test
	public void aYesNoMenuOnAnyStepIsAnsweredYesEvenRepeatedly()
	{
		List<String> yesNo = Arrays.asList("Yes", "No");
		Set<String> chosen = new HashSet<>();
		assertEquals("Yes", pick(yesNo, chosen, false, NONE));
		assertTrue(QuestDialogueRules.isConfirmOption("Yes"));
		assertEquals("a confirmation may be chosen again", "Yes", pick(yesNo, chosen, false, NONE));
	}

	@Test
	public void aQuestStartMenuIsNotTreatedAsAnExit()
	{
		List<String> start = Arrays.asList("Yes.", "No.");
		assertNull(QuestDialogueRules.firstExitOption(start));
		assertEquals("Yes.", pick(start, new HashSet<>(), true, NONE));
	}

	@Test
	public void withoutAPoliteExitAnExhaustedMenuIsNotLeftByARefusal()
	{
		List<String> options = Arrays.asList("Why are you helping me?", "Nothing", "No.");
		Set<String> chosen = new HashSet<>();
		chosen.add(QuestDialogueRules.normalize("Why are you helping me?"));
		assertNull(QuestDialogueRules.pickOptionOrExit(options, STEP, QUEST, NONE, 5, null, chosen, false));
	}

	@Test
	public void aReplacementItemRequestIsOnlyUnlockedByAMissingItem()
	{
		assertFalse(QuestDialogueRules.rankOptions(SPRIA, STEP).contains("Can I have another pair of Reinforced Goggles?"));
		// naming the item in the step text is not enough
		List<String> wordsInStep = Arrays.asList("Climb down.", "Reinforced goggles");
		assertFalse(QuestDialogueRules.rankOptions(SPRIA, wordsInStep).contains("Can I have another pair of Reinforced Goggles?"));
		assertTrue(QuestDialogueRules.rankOptions(SPRIA, STEP, Collections.emptyList(),
			Collections.singletonList("Reinforced goggles"), null).contains("Can I have another pair of Reinforced Goggles?"));
	}

	@Test
	public void aMissingItemNeverUnlocksARiskyOption()
	{
		List<String> shop = Arrays.asList("Can I buy some goggles?", "Why are you helping me?");
		List<String> ranked = QuestDialogueRules.rankOptions(shop, STEP, Collections.emptyList(),
			Collections.singletonList("Reinforced goggles"), null);
		assertEquals(Collections.singletonList("Why are you helping me?"), ranked);
	}
}
