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
	private static final int TITHE_FARM = 7222;
	private static final int SLAYER_CAVE = 9624;
	private static final int VARROCK = 12853;
	private static final int UNKNOWN = 12345;
	private static final long MINUTE = 60_000;

	private static ContentDetector.Tick at(int region, Skill... xp)
	{
		return new ContentDetector.Tick(false, -1, region, 0, List.of(xp));
	}

	private static ContentDetector.Tick instance(int template, Skill... xp)
	{
		return new ContentDetector.Tick(true, template, 7735, 0, List.of(xp));
	}

	private static String name(ContentDetector detector)
	{
		ContentDetector.Content content = detector.current();
		return content == null ? null : content.getName();
	}

	@Test
	public void regionNamesTheSegmentLikeDiscord()
	{
		ContentDetector detector = new ContentDetector();
		List<ContentDetector.Transition> enter = detector.update(at(SLAYER_CAVE), 0);

		assertEquals(1, enter.size());
		assertTrue(enter.get(0).isStart());
		assertEquals(new ContentDetector.Content("Stronghold Slayer Cave", "dungeon"), enter.get(0).getContent());
		assertTrue(detector.update(at(SLAYER_CAVE + 1), 600).isEmpty());

		List<ContentDetector.Transition> city = detector.update(at(VARROCK), 1200);
		assertEquals(0, city.get(0).getStartMs());
		assertFalse(city.get(0).isStart());
		assertEquals(new ContentDetector.Content("Varrock", "city"), city.get(1).getContent());

		List<ContentDetector.Transition> leave = detector.update(at(UNKNOWN), 1800);
		assertEquals(1, leave.size());
		assertFalse(leave.get(0).isStart());
		assertNull(detector.current());
	}

	@Test
	public void instanceUsesTheTemplateRegion()
	{
		ContentDetector detector = new ContentDetector();
		List<ContentDetector.Transition> enter = detector.update(instance(TITHE_FARM), 1000);

		assertEquals(new ContentDetector.Content("Tithe Farm", "minigame"), enter.get(0).getContent());
		assertTrue(detector.update(instance(TITHE_FARM), 1600).isEmpty());
		assertEquals(1000, detector.update(at(UNKNOWN), 9000).get(0).getStartMs());
	}

	@Test
	public void xpDropTrainsTheSkill()
	{
		ContentDetector detector = new ContentDetector();
		List<ContentDetector.Transition> start = detector.update(at(UNKNOWN, Skill.WOODCUTTING), 0);

		assertEquals(new ContentDetector.Content("Training: Woodcutting", "training"), start.get(0).getContent());
	}

	@Test
	public void priorityPicksBetweenAreaAndSkill()
	{
		ContentDetector detector = new ContentDetector();
		detector.update(instance(TITHE_FARM), 0);
		detector.update(instance(TITHE_FARM, Skill.FARMING), 600);
		assertEquals("Training: Farming", name(detector));

		detector.update(at(UNKNOWN, Skill.HITPOINTS, Skill.ATTACK), 1200);
		assertEquals("Training: Attack", name(detector));
		detector.update(at(UNKNOWN, Skill.SLAYER, Skill.HITPOINTS), 1800);
		assertEquals("Training: Slayer", name(detector));

		detector.update(at(SLAYER_CAVE), 2400);
		assertEquals("Stronghold Slayer Cave", name(detector));
		assertTrue(detector.update(at(SLAYER_CAVE, Skill.HITPOINTS), 3000).isEmpty());
		detector.update(at(SLAYER_CAVE, Skill.RANGED), 3600);
		assertEquals("Training: Ranged", name(detector));
	}

	@Test
	public void staleSkillTimesOutBackToTheArea()
	{
		ContentDetector detector = new ContentDetector();
		detector.update(instance(TITHE_FARM, Skill.FARMING), 0);
		assertEquals("Training: Farming", name(detector));

		assertTrue(detector.update(instance(TITHE_FARM), ContentDetector.ACTION_TIMEOUT_MS).isEmpty());
		List<ContentDetector.Transition> stale = detector.update(instance(TITHE_FARM), ContentDetector.ACTION_TIMEOUT_MS + 600);

		assertEquals("Training: Farming", stale.get(0).getContent().getName());
		assertFalse(stale.get(0).isStart());
		assertEquals(new ContentDetector.Content("Tithe Farm", "minigame"), stale.get(1).getContent());

		detector.update(at(UNKNOWN, Skill.MINING), 10 * MINUTE);
		assertEquals(1, detector.update(at(UNKNOWN), 16 * MINUTE).size());
		assertNull(detector.current());
	}

	@Test
	public void pausedTicksDoNotEndTheSegment()
	{
		ContentDetector detector = new ContentDetector();
		detector.update(at(SLAYER_CAVE, Skill.SLAYER), 500);

		assertTrue(detector.update(at(SLAYER_CAVE), 4 * MINUTE).isEmpty());
		assertEquals("Training: Slayer", name(detector));
		assertEquals(500, detector.end().get(0).getStartMs());
	}

	@Test
	public void endClosesTheOpenSegment()
	{
		ContentDetector detector = new ContentDetector();
		detector.update(at(UNKNOWN, Skill.MINING), 500);

		List<ContentDetector.Transition> end = detector.end();

		assertEquals(1, end.size());
		assertEquals(500, end.get(0).getStartMs());
		assertTrue(detector.end().isEmpty());
		assertEquals("Varrock", detector.update(at(VARROCK), 900).get(0).getContent().getName());
	}

	@Test
	public void missingDiscordPluginMeansNoSegments()
	{
		DiscordCatalog missing = DiscordCatalog.load(DiscordCatalog.EVENT_TYPE + "Gone", getClass().getClassLoader());
		DiscordCatalog wrong = DiscordCatalog.load("net.runelite.client.plugins.discord.DiscordAreaType", getClass().getClassLoader());
		ContentDetector detector = new ContentDetector(missing);

		assertTrue(DiscordCatalog.shared().available());
		assertFalse(missing.available());
		assertFalse(wrong.available());
		assertNull(wrong.region(TITHE_FARM));
		assertNull(missing.skill(Skill.FARMING));
		assertTrue(detector.update(instance(TITHE_FARM, Skill.FARMING), 0).isEmpty());
		assertNull(detector.current());
		assertTrue(detector.end().isEmpty());
	}

	@Test
	public void contentNamesFitTheApi()
	{
		assertEquals("Tzék-Jad 3 1", ContentDetector.clean("Tzék-Jad <3 #1"));
		assertEquals("", ContentDetector.clean("<>#"));
	}
}
