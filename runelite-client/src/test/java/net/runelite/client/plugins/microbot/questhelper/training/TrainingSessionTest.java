package net.runelite.client.plugins.microbot.questhelper.training;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public class TrainingSessionTest
{
	private static final class FakeTrainer implements SkillTrainer
	{
		final AtomicInteger level;
		final int gainPerTick;
		int ticks;
		int resets;
		boolean failNext;

		FakeTrainer(AtomicInteger level, int gainPerTick)
		{
			this.level = level;
			this.gainPerTick = gainPerTick;
		}

		@Override
		public String skill()
		{
			return "COOKING";
		}

		@Override
		public String name()
		{
			return "fake";
		}

		@Override
		public List<Supply> supplies(int lvl)
		{
			return Collections.emptyList();
		}

		@Override
		public void reset()
		{
			resets++;
		}

		@Override
		public Outcome tick(TrainerContext context)
		{
			ticks++;
			if (failNext)
			{
				return Outcome.failed("out of food");
			}
			level.addAndGet(gainPerTick);
			return Outcome.working();
		}
	}

	private static final TrainerContext CONTEXT = new TrainerContext()
	{
		@Override
		public int level()
		{
			return 1;
		}

		@Override
		public boolean mayBuy(List<String> itemNames, long totalGp)
		{
			return false;
		}

		@Override
		public void spent(long totalGp)
		{
		}
	};

	@Test
	public void stopsExactlyAtTheTargetLevel()
	{
		AtomicInteger level = new AtomicInteger(10);
		FakeTrainer trainer = new FakeTrainer(level, 1);
		TrainingSession session = new TrainingSession("COOKING", 13, trainer, level::get, () -> 0, 1000, CONTEXT);
		assertEquals(TrainingSession.State.RUNNING, session.tick());
		assertEquals(TrainingSession.State.RUNNING, session.tick());
		assertEquals(TrainingSession.State.REACHED, session.tick());
		assertEquals(3, trainer.ticks);
		assertEquals(TrainingSession.State.REACHED, session.tick());
		assertEquals("a finished session never ticks the trainer again", 3, trainer.ticks);
		assertTrue("the trainer is reset when the session ends", trainer.resets >= 2);
	}

	@Test
	public void alreadyAtTheLevelNeverTicksTheTrainer()
	{
		AtomicInteger level = new AtomicInteger(40);
		FakeTrainer trainer = new FakeTrainer(level, 1);
		TrainingSession session = new TrainingSession("COOKING", 40, trainer, level::get, () -> 0, 1000, CONTEXT);
		assertEquals(TrainingSession.State.REACHED, session.tick());
		assertEquals(0, trainer.ticks);
	}

	@Test
	public void aFailingTrainerEndsTheSessionWithItsMessage()
	{
		AtomicInteger level = new AtomicInteger(1);
		FakeTrainer trainer = new FakeTrainer(level, 0);
		trainer.failNext = true;
		TrainingSession session = new TrainingSession("COOKING", 5, trainer, level::get, () -> 0, 1000, CONTEXT);
		assertEquals(TrainingSession.State.FAILED, session.tick());
		assertEquals("out of food", session.message());
	}

	@Test
	public void timesOut()
	{
		AtomicInteger level = new AtomicInteger(1);
		AtomicLong now = new AtomicLong(0);
		FakeTrainer trainer = new FakeTrainer(level, 0);
		TrainingSession session = new TrainingSession("COOKING", 5, trainer, level::get, now::get, 1000, CONTEXT);
		assertEquals(TrainingSession.State.RUNNING, session.tick());
		now.set(1500);
		assertEquals(TrainingSession.State.FAILED, session.tick());
		assertTrue(session.message().contains("timed out"));
	}

	@Test
	public void abortStopsTheTrainer()
	{
		AtomicInteger level = new AtomicInteger(1);
		FakeTrainer trainer = new FakeTrainer(level, 0);
		TrainingSession session = new TrainingSession("COOKING", 5, trainer, level::get, () -> 0, 1000, CONTEXT);
		int before = trainer.resets;
		session.abort();
		assertEquals(before + 1, trainer.resets);
		assertEquals(TrainingSession.State.FAILED, session.tick());
	}

	private static HubPluginTrainer.PluginControl control(List<String> log, boolean canStart)
	{
		return new HubPluginTrainer.PluginControl()
		{
			@Override
			public boolean start(String pluginName)
			{
				log.add("start " + pluginName);
				return canStart;
			}

			@Override
			public void stop(String pluginName)
			{
				log.add("stop " + pluginName);
			}
		};
	}

	@Test
	public void hubPluginIsStartedOnceAndStoppedAtTheTarget()
	{
		List<String> log = new ArrayList<>();
		HubPluginTrainer hub = new HubPluginTrainer("FISHING", "Auto Fishing", control(log, true));
		AtomicInteger level = new AtomicInteger(10);
		TrainingSession session = new TrainingSession("FISHING", 12, hub, level::get, () -> 0, 1000, CONTEXT);
		session.tick();
		session.tick();
		level.set(12);
		assertEquals(TrainingSession.State.REACHED, session.tick());
		assertEquals(Arrays.asList("start Auto Fishing", "stop Auto Fishing"), log);
	}

	@Test
	public void hubPluginThatCannotStartFails()
	{
		List<String> log = new ArrayList<>();
		HubPluginTrainer hub = new HubPluginTrainer("FISHING", "Missing", control(log, false));
		AtomicInteger level = new AtomicInteger(10);
		TrainingSession session = new TrainingSession("FISHING", 12, hub, level::get, () -> 0, 1000, CONTEXT);
		assertEquals(TrainingSession.State.FAILED, session.tick());
		assertTrue(session.message().contains("Missing"));
	}

	@Test
	public void skillPluginMapParsesAndRoundTrips()
	{
		SkillPluginMap map = SkillPluginMap.parse("Fishing=Auto Fishing; mining = Motherlode Mine;bad;=x");
		assertEquals("Auto Fishing", map.pluginFor("FISHING"));
		assertEquals("Motherlode Mine", map.pluginFor("Mining"));
		assertNull(map.pluginFor("Cooking"));
		assertEquals("Auto Fishing", SkillPluginMap.parse(map.serialise()).pluginFor("fishing"));
	}

	@Test
	public void etaGrowsWithTheGap()
	{
		assertEquals(0, TrainingEta.minutes("COOKING", 40, 40));
		assertTrue(TrainingEta.minutes("COOKING", 1, 40) < TrainingEta.minutes("COOKING", 1, 60));
		assertEquals(83, TrainingEta.xpForLevel(2));
		assertEquals(388, TrainingEta.xpForLevel(5));
	}
}
