package net.runelite.client.plugins.microbot.ddchopper;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BreakPlanTest
{
	@Test
	public void valuesStayInsideTheRanges()
	{
		BreakPlan plan = new BreakPlan(30, 90, 5, 20, new Random(42));
		for (int i = 0; i < 500; i++)
		{
			int play = plan.nextPlayMinutes();
			int rest = plan.nextBreakMinutes();
			assertTrue(play >= 30 && play <= 90);
			assertTrue(rest >= 5 && rest <= 20);
		}
	}

	@Test
	public void sameSeedGivesSameSchedule()
	{
		BreakPlan a = new BreakPlan(30, 90, 5, 20, new Random(7));
		BreakPlan b = new BreakPlan(30, 90, 5, 20, new Random(7));
		for (int i = 0; i < 20; i++)
		{
			assertEquals(a.nextPlayMinutes(), b.nextPlayMinutes());
			assertEquals(a.nextBreakMinutes(), b.nextBreakMinutes());
		}
	}

	@Test
	public void scheduleActuallyVaries()
	{
		BreakPlan plan = new BreakPlan(30, 90, 5, 20, new Random(1));
		int first = plan.nextPlayMinutes();
		boolean varied = false;
		for (int i = 0; i < 50; i++)
		{
			varied |= plan.nextPlayMinutes() != first;
		}
		assertTrue(varied);
	}

	@Test
	public void swappedAndDegenerateRangesAreNormalised()
	{
		BreakPlan plan = new BreakPlan(90, 30, 20, 5, new Random(3));
		assertEquals(30, plan.minPlay());
		assertEquals(90, plan.maxPlay());
		assertEquals(5, plan.minBreak());
		assertEquals(20, plan.maxBreak());
		BreakPlan fixed = new BreakPlan(10, 10, 0, -4, new Random(3));
		assertEquals(10, fixed.nextPlayMinutes());
		assertTrue(fixed.nextBreakMinutes() >= 1);
	}
}
