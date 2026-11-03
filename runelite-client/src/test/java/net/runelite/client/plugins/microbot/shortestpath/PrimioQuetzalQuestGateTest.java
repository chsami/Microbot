package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PrimioQuetzalQuestGateTest
{
	@Test
	public void everyPrimioTransportRequiresChildrenOfTheSun()
	{
		List<Transport> primio = Transport.loadAllFromResources().values().stream()
			.flatMap(Set::stream)
			.filter(t -> "Primio".equals(t.getName()))
			.collect(Collectors.toList());

		assertFalse("precondition: Primio transports should be loaded", primio.isEmpty());
		assertTrue("the NPC-type Primio rows should be loaded",
			primio.stream().anyMatch(t -> t.getType() == TransportType.NPC));

		for (Transport transport : primio)
		{
			assertTrue("Primio transport without a quest gate: " + transport, transport.isQuestLocked());
			assertEquals("Primio transport not gated on Children of the Sun: " + transport,
				QuestState.FINISHED, transport.getQuests().get(Quest.CHILDREN_OF_THE_SUN));
		}
	}
}
