package net.runelite.client.plugins.microbot.util.mouse;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BotEventGuardTest
{
	@Test
	public void beginStampsTheLastSyntheticTimeAcrossThreads() throws Exception
	{
		Thread script = new Thread(() ->
		{
			BotEventGuard.begin();
			BotEventGuard.end();
		});
		script.start();
		script.join();

		assertFalse(BotEventGuard.isSynthetic());
		assertTrue(BotEventGuard.nanosSinceLastSynthetic() < 5_000_000_000L);
	}
}
