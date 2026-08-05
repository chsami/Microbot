package shortestpath.pathfinder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import shortestpath.Destination;
import shortestpath.DestinationRequirements;
import shortestpath.ItemVariations;
import shortestpath.JewelleryBoxTier;
import shortestpath.PrimitiveIntHashMap;
import shortestpath.ShortestPathConfig;
import shortestpath.ShortestPathPlugin;
import shortestpath.TeleportationItem;
import shortestpath.WorldPointUtil;
import shortestpath.leagues.LeagueModeState;
import shortestpath.leagues.LeagueRegion;
import shortestpath.leagues.LeagueRegionChecker;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportLoader;
import shortestpath.transport.TransportType;
import shortestpath.transport.TransportTypeConfig;
import shortestpath.transport.parser.VarRequirement;
import shortestpath.transport.requirement.ItemRequirement;
import shortestpath.transport.requirement.TransportItems;

@SuppressWarnings("SameParameterValue")
public class PathfinderConfig
{
	public static final List<Integer> RUNE_POUCHES = Arrays.asList(
		ItemID.BH_RUNE_POUCH, ItemID.BH_RUNE_POUCH_TROUVER,
		ItemID.DIVINE_RUNE_POUCH, ItemID.DIVINE_RUNE_POUCH_TROUVER
	);
	public static final int[] RUNE_POUCH_RUNE_VARBITS =
		{
			VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3, VarbitID.RUNE_POUCH_TYPE_4,
			VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6
		};
	public static final int[] RUNE_POUCH_AMOUNT_VARBITS =
		{
			VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_QUANTITY_3, VarbitID.RUNE_POUCH_QUANTITY_4,
			VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6
		};
	public static final Set<Integer> CURRENCIES = Set.of(
		ItemID.COINS, ItemID.VILLAGE_TRADE_STICKS, ItemID.ECTOTOKEN, ItemID.WARGUILD_TOKENS);
	private static final TransportItems DRAMEN_STAFF = new TransportItems(
		new int[][]{null},
		new int[][]{ItemVariations.DRAMEN_STAFF.getIds()},
		new int[][]{null},
		new int[]{1});

	private final SplitFlagMap mapData;
	private final ThreadLocal<CollisionMap> map;
	/**
	 * All transports by origin. The WorldPointUtil.UNDEFINED key is used for transports centered on the player.
	 */
	// Flat list of every loaded transport. refreshTransports only ever iterates these (the origin
	// is re-derived from each transport), so the per-origin Set/HashMap/Integer-key map the loader
	// produces is flattened here and not retained (issue #491).
	private final Transport[] allTransports;
	private final Map<String, Set<Integer>> allDestinations;
	private final Map<String, Set<Integer>> filteredDestinations;
	/**
	 * Per packed tile; only bank.tsv rows with Skills/Quests/Varbits/VarPlayers.
	 */
	private final Map<Integer, DestinationRequirements> bankRequirements;
	private final Map<Integer, Integer> itemsAndQuantities = new HashMap<>(28 + 11 + 500);
	private final List<Integer> filteredTargets = new ArrayList<>(4);
	private final Client client;
	private final ShortestPathConfig config;
	// Centralized transport type enable/disable config
	private final TransportTypeConfig transportTypeConfig;
	private final int[] boostedSkillLevelsAndMore = new int[Skill.values().length + 3];
	private final Map<Quest, QuestState> questStates = new HashMap<>();
	private final Map<Integer, Integer> varbitValues = new HashMap<>();
	private final Map<Integer, Integer> varPlayerValues = new HashMap<>();
	@Getter
	private final LeagueModeState leagueModeState = new LeagueModeState();
	public ItemContainer bank = null;
	public Set<String> availableSpiritTrees = null;
	/**
	 * Bank tiles the player may use for path banking state (requirements satisfied). Rebuilt in {@link #refresh()}.
	 */
	private Set<Integer> accessibleBankTiles = Set.of();
	/**
	 * Which transports are available for the current user configuration in the
	 * unbanked/banked state.
	 * - transportAvailabilityWithoutBank answers the question, which transport can a player take right now?
	 * - transportAvailabilityWithBank answers the question, which transports can a player take if they visit a bank?
	 */
	private TransportAvailability transportAvailabilityWithoutBank;
	private TransportAvailability transportAvailabilityWithBank;
	/**
	 * Reference that points to either allDestinations or filteredDestinations
	 */
	private Map<String, Set<Integer>> destinations;
	@Getter
	private long calculationCutoffMillis;
	@Getter
	private boolean avoidWilderness;
	// POH-specific settings (not tied to a single TransportType)
	private boolean usePohFairyRing,
		usePohSpiritTree,
		usePohMountedItems,
		usePoh,
		usePohObelisk,
		includeBankPath;
	private JewelleryBoxTier pohJewelleryBoxTier;
	private int costConsumableTeleportationItems;
	private int currencyThreshold;
	@Getter
	private boolean isOnSailingBoat;

