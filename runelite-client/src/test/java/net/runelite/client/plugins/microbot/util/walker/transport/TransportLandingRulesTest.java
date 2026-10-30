package net.runelite.client.plugins.microbot.util.walker.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TransportLandingRulesTest
{
	@Test
	public void aDialogueKeepsTheWaitGoingUntilTheBudgetEnds()
	{
		assertTrue(TransportLandingRules.keepWaiting(true, false, false, 6_000));
		assertFalse(TransportLandingRules.keepWaiting(true, false, false, 15_000));
		assertFalse(TransportLandingRules.keepWaiting(false, false, false, 1_000));
	}

	@Test
	public void movingOrAnimatingAlsoKeepsWaiting()
	{
		assertTrue(TransportLandingRules.keepWaiting(false, true, false, 100));
		assertTrue(TransportLandingRules.keepWaiting(false, false, true, 100));
	}

	@Test
	public void interactionWithActivityIsNotARefusal()
	{
		assertEquals(TransportDispatchTrace.Verdict.INTERACTION_LEFT_ORIGIN,
			TransportDispatchTrace.classify(false, true, true, null, null, true));
		assertEquals(TransportDispatchTrace.Verdict.REFUSED_AFTER_INTERACTION,
			TransportDispatchTrace.classify(false, true, false, null, null, true));
		assertEquals(TransportDispatchTrace.Verdict.REFUSED_AFTER_INTERACTION,
			TransportDispatchTrace.classify(false, true, null, null, true));
	}

	@Test
	public void theHolyBarrierSendsYouToDrezel()
	{
		assertEquals("Drezel", TransportLandingRules.talkFirstNpc(3443).orElse(null));
		assertFalse(TransportLandingRules.talkFirstNpc(1234).isPresent());
	}

	private static final net.runelite.api.coords.WorldPoint ORIGIN = new net.runelite.api.coords.WorldPoint(3000, 3000, 0);
	private static final net.runelite.api.coords.WorldPoint DESTINATION = new net.runelite.api.coords.WorldPoint(3003, 3000, 0);
	private static final net.runelite.api.coords.WorldPoint FAR = new net.runelite.api.coords.WorldPoint(3010, 3010, 0);

	private static TransportDispatchTrace.Verdict verdictAfter(boolean[] observations, net.runelite.api.coords.WorldPoint player)
	{
		TransportDispatchTrace trace = new TransportDispatchTrace();
		trace.interacted();
		for (boolean ongoing : observations)
		{
			trace.activityOngoing(ongoing);
		}
		return trace.verdict(false, player, ORIGIN, DESTINATION);
	}

	@Test
	public void aWalkUpThatEndedAtTheOriginIsARefusal()
	{
		assertEquals(TransportDispatchTrace.Verdict.REFUSED_AFTER_INTERACTION,
			verdictAfter(new boolean[] {true, true, true, false}, ORIGIN));
	}

	@Test
	public void aMessageBoxThatHasRunItsCourseIsARefusal()
	{
		assertEquals(TransportDispatchTrace.Verdict.REFUSED_AFTER_INTERACTION,
			verdictAfter(new boolean[] {true, false}, ORIGIN));
		assertEquals(TransportDispatchTrace.Verdict.REFUSED_AFTER_INTERACTION,
			verdictAfter(new boolean[0], ORIGIN));
	}

	@Test
	public void activityStillGoingOnOrMovingThePlayerIsNotARefusal()
	{
		assertEquals(TransportDispatchTrace.Verdict.INTERACTION_LEFT_ORIGIN,
			verdictAfter(new boolean[] {true, true}, ORIGIN));
		assertEquals(TransportDispatchTrace.Verdict.INTERACTION_LEFT_ORIGIN,
			verdictAfter(new boolean[] {false}, FAR));
	}

	@Test
	public void anOptionsOnlyDialogueIsNotWaitedOn()
	{
		assertTrue(TransportLandingRules.optionsOnly(true, false, true, false, false));
		assertFalse(TransportLandingRules.optionsOnly(true, true, true, false, false));
		assertFalse(TransportLandingRules.optionsOnly(true, false, true, false, true));
		assertFalse(TransportLandingRules.optionsOnly(false, false, false, false, false));
	}
}
