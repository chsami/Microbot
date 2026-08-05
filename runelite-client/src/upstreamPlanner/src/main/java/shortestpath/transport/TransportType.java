package shortestpath.transport;

import java.util.function.Function;

import lombok.Getter;
import net.runelite.api.Skill;
import shortestpath.ShortestPathConfig;

@Getter
public enum TransportType
{
	TRANSPORT("/transports/transports.tsv", null, null, null, null),
	AGILITY_SHORTCUT("/transports/agility_shortcuts.tsv", "useAgilityShortcuts", ShortestPathConfig::useAgilityShortcuts, "costAgilityShortcuts", ShortestPathConfig::costAgilityShortcuts)
		{
			@Override
			public TransportType refine(int[] skillLevels)
			{
				if (skillLevels[Skill.RANGED.ordinal()] > 1 || skillLevels[Skill.STRENGTH.ordinal()] > 1)
				{
					return GRAPPLE_SHORTCUT;
				}
				return this;
			}
		},
	GRAPPLE_SHORTCUT(null, "useGrappleShortcuts", ShortestPathConfig::useGrappleShortcuts, "costGrappleShortcuts", ShortestPathConfig::costGrappleShortcuts),
	BOAT("/transports/boats.tsv", "useBoats", ShortestPathConfig::useBoats, "costBoats", ShortestPathConfig::costBoats),
	CANOE("/transports/canoes.tsv", "useCanoes", ShortestPathConfig::useCanoes, "costCanoes", ShortestPathConfig::costCanoes),
	CHARTER_SHIP("/transports/charter_ships.tsv", "useCharterShips", ShortestPathConfig::useCharterShips, "costCharterShips", ShortestPathConfig::costCharterShips),
	SHIP("/transports/ships.tsv", "useShips", ShortestPathConfig::useShips, "costShips", ShortestPathConfig::costShips),
	FAIRY_RING("/transports/fairy_rings.tsv", "useFairyRings", ShortestPathConfig::useFairyRings, "costFairyRings", ShortestPathConfig::costFairyRings, 6),
	GNOME_GLIDER("/transports/gnome_gliders.tsv", "useGnomeGliders", ShortestPathConfig::useGnomeGliders, "costGnomeGliders", ShortestPathConfig::costGnomeGliders, 6),
	HOT_AIR_BALLOON("/transports/hot_air_balloons.tsv", "useHotAirBalloons", ShortestPathConfig::useHotAirBalloons, "costHotAirBalloons", ShortestPathConfig::costHotAirBalloons, 7),
	MAGIC_CARPET("/transports/magic_carpets.tsv", "useMagicCarpets", ShortestPathConfig::useMagicCarpets, "costMagicCarpets", ShortestPathConfig::costMagicCarpets),
	MAGIC_MUSHTREE("/transports/magic_mushtrees.tsv", "useMagicMushtrees", ShortestPathConfig::useMagicMushtrees, "costMagicMushtrees", ShortestPathConfig::costMagicMushtrees, 5),
	MINECART("/transports/minecarts.tsv", "useMinecarts", ShortestPathConfig::useMinecarts, "costMinecarts", ShortestPathConfig::costMinecarts),
	QUETZAL("/transports/quetzals.tsv", "useQuetzals", ShortestPathConfig::useQuetzals, "costQuetzals", ShortestPathConfig::costQuetzals, 5)
		{
			@Override
			public TransportType sharesDestinationsWith()
			{
				return QUETZAL_WHISTLE;
			}
		},
	QUETZAL_WHISTLE("/transports/quetzal_whistle.tsv", "useQuetzals", ShortestPathConfig::useQuetzals, "costQuetzalWhistle", ShortestPathConfig::costQuetzals)
		{
			@Override
			public boolean isTeleport()
			{
				return true;
			}

			@Override
			public TransportType sharesDestinationsWith()
			{
				return QUETZAL;
			}

			@Override
			public Function<ShortestPathConfig, Integer> differentialCostFunction()
			{
				return ShortestPathConfig::costQuetzalWhistle;
			}
		},
	SEASONAL_TRANSPORTS("/transports/seasonal_transports.tsv", "useSeasonalTransports", ShortestPathConfig::useSeasonalTransports, "costSeasonalTransports", ShortestPathConfig::costSeasonalTransports),
	SPIRIT_TREE("/transports/spirit_trees.tsv", "useSpiritTrees", ShortestPathConfig::useSpiritTrees, "costSpiritTrees", ShortestPathConfig::costSpiritTrees, 5),
	TELEPORTATION_BOX("/transports/teleportation_boxes.tsv", null, null, "costTeleportationBoxes", ShortestPathConfig::costTeleportationBoxes),
	TELEPORTATION_ITEM("/transports/teleportation_items.tsv", null, null, "costNonConsumableTeleportationItems", ShortestPathConfig::costNonConsumableTeleportationItems)
		{
			@Override
			public boolean isTeleport()
			{
				return true;
			}
		},
	TELEPORTATION_LEVER("/transports/teleportation_levers.tsv", "useTeleportationLevers", ShortestPathConfig::useTeleportationLevers, "costTeleportationLevers", ShortestPathConfig::costTeleportationLevers),
	TELEPORTATION_MINIGAME("/transports/teleportation_minigames.tsv", "useTeleportationMinigames", ShortestPathConfig::useTeleportationMinigames, "costTeleportationMinigames", ShortestPathConfig::costTeleportationMinigames)
		{
			@Override
			public boolean isTeleport()
			{
				return true;
			}
		},
	TELEPORTATION_PORTAL("/transports/teleportation_portals.tsv", "useTeleportationPortals", ShortestPathConfig::useTeleportationPortals, "costTeleportationPortals", ShortestPathConfig::costTeleportationPortals),
	TELEPORTATION_PORTAL_POH("/transports/teleportation_portals_poh.tsv", "useTeleportationPortalsPoh", ShortestPathConfig::useTeleportationPortalsPoh, null, null),
	TELEPORTATION_SPELL("/transports/teleportation_spells.tsv", "useTeleportationSpells", ShortestPathConfig::useTeleportationSpells, "costTeleportationSpells", ShortestPathConfig::costTeleportationSpells)
		{
			@Override
			public boolean isTeleport()
			{
				return true;
			}
		},
	TELEPORTATION_SPELL_HOME("/transports/teleportation_spells_home.tsv", "useTeleportationSpellsHome", ShortestPathConfig::useTeleportationSpellsHome, "costTeleportationSpellsHome", ShortestPathConfig::costTeleportationSpellsHome)
		{
			@Override
			public boolean isTeleport()
			{
				return true;
			}
		},
	WILDERNESS_OBELISK("/transports/wilderness_obelisks.tsv", "useWildernessObelisks", ShortestPathConfig::useWildernessObelisks, "costWildernessObelisks", ShortestPathConfig::costWildernessObelisks),
	;