	public PathfinderConfig(Client client, ShortestPathConfig config)
	{
		this.client = client;
		this.config = config;
		this.transportTypeConfig = new TransportTypeConfig(config);
		this.mapData = SplitFlagMap.fromResources();
		this.map = ThreadLocal.withInitial(() -> new CollisionMap(mapData));
		Map<Integer, Set<Transport>> loadedTransports = TransportLoader.loadAllFromResources();
		remapPohDestinations(loadedTransports);
		this.allTransports = flatten(loadedTransports);
		this.transportAvailabilityWithoutBank = new TransportAvailability.Builder(allTransports.length).build();
		this.transportAvailabilityWithBank = new TransportAvailability.Builder(allTransports.length).build();
		this.allDestinations = Destination.loadAllFromResources();
		this.filteredDestinations = filterDestinations(allDestinations);
		this.destinations = allDestinations;
		this.bankRequirements = Destination.loadBankRequirementsFromResources();
	}

	protected PathfinderConfig(Client client, ShortestPathConfig config,
		SplitFlagMap mapData, Map<Integer, Set<Transport>> allTransports,
		Map<String, Set<Integer>> allDestinations, Map<String, Set<Integer>> filteredDestinations,
		Map<Integer, DestinationRequirements> bankRequirements)
	{
		this.client = client;
		this.config = config;
		this.transportTypeConfig = new TransportTypeConfig(config);
		this.mapData = mapData;
		this.map = ThreadLocal.withInitial(() -> new CollisionMap(this.mapData));
		this.allTransports = flatten(allTransports);
		this.transportAvailabilityWithoutBank = new TransportAvailability.Builder(this.allTransports.length).build();
		this.transportAvailabilityWithBank = new TransportAvailability.Builder(this.allTransports.length).build();
		this.allDestinations = allDestinations;
		this.filteredDestinations = filteredDestinations;
		this.destinations = allDestinations;
		this.bankRequirements = bankRequirements;
	}

	/**
	 * Pure combat-level formula, extracted for testability.
	 */
	static int computeCombatLevel(int attack, int strength, int defence, int hitpoints, int magic, int ranged, int prayer)
	{
		// Integer division is intentional here — it matches the OSRS floor(x/2) steps in the formula.
		double base = 0.25 * (defence + hitpoints + Math.floorDiv(prayer, 2));
		double melee = (13 * (attack + strength)) / 40.0;
		double range = (13 * (3 * Math.floorDiv(ranged, 2))) / 40.0;
		double mage = (13 * (3 * Math.floorDiv(magic, 2))) / 40.0;
		return (int) Math.floor(base + Math.max(Math.max(melee, range), Math.max(melee, mage)));
	}

	static String getPlantedSpiritTreeName(int x, int y)
	{
		if (x >= 3058 && x <= 3062 && y >= 3256 && y <= 3260)
		{
			return "Port Sarim";
		}
		if (x >= 2611 && x <= 2615 && y >= 3855 && y <= 3860)
		{
			return "Etceteria";
		}
		if (x >= 2800 && x <= 2804 && y >= 3201 && y <= 3205)
		{
			return "Brimhaven";
		}
		if (x >= 1691 && x <= 1695 && y >= 3540 && y <= 3544)
		{
			return "Hosidius";
		}
		if (x >= 1251 && x <= 1255 && y >= 3748 && y <= 3752)
		{
			return "Farming Guild";
		}
		return null;
	}

	public CollisionMap getMap()
	{
		return map.get();
	}

	/**
	 * WARNING: This method collapses the banked/unbanked transport distinction into a single view.
	 * <p>
	 * It exists only for legacy display-oriented callers such as overlays which want a coarse
	 * "currently relevant" set of transports to render. It must not be used for path-state-sensitive
	 * logic, because transport availability now depends on whether a path has visited a bank.
	 * <p>
	 * Use {@link #getTransportAvailability(boolean)}, {@link #getTransportsPacked(boolean)}, or
	 * {@link #getUsableTeleports(boolean)} for pathfinding and path analysis code.
	 */
	public PrimitiveIntHashMap<Transport[]> getTransports()
	{
		return getTransportAvailability(includeBankPath).getDisplayTransports();
	}

	public PrimitiveIntHashMap<Transport[]> getTransportsPacked(boolean bankVisited)
	{
		return getTransportAvailability(bankVisited).getTransportsPacked();
	}

	public Transport[] getUsableTeleports(boolean bankVisited)
	{
		return getTransportAvailability(bankVisited).getUsableTeleports();
	}

