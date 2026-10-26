package net.runelite.client.plugins.microbot.questhelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class QuestPriceLookupTest
{
	@Test
	public void retriesWithBackoffThenSucceeds()
	{
		AtomicInteger calls = new AtomicInteger();
		List<Long> sleeps = new ArrayList<>();
		int price = QuestPriceLookup.withRetry(() -> calls.incrementAndGet() < 3 ? -1 : 250, 3, 500, sleeps::add, -1);
		assertEquals(250, price);
		assertEquals(3, calls.get());
		assertEquals(java.util.Arrays.asList(500L, 1000L), sleeps);
	}

	@Test
	public void fallsBackAfterAllAttemptsAndSurvivesExceptions()
	{
		int price = QuestPriceLookup.withRetry(() -> { throw new IllegalStateException(); }, 2, 1, ms -> { }, 777);
		assertEquals(777, price);
	}
}
