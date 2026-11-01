package net.runelite.client.plugins.microbot.diagnostics;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.client.eventbus.EventBus;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.mock;

public class FlightRecorderTest
{
	private static final Set<String> NONE = Set.of();
	private static final Set<String> FOUNDRY = Set.of("GiantsFoundryScript");

	private FlightRecorder recorder;

	@Before
	public void setUp()
	{
		recorder = new FlightRecorder(mock(Client.class), mock(EventBus.class));
	}

	private static FlightRecorder.PlayerState at(String location, int animation)
	{
		return new FlightRecorder.PlayerState(location, animation, null, 50, 45, 100);
	}

	private List<String> lines()
	{
		return Arrays.stream(recorder.dump().split("\n")).skip(1).map(l -> l.split(" ", 3)[2]).collect(Collectors.toList());
	}

	@Test
	public void recordsOnlyWhatChangedEachTick()
	{
		recorder.tick(1, at("3361,11500,0", -1), "IDLE", NONE, false);
		recorder.tick(2, at("3361,11500,0", -1), "IDLE", NONE, false);
		recorder.tick(3, at("3362,11500,0", 899), "Filling crucible", NONE, false);

		assertEquals(List.of("p=3361,11500,0 a=-1 hp=50 pr=45 run=100 st=\"IDLE\"", "p=3362,11500,0 a=899 st=\"Filling crucible\""), lines());
	}

	@Test
	public void writesTheFirstInventoryInFullThenOnlyDifferences()
	{
		recorder.recordContainer("inv", 93, Map.of(2361, 14));
		recorder.recordContainer("inv", 93, Map.of(2361, 14));
		recorder.recordContainer("inv", 93, Map.of(2359, 14));

		assertEquals(List.of("inv=2361*14", "inv +2359*14 -2361*14"), lines());
	}

	@Test
	public void collapsesVarChangesIntoTheirTickAndCapsThem()
	{
		recorder.recordVar("v13914", 1);
		recorder.recordVar("v13914", 2);
		recorder.recordVar("vp281", 7);
		recorder.tick(5, null, null, NONE, false);
		for (int i = 0; i < FlightRecorder.MAX_VARS_PER_TICK + 4; i++)
		{
			recorder.recordVar("v" + i, i);
		}
		recorder.tick(6, null, null, NONE, false);

		List<String> lines = lines();
		assertEquals("v13914=2 vp281=7", lines.get(0));
		assertTrue(lines.get(1).endsWith(" +4 vars"));
	}

	@Test
	public void marksAStallOnceWhileAScriptRunsAndResetsOnChange()
	{
		recorder.tick(100, at("1,1,0", -1), null, FOUNDRY, false);
		recorder.recordClick("Fill", "Crucible", 44776, "GAME_OBJECT_FIRST_OPTION");
		for (int t = 101; t <= 100 + FlightRecorder.STUCK_TICKS + 10; t++)
		{
			recorder.tick(t, at("1,1,0", -1), null, FOUNDRY, false);
		}
		recorder.tick(200, at("1,2,0", -1), null, FOUNDRY, false);
		for (int t = 201; t <= 200 + FlightRecorder.STUCK_TICKS; t++)
		{
			recorder.tick(t, at("1,2,0", -1), null, FOUNDRY, false);
		}

		List<String> stalls = lines().stream().filter(l -> l.startsWith("!")).collect(Collectors.toList());
		assertEquals(List.of(
			"! no game change for 50 ticks while GiantsFoundryScript runs, 1 clicks meanwhile",
			"! no game change for 50 ticks while GiantsFoundryScript runs, 0 clicks meanwhile"), stalls);
	}

	@Test
	public void recordsScriptStartAndStop()
	{
		recorder.tick(1, null, null, FOUNDRY, false);
		recorder.tick(2, null, null, NONE, false);

		assertEquals(List.of("scripts+GiantsFoundryScript", "scripts-GiantsFoundryScript"), lines());
	}

	@Test
	public void recordsATraceOnlyWhenItsValueChanges()
	{
		recorder.recordTrace("GiantsFoundryScript.state", "FILL");
		recorder.recordTrace("GiantsFoundryScript.state", "FILL");
		recorder.recordTrace("GiantsFoundryScript.state", "BANK");

		assertEquals(List.of("trace:GiantsFoundryScript.state=FILL", "trace:GiantsFoundryScript.state=BANK"), lines());
	}

