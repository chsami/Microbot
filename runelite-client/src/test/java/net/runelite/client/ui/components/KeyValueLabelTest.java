package net.runelite.client.ui.components;

import java.awt.Component;
import java.awt.Container;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class KeyValueLabelTest
{
	@Test
	public void staysOnOneLineWhenTheCellIsTooNarrow()
	{
		KeyValueLabel label = new KeyValueLabel();
		label.set("Per hour: ", "123,456,789");
		int height = label.getPreferredSize().height;
		label.setSize(40, height);
		layout(label);

		Container row = (Container) label.getComponent(0);
		Component key = row.getComponent(0);
		Component value = row.getComponent(1);
		assertEquals(key.getY(), value.getY());
		assertTrue(value.getY() + value.getHeight() <= height);
		assertEquals(height, label.getPreferredSize().height);
	}

	private static void layout(Container c)
	{
		c.doLayout();
		for (Component child : c.getComponents())
		{
			if (child instanceof Container)
			{
				layout((Container) child);
			}
		}
	}
}
