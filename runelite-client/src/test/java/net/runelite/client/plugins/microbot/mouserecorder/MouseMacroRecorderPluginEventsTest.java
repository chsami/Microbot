package net.runelite.client.plugins.microbot.mouserecorder;

import java.lang.reflect.Field;
import net.runelite.client.eventbus.EventBus;
import org.junit.Test;

public class MouseMacroRecorderPluginEventsTest
{
	@Test
	public void thePluginAndItsProbeRegisterOnTheEventBus() throws Exception
	{
		MouseMacroRecorderPlugin plugin = new MouseMacroRecorderPlugin();
		Field probe = MouseMacroRecorderPlugin.class.getDeclaredField("botMenuProbe");
		probe.setAccessible(true);
		EventBus eventBus = new EventBus();

		eventBus.register(plugin);
		eventBus.register(probe.get(plugin));
		eventBus.unregister(probe.get(plugin));
		eventBus.unregister(plugin);
	}
}
