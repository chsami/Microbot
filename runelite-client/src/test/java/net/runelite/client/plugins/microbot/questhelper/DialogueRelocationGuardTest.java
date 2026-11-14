package net.runelite.client.plugins.microbot.questhelper;

import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DialogueRelocationGuardTest
{
	private static final WorldPoint TAMAYU = new WorldPoint(2845, 3041, 0);
	private static final WorldPoint BESIDE_TAMAYU = new WorldPoint(2840, 3039, 0);
	private static final WorldPoint HUNT_CUTSCENE = new WorldPoint(2520, 4568, 0);
	private static final long START = 1_000_000L;

	private DialogueRelocationGuard guard;

	@Before
	public void setUp()
	{
		guard = new DialogueRelocationGuard();
	}

	@Test
	public void holdsWhenTheGameMovesThePlayerAwayFromTheStepAfterADialogue()
	{
		guard.noteDialogue(BESIDE_TAMAYU, START);

		assertTrue(guard.shouldHold(HUNT_CUTSCENE, TAMAYU, START + 5_000));
		assertTrue(guard.shouldHold(HUNT_CUTSCENE, TAMAYU, START + 6_000));
	}

	@Test
	public void releasesWhenTheGameMovesThePlayerBack()
	{
		guard.noteDialogue(BESIDE_TAMAYU, START);
		assertTrue(guard.shouldHold(HUNT_CUTSCENE, TAMAYU, START + 5_000));

		guard.noteDialogue(BESIDE_TAMAYU, START + 15_000);

		assertFalse(guard.shouldHold(BESIDE_TAMAYU, TAMAYU, START + 20_000));
	}

	@Test
	public void releasesAfterTheHoldLimitSoARealTripCanContinue()
	{
		guard.noteDialogue(BESIDE_TAMAYU, START);
		long heldFrom = START + 5_000;
		assertTrue(guard.shouldHold(HUNT_CUTSCENE, TAMAYU, heldFrom));

		assertTrue(guard.shouldHold(HUNT_CUTSCENE, TAMAYU, heldFrom + DialogueRelocationGuard.MAX_HOLD_MS - 1));
		assertFalse(guard.shouldHold(HUNT_CUTSCENE, TAMAYU, heldFrom + DialogueRelocationGuard.MAX_HOLD_MS));
		assertFalse(guard.shouldHold(HUNT_CUTSCENE, TAMAYU, heldFrom + DialogueRelocationGuard.MAX_HOLD_MS + 600));
	}

	@Test
	public void dialogueDuringTheCutsceneKeepsTheHoldAlive()
	{
		guard.noteDialogue(BESIDE_TAMAYU, START);
		long heldFrom = START + 5_000;
		assertTrue(guard.shouldHold(HUNT_CUTSCENE, TAMAYU, heldFrom));

		long spokenAt = heldFrom + DialogueRelocationGuard.MAX_HOLD_MS - 1_000;
		guard.noteDialogue(HUNT_CUTSCENE, spokenAt);

		assertTrue(guard.shouldHold(HUNT_CUTSCENE, TAMAYU, heldFrom + DialogueRelocationGuard.MAX_HOLD_MS + 5_000));
		assertFalse(guard.shouldHold(HUNT_CUTSCENE, TAMAYU, spokenAt + DialogueRelocationGuard.MAX_HOLD_MS));
	}

	@Test
	public void doesNotHoldWhenTheDialogueLeavesThePlayerInPlace()
	{
		guard.noteDialogue(BESIDE_TAMAYU, START);

		assertFalse(guard.shouldHold(BESIDE_TAMAYU, TAMAYU, START + 5_000));
	}

	@Test
	public void doesNotHoldWhenTheDialogueTravelLandsNearTheStep()
	{
		guard.noteDialogue(HUNT_CUTSCENE, START);

		assertFalse(guard.shouldHold(BESIDE_TAMAYU, TAMAYU, START + 5_000));
	}

	@Test
	public void doesNotHoldAWalkThatStartsWithoutADialogue()
	{
		assertFalse(guard.shouldHold(HUNT_CUTSCENE, TAMAYU, START));
	}

	@Test
	public void aLaterWalkIsNotMistakenForARelocation()
	{
		guard.noteDialogue(BESIDE_TAMAYU, START);
		assertFalse(guard.shouldHold(BESIDE_TAMAYU, HUNT_CUTSCENE, START + 5_000));

		assertFalse(guard.shouldHold(new WorldPoint(2700, 3200, 0), HUNT_CUTSCENE, START + 60_000));
	}

	@Test
	public void anOldDialogueIsNotUsedAsTheStartingPoint()
	{
		guard.noteDialogue(BESIDE_TAMAYU, START);

		assertFalse(guard.shouldHold(HUNT_CUTSCENE, TAMAYU, START + DialogueRelocationGuard.ANCHOR_TTL_MS + 1));
	}

	@Test
	public void doesNotHoldAStepWithoutALocation()
	{
		guard.noteDialogue(BESIDE_TAMAYU, START);

		assertFalse(guard.shouldHold(HUNT_CUTSCENE, null, START + 5_000));
	}

	@Test
	public void resetDropsTheHold()
	{
		guard.noteDialogue(BESIDE_TAMAYU, START);
		assertTrue(guard.shouldHold(HUNT_CUTSCENE, TAMAYU, START + 5_000));

		guard.reset();

		assertFalse(guard.shouldHold(HUNT_CUTSCENE, TAMAYU, START + 6_000));
	}
}
