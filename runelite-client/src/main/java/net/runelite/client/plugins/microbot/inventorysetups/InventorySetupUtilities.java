package net.runelite.client.plugins.microbot.inventorysetups;

import java.awt.Component;
import java.awt.Container;
import java.util.Set;
import javax.swing.SwingUtilities;
import net.runelite.client.util.SwingUtil;
import static net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin.MAX_SETUP_NAME_LENGTH;


public class InventorySetupUtilities
{
	private InventorySetupUtilities()
	{

	}

	static public int parseTextInputAmount(String input)
	{
		// only take the first 10 characters (max amount is 2.147B which is only 10 digits)
		if (input.length() > 10)
		{
			return Integer.MAX_VALUE;
		}
		input = input.toLowerCase();
		if (input.isEmpty())
		{
			return 1;
		}

		char finalChar = input.charAt(input.length() - 1);
		int factor = 1;
		if (Character.isLetter(finalChar))
		{
			input = input.substring(0, input.length() - 1);
			switch (finalChar)
			{
				case 'k':
					factor = 1000;
					break;
				case 'm':
					factor = 1000000;
					break;
				case 'b':
					factor = 1000000000;
					break;
			}
		}

		if (input.isEmpty())
		{
			return 1;
		}

		// limit to max int value
		long quantityLong = Long.parseLong(input) * factor;
		int quantity = (int) Math.min(quantityLong, Integer.MAX_VALUE);
		quantity = Math.max(quantity, 1);

		return quantity;
	}

	static public String findNewName(String originalName, final Set<String> objects)
	{
		// Do not allow names of more than MAX_SETUP_NAME_LENGTH chars
		if (originalName.length() > MAX_SETUP_NAME_LENGTH)
		{
			originalName = originalName.substring(0, MAX_SETUP_NAME_LENGTH);
		}

		// Fix duplicate name by adding an incrementing number to the duplicate
		String newName = originalName;
		int i = 1;
		while (objects.contains(newName) || newName.isEmpty())
		{
			String i_str = String.valueOf(i);
			if (originalName.length() + i_str.length() > MAX_SETUP_NAME_LENGTH)
			{
				int chars_to_cut_off = i_str.length() - (MAX_SETUP_NAME_LENGTH - originalName.length());
				newName = originalName.substring(0, MAX_SETUP_NAME_LENGTH - chars_to_cut_off) + i++;
			}
			else
			{
				newName = originalName + i++;
			}
		}
		return newName;
	}

	public static void fastRemoveAll(Container c)
	{
		fastRemoveAll(c, true);
	}

	private static void fastRemoveAll(Container c, boolean isMainParent)
	{
		// If we are not on the EDT this will deadlock, in addition to being totally unsafe
		assert SwingUtilities.isEventDispatchThread();

		// when a component is removed it has to be resized for some reason, but only if it's valid
		// so we make sure to invalidate everything before removing it
		c.invalidate();
		for (int i = 0; i < c.getComponentCount(); i++)
		{
			Component ic = c.getComponent(i);

			// removeAll and removeNotify are both recursive, so we have to recurse before them
			if (ic instanceof Container)
			{
				fastRemoveAll((Container) ic, false);
			}

			// each removeNotify needs to remove anything from the event queue that is for that widget
			// this however requires taking a lock, and is moderately slow, so we just execute all of
			// those events with a secondary event loop
			SwingUtil.pumpPendingEvents();

			// call removeNotify early; this is most of the work in removeAll, and generates events that
			// the next secondaryLoop will pickup
			ic.removeNotify();
		}

		if (isMainParent)
		{
			// Actually remove anything
			c.removeAll();
		}
	}
}