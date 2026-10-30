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
}
