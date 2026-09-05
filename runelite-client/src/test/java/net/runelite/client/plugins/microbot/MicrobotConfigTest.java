package net.runelite.client.plugins.microbot;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class MicrobotConfigTest
{
	@Test
	public void inputYieldingIsDisabledByDefault()
	{
		MicrobotConfig config = new MicrobotConfig() { };

		assertTrue(config.disableInputYielding());
	}
}
