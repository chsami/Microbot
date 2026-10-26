package net.runelite.client.plugins.microbot.ddchopper;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Every known woodcutting spot, one rectangular area per tree type. {@link #AUTO} is the dropdown's "pick the optimal
 * spot" entry and has no tree.
 *
 * <p>{@code restricted} spots need a boat, a quest gate or a guild/region unlock to reach. They are never used unless
 * the config explicitly allows restricted spots, so the walker is never sent on a surprise ship or teleport (the same
 * principle as the quest walk rule).</p>
 *
 * <p>Areas are the OSRS tile rectangles the trees stand in. Only the Draynor willows are cross-checked against data in
 * this repo; the rest are from the OSRS Wiki tree maps and should be confirmed in game. If no tree is found in the
 * rectangle the script searches around its centre, so a slightly-off rectangle still works.</p>
 */
@Getter
public enum Spot
{
	AUTO("Auto (optimal spot)", null, false, 1, "", false, "", 0, 0, 0, 0, 0, 0),

	// Normal trees
	LUMBRIDGE_TREES("Normal: Lumbridge west", TreeType.NORMAL, false, 1, "", false, "Lumbridge Castle roof", 15, 10,
		3190, 3232, 3210, 3256),
	DRAYNOR_TREES("Normal: Draynor east of bank", TreeType.NORMAL, false, 1, "", false, "Draynor Village", 12, 8,
		3100, 3238, 3116, 3256),

	// Oaks
	DRAYNOR_OAKS("Oak: Draynor west of bank", TreeType.OAK, false, 1, "", false, "Draynor Village", 8, 4,
		3076, 3238, 3088, 3254),
	VARROCK_WEST_OAKS("Oak: Varrock West", TreeType.OAK, false, 1, "", false, "Varrock West", 30, 5,
		3158, 3412, 3172, 3426),
	LUMBRIDGE_OAK("Oak: Lumbridge castle courtyard", TreeType.OAK, false, 1, "", false, "Lumbridge Castle roof", 15, 1,
		3216, 3203, 3222, 3209),
	FALADOR_OAKS("Oak: Falador", TreeType.OAK, false, 1, "", false, "Falador East", 20, 3,
		2998, 3374, 3008, 3386),
	SEERS_OAKS("Oak: Seers' Village", TreeType.OAK, true, 1, "", false, "Seers' Village", 20, 4,
		2712, 3462, 2722, 3472),

	// Willows
	DRAYNOR_WILLOWS("Willow: Draynor", TreeType.WILLOW, false, 1, "", false, "Draynor Village", 6, 6,
		3084, 3226, 3093, 3242),
	RIMMINGTON_WILLOWS("Willow: Rimmington", TreeType.WILLOW, false, 1, "", false, "Falador West", 40, 4,
		2956, 3180, 2970, 3198),
	BARBARIAN_WILLOWS("Willow: Barbarian Outpost", TreeType.WILLOW, true, 1, "", false, "Seers' Village", 45, 4,
		2495, 3505, 2512, 3520),
	CATHERBY_WILLOWS("Willow: Catherby", TreeType.WILLOW, true, 1, "", false, "Catherby", 8, 4,
		2786, 3425, 2796, 3437),
	SEERS_WILLOWS("Willow: Seers' Village", TreeType.WILLOW, true, 1, "", false, "Seers' Village", 15, 4,
		2708, 3502, 2716, 3512),

	// Teak (all hardwood groves need a boat)
	TAI_BWO_TEAK("Teak: Tai Bwo Wannai", TreeType.TEAK, true, 1, "Boat to Karamja", true, "Tai Bwo Wannai (none)", 80, 6,
		2822, 3070, 2832, 3082),
	FOSSIL_TEAK("Teak: Fossil Island", TreeType.TEAK, true, 1, "Boat to Fossil Island", true, "Fossil Island", 30, 8,
		3700, 3835, 3715, 3845),

	// Maples
	SEERS_MAPLES("Maple: Seers' Village", TreeType.MAPLE, true, 1, "", false, "Seers' Village", 12, 5,
		2718, 3496, 2735, 3506),
	CORSAIR_MAPLES("Maple: Corsair Cove", TreeType.MAPLE, true, 1, "Boat to Corsair Cove", true, "Corsair Cove (none)",
		80, 3, 2565, 2855, 2580, 2870),

	// Mahogany
	TAI_BWO_MAHOGANY("Mahogany: Tai Bwo Wannai", TreeType.MAHOGANY, true, 1, "Boat to Karamja", true,
		"Tai Bwo Wannai (none)", 80, 5, 2826, 3082, 2840, 3092),
	FOSSIL_MAHOGANY("Mahogany: Fossil Island", TreeType.MAHOGANY, true, 1, "Boat to Fossil Island", true,
		"Fossil Island", 30, 8, 3700, 3835, 3715, 3845),

	// Yews
	EDGEVILLE_YEWS("Yew: Edgeville", TreeType.YEW, false, 1, "", false, "Edgeville", 6, 5,
		3084, 3466, 3093, 3484),
	VARROCK_PALACE_YEWS("Yew: Varrock Palace", TreeType.YEW, false, 1, "", false, "Varrock West", 45, 5,
		3200, 3497, 3230, 3509),
	FALADOR_SOUTH_YEWS("Yew: Falador south", TreeType.YEW, false, 1, "", false, "Falador East", 45, 3,
		3010, 3308, 3040, 3322),
	RIMMINGTON_YEWS("Yew: Rimmington", TreeType.YEW, false, 1, "", false, "Falador West", 45, 2,
		2925, 3222, 2940, 3235),
	DRAYNOR_MANOR_YEWS("Yew: Draynor Manor", TreeType.YEW, false, 1, "", false, "Draynor Village", 35, 4,
		3084, 3262, 3108, 3276),
	CATHERBY_YEWS("Yew: Catherby", TreeType.YEW, true, 1, "", false, "Catherby", 12, 4,
		2750, 3425, 2768, 3440),
	SEERS_YEWS("Yew: Seers' Village", TreeType.YEW, true, 1, "", false, "Seers' Village", 20, 5,
		2700, 3455, 2722, 3470),
	GNOME_YEWS("Yew: Tree Gnome Stronghold", TreeType.YEW, true, 1, "Tree Gnome Village quest gate", true,
		"Tree Gnome Stronghold", 15, 4, 2435, 3512, 2447, 3522),
	GUILD_YEWS("Yew: Woodcutting Guild", TreeType.YEW, true, 60, "60 Woodcutting, Kourend access (boat)", true,
		"Woodcutting Guild", 12, 8, 1585, 3480, 1597, 3492),

	// Magics
	SEERS_MAGIC("Magic: Seers' Village", TreeType.MAGIC, true, 1, "", false, "Seers' Village", 35, 3,
		2692, 3396, 2706, 3402),
	GNOME_MAGIC("Magic: Tree Gnome Stronghold", TreeType.MAGIC, true, 1, "Tree Gnome Village quest gate", true,
		"Tree Gnome Stronghold", 25, 3, 2428, 3416, 2440, 3428),
	GUILD_MAGIC("Magic: Woodcutting Guild", TreeType.MAGIC, true, 60, "60 Woodcutting, Kourend access (boat)", true,
		"Woodcutting Guild", 12, 4, 1580, 3468, 1594, 3478),

	// Redwoods
	GUILD_REDWOOD("Redwood: Woodcutting Guild", TreeType.REDWOOD, true, 60, "60 Woodcutting, Kourend access (boat)", true,
		"Woodcutting Guild", 10, 6, 1566, 3486, 1576, 3496);

	private final String label;
	private final TreeType tree;
	private final boolean members;
	/** Woodcutting level the spot itself demands (the Guild needs 60), on top of the tree level. */
	private final int spotLevel;
	private final String requirement;
	/** True when reaching the spot needs a boat, quest gate or similar. Excluded unless explicitly allowed. */
	private final boolean restricted;
	private final String nearestBank;
	/** Rough walking distance to the nearest bank in tiles. */
	private final int bankTiles;
	private final int trees;
	private final int minX;
	private final int minY;
	private final int maxX;
	private final int maxY;

	Spot(String label, TreeType tree, boolean members, int spotLevel, String requirement, boolean restricted,
		String nearestBank, int bankTiles, int trees, int minX, int minY, int maxX, int maxY)
	{
		this.label = label;
		this.tree = tree;
		this.members = members;
		this.spotLevel = spotLevel;
		this.requirement = requirement;
		this.restricted = restricted;
		this.nearestBank = nearestBank;
		this.bankTiles = bankTiles;
		this.trees = trees;
		this.minX = minX;
		this.minY = minY;
		this.maxX = maxX;
		this.maxY = maxY;
	}

	public boolean contains(WorldPoint p)
	{
		return p != null && p.getPlane() == 0 && p.getX() >= minX && p.getX() <= maxX
			&& p.getY() >= minY && p.getY() <= maxY;
	}

	public WorldPoint center()
	{
		return new WorldPoint((minX + maxX) / 2, (minY + maxY) / 2, 0);
	}

	/**
	 * Optimal rank within a tree: lower is better. Power-chop prefers the most trees (no waiting for respawns) then
	 * the nearest bank; banking prefers the shortest bank walk then the most trees.
	 */
	public int rank(ChopMode mode)
	{
		return mode == ChopMode.BANK ? bankTiles * 100 - trees : -trees * 100 + bankTiles;
	}

	/** Whether this spot can be used: membership, level, and the restricted (boat / gated) flag. */
	public boolean eligible(int woodcuttingLevel, boolean members, boolean allowRestricted)
	{
		return tree != null
			&& (!this.members || members)
			&& woodcuttingLevel >= spotLevel
			&& woodcuttingLevel >= tree.getLevel()
			&& (!restricted || allowRestricted);
	}

	/** Eligible spots for a tree type. */
	public static List<Spot> forTree(TreeType tree, int woodcuttingLevel, boolean members, boolean allowRestricted)
	{
		List<Spot> out = new ArrayList<>();
		for (Spot s : values())
		{
			if (s.tree == tree && s.eligible(woodcuttingLevel, members, allowRestricted))
			{
				out.add(s);
			}
		}
		return out;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
