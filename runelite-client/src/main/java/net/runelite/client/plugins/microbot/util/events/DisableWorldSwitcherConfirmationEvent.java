package net.runelite.client.plugins.microbot.util.events;

import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.settings.Rs2Settings;

public class DisableWorldSwitcherConfirmationEvent implements BlockingEvent
{
	@Override
	public boolean validate()
	{
		return Microbot.isLoggedIn() && Rs2Settings.isWorldSwitcherConfirmationEnabled();
	}

	@Override
	public boolean execute()
	{
		return Rs2Settings.disableWorldSwitcherConfirmation();
	}

	@Override
	public BlockingEventPriority priority()
	{
		return BlockingEventPriority.HIGH;
	}
}
