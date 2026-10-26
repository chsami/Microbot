package net.runelite.client.plugins.microbot.ddchopper.forestry;

/**
 * Everything the Forestry handlers look for, in one place. Detection is by NPC/object name (case-insensitive), which
 * survives cache updates better than numeric ids. The id arrays are optional extras and are EMPTY: they have not been
 * verified against the game cache. If a name below is wrong the handler simply never sees the event and the script
 * keeps chopping, so a wrong entry costs nothing. Fix names/ids here only.
 */
public final class ForestryIds
{
	private ForestryIds()
	{
	}

	// Rising Roots: a glowing root object appears next to the player; chop it before it grows.
	public static final String[] ROOTS_OBJECTS = {"Rising roots", "Rising root", "Glowing root"};
	public static final int[] ROOTS_OBJECT_IDS = {};

	// Struggling Sapling: a sapling object; ingredients are added until the mulch is right.
	public static final String[] SAPLING_OBJECTS = {"Struggling sapling", "Sapling"};
	public static final int[] SAPLING_OBJECT_IDS = {};

	// Friendly Entlings: Entling NPCs that ask to be pruned a certain way.
	public static final String[] ENTLING_NPCS = {"Friendly entling", "Entling"};
	public static final int[] ENTLING_NPC_IDS = {};

	// Beehives: a beehive object that is built by using logs on it.
	public static final String[] BEEHIVE_OBJECTS = {"Beehive", "Bee hive"};
	public static final int[] BEEHIVE_OBJECT_IDS = {};

	// Pheasant Control: take an egg from the nest, return it to the pheasant.
	public static final String[] PHEASANT_NPCS = {"Pheasant"};
	public static final String[] PHEASANT_NEST_OBJECTS = {"Pheasant nest"};
	public static final String PHEASANT_EGG_ITEM = "Pheasant egg";
	public static final int[] PHEASANT_NPC_IDS = {};

	// Poachers: traps set around the area; disarm them.
	public static final String[] POACHER_OBJECTS = {"Fox trap", "Poacher's trap"};
	public static final String[] POACHER_NPCS = {"Poacher"};
	public static final int[] POACHER_OBJECT_IDS = {};

	// Enchantment Ritual: ritual circles around a dryad; click the circle that matches the shown symbol.
	public static final String[] RITUAL_OBJECTS = {"Ritual circle", "Enchantment circle"};
	public static final String[] RITUAL_NPCS = {"Dryad"};
	public static final int[] RITUAL_OBJECT_IDS = {};

	// Woodcutting Leprechaun: deposits logs for you when you use them on him.
	public static final String[] LEPRECHAUN_NPCS = {"Woodcutting Leprechaun", "Leprechaun"};
	public static final int[] LEPRECHAUN_NPC_IDS = {};

	// Flowering Tree: a blossoming tree that is pollinated by chopping/using it.
	public static final String[] FLOWERING_OBJECTS = {"Flowering tree"};
	public static final int[] FLOWERING_OBJECT_IDS = {};
}
