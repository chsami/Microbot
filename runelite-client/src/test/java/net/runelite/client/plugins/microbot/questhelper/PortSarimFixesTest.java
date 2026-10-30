package net.runelite.client.plugins.microbot.questhelper;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.transport.TransportRefusalLedger;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PortSarimFixesTest
{
	private static final WorldPoint MANHOLE = new WorldPoint(3018, 3231, 0);
	private static final WorldPoint RAT_PITS = new WorldPoint(2962, 9650, 0);
	private static final WorldPoint REDBEARD = new WorldPoint(3053, 3251, 0);

	@Test
	public void manholeIsGatedBehindRatcatchers() throws Exception
	{
		String row = null;
		try (InputStream in = getClass().getResourceAsStream(
			"/net/runelite/client/plugins/microbot/shortestpath/transports.tsv");
			BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = r.readLine()) != null)
			{
				if (line.startsWith("3018 3231 0\t2962 9650 0\t"))
				{
					row = line;
				}
			}
		}
		assertNotNull(row);
		assertEquals("Ratcatchers", row.split("\t", -1)[6]);
	}

	@Test
	public void refusedShortcutStrikesOutAtOnceWhenWalkingRoundIsPossible()
	{
		assertTrue(TransportRefusalLedger.onFootRouteLikely(MANHOLE, RAT_PITS, REDBEARD));
		assertTrue(TransportRefusalLedger.isStrikeOut(1, true));
		assertFalse(TransportRefusalLedger.isStrikeOut(1, false));
		assertTrue(TransportRefusalLedger.isStrikeOut(2, false));
	}

	@Test
	public void transportToTheTargetItselfKeepsTwoStrikes()
	{
		assertFalse(TransportRefusalLedger.onFootRouteLikely(MANHOLE, RAT_PITS, new WorldPoint(2962, 9650, 0)));
		assertFalse(TransportRefusalLedger.onFootRouteLikely(MANHOLE, RAT_PITS, null));
	}

	@Test
	public void approachThroughAWallDoesNotCountAsArrived()
	{
		assertEquals(3, QuestObjectActions.arrivalTolerance(true));
		assertEquals(0, QuestObjectActions.arrivalTolerance(false));
	}
}