	private final String resourcePath;
	private final String enabledKey;
	private final Function<ShortestPathConfig, Boolean> enabledGetter;
	private final String costKey;
	private final Function<ShortestPathConfig, Integer> costGetter;
	private final Integer radiusThreshold;

	TransportType(
		String resourcePath,
		String enabledKey,
		Function<ShortestPathConfig, Boolean> enabledGetter,
		String costKey,
		Function<ShortestPathConfig, Integer> costGetter)
	{
		this(resourcePath, enabledKey, enabledGetter, costKey, costGetter, null);
	}

	TransportType(
		String resourcePath,
		String enabledKey,
		Function<ShortestPathConfig, Boolean> enabledGetter,
		String costKey,
		Function<ShortestPathConfig, Integer> costGetter,
		Integer radiusThreshold)
	{
		this.resourcePath = resourcePath;
		this.enabledKey = enabledKey;
		this.enabledGetter = enabledGetter;
		this.costKey = costKey;
		this.costGetter = costGetter;
		this.radiusThreshold = radiusThreshold;
	}

	public boolean hasResourcePath()
	{
		return resourcePath != null;
	}

	public boolean hasRadiusThreshold()
	{
		return radiusThreshold != null;
	}

	public boolean hasEnabledGetter()
	{
		return enabledGetter != null;
	}

	public boolean hasCostGetter()
	{
		return costGetter != null;
	}

	/*
	 * Indicates whether a TransportType is a teleport.
	 * Levers, portals and wilderness obelisks are considered transports
	 * and not teleports because they have a pre-defined origin and no
	 * wilderness level limit.
	 */
	public boolean isTeleport()
	{
		return false;
	}


	/**
	 * Stores which transport type this transport shares destinations with, if any.
	 * Used for delayed visit pathfinding so both types can compete in the priority queue.
	 */
	public TransportType sharesDestinationsWith()
	{
		return null;
	}

	/**
	 * Additional cost applied on top of the base cost when this transport type
	 * shares destinations with another type. Represents the differential cost
	 * (e.g. how many extra tiles the whistle must save over a landing site
	 * to justify using a charge).
	 */
	public Function<ShortestPathConfig, Integer> differentialCostFunction()
	{
		return null;
	}


	/**
	 * Refines the TransportType based on the required skill levels.
	 */
	public TransportType refine(int[] skillLevels)
	{
		return this;
	}
}