	@Test
	public void capturesScriptPrintsButNotLogLinesAndFoldsRepeats()
	{
		PrintStream out = System.out;
		recorder.start();
		try
		{
			System.out.println("Cannot invoke \"Stage.getHeat()\" because the return value is null");
			System.out.println("Cannot invoke \"Stage.getHeat()\" because the return value is null");
			System.out.println("Cannot invoke \"Stage.getHeat()\" because the return value is null");
			System.out.println("2026-10-25 21:40:06 GMT [Client] INFO  Microbot - a log line");
			new IllegalStateException("crucible").printStackTrace();
		}
		finally
		{
			recorder.stop();
		}
		assertSame(out, System.out);

		List<String> lines = lines();
		assertEquals("out=\"Cannot invoke \"Stage.getHeat()\" because the return value is null\"", lines.get(0));
		assertEquals("(previous line repeated 2 more times)", lines.get(1));
		assertEquals("err=\"java.lang.IllegalStateException: crucible\"", lines.get(2));
		assertEquals(FlightRecorder.MAX_STACK_FRAMES, lines.stream().filter(l -> l.startsWith("err=\"at ")).count());
		assertFalse(lines.stream().anyMatch(l -> l.contains("a log line")));
	}

	@Test
	public void dropsTheOldestLinesPastTheSizeLimit()
	{
		for (int i = 0; i < 20000; i++)
		{
			recorder.record("msg=\"You add the bar to the crucible " + i + ".\"", true);
		}

		String dump = recorder.dump();
		assertTrue(dump.length() <= FlightRecorder.MAX_CHARS + FlightRecorder.LEGEND.length() + 1);
		assertTrue(dump.contains("crucible 19999."));
		assertFalse(dump.contains("crucible 0."));
	}

	@Test
	public void clickTargetsKeepThingsButNotOtherPlayersOrInterfaceText()
	{
		assertEquals("player", FlightRecorder.clickTarget("PLAYER_FOURTH_OPTION", -1, -1, "Zezima  (level-126)"));
		assertEquals("player", FlightRecorder.clickTarget("WIDGET_TARGET_ON_PLAYER", -1, -1, "Bones -> Zezima  (level-126)"));
		assertEquals("player", FlightRecorder.clickTarget("ITEM_USE_ON_PLAYER", 526, -1, "Bones -> Zezima  (level-126)"));
		assertEquals("Iron ore", FlightRecorder.clickTarget("GROUND_ITEM_THIRD_OPTION", -1, -1, "Iron ore"));
		assertEquals("Crucible", FlightRecorder.clickTarget("GAME_OBJECT_FIRST_OPTION", -1, -1, "Crucible"));
		assertEquals("Adamantite bar", FlightRecorder.clickTarget("CC_OP", 2361, 983043, "Adamantite bar"));
		assertEquals("w429", FlightRecorder.clickTarget("CC_OP", -1, 429 << 16 | 11, "Zezima"));
		assertEquals("-", FlightRecorder.clickTarget("CANCEL", -1, -1, ""));
	}

	@Test
	public void traceValuesAreTurnedIntoTextOutsideTheRecorderLock()
	{
		recorder.start();
		try
		{
			Object value = new Object()
			{
				@Override
				public String toString()
				{
					assertFalse(Thread.holdsLock(recorder));
					return "Crucible";
				}
			};
			FlightRecorder.trace("GiantsFoundryScript.target", value);
		}
		finally
		{
			recorder.stop();
		}
		assertTrue(recorder.dump().contains("trace:GiantsFoundryScript.target=Crucible"));
	}

	@Test
	public void aScriptStartingIsAChangeSoTheStallClockStartsThere()
	{
		recorder.tick(1, at("1,1,0", -1), null, NONE, false);
		recorder.tick(80, at("1,1,0", -1), null, FOUNDRY, false);
		recorder.tick(81, at("1,1,0", -1), null, FOUNDRY, false);

		assertFalse(lines().stream().anyMatch(l -> l.startsWith("!")));
	}

	@Test
	public void noStallIsMarkedWhileScriptsArePaused()
	{
		recorder.tick(1, at("1,1,0", -1), null, FOUNDRY, false);
		for (int t = 2; t <= FlightRecorder.STUCK_TICKS + 10; t++)
		{
			recorder.tick(t, at("1,1,0", -1), null, FOUNDRY, true);
		}

		assertFalse(lines().stream().anyMatch(l -> l.startsWith("!")));
	}

