package net.runelite.client.plugins.microbot.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.List;
import org.junit.Test;

public class MicrobotPluginConfigurationDescriptorTest
{
	@Test
	public void theSevenArgumentConstructorStillWorksForHubPlugins() throws Exception
	{
		MicrobotPluginConfigurationDescriptor d = new MicrobotPluginConfigurationDescriptor("Flipper", "", new String[0], null, null, null, List.of("Other"));

		assertEquals(List.of("Other"), d.getConflicts());
		assertNull(d.getMicrobotHubName());
		assertNull(d.getInternalPluginHubName());
	}
}
