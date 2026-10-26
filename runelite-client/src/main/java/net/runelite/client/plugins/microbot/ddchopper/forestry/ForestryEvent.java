package net.runelite.client.plugins.microbot.ddchopper.forestry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import net.runelite.client.plugins.microbot.ddchopper.DDChopperConfig;

/** The detection table: which NPC/object names mean which Forestry event, the config switch and the actions to try. */
public enum ForestryEvent
{
	RISING_ROOTS("Rising Roots", c -> c.forestryRoots(),
		new String[0], ForestryIds.ROOTS_OBJECTS, "Chop down", "Chop"),
	STRUGGLING_SAPLING("Struggling Sapling", c -> c.forestrySapling(),
		new String[0], ForestryIds.SAPLING_OBJECTS, "Add-ingredients", "Add", "Interact"),
	FRIENDLY_ENTLINGS("Friendly Entlings", c -> c.forestryEntlings(),
		ForestryIds.ENTLING_NPCS, new String[0], "Prune", "Interact"),
	BEEHIVES("Beehives", c -> c.forestryBeehives(),
		new String[0], ForestryIds.BEEHIVE_OBJECTS, "Build", "Interact"),
	PHEASANT_CONTROL("Pheasant Control", c -> c.forestryPheasant(),
		ForestryIds.PHEASANT_NPCS, ForestryIds.PHEASANT_NEST_OBJECTS, "Return-egg", "Take-egg"),
	POACHERS("Poachers / Fox", c -> c.forestryPoachers(),
		ForestryIds.POACHER_NPCS, ForestryIds.POACHER_OBJECTS, "Disarm", "Interact"),
	ENCHANTMENT_RITUAL("Enchantment Ritual", c -> c.forestryRitual(),
		ForestryIds.RITUAL_NPCS, ForestryIds.RITUAL_OBJECTS, "Enchant", "Interact"),
	LEPRECHAUN("Woodcutting Leprechaun", c -> c.forestryLeprechaun(),
		ForestryIds.LEPRECHAUN_NPCS, new String[0], "Deposit", "Talk-to"),
	FLOWERING_TREE("Flowering Tree", c -> c.forestryFlowering(),
		new String[0], ForestryIds.FLOWERING_OBJECTS, "Pollinate", "Chop down", "Interact");

	private final String label;
	private final Predicate<DDChopperConfig> enabled;
	private final String[] npcNames;
	private final String[] objectNames;
	private final String[] actions;

	ForestryEvent(String label, Predicate<DDChopperConfig> enabled, String[] npcNames, String[] objectNames,
		String... actions)
	{
		this.label = label;
		this.enabled = enabled;
		this.npcNames = npcNames;
		this.objectNames = objectNames;
		this.actions = actions;
	}

	public String label()
	{
		return label;
	}

	public boolean isEnabled(DDChopperConfig config)
	{
		return enabled.test(config);
	}

	public String[] npcNames()
	{
		return npcNames;
	}

	public String[] objectNames()
	{
		return objectNames;
	}

	public String[] actions()
	{
		return actions;
	}

	/** Every NPC or object name that can signal any event. Used to filter the scene cheaply. */
	public static String[] allNames(boolean npcs)
	{
		List<String> out = new ArrayList<>();
		for (ForestryEvent e : values())
		{
			for (String n : npcs ? e.npcNames : e.objectNames)
			{
				if (!out.contains(n))
				{
					out.add(n);
				}
			}
		}
		return out.toArray(new String[0]);
	}

	/**
	 * Events signalled by the NPC and object names currently around, in declaration (priority) order, limited to the
	 * ones the predicate allows. Names are matched case-insensitively; null entries are ignored.
	 */
	public static List<ForestryEvent> detect(Collection<String> npcNamesAround, Collection<String> objectNamesAround,
		Predicate<ForestryEvent> allowed)
	{
		List<ForestryEvent> out = new ArrayList<>();
		for (ForestryEvent e : values())
		{
			if (allowed.test(e) && (anyMatch(npcNamesAround, e.npcNames) || anyMatch(objectNamesAround, e.objectNames)))
			{
				out.add(e);
			}
		}
		return out;
	}

	private static boolean anyMatch(Collection<String> around, String[] names)
	{
		if (around == null)
		{
			return false;
		}
		for (String a : around)
		{
			if (a == null)
			{
				continue;
			}
			String lower = a.toLowerCase(Locale.ROOT).trim();
			for (String n : names)
			{
				if (n.toLowerCase(Locale.ROOT).equals(lower))
				{
					return true;
				}
			}
		}
		return false;
	}
}