	public TransportAvailability getTransportAvailability(boolean bankVisited)
	{
		return bankVisited ? transportAvailabilityWithBank : transportAvailabilityWithoutBank;
	}

	public boolean isBankPathEnabled()
	{
		return includeBankPath;
	}

	public boolean hasDestination(String destinationType)
	{
		return destinations.containsKey(destinationType);
	}

	public Set<Integer> getDestinations(String destinationType)
	{
		return destinations.get(destinationType);
	}

	/**
	 * Whether standing on this tile may flip the path into {@code bankVisited} (inventory-from-bank) state.
	 */
	public boolean bankAccessible(int packedPosition)
	{
		return accessibleBankTiles.contains(packedPosition);
	}

	public void refresh()
	{
		calculationCutoffMillis = (long) config.calculationCutoff() * Constants.GAME_TICK_LENGTH;
		avoidWilderness = ShortestPathPlugin.override("avoidWilderness", config.avoidWilderness());
		usePoh = ShortestPathPlugin.override("usePoh", config.usePoh());
		leagueModeState.refresh(client);

		// Refresh transport type enabled states
		transportTypeConfig.refresh();
		// POH-specific settings
		usePohFairyRing = ShortestPathPlugin.override("usePohFairyRing", config.usePohFairyRing());
		usePohSpiritTree = ShortestPathPlugin.override("usePohSpiritTree", config.usePohSpiritTree());
		usePohMountedItems = ShortestPathPlugin.override("usePohMountedItems", config.usePohMountedItems());
		usePohObelisk = ShortestPathPlugin.override("usePohObelisk", config.usePohObelisk());
		pohJewelleryBoxTier = ShortestPathPlugin.override("pohJewelleryBoxTier", config.pohJewelleryBoxTier());

		// Other settings (useTeleportationItems is now managed by transportTypeConfig)
		currencyThreshold = ShortestPathPlugin.override("currencyThreshold", config.currencyThreshold());
		includeBankPath = ShortestPathPlugin.override("includeBankPath", config.includeBankPath());

		// Note: Transport type costs are now managed by transportTypeConfig.getCost()
		costConsumableTeleportationItems = ShortestPathPlugin.override("costConsumableTeleportationItems", config.costConsumableTeleportationItems());

		if (GameState.LOGGED_IN.equals(client.getGameState()))
		{
			isOnSailingBoat = client.getVarbitValue(VarbitID.SAILING_BOARDED_BOAT) != 0;

			int i = 0;
			for (; i < Skill.values().length; i++)
			{
				boostedSkillLevelsAndMore[i] = client.getBoostedSkillLevel(Skill.values()[i]);
			}
			boostedSkillLevelsAndMore[i++] = client.getTotalLevel(); // skill total level
			boostedSkillLevelsAndMore[i++] = getCombatLevel(); // combat level
			boostedSkillLevelsAndMore[i] = client.getVarpValue(VarPlayerID.QP); // quest points

			refreshTransports();
		}

		refreshDestinations();
		rebuildAccessibleBankTiles();
	}

	private void refreshDestinations()
	{
		destinations = avoidWilderness ? filteredDestinations : allDestinations;
	}

	private void rebuildAccessibleBankTiles()
	{
		Set<Integer> bankLocs = destinations.get("bank");
		if (bankLocs == null)
		{
			accessibleBankTiles = Set.of();
			return;
		}
		if (!GameState.LOGGED_IN.equals(client.getGameState()))
		{
			accessibleBankTiles = Set.copyOf(bankLocs);
			return;
		}
		Set<Integer> acc = new HashSet<>(bankLocs.size());
		for (Integer p : bankLocs)
		{
			DestinationRequirements req = bankRequirements.getOrDefault(p, DestinationRequirements.EMPTY);
			if (satisfiesBankDestinationRequirements(req))
			{
				acc.add(p);
			}
		}
		accessibleBankTiles = Collections.unmodifiableSet(acc);
	}

