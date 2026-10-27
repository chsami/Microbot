package net.runelite.client.plugins.microbot.ddchopper;

import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.microbot.Microbot;

import java.util.HashMap;
import java.util.Map;

/** Turns off ships, spirit trees and teleports in the shortest path planner while the script runs. */
final class WalkerOverrides
{
	private boolean applied;

	/** Null-safe if the event bus is absent. */
	void apply()
	{
		if (Microbot.getEventBus() == null)
		{
			return;
		}
		Map<String, Object> overrides = new HashMap<>();
		overrides.put("useShips", false);
		overrides.put("useBoats", false);
		overrides.put("useSpiritTrees", false);
		overrides.put("useTeleportationItems", "None");
		overrides.put("useTeleportationLevers", false);
		overrides.put("useTeleportationMinigames", false);
		overrides.put("useTeleportationPortals", false);
		overrides.put("useTeleportationSpells", false);
		overrides.put("useWildernessObelisks", false);
		Map<String, Object> data = new HashMap<>();
		data.put("config", overrides);
		Microbot.getEventBus().post(new PluginMessage("shortestpath", "path", data));
		applied = true;
	}

	void clear()
	{
		if (applied && Microbot.getEventBus() != null)
		{
			Microbot.getEventBus().post(new PluginMessage("shortestpath", "clear"));
		}
		applied = false;
	}
}