	@Test
	public void aRepeatCountStaysWithItsOwnLine()
	{
		recorder.recordPrint("out", "Waiting for bank");
		recorder.recordPrint("out", "Waiting for bank");
		recorder.recordClick("Bank", "Bank chest", 44630, "GAME_OBJECT_FIRST_OPTION");
		recorder.recordPrint("out", "Waiting for bank");

		assertEquals(List.of("out=\"Waiting for bank\"", "(previous line repeated 1 more times)",
			"click=Bank>Bank chest(44630,GAME_OBJECT_FIRST_OPTION)", "out=\"Waiting for bank\""), lines());
	}

	@Test
	public void stoppingForgetsTheStateSoARestartDiffsFromScratch()
	{
		recorder.start();
		recorder.recordContainer("inv", 93, Map.of(2361, 14));
		recorder.stop();
		recorder.start();
		recorder.recordContainer("inv", 93, Map.of(2361, 13));
		recorder.stop();

		assertEquals(List.of("inv=2361*14", "inv=2361*13"), lines());
	}

	@Test
	public void keepsMultiBytePrintsIntact()
	{
		recorder.start();
		try
		{
			System.out.println("Pas de barres d'adamantite trouvées");
			for (byte b : "Barre trouvée\n".getBytes(java.nio.charset.StandardCharsets.UTF_8))
			{
				System.out.write(b);
			}
		}
		finally
		{
			recorder.stop();
		}

		assertEquals(List.of("out=\"Pas de barres d'adamantite trouvées\"", "out=\"Barre trouvée\""), lines());
	}

	@Test
	public void keepsNoMoreThanTheLastTwentyMinutes()
	{
		java.time.Instant start = java.time.Instant.parse("2026-10-31T08:00:00Z");
		recorder.clock = java.time.Clock.fixed(start, java.time.ZoneOffset.UTC);
		recorder.record("msg=\"old\"", true);
		recorder.clock = java.time.Clock.fixed(start.plusSeconds(19 * 60), java.time.ZoneOffset.UTC);
		recorder.record("msg=\"recent\"", true);
		recorder.clock = java.time.Clock.fixed(start.plusSeconds(21 * 60), java.time.ZoneOffset.UTC);

		assertEquals(List.of("msg=\"recent\""), lines());
	}

	@Test
	public void errorLinesAndTheLegendCanBeLeftOut()
	{
		String dump = FlightRecorder.LEGEND + "\n1 08:00:00.0 out=\"Waiting\"\n2 08:00:00.6 err=\"java.lang.NullPointerException\"\n3 08:00:01.2 err=\"at Foo.bar(Foo.java:1)\"\n4 08:00:01.8 (previous line repeated 3 more times)\n5 08:00:02.4 msg=\"Done\"\n";

		assertEquals("1 08:00:00.0 out=\"Waiting\"\n5 08:00:02.4 msg=\"Done\"\n", FlightRecorder.withoutErrors(FlightRecorder.withoutLegend(dump)));
	}

	@Test
	public void errorFilterKeepsLinesOutsideTheRecorderFormat()
	{
		String dump = "# legend err=\"x\"\nnot a line err=\"x\"\n7 08:00:00.0 err=\"boom\"\n8 08:00:00.6 out=\"ok\"\n\n";

		assertEquals("# legend err=\"x\"\nnot a line err=\"x\"\n8 08:00:00.6 out=\"ok\"\n", FlightRecorder.withoutErrors(dump));
	}

	@Test
	public void recognisesStackFrameLines()
	{
		assertTrue(FlightRecorder.isFrame("\tat Foo.bar(Foo.java:1)".getBytes(), 23));
		assertTrue(FlightRecorder.isFrame("\t... 12 more".getBytes(), 12));
		assertFalse(FlightRecorder.isFrame("attack failed".getBytes(), 13));
		assertFalse(FlightRecorder.isFrame("java.lang.NullPointerException".getBytes(), 30));
	}

	@Test
	public void diffListsAddedAndRemovedItemsById()
	{
		assertEquals("+995*100 -2361*3", FlightRecorder.diff(Map.of(2361, 3, 995, 50), Map.of(995, 150)));
		assertEquals("", FlightRecorder.diff(Map.of(1, 1), Map.of(1, 1)));
	}
}