	/**
	 * Quest/skill/var gates for bank tiles (not used for transport overlays).
	 */
	private boolean satisfiesBankDestinationRequirements(DestinationRequirements dr)
	{
		if (dr == null || dr.isEmpty())
		{
			return true;
		}
		int[] requiredLevels = dr.getSkillLevels();
		for (int i = 0; i < boostedSkillLevelsAndMore.length; i++)
		{
			int need = i < requiredLevels.length ? requiredLevels[i] : 0;
			if (boostedSkillLevelsAndMore[i] < need)
			{
				return false;
			}
		}
		for (Quest quest : dr.getQuests())
		{
			if (!QuestState.FINISHED.equals(getQuestState(quest)))
			{
				return false;
			}
		}
		for (VarRequirement req : dr.getVarbits())
		{
			if (!req.checkValue(client.getVarbitValue(req.getId())))
			{
				return false;
			}
		}
		for (VarRequirement req : dr.getVarPlayers())
		{
			if (!req.checkValue(client.getVarpValue(req.getId())))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Changes to the config might have invalidated some locations, e.g. those in the wilderness
	 */
	public void filterLocations(Set<Integer> locations, boolean canReviveFiltered)
	{
		if (avoidWilderness)
		{
			locations.removeIf(location ->
			{
				boolean inWilderness = WildernessChecker.isInWilderness(location);
				if (inWilderness)
				{
					filteredTargets.add(location);
				}
				return inWilderness;
			});
			// If we ended up with no valid locations we re-include the filtered locations
			if (locations.isEmpty())
			{
				locations.addAll(filteredTargets);
				filteredTargets.clear();
			}
		}
		else if (canReviveFiltered)
		{ // Re-include previously filtered locations
			locations.addAll(filteredTargets);
			filteredTargets.clear();
		}
	}

	/**
	 * Returns the user-configured additional cost for a given transport
	 */
	public int getAdditionalTransportCost(Transport transport)
	{
		if (transport.isConsumable() && TransportType.TELEPORTATION_ITEM.equals(transport.getType()))
		{
			return costConsumableTeleportationItems;
		}
		if (transport.isConsumable() && TransportType.QUETZAL_WHISTLE.equals(transport.getType()))
		{
			return transportTypeConfig.getCost(transport.getType()) + costConsumableTeleportationItems;
		}
		return transportTypeConfig.getCost(transport.getType());
	}

	/** Adapter hook for immutable per-search walking penalties; upstream behavior defaults to zero. */
	public int getAdditionalWalkingCost(int packedDestination)
	{
		return 0;
	}

	/**
	 * Returns the differential cost for a transport type that shares destinations with another type.
	 * This cost is only applied when the transport is in delayed-visit competition with its partner,
	 * not globally against all other transport types.
	 */
	public int getDifferentialCost(Transport transport)
	{
		if (transport.getType().differentialCostFunction() != null)
		{
			return transport.getType().differentialCostFunction().apply(config);
		}
		return 0;
	}

	static Map<String, Set<Integer>> filterDestinations(Map<String, Set<Integer>> allDestinations)
	{
		Map<String, Set<Integer>> filteredDestinations = new HashMap<>(allDestinations.size());
		for (Map.Entry<String, Set<Integer>> entry : allDestinations.entrySet())
		{
			String destinationType = entry.getKey();
			Set<Integer> usableDestinations = new HashSet<>(entry.getValue().size());
			for (Integer destination : entry.getValue())
			{
				// We filter based on whether the destination is inside or outside wilderness
				if (!WildernessChecker.isInWilderness(destination))
				{
					usableDestinations.add(destination);
				}
			}
			// If all destinations of a destination type have been filtered away then we don't add the entry
			if (!usableDestinations.isEmpty())
			{
				// If no destinations of a destination type have been filtered away then we re-use the same set reference
				filteredDestinations.put(destinationType, usableDestinations);
			}
		}
		return filteredDestinations;
	}

	private void refreshTransports()
	{
		if (!Thread.currentThread().equals(client.getClientThread()))
		{
			return; // Has to run on the client thread; data will be refreshed when path finding commences
		}

		// Fairy ring staff/diary requirements are enforced later in hasRequiredItems().
		transportTypeConfig.disableUnless(TransportType.FAIRY_RING,
			client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST) > 39);
		transportTypeConfig.disableUnless(TransportType.GNOME_GLIDER,
			QuestState.FINISHED.equals(getQuestState(Quest.THE_GRAND_TREE)));
		transportTypeConfig.disableUnless(TransportType.MAGIC_MUSHTREE,
			QuestState.FINISHED.equals(getQuestState(Quest.BONE_VOYAGE)));
		transportTypeConfig.disableUnless(TransportType.SPIRIT_TREE,
			QuestState.FINISHED.equals(getQuestState(Quest.TREE_GNOME_VILLAGE)));

		TransportAvailability.Builder withoutBank = new TransportAvailability.Builder(allTransports.length);
		TransportAvailability.Builder withBank = new TransportAvailability.Builder(allTransports.length);
		for (Transport transport : allTransports)
		{
			for (Quest quest : transport.getQuests())
			{
				try
				{
					questStates.put(quest, getQuestState(quest));
				}
				catch (NullPointerException ignored)
				{
				}
			}

			for (VarRequirement varRequirement : transport.getVarRequirements())
			{
				if (varRequirement.isVarbit())
				{
					varbitValues.put(varRequirement.getId(), client.getVarbitValue(varRequirement.getId()));
				}
				else
				{
					varPlayerValues.put(varRequirement.getId(), client.getVarpValue(varRequirement.getId()));
				}
			}

			if (!useTransport(transport))
			{
				continue;
			}

			boolean usableWithoutBank = hasRequiredItems(transport, true, true, false, true);
			boolean usableWithBank = hasRequiredItems(transport, true, true, includeBankPath, true);
			if (usableWithoutBank)
			{
				withoutBank.add(transport);
			}
			if (usableWithBank)
			{
				withBank.add(transport);
			}
		}

		withoutBank.remapPohTransports();
		withBank.remapPohTransports();
		transportAvailabilityWithoutBank = withoutBank.build();
		transportAvailabilityWithBank = withBank.build();
	}

	public boolean avoidWilderness(int packedPosition, int packedNeighborPosition, boolean targetInWilderness)
	{
		return avoidWilderness
			&& !targetInWilderness
			&& !WildernessChecker.isInWilderness(packedPosition)
			&& WildernessChecker.isInWilderness(packedNeighborPosition);
	}

	/**
	 * League-mode neighbour gate: parallels {@link #avoidWilderness} but
	 * blocks crossing into the always-blocked Misthalin region. Always
	 * returns {@code false} on non-seasonal worlds so vanilla pathfinding is
	 * unaffected.
	 */
	public boolean avoidBlockedRegion(int packedPosition, int packedNeighborPosition, boolean targetInBlockedRegion)
	{
		if (!leagueModeState.isSeasonal())
		{
			return false;
		}
		return !targetInBlockedRegion
			&& !leagueModeState.isInBlockedRegion(packedPosition)
			&& leagueModeState.isInBlockedRegion(packedNeighborPosition);
	}

	/**
	 * Whether both endpoints of the supplied transport are in unlocked
	 * regions for the current league state. Always-unlocked tiles
	 * (NEUTRAL, Varlamore, Karamja) pass through unchanged on any world.
	 *
	 * <p>If the transport declares a {@link Transport#getRegionOverride()
	 * region override}, it replaces the chunk-classifier result for the
	 * destination endpoint. Used for shortcuts whose destination chunk
	 * sits in a different region than the wiki classifies the shortcut
	 * under (e.g. Trollheim Wilderness climb — destination chunk is
	 * Wilderness, but the shortcut is wiki-listed as Asgarnia).
	 */
	private boolean isTransportRegionAllowed(Transport transport)
	{
		if (!leagueModeState.isSeasonal())
		{
			return true;
		}
		LeagueRegion origin = LeagueRegionChecker.getRegion(transport.getOrigin());
		if (!leagueModeState.isUnlocked(origin))
		{
			return false;
		}
		LeagueRegion destination = transport.getRegionOverride() != null
			? transport.getRegionOverride()
			: LeagueRegionChecker.getRegion(transport.getDestination());
		return leagueModeState.isUnlocked(destination);
	}

	/**
	 * Remaps POH transport destinations to the house landing tile.
	 * Transports that arrive inside the POH (e.g., fairy ring DIQ, spirit tree "Your house")
	 * are remapped so chaining with other POH transports is possible.
	 * Called once at load time since Transport objects in allTransports are shared references.
	 */
	private static Transport[] flatten(Map<Integer, Set<Transport>> transports)
	{
		List<Transport> all = new ArrayList<>();
		for (Set<Transport> set : transports.values())
		{
			all.addAll(set);
		}
		return all.toArray(new Transport[0]);
	}

	static void remapPohDestinations(Map<Integer, Set<Transport>> transports)
	{
		int pohLanding = WorldPointUtil.packWorldPoint(1923, 5709, 0);
		for (Set<Transport> transportSet : transports.values())
		{
			for (Transport transport : transportSet)
			{
				int destination = transport.getDestination();
				int destX = WorldPointUtil.unpackWorldX(destination);
				int destY = WorldPointUtil.unpackWorldY(destination);
				if (destination != pohLanding && ShortestPathPlugin.isInsidePoh(destX, destY))
				{
					transport.setDestination(pohLanding);
				}
			}
		}
	}

	public QuestState getQuestState(Quest quest)
	{
		return quest.getState(client);
	}

	private boolean completedQuests(Transport transport)
	{
		for (Quest quest : transport.getQuests())
		{
			if (!QuestState.FINISHED.equals(questStates.getOrDefault(quest, QuestState.NOT_STARTED)))
			{
				return false;
			}
		}
		return true;
	}

	public boolean varbitChecks(Transport transport)
	{
		for (VarRequirement varRequirement : transport.getVarbits())
		{
			if (!varRequirement.check(varbitValues))
			{
				return true;
			}
		}
		return false;
	}

	public boolean varPlayerChecks(Transport transport)
	{
		for (VarRequirement varRequirement : transport.getVarPlayers())
		{
			if (!varRequirement.check(varPlayerValues))
			{
				return true;
			}
		}
		return false;
	}

	private boolean useTransport(Transport transport)
	{
		// Sailing: suppress teleports while the player is aboard a boat.
		// We don't model sailing navigation, so teleporting away mid-ocean would produce
		// confusing suggestions. Pathfinding resumes normally after disembarking.
		if (isOnSailingBoat && transport.getType().isTeleport())
		{
			return false;
		}

		// Master POH gate - if POH is disabled, reject all POH transports
		if (!usePoh)
		{
			int originX = WorldPointUtil.unpackWorldX(transport.getOrigin());
			int originY = WorldPointUtil.unpackWorldY(transport.getOrigin());
			int destX = WorldPointUtil.unpackWorldX(transport.getDestination());
			int destY = WorldPointUtil.unpackWorldY(transport.getDestination());
			if (ShortestPathPlugin.isInsidePoh(originX, originY) || ShortestPathPlugin.isInsidePoh(destX, destY))
			{
				return false;
			}
		}

		// League region gate: in seasonal mode, drop transports that touch the
		// always-blocked region or a region the player has not unlocked.
		if (!isTransportRegionAllowed(transport))
		{
			return false;
		}

		final boolean isQuestLocked = transport.isQuestLocked();
		TransportType type = transport.getType();

		// Check if transport type is enabled in config
		if (!transportTypeConfig.isEnabled(type))
		{
			return false;
		}

		// Handle POH variants for types that have them
		if (!checkPohVariant(transport, type))
		{
			return false;
		}

		// Handle special cases for teleportation items and seasonal transports
		if (!checkTeleportationItemRules(transport, type))
		{
			return false;
		}

		// Handle jewellery box tier filtering
		if (TransportType.TELEPORTATION_BOX.equals(type))
		{
			if (!checkJewelleryBoxTier(transport))
			{
				return false;
			}
		}

		if (!hasRequiredLevels(transport))
		{
			return false;
		}

		if (isQuestLocked && !completedQuests(transport))
		{
			return false;
		}

		if (varbitChecks(transport))
		{
			return false;
		}

		if (varPlayerChecks(transport))
		{
			return false;
		}

		if (TransportType.SPIRIT_TREE.equals(type) || TransportType.SEASONAL_TRANSPORTS.equals(type))
		{
			return checkPlantedSpiritTrees(transport);
		}

		return true;
	}

	private boolean checkPlantedSpiritTrees(Transport transport)
	{
		int originX = WorldPointUtil.unpackWorldX(transport.getOrigin());
		int originY = WorldPointUtil.unpackWorldY(transport.getOrigin());

		// Check planted spirit tree origins (travel FROM a planted tree)
		if (isPlantedSpiritTreeAllowed(originX, originY))
		{
			return false;
		}

		// Check planted spirit tree destinations (travel TO a planted tree)
		int destX = WorldPointUtil.unpackWorldX(transport.getDestination());
		int destY = WorldPointUtil.unpackWorldY(transport.getDestination());

		return !isPlantedSpiritTreeAllowed(destX, destY);
	}

	/**
	 * Checks POH-specific transport variants (fairy ring, spirit tree, obelisk inside POH).
	 * Returns false if the transport is a POH variant and that variant is disabled.
	 */
	private boolean checkPohVariant(Transport transport, TransportType type)
	{
		int originX = WorldPointUtil.unpackWorldX(transport.getOrigin());
		int originY = WorldPointUtil.unpackWorldY(transport.getOrigin());
		int destX = WorldPointUtil.unpackWorldX(transport.getDestination());
		int destY = WorldPointUtil.unpackWorldY(transport.getDestination());

		if (!ShortestPathPlugin.isInsidePoh(originX, originY) && !ShortestPathPlugin.isInsidePoh(destX, destY))
		{
			return true; // Not a POH transport
		}

		// POH fairy ring
		if (TransportType.FAIRY_RING.equals(type))
		{
			return usePohFairyRing;
		}
		// POH spirit tree
		if (TransportType.SPIRIT_TREE.equals(type))
		{
			return usePohSpiritTree;
		}
		// POH obelisk
		if (TransportType.WILDERNESS_OBELISK.equals(type))
		{
			return usePohObelisk;
		}

		return true;
	}

	/**
	 * Checks teleportation item rules (consumable vs non-consumable, inventory settings).
	 * Returns false if the transport should be filtered out based on teleportation item settings.
	 */
	private boolean checkTeleportationItemRules(Transport transport, TransportType type)
	{
		if (!TransportType.TELEPORTATION_ITEM.equals(type)
			&& !TransportType.SEASONAL_TRANSPORTS.equals(type)
			&& !TransportType.QUETZAL_WHISTLE.equals(type))
		{
			return true; // Not a teleportation item type
		}

		switch (transportTypeConfig.getTeleportationItemSetting())
		{
			case ALL:
				return true;
			case ALL_NON_CONSUMABLE:
			case UNLOCKED_NON_CONSUMABLE:
			case INVENTORY_NON_CONSUMABLE:
			case INVENTORY_AND_BANK_NON_CONSUMABLE:
				return !transport.isConsumable();
			case UNLOCKED:
			case INVENTORY:
			case INVENTORY_AND_BANK:
				return true; // Will be checked later by hasRequiredItems
			case NONE:
				return false;
		}
		return true;
	}

	/**
	 * Checks if a TELEPORTATION_BOX transport should be used based on POH settings.
	 * Handles jewellery box tiers and mounted items.
	 */
	private boolean checkJewelleryBoxTier(Transport transport)
	{
		String objectInfo = transport.getObjectInfo();
		if (objectInfo == null)
		{
			return false;
		}

		// Check if this is a mounted item (glory, xeric's, digsite, mythical cape)
		boolean isMountedGlory = objectInfo.contains("Amulet of Glory");
		boolean isMountedItem = isMountedGlory ||
			objectInfo.contains("Xeric's Talisman") ||
			objectInfo.contains("Digsite") ||
			objectInfo.contains("Mythical cape");

		if (isMountedItem)
		{
			// If mounted glory and ornate jewellery box is enabled, skip the glory
			// because the ornate box already covers all 4 destinations with correct prefixes
			if (isMountedGlory && JewelleryBoxTier.ORNATE.equals(pohJewelleryBoxTier))
			{
				return false;
			}
			return usePohMountedItems;
		}

		// Filter jewellery boxes by tier
		if (JewelleryBoxTier.NONE.equals(pohJewelleryBoxTier))
		{
			return false;
		}

		// Basic box (37492): destinations 1-9
		if (objectInfo.contains("Basic Jewellery Box 37492"))
		{
			return true; // All tiers include basic
		}

		// Fancy box (37501): destinations A-J
		if (objectInfo.contains("Fancy Jewellery Box 37501"))
		{
			return JewelleryBoxTier.FANCY.equals(pohJewelleryBoxTier) ||
				JewelleryBoxTier.ORNATE.equals(pohJewelleryBoxTier);
		}

		// Ornate box (37520): destinations K-R
		if (objectInfo.contains("Ornate Jewellery Box 37520"))
		{
			return JewelleryBoxTier.ORNATE.equals(pohJewelleryBoxTier);
		}

		return false;
	}

	/**
	 * Checks if the player has all the required skill levels for the transport
	 */
	private boolean hasRequiredLevels(Transport transport)
	{
		// In leagues some skills are disabled so the max total level is lower than
		// the standard 2376. Holding the item (e.g. Max cape) already proves the
		// player is maxed for the available skills, so skip the total-level check.
		final int totalLevelIndex = Skill.values().length;
		int[] requiredLevels = transport.getSkillLevels();
		for (int i = 0; i < boostedSkillLevelsAndMore.length; i++)
		{
			if (leagueModeState.isSeasonal() && i == totalLevelIndex)
			{
				continue;
			}
			int boostedLevel = boostedSkillLevelsAndMore[i];
			int requiredLevel = requiredLevels[i];
			if (boostedLevel < requiredLevel)
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Checks if the player has all the required equipment and inventory items for the transport
	 */
	private boolean hasRequiredItems(
		Transport transport,
		boolean checkInventory,
		boolean checkEquipment,
		boolean checkBank,
		boolean checkRunePouch)
	{
		if (TransportType.TELEPORTATION_ITEM.equals(transport.getType()) ||
			TransportType.SEASONAL_TRANSPORTS.equals(transport.getType()) ||
			TransportType.QUETZAL_WHISTLE.equals(transport.getType()))
		{
			switch (transportTypeConfig.getTeleportationItemSetting())
			{
				case ALL:
				case ALL_NON_CONSUMABLE:
				case UNLOCKED:
				case UNLOCKED_NON_CONSUMABLE:
					return true;
				case NONE:
					return false;
				default:
					break;
			}
		}

		// Fairy rings require Dramen/Lunar staff unless Lumbridge Elite diary is complete
		if (TransportType.FAIRY_RING.equals(transport.getType()))
		{
			int lumbridgeDiaryComplete = varbitValues.getOrDefault(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE, 0);
			if (lumbridgeDiaryComplete != 1)
			{
				if (!hasRequiredItems(DRAMEN_STAFF, checkInventory, checkEquipment, checkBank, checkRunePouch))
				{
					return false;
				}
			}
		}

		return hasRequiredItems(transport.getItemRequirements(),
			checkInventory, checkEquipment, checkBank, checkRunePouch);
	}

	/**
	 * Checks if the player has all the required equipment and inventory items for the transport
	 */
	private boolean hasRequiredItems(
		TransportItems transportItems,
		boolean checkInventory,
		boolean checkEquipment,
		boolean checkBank,
		boolean checkRunePouch)
	{
		if (transportItems == null)
		{
			return true;
		}
		itemsAndQuantities.clear();

		if (checkInventory)
		{
			ItemContainer inventory = client.getItemContainer(InventoryID.INV);
			if (inventory != null)
			{
				for (Item item : inventory.getItems())
				{
					if (item.getId() >= 0 && item.getQuantity() > 0)
					{
						itemsAndQuantities.put(item.getId(), item.getQuantity());
					}
				}
			}
		}

		if (checkEquipment)
		{
			ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
			if (equipment != null)
			{
				for (Item item : equipment.getItems())
				{
					if (item.getId() >= 0 && item.getQuantity() > 0)
					{
						itemsAndQuantities.put(item.getId(), item.getQuantity());
					}
				}
			}
		}

		if (checkBank)
		{
			TeleportationItem teleportSetting = transportTypeConfig.getTeleportationItemSetting();
			if (bank != null
				&& (TeleportationItem.INVENTORY_AND_BANK.equals(teleportSetting)
				|| TeleportationItem.INVENTORY_AND_BANK_NON_CONSUMABLE.equals(teleportSetting)))
			{
				for (Item item : bank.getItems())
				{
					if (item.getId() >= 0 && item.getQuantity() > 0)
					{
						itemsAndQuantities.put(item.getId(), item.getQuantity());
					}
				}
			}
		}

		if (checkRunePouch)
		{
			if (RUNE_POUCHES.stream().anyMatch(itemsAndQuantities::containsKey))
			{
				EnumComposition runePouchEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
				for (int i = 0; i < RUNE_POUCH_RUNE_VARBITS.length; i++)
				{
					int runeEnumId = client.getVarbitValue(RUNE_POUCH_RUNE_VARBITS[i]);
					int runeId = runeEnumId > 0 ? runePouchEnum.getIntValue(runeEnumId) : 0;
					int runeAmount = client.getVarbitValue(RUNE_POUCH_AMOUNT_VARBITS[i]);
					if (runeId > 0 && runeAmount > 0)
					{
						itemsAndQuantities.put(runeId, runeAmount);
					}
				}
			}
		}

		boolean usingStaff = false;
		boolean usingOffhand = false;
		for (ItemRequirement req : transportItems.getRequirements())
		{
			boolean missing = true;
			int requiredQuantity = req.getQuantity();
			if (req.getItemIds() != null)
			{
				for (int itemId : req.getItemIds())
				{
					int quantity = itemsAndQuantities.getOrDefault(itemId, 0);
					if (requiredQuantity > 0 && quantity >= requiredQuantity || requiredQuantity == 0 && quantity == 0)
					{
						if (CURRENCIES.contains(itemId) && requiredQuantity > currencyThreshold)
						{
							return false;
						}
						missing = false;
						break;
					}
				}
			}
			if (missing && !usingStaff && req.getStaffIds() != null)
			{
				for (int itemId : req.getStaffIds())
				{
					int quantity = itemsAndQuantities.getOrDefault(itemId, 0);
					if (requiredQuantity > 0 && quantity >= 1 || requiredQuantity == 0 && quantity == 0)
					{
						usingStaff = true;
						missing = false;
						break;
					}
				}
			}
			if (missing && !usingOffhand && req.getOffhandIds() != null)
			{
				for (int itemId : req.getOffhandIds())
				{
					int quantity = itemsAndQuantities.getOrDefault(itemId, 0);
					if (requiredQuantity > 0 && quantity >= 1 || requiredQuantity == 0 && quantity == 0)
					{
						usingOffhand = true;
						missing = false;
						break;
					}
				}
			}
			if (missing)
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Calculates the combat level of the player
	 */
	private int getCombatLevel()
	{
		int attack = client.getRealSkillLevel(Skill.ATTACK);
		int strength = client.getRealSkillLevel(Skill.STRENGTH);
		int defence = client.getRealSkillLevel(Skill.DEFENCE);
		int hitpoints = client.getRealSkillLevel(Skill.HITPOINTS);
		int magic = client.getRealSkillLevel(Skill.MAGIC);
		int ranged = client.getRealSkillLevel(Skill.RANGED);
		int prayer = client.getRealSkillLevel(Skill.PRAYER);
		return computeCombatLevel(attack, strength, defence, hitpoints, magic, ranged, prayer);
	}

	private boolean isPlantedSpiritTreeAllowed(int x, int y)
	{
		String treeName = getPlantedSpiritTreeName(x, y);
		if (treeName == null)
		{
			return false; // 
		}
		if (availableSpiritTrees == null)
		{
			return true;
		}
		return !availableSpiritTrees.contains(treeName);
	}
}
