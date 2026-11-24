package net.runelite.client.plugins.microbot.ui;

import java.awt.Color;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ColorUtil;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class MicrobotPluginListRedesignTest
{
	@Test
	public void headingSaysPluginsWhenNothingIsPinned()
	{
		assertEquals("Plugins (4)", MicrobotPluginListPanel.allHeading(0, 4));
		assertEquals("Other plugins (4)", MicrobotPluginListPanel.allHeading(2, 4));
	}

	@Test
	public void darkTagColoursAreLifted()
	{
		for (Color dark : new Color[]{Color.BLACK, new Color(0x00008B), new Color(0x8B0000)})
		{
			assertEquals(ColorUtil.colorLerp(dark, ColorScheme.TEXT_COLOR, 0.5), MicrobotPluginListItem.readableTagColor(dark));
		}
		Color green = new Color(70, 167, 88);
		assertEquals(green, MicrobotPluginListItem.readableTagColor(green));
	}
}
