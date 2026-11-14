package net.runelite.client.plugins.microbot.questhelper;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QuestScriptDialogueRelocationTest
{
	private static final WorldPoint TAMAYU = new WorldPoint(2845, 3041, 0);
	private static final WorldPoint BESIDE_TAMAYU = new WorldPoint(2840, 3039, 0);
	private static final WorldPoint HUNT_CUTSCENE = new WorldPoint(2520, 4568, 0);
	private static final long START = 1_000_000L;

	private String previousStatus;

	@Before
	public void setUp()
	{
		previousStatus = Microbot.status;
		Microbot.status = "";
	}

	@After
	public void tearDown()
	{
		Microbot.status = previousStatus;
	}

	@Test
	public void holdsAndExplainsTheWaitWhenTheGameMovesThePlayerAfterADialogue()
	{
		QuestScript script = new QuestScript();
		script.noteDialogueAt(BESIDE_TAMAYU, START);

		assertTrue(script.holdForDialogueRelocation(HUNT_CUTSCENE, TAMAYU, START + 5_000));
		assertEquals(QuestScript.RELOCATION_WAIT_STATUS, Microbot.status);
	}

	@Test
	public void statusNamesTheWaitLimitAndThatItContinuesByItself()
	{
		assertEquals("Quest helper: the game moved you, waiting up to 20 seconds in case a cutscene is playing, then continuing",
			QuestScript.RELOCATION_WAIT_STATUS);
	}

	@Test
	public void statusIsReplacedWhenThePlayerIsMovedBack()
	{
		QuestScript script = new QuestScript();
		script.noteDialogueAt(BESIDE_TAMAYU, START);
		assertTrue(script.holdForDialogueRelocation(HUNT_CUTSCENE, TAMAYU, START + 5_000));

		assertFalse(script.holdForDialogueRelocation(BESIDE_TAMAYU, TAMAYU, START + 20_000));
		assertEquals("Quest helper: continuing", Microbot.status);
	}

	@Test
	public void statusIsReplacedWhenTheWaitLimitPasses()
	{
		QuestScript script = new QuestScript();
		script.noteDialogueAt(BESIDE_TAMAYU, START);
		long heldFrom = START + 5_000;
		assertTrue(script.holdForDialogueRelocation(HUNT_CUTSCENE, TAMAYU, heldFrom));

		assertFalse(script.holdForDialogueRelocation(HUNT_CUTSCENE, TAMAYU, heldFrom + DialogueRelocationGuard.MAX_HOLD_MS));
		assertEquals("Quest helper: continuing", Microbot.status);
	}

	@Test
	public void anotherStatusIsLeftAloneWhenNothingIsHeld()
	{
		QuestScript script = new QuestScript();
		Microbot.status = "Quest helper: walking to bank for missing items";

		assertFalse(script.holdForDialogueRelocation(BESIDE_TAMAYU, TAMAYU, START));
		assertEquals("Quest helper: walking to bank for missing items", Microbot.status);
	}

	@Test
	public void stoppingTheScriptDoesNotLeaveTheWaitStatusBehind()
	{
		QuestScript script = new QuestScript();
		script.noteDialogueAt(BESIDE_TAMAYU, START);
		assertTrue(script.holdForDialogueRelocation(HUNT_CUTSCENE, TAMAYU, START + 5_000));

		script.stopLoop();

		assertEquals("Quest helper: continuing", Microbot.status);
		assertFalse(script.holdForDialogueRelocation(HUNT_CUTSCENE, TAMAYU, START + 6_000));
	}
}
