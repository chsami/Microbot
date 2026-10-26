package net.runelite.client.plugins.microbot.ddchopper.forestry;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.ddchopper.DDChopperConfig;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Basic Forestry event handling while chopping. Isolated on purpose: every step is wrapped, every event has a time and
 * attempt budget ({@link ForestryTracker}), and anything that is not found just means "no event", so the script falls
 * back to chopping. Names come from {@link ForestryIds}.
 */
@Slf4j
public class ForestryHandler
{
	private static final int RADIUS = 14;

	private final ForestryTracker tracker = new ForestryTracker();
	private volatile String lastEvent = "";

	public void reset()
	{
		tracker.reset();
		lastEvent = "";
	}

	/** Label of the event worked on last, for the status line. */
	public String lastEvent()
	{
		return lastEvent;
	}

	/** Returns true when this tick was spent on an event. Never throws. */
	public boolean tick(DDChopperConfig config)
	{
		try
		{
			if (!config.forestryEnabled())
			{
				return false;
			}
			long now = System.currentTimeMillis();
			WorldPoint me = Rs2Player.getWorldLocation();
			if (me == null)
			{
				return false;
			}
			List<Rs2NpcModel> npcs = nearbyNpcs(me);
			List<Rs2TileObjectModel> objects = nearbyObjects(me);
			List<String> npcNames = new ArrayList<>();
			for (Rs2NpcModel n : npcs)
			{
				npcNames.add(n.getName());
			}
			List<String> objectNames = new ArrayList<>();
			for (Rs2TileObjectModel o : objects)
			{
				objectNames.add(o.getName());
			}
			Predicate<ForestryEvent> allowed = e -> e.isEnabled(config) && !tracker.isIgnored(e, now);
			List<ForestryEvent> events = ForestryEvent.detect(npcNames, objectNames, allowed);
			for (ForestryEvent e : ForestryEvent.values())
			{
				if (!events.contains(e))
				{
					tracker.done(e);
				}
			}
			for (ForestryEvent event : events)
			{
				if (!tracker.attempt(event, now))
				{
					Microbot.log("DD Chopper: giving up on Forestry event " + event.label() + " for a while");
					continue;
				}
				if (handle(event, npcs, objects))
				{
					lastEvent = event.label();
					return true;
				}
			}
		}
		catch (Exception e)
		{
			log.warn("Forestry handler failed, continuing to chop", e);
		}
		return false;
	}

	private List<Rs2NpcModel> nearbyNpcs(WorldPoint me)
	{
		List<Rs2NpcModel> all = Microbot.getRs2NpcCache().query().withNames(ForestryEvent.allNames(true))
			.toListOnClientThread();
		List<Rs2NpcModel> out = new ArrayList<>();
		for (Rs2NpcModel n : all)
		{
			if (n != null && n.getWorldLocation() != null && n.getWorldLocation().distanceTo2D(me) <= RADIUS)
			{
				out.add(n);
			}
		}
		return out;
	}

	private List<Rs2TileObjectModel> nearbyObjects(WorldPoint me)
	{
		List<Rs2TileObjectModel> all = Microbot.getRs2TileObjectCache().query()
			.withNames(ForestryEvent.allNames(false)).toListOnClientThread();
		List<Rs2TileObjectModel> out = new ArrayList<>();
		for (Rs2TileObjectModel o : all)
		{
			if (o != null && o.getWorldLocation().distanceTo2D(me) <= RADIUS)
			{
				out.add(o);
			}
		}
		return out;
	}

	private boolean handle(ForestryEvent event, List<Rs2NpcModel> npcs, List<Rs2TileObjectModel> objects)
	{
		Rs2NpcModel npc = firstNpc(event, npcs);
		Rs2TileObjectModel obj = firstObject(event, objects);
		Microbot.status = "Forestry: " + event.label();
		switch (event)
		{
			case BEEHIVES:
				return obj != null && useLogsOn(obj, null);
			case LEPRECHAUN:
				return npc != null && useLogsOn(null, npc);
			case PHEASANT_CONTROL:
				return pheasant(npc, obj);
			default:
				return npc != null ? clickNpc(npc, event.actions()) : obj != null && clickObject(obj, event.actions());
		}
	}

	private boolean pheasant(Rs2NpcModel pheasant, Rs2TileObjectModel nest)
	{
		if (Rs2Inventory.hasItem(ForestryIds.PHEASANT_EGG_ITEM))
		{
			Rs2ItemModel egg = Rs2Inventory.get(ForestryIds.PHEASANT_EGG_ITEM);
			return pheasant != null && egg != null && Rs2Inventory.use(egg) && pheasant.click();
		}
		return nest != null && clickObject(nest, "Take-egg", "Take");
	}

	private boolean useLogsOn(Rs2TileObjectModel obj, Rs2NpcModel npc)
	{
		Rs2ItemModel logs = Rs2Inventory.get(item -> item.getName() != null
			&& item.getName().toLowerCase().endsWith("logs"));
		if (logs == null || !Rs2Inventory.use(logs))
		{
			return false;
		}
		return obj != null ? obj.click("Use") : npc.click("Use");
	}

	private static boolean clickNpc(Rs2NpcModel npc, String... actions)
	{
		for (String a : actions)
		{
			if (npc.click(a))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean clickObject(Rs2TileObjectModel obj, String... actions)
	{
		for (String a : actions)
		{
			if (obj.hasAction(a))
			{
				return obj.click(a);
			}
		}
		return false;
	}

	private static Rs2NpcModel firstNpc(ForestryEvent event, List<Rs2NpcModel> npcs)
	{
		for (Rs2NpcModel n : npcs)
		{
			if (matches(n.getName(), event.npcNames()))
			{
				return n;
			}
		}
		return null;
	}

	private static Rs2TileObjectModel firstObject(ForestryEvent event, List<Rs2TileObjectModel> objects)
	{
		for (Rs2TileObjectModel o : objects)
		{
			if (matches(o.getName(), event.objectNames()))
			{
				return o;
			}
		}
		return null;
	}

	private static boolean matches(String name, String[] names)
	{
		for (String n : names)
		{
			if (n.equalsIgnoreCase(name))
			{
				return true;
			}
		}
		return false;
	}
}
