package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

/** A sent click is not an interaction that happened; the second attempt follows a camera adjustment. */
public class VerifiedInteractionTest
{
	private final AtomicLong now = new AtomicLong();
	private final AtomicInteger clicks = new AtomicInteger();
	private final AtomicInteger cameraTurns = new AtomicInteger();

	private boolean run(boolean clickWorks, java.util.function.BooleanSupplier effect)
	{
		return VerifiedInteraction.run(() ->
			{
				clicks.incrementAndGet();
				return clickWorks;
			}, effect, cameraTurns::incrementAndGet, now::get, now::addAndGet);
	}

	@Test
	public void aClickWithAnImmediateEffectIsOneClickAndNoCameraTurn()
	{
		assertTrue(run(true, () -> true));
		assertEquals(1, clicks.get());
		assertEquals(0, cameraTurns.get());
	}

	@Test
	public void aClickWhoseEffectShowsLaterWithinTheWaitCounts()
	{
		assertTrue(run(true, () -> now.get() >= 500));
		assertEquals(1, clicks.get());
	}

	@Test
	public void noEffectTurnsTheCameraAndClicksOnceMore()
	{
		AtomicInteger clicksSeenWhenChecked = new AtomicInteger();
		assertTrue(run(true, () ->
		{
			clicksSeenWhenChecked.set(clicks.get());
			return cameraTurns.get() > 0;
		}));
		assertEquals(2, clicks.get());
		assertEquals(1, cameraTurns.get());
		assertEquals(2, clicksSeenWhenChecked.get());
	}

	@Test
	public void theRetryRunsTheWholeClickAgainSoAnItemIsSelectedAgain()
	{
		AtomicInteger selected = new AtomicInteger();
		AtomicInteger clicked = new AtomicInteger();
		VerifiedInteraction.run(() ->
			{
				selected.incrementAndGet(); // Rs2Inventory.use(item)
				clicked.incrementAndGet(); // npc.click("")
				return true;
			}, () -> false, cameraTurns::incrementAndGet, now::get, now::addAndGet);
		assertEquals(2, selected.get());
		assertEquals(selected.get(), clicked.get());
	}

	@Test
	public void noEffectEverReportsFailureAfterTwoClicksAndOneTurn()
	{
		assertFalse(run(true, () -> false));
		assertEquals(2, clicks.get());
		assertEquals(1, cameraTurns.get());
		assertTrue("waits are bounded", now.get() <= 2 * VerifiedInteraction.EFFECT_WAIT_MS + 200);
	}

	@Test
	public void aClickThatCouldNotBeSentIsNotWaitedOn()
	{
		assertFalse(run(false, () -> true));
		assertEquals(2, clicks.get());
		assertEquals(0, now.get());
	}
}
