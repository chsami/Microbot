package net.runelite.client.plugins.microbot.mouserecorder;

import java.util.List;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ContentDetectorTest
{
	private static ContentDetector.Tick open()
	{
		return new ContentDetector.Tick(false, -1, null, null);
	}

	@Test
	public void titheFarmInstanceStartsAndLeavingEndsIt()
	{
		ContentDetector detector = new ContentDetector();
		List<ContentDetector.Transition> enter = detector.update(new ContentDetector.Tick(true, 7222, null, Skill.FARMING), 1000);
		List<ContentDetector.Transition> stay = detector.update(new ContentDetector.Tick(true, 7222, null, null), 1600);
		List<ContentDetector.Transition> leave = detector.update(new ContentDetector.Tick(false, -1, null, null), 9000);

		assertEquals(1, enter.size());
		assertTrue(enter.get(0).isStart());
		assertEquals("Tithe Farm", enter.get(0).getContent().getName());
		assertEquals("minigame", enter.get(0).getContent().getKind());
		assertTrue(stay.isEmpty());
		assertFalse(leave.get(0).isStart());
		assertEquals(1000, leave.get(0).getStartMs());
		assertEquals("Skilling: Farming", leave.get(1).getContent().getName());
	}

	@Test
	public void combatLingersAfterTheLastTargetedTick()
	{
		ContentDetector detector = new ContentDetector();
		detector.update(new ContentDetector.Tick(false, -1, "Zulrah", null), 0);
		for (int i = 1; i < ContentDetector.COMBAT_LINGER_TICKS; i++)
		{
			assertTrue(detector.update(open(), i * 600L).isEmpty());
		}
		List<ContentDetector.Transition> end = detector.update(open(), 99_000);

		assertEquals("Combat: Zulrah", end.get(0).getContent().getName());
		assertFalse(end.get(0).isStart());
		assertNull(detector.current());
	}

	@Test
	public void combatXpDoesNotCountAsSkilling()
	{
		ContentDetector detector = new ContentDetector();

		assertTrue(detector.update(new ContentDetector.Tick(false, -1, null, Skill.RANGED), 0).isEmpty());
		assertEquals("Skilling: Woodcutting", detector.update(new ContentDetector.Tick(false, -1, null, Skill.WOODCUTTING), 600).get(0).getContent().getName());
	}

	@Test
	public void endClosesTheOpenSegment()
	{
		ContentDetector detector = new ContentDetector();
		detector.update(new ContentDetector.Tick(false, -1, null, Skill.MINING), 500);

		List<ContentDetector.Transition> end = detector.end();

		assertEquals(1, end.size());
		assertEquals(500, end.get(0).getStartMs());
		assertTrue(detector.end().isEmpty());
	}

	@Test
	public void contentNamesFitTheApi()
	{
		ContentDetector detector = new ContentDetector();
		String name = detector.update(new ContentDetector.Tick(false, -1, "Tz\u00e9k-Jad <3 #1 " + "x".repeat(80), null), 0).get(0).getContent().getName();

		assertTrue(name.length() <= ContentDetector.MAX_NAME);
		assertTrue(name.matches("[\\p{L}\\p{N} '()\\-:.&,/]+"));
		assertTrue(name.startsWith("Combat: Tz\u00e9k-Jad 3 1 x"));
		assertEquals("", ContentDetector.clean("<>#"));
	}
}
