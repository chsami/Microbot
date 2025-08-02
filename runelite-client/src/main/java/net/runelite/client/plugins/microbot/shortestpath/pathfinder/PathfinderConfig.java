package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import java.util.stream.IntStream;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.itemcharges.ItemChargeConfig;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.shortestpath.*;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.cache.Rs2QuestCache;
import net.runelite.client.plugins.microbot.util.cache.Rs2SkillCache;
import net.runelite.client.plugins.microbot.util.cache.Rs2SpiritTreeCache;
import net.runelite.client.plugins.microbot.util.cache.Rs2VarPlayerCache;
import net.runelite.client.plugins.microbot.util.cache.Rs2VarbitCache;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.magic.RuneFilter;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.runelite.client.plugins.microbot.shortestpath.TransportType.TELEPORTATION_ITEM;
import static net.runelite.client.plugins.microbot.shortestpath.TransportType.TELEPORTATION_SPELL;
@Slf4j
public class PathfinderConfig {
    private static final WorldArea WILDERNESS_ABOVE_GROUND = new WorldArea(2944, 3523, 448, 448, 0);
    private static final WorldArea WILDERNESS_ABOVE_GROUND_LEVEL_19 = new WorldArea(2944, 3672, 448, 448, 0);
    private static final WorldArea WILDERNESS_ABOVE_GROUND_LEVEL_29 = new WorldArea(2944, 3752, 448, 448, 0);
    private static final WorldArea WILDERNESS_UNDERGROUND = new WorldArea(2944, 9918, 320, 442, 0);
    private static final WorldArea WILDERNESS_UNDERGROUND_LEVEL_19 = new WorldArea(2944, 10067, 320, 442, 0);
    private static final WorldArea WILDERNESS_UNDERGROUND_LEVEL_29 = new WorldArea(2944, 10147, 320, 442, 0);
    private static final WorldArea FEROX_ENCLAVE_1 = new WorldArea(3123, 3622, 2, 10, 0);
    private static final WorldArea FEROX_ENCLAVE_2 = new WorldArea(3125, 3617, 16, 23, 0);
    private static final WorldArea FEROX_ENCLAVE_3 = new WorldArea(3138, 3636, 18, 10, 0);
    private static final WorldArea FEROX_ENCLAVE_4 = new WorldArea(3141, 3625, 14, 11, 0);
    private static final WorldArea FEROX_ENCLAVE_5 = new WorldArea(3141, 3619, 7, 6, 0);
    private static final WorldArea NOT_WILDERNESS_1 = new WorldArea(2997, 3525, 34, 9, 0);
    private static final WorldArea NOT_WILDERNESS_2 = new WorldArea(3005, 3534, 21, 10, 0);
    private static final WorldArea NOT_WILDERNESS_3 = new WorldArea(3000, 3534, 5, 5, 0);
    private static final WorldArea NOT_WILDERNESS_4 = new WorldArea(3031, 3525, 2, 2, 0);

    private final SplitFlagMap mapData;
    private final ThreadLocal<CollisionMap> map;
    /** All transports by origin {@link WorldPoint}. The null key is used for transports centered on the player. */
	@Getter
    private final Map<WorldPoint, Set<Transport>> allTransports;
    @Setter
    private Set<Transport> usableTeleports;
    private final List<WorldPoint> filteredTargets = new ArrayList<>(4);

    @Getter
    private ConcurrentHashMap<WorldPoint, Set<Transport>> transports;
    // Copy of transports with packed positions for the hotpath; lists are not copied and are the same reference in both maps
    @Getter
    @Setter
    private PrimitiveIntHashMap<Set<Transport>> transportsPacked;

    private final Client client;
    private final ShortestPathConfig config;

	private final List<QuestState> questStateOrder = Arrays.asList(
		QuestState.NOT_STARTED,
		QuestState.IN_PROGRESS,
		QuestState.FINISHED
	);

    @Getter
    private long calculationCutoffMillis;
    @Getter
    private boolean avoidWilderness;
    private boolean useAgilityShortcuts,
            useGrappleShortcuts,
            useBoats,
            useCanoes,
            useCharterShips,
            useShips,
            useFairyRings,
            useGnomeGliders,
            useMinecarts,
            useQuetzals,
            useSpiritTrees,
            useTeleportationLevers,
            useTeleportationMinigames,
            useTeleportationPortals,
            useTeleportationSpells,
            useMagicCarpets,
            useWildernessObelisks;
    //START microbot variables
    @Getter
    private int distanceBeforeUsingTeleport;
    @Getter
    private final List<Restriction> resourceRestrictions;
    @Getter
    private List<Restriction> customRestrictions;
    @Getter
    private Set<Integer> restrictedPointsPacked;
    private boolean useNpcs;
    //END microbot variables
    private TeleportationItem useTeleportationItems;

    @Getter
    @Setter
    // Used for manual calculating paths without teleport & items in caves
    private boolean ignoreTeleportAndItems = false;
    
    @Getter
    @Setter
    // Used to include bank items when searching for item requirements
    private boolean useBankItems = false;

    public PathfinderConfig(SplitFlagMap mapData, Map<WorldPoint, Set<Transport>> transports,
                            List<Restriction> restrictions,
                            Client client, ShortestPathConfig config) {
        this.mapData = mapData;
        this.map = ThreadLocal.withInitial(() -> new CollisionMap(this.mapData));
        this.allTransports = transports;
        this.usableTeleports = new HashSet<>(allTransports.size() / 20);
        this.transports = new ConcurrentHashMap<>(allTransports.size() / 2);
        this.transportsPacked = new PrimitiveIntHashMap<>(allTransports.size() / 2);
        this.client = client;
        this.config = config;
        //START microbot variables
        this.resourceRestrictions = restrictions;
        this.customRestrictions = new ArrayList<>();
        this.restrictedPointsPacked = new HashSet<>();
        //END microbot variables
    }

    public CollisionMap getMap() {
        return map.get();
    }

    public void refresh(WorldPoint target) {
        calculationCutoffMillis = config.calculationCutoff() * Constants.GAME_TICK_LENGTH;
        avoidWilderness = ShortestPathPlugin.override("avoidWilderness", config.avoidWilderness());
        useAgilityShortcuts = ShortestPathPlugin.override("useAgilityShortcuts", config.useAgilityShortcuts());
        useGrappleShortcuts = ShortestPathPlugin.override("useGrappleShortcuts", config.useGrappleShortcuts());
        useBoats = ShortestPathPlugin.override("useBoats", config.useBoats());
        useCanoes = ShortestPathPlugin.override("useCanoes", config.useCanoes());
        useCharterShips = ShortestPathPlugin.override("useCharterShips", config.useCharterShips());
        useShips = ShortestPathPlugin.override("useShips", config.useShips());
        useFairyRings = ShortestPathPlugin.override("useFairyRings", config.useFairyRings());
        useGnomeGliders = ShortestPathPlugin.override("useGnomeGliders", config.useGnomeGliders());
        useMinecarts = ShortestPathPlugin.override("useMinecarts", config.useMinecarts());
        useQuetzals = ShortestPathPlugin.override("useQuetzals", config.useQuetzals());
        useSpiritTrees = ShortestPathPlugin.override("useSpiritTrees", config.useSpiritTrees());
        useTeleportationItems = ShortestPathPlugin.override("useTeleportationItems", config.useTeleportationItems());
        useTeleportationMinigames = ShortestPathPlugin.override("useTeleportationMinigames",config.useTeleportationMinigames());
        useTeleportationLevers = ShortestPathPlugin.override("useTeleportationLevers", config.useTeleportationLevers());
        useTeleportationPortals = ShortestPathPlugin.override("useTeleportationPortals", config.useTeleportationPortals());
        useTeleportationSpells = ShortestPathPlugin.override("useTeleportationSpells", config.useTeleportationSpells());
        useWildernessObelisks = ShortestPathPlugin.override("useWildernessObelisks", config.useWildernessObelisks());
        useMagicCarpets = ShortestPathPlugin.override("useMagicCarpets", config.useMagicCarpets());
        distanceBeforeUsingTeleport = ShortestPathPlugin.override("distanceBeforeUsingTeleports", config.distanceBeforeUsingTeleport());

        //START microbot variables
        useNpcs = config.useNpcs();
        //END microbot variables

        if (GameState.LOGGED_IN.equals(client.getGameState())) {
            refreshTransports(target);
            //START microbot variables
            refreshRestrictionData();
            
            // Do not switch back to inventory tab if we are inside of the telekinetic room in Mage Training Arena
            if (Rs2Player.getWorldLocation().getRegionID() != 13463) {
                Rs2Tab.switchTo(InterfaceTab.INVENTORY);
            }
            //END microbot variables
        }
    }

    /** Specialized method for only updating player-held item and spell transports */
    public void refreshTeleports(int packedLocation, int wildernessLevel) {
        Set<Transport> usableWildyTeleports = new HashSet<>(usableTeleports.size());
        if (ignoreTeleportAndItems) return;

        for (Transport teleport : usableTeleports) {
            if (wildernessLevel <= teleport.getMaxWildernessLevel()) {
                usableWildyTeleports.add(teleport);
            }
        }

        if (!usableWildyTeleports.isEmpty()) {
            // Added extra code to check if the key already exists
            // if the key already exists we append instead of overwriting
            // The issue was that the transport list would contain a transport object on the same
            // tile as the player, this would then be overwritten by the usableWildyTeleports
            // therefor losing the original transport object
            WorldPoint key = WorldPointUtil.unpackWorldPoint(packedLocation);
            Set<Transport> existingTeleports = transports.get(key);
            if (existingTeleports != null) {
                existingTeleports.addAll(usableWildyTeleports);
            } else {
                transports.put(key, usableWildyTeleports);
            }
            transportsPacked.put(packedLocation, usableWildyTeleports);
        }
    }

    public void filterLocations(Set<WorldPoint> locations, boolean canReviveFiltered) {
        if (avoidWilderness) {
            locations.removeIf(location -> {
                boolean inWilderness = PathfinderConfig.isInWilderness(location);
                if (inWilderness) {
                    filteredTargets.add(location);
                }
                return inWilderness;
            });
            // If we ended up with no valid locations we re-include the filtered locations
            if (locations.isEmpty()) {
                locations.addAll(filteredTargets);
                filteredTargets.clear();
            }
        } else if (canReviveFiltered) { // Re-include previously filtered locations
            locations.addAll(filteredTargets);
            filteredTargets.clear();
        }
    }
    
    
    /**
     * Refreshes transport data with optional target-based optimization.
     * 
     * @param target Optional target destination for optimized filtering (null for standard filtering)
     */
    private void refreshTransports(WorldPoint target) {
        useFairyRings &= !QuestState.NOT_STARTED.equals(Rs2Player.getQuestState(Quest.FAIRYTALE_II__CURE_A_QUEEN))
                && (Rs2Inventory.contains(ItemID.DRAMEN_STAFF, ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF)
                || Rs2Equipment.isWearing(ItemID.DRAMEN_STAFF)
                || Rs2Equipment.isWearing(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF)
                || (ShortestPathPlugin.getPathfinderConfig().useBankItems && (Rs2Bank.hasItem(ItemID.DRAMEN_STAFF)|| Rs2Bank.hasItem(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF)))
                || Microbot.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE) == 1);
        useGnomeGliders &= QuestState.FINISHED.equals(Rs2Player.getQuestState(Quest.THE_GRAND_TREE));
        useSpiritTrees &= QuestState.FINISHED.equals(Rs2Player.getQuestState(Quest.TREE_GNOME_VILLAGE));
        useQuetzals &= QuestState.FINISHED.equals(Rs2Player.getQuestState(Quest.TWILIGHTS_PROMISE));

        transports.clear();
        transportsPacked.clear();
        usableTeleports.clear();
         // Check spirit tree farming states for farmable spirit trees
        Rs2SpiritTreeCache.getInstance().update();       
        //Rs2SpiritTreeCache.logAllTreeStates();                     
        for (Map.Entry<WorldPoint, Set<Transport>> entry : allTransports.entrySet()) {
            WorldPoint point = entry.getKey();
            Set<Transport> usableTransports = new HashSet<>(entry.getValue().size());
            for (Transport transport : entry.getValue()) {
				// Mutate action
				updateActionBasedOnQuestState(transport);

                if (point == null && useTransport(transport)) {
                    usableTeleports.add(transport);
                } else if (useTransport(transport)) {
                    usableTransports.add(transport);
                }
            }

            if (point != null && !usableTransports.isEmpty()) {
                transports.put(point, usableTransports);
                transportsPacked.put(WorldPointUtil.packWorldPoint(point), usableTransports);
            }
        }
        
        // Filter similar transports based on distance when walk with banked transports is enabled
        if (useBankItems && config.maxSimilarTransportDistance() > 0) {            
            filterSimilarTransports(target);                                    
        }
    }

    public void refresh() {
        refresh(null);        
    }

    private void refreshRestrictionData() {
        restrictedPointsPacked.clear();
        List<Restriction> allRestrictions = Stream.concat(resourceRestrictions.stream(), customRestrictions.stream())
                .collect(Collectors.toList());

        allRestrictions.stream()
            .filter(entry -> {
                // Explicit restriction: no requirements
                if (entry.getQuests().isEmpty() && entry.getVarbits().isEmpty() && entry.getVarplayers().isEmpty() && !entry.isMembers() && Arrays.stream(entry.getSkillLevels()).allMatch(level -> level == 0) && entry.getItemIdRequirements().isEmpty()) {
                    return true;
                }

                // Members world check
                if (entry.isMembers() && !client.getWorldType().contains(WorldType.MEMBERS)) {
                    return true;
                }
                // Quest check
				if (entry.getQuests().entrySet().stream().anyMatch(qe -> {
					QuestState playerState = Rs2QuestCache.getQuestState(qe.getKey());
					QuestState requiredState = qe.getValue();
					int playerIndex = questStateOrder.indexOf(playerState);
					int requiredIndex = questStateOrder.indexOf(requiredState);
					return playerIndex < requiredIndex;
				})) {
					return true;
				}
                // Varbit check
                if (entry.getVarbits().stream().anyMatch(varbitCheck -> !varbitCheck.matches(Rs2VarbitCache.getVarbitValue(varbitCheck.getVarbitId())))) {
                    return true;
                }
                // Varplayer check
                if (entry.getVarplayers().stream().anyMatch(varplayerCheck -> !varplayerCheck.matches(Rs2VarPlayerCache.getVarPlayerValue(varplayerCheck.getVarplayerId())))) {
                    return true;
                }
                // Skill level check
                if (!hasRequiredLevels(entry)) {
                    return true;
                }
                // Item requirement check
                if (!entry.getItemIdRequirements().isEmpty() && !hasRequiredItems(entry)) {
                    return true;
                }
                return false;
            })
            .forEach(entry -> restrictedPointsPacked.add(entry.getPackedWorldPoint()));
    }

    public static boolean isInWilderness(WorldPoint p) {
        return WILDERNESS_ABOVE_GROUND.distanceTo2D(p) == 0
                && FEROX_ENCLAVE_1.distanceTo2D(p) != 0
                && FEROX_ENCLAVE_2.distanceTo2D(p) != 0
                && FEROX_ENCLAVE_3.distanceTo2D(p) != 0
                && FEROX_ENCLAVE_4.distanceTo2D(p) != 0
                && FEROX_ENCLAVE_5.distanceTo2D(p) != 0
                && NOT_WILDERNESS_1.distanceTo2D(p) != 0
                && NOT_WILDERNESS_2.distanceTo2D(p) != 0
                && NOT_WILDERNESS_3.distanceTo2D(p) != 0
                && NOT_WILDERNESS_4.distanceTo2D(p) != 0
                || WILDERNESS_UNDERGROUND.distanceTo2D(p) == 0;
    }

    public static boolean isInWilderness(int packedPoint) {
        return WorldPointUtil.distanceToArea2D(packedPoint, WILDERNESS_ABOVE_GROUND) == 0
                && WorldPointUtil.distanceToArea2D(packedPoint, FEROX_ENCLAVE_1) != 0
                && WorldPointUtil.distanceToArea2D(packedPoint, FEROX_ENCLAVE_2) != 0
                && WorldPointUtil.distanceToArea2D(packedPoint, FEROX_ENCLAVE_3) != 0
                && WorldPointUtil.distanceToArea2D(packedPoint, FEROX_ENCLAVE_4) != 0
                && WorldPointUtil.distanceToArea2D(packedPoint, FEROX_ENCLAVE_5) != 0
                && WorldPointUtil.distanceToArea2D(packedPoint, NOT_WILDERNESS_1) != 0
                && WorldPointUtil.distanceToArea2D(packedPoint, NOT_WILDERNESS_2) != 0
                && WorldPointUtil.distanceToArea2D(packedPoint, NOT_WILDERNESS_3) != 0
                && WorldPointUtil.distanceToArea2D(packedPoint, NOT_WILDERNESS_4) != 0
                || WorldPointUtil.distanceToArea2D(packedPoint, WILDERNESS_UNDERGROUND) == 0;
    }

    public static boolean isInWilderness(Set<WorldPoint> worldPoints) {
        for (WorldPoint worldPoint : worldPoints) {
            if (isInWilderness(worldPoint)) {
                return true;
            }
        }
        return false;
    }

    public boolean avoidWilderness(int packedPosition, int packedNeightborPosition, boolean targetInWilderness) {
        return avoidWilderness && !targetInWilderness
                && !isInWilderness(packedPosition) && isInWilderness(packedNeightborPosition);
    }

    public boolean isInLevel19Wilderness(int packedPoint) {
        return WorldPointUtil.distanceToArea(packedPoint, WILDERNESS_ABOVE_GROUND_LEVEL_19) == 0
                || WorldPointUtil.distanceToArea(packedPoint, WILDERNESS_UNDERGROUND_LEVEL_19) == 0;
    }

    public boolean isInLevel29Wilderness(int packedPoint){
        return WorldPointUtil.distanceToArea(packedPoint, WILDERNESS_ABOVE_GROUND_LEVEL_29) == 0
                || WorldPointUtil.distanceToArea(packedPoint, WILDERNESS_UNDERGROUND_LEVEL_29) == 0;

    }

	private boolean completedQuests(Transport transport) {
		return transport.getQuests().entrySet().stream()
			.allMatch(entry -> {
				QuestState playerState = Rs2QuestCache.getQuestState(entry.getKey());
				QuestState requiredState = entry.getValue();
				int playerIndex = questStateOrder.indexOf(playerState);
				int requiredIndex = questStateOrder.indexOf(requiredState);
				return playerIndex >= requiredIndex;
			});
	}

    private boolean varbitChecks(Transport transport) {
        return transport.getVarbits().isEmpty() ||
			transport.getVarbits().stream()
				.allMatch(varbitCheck -> varbitCheck.matches(Rs2VarbitCache.getVarbitValue(varbitCheck.getVarbitId())));
    }

	private boolean varplayerChecks(Transport transport) {
		return transport.getVarplayers().isEmpty() ||
			transport.getVarplayers().stream()
				.allMatch(varplayerCheck -> varplayerCheck.matches(Rs2VarPlayerCache.getVarPlayerValue(varplayerCheck.getVarplayerId())));
	}

    private boolean useTransport(Transport transport) {
        // Check if the feature flag is disabled
        if (!isFeatureEnabled(transport)) {
			log.debug("Transport Type {} is disabled by feature flag", transport.getType());
			return false;
		}
        // If the transport requires you to be in a members world (used for more granular member requirements)
        if (transport.isMembers() && !client.getWorldType().contains(WorldType.MEMBERS)) {
			log.debug("Transport ( O: {} D: {} ) requires members world", transport.getOrigin(), transport.getDestination());
			return false;
		}
        // If you don't meet level requirements
        if (!hasRequiredLevels(transport)) {
			log.debug("Transport ( O: {} D: {} ) requires skill levels {}", transport.getOrigin(), transport.getDestination(), Arrays.toString(transport.getSkillLevels()));
			return false;
		}
        // If the transport has quest requirements & the quest haven't been completed
        if (transport.isQuestLocked() && !completedQuests(transport)) {
			log.debug("Transport ( O: {} D: {} ) requires quests {}", transport.getOrigin(), transport.getDestination(), transport.getQuests());
			return false;
		}
        // Check Spirit Tree specific requirements (farming state for farmable trees)
        if (transport.getType() == TransportType.SPIRIT_TREE) return isSpiritTreeUsable(transport);
        // If the transport has varbit requirements & the varbits do not match
        if (!varbitChecks(transport)) {
			log.debug("Transport ( O: {} D: {} ) requires varbits {}", transport.getOrigin(), transport.getDestination(), transport.getVarbits());
			return false;
		}
        // If the transport has varplayer requirements & the varplayers do not match
        if (!varplayerChecks(transport)) {
			log.debug("Transport ( O: {} D: {} ) requires varplayers {}", transport.getOrigin(), transport.getDestination(), transport.getVarplayers());
			return false;
		}
        // If you don't have the required currency & amount for transport
        if (transport.getCurrencyAmount() > 0 
            && !Rs2Inventory.hasItemAmount(transport.getCurrencyName(), transport.getCurrencyAmount())
            && !(ShortestPathPlugin.getPathfinderConfig().useBankItems && Rs2Bank.count(transport.getCurrencyName()) >= transport.getCurrencyAmount())) {
			log.debug("Transport ( O: {} D: {} ) requires {} x {}", transport.getOrigin(), transport.getDestination(), transport.getCurrencyAmount(), transport.getCurrencyName());
			return false;
		}
        // Check if Teleports are globally disabled
        if (TransportType.isTeleport(transport.getType()) && Rs2Walker.disableTeleports) {
			log.debug("Transport ( O: {} D: {} ) is a teleport but teleports are globally disabled", transport.getOrigin(), transport.getDestination());
			return false;
		}
        // Check Teleport Item Settings
        if (transport.getType() == TELEPORTATION_ITEM) {
			boolean isUsable = isTeleportationItemUsable(transport);
			if (!isUsable)
			{
				log.debug("Transport ( O: {} D: {} ) is a teleport item but is not usable", transport.getOrigin(), transport.getDestination());
			}
			return isUsable;
		}
        // Check Teleport Spell Settings
        if (transport.getType() == TELEPORTATION_SPELL) {
			boolean isUsable = isTeleportationSpellUsable(transport);
			if (!isUsable)
			{
				log.debug("Transport ( O: {} D: {} ) is a teleport spell but is not usable", transport.getOrigin(), transport.getDestination());
			}
			return isUsable;
		}
        // Used for Generic Item Requirements
        if (!transport.getItemIdRequirements().isEmpty()) {
			boolean hasRequiredItems = hasRequiredItems(transport);
			if (!hasRequiredItems)
			{
				log.debug("Transport ( O: {} D: {} ) requires items {}", transport.getOrigin(), transport.getDestination(), transport.getItemIdRequirements().stream().flatMap(Set::stream).collect(Collectors.toSet()));
			}
			return hasRequiredItems;
		}
        

        return true;
    }

    /** Checks if the player has all the required skill levels for the transport */
    private boolean hasRequiredLevels(Transport transport) {
        int[] requiredLevels = transport.getSkillLevels();
        Skill[] skills = Skill.values();
        return IntStream.range(0, requiredLevels.length)
            .filter(i -> requiredLevels[i] > 0)
            .allMatch(i -> Rs2SkillCache.getBoostedSkillLevel(skills[i]) >= requiredLevels[i]);
    }

    /** Checks if the player has all the required skill levels for the restriction */
    private boolean hasRequiredLevels(Restriction restriction) {
        int[] requiredLevels = restriction.getSkillLevels();
        Skill[] skills = Skill.values();
        return IntStream.range(0, requiredLevels.length)
            .filter(i -> requiredLevels[i] > 0)
            .allMatch(i -> Rs2SkillCache.getBoostedSkillLevel(skills[i]) >= requiredLevels[i]);
    }

	private void updateActionBasedOnQuestState(Transport transport) {
		if (Objects.equals(transport.getType(), TransportType.SHIP) &&
			(Objects.equals(transport.getName(), "Veos") || Objects.equals(transport.getName(), "Captain Magoro"))) {
			QuestState questState = Rs2QuestCache.getQuestState(Quest.CLIENT_OF_KOUREND);
			if (questState != QuestState.FINISHED && !Objects.equals(transport.getAction(), "Talk-to")) {
				transport.setAction("Talk-to");
			}
		}
	}

    private boolean isFeatureEnabled(Transport transport) {
        TransportType type = transport.getType();
        
        if (!client.getWorldType().contains(WorldType.MEMBERS)) {
            // Transport types that require membership
            switch (type) {
                case AGILITY_SHORTCUT:
                case GRAPPLE_SHORTCUT:
                case BOAT:
                case CHARTER_SHIP:
                case FAIRY_RING:
                case GNOME_GLIDER:
                case MINECART:
                case QUETZAL:
                case WILDERNESS_OBELISK:
                case TELEPORTATION_LEVER:
                case TELEPORTATION_MINIGAME:
                case MAGIC_CARPET:
                case SPIRIT_TREE:
                    return false;
            }
        }

        switch (type) {
            case AGILITY_SHORTCUT:
                return useAgilityShortcuts;
            case GRAPPLE_SHORTCUT:
                return useGrappleShortcuts;
            case BOAT:
                return useBoats;
            case CANOE:
                return useCanoes;
            case CHARTER_SHIP:
                return useCharterShips;
            case SHIP:
                return useShips;
            case FAIRY_RING:
                return useFairyRings;
            case GNOME_GLIDER:
                return useGnomeGliders;
            case MINECART:
                return useMinecarts;
            case NPC:
                return useNpcs;
            case QUETZAL:
                return useQuetzals;
            case SPIRIT_TREE:
                return useSpiritTrees;
            case TELEPORTATION_ITEM:
                return useTeleportationItems != TeleportationItem.NONE;
            case TELEPORTATION_MINIGAME:
                return useTeleportationMinigames;
            case TELEPORTATION_LEVER:
                return useTeleportationLevers;
            case TELEPORTATION_PORTAL:
                return useTeleportationPortals;
            case TELEPORTATION_SPELL:
                return useTeleportationSpells;
            case MAGIC_CARPET:
                return useMagicCarpets;
            case WILDERNESS_OBELISK:
                return useWildernessObelisks;
            default:
                return true; // Default to enabled if no specific toggle
        }
    }

    /** Checks if a teleportation item is usable */
    private boolean isTeleportationItemUsable(Transport transport) {
        if (useTeleportationItems == TeleportationItem.NONE) return false;
        // Check consumable items configuration
        if (useTeleportationItems == TeleportationItem.INVENTORY_NON_CONSUMABLE && transport.isConsumable()) return false;
        
        return hasRequiredItems(transport);
    }

    /** Checks if the player has any of the required equipment and inventory items for the transport */
    private boolean hasRequiredItems(Transport transport) {
        if (requiresChronicle(transport)) return hasChronicleCharges();

//        return transport.getItemIdRequirements()
//                .stream()
//                .flatMap(Collection::stream)
//                .anyMatch(itemId -> Rs2Equipment.isWearing(itemId) || Rs2Inventory.hasItem(itemId) || Rs2Bank.hasItem(itemId));

        return transport.getItemIdRequirements()
                .stream()
                .flatMap(Collection::stream)
                .anyMatch(itemId -> Rs2Equipment.isWearing(itemId) || Rs2Inventory.hasItem(itemId) || (ShortestPathPlugin.getPathfinderConfig().useBankItems && Rs2Bank.hasItem(itemId)));
    }

    /** Checks if the player has any of the required equipment and inventory items for the restriction */
    private boolean hasRequiredItems(Restriction restriction) {
        return restriction.getItemIdRequirements()
                .stream()
                .flatMap(Collection::stream)
                .anyMatch(itemId -> Rs2Equipment.isWearing(itemId) || Rs2Inventory.hasItem(itemId));
    }

    
    private boolean isTeleportationSpellUsable(Transport transport) {
        
        boolean hasMultipleDestination = transport.getDisplayInfo().contains(":");
        String displayInfo = hasMultipleDestination
                ? transport.getDisplayInfo().split(":")[0].trim().toLowerCase()
                : transport.getDisplayInfo();
        Rs2Spells rs2Spell = Rs2Magic.getRs2Spell(displayInfo);
        if (rs2Spell == null) return false;
        return Rs2Magic.hasRequiredRunes(rs2Spell, RuneFilter.builder().includeBank(useBankItems).build());
//        return Rs2Magic.quickCanCast(displayInfo);
    }

    /** Checks if the transport requires the Chronicle */
    private boolean requiresChronicle(Transport transport) {
        return transport.getItemIdRequirements()
                .stream()
                .flatMap(Collection::stream)
                .anyMatch(itemId -> itemId == ItemID.CHRONICLE);
    }

    /** Checks if the Chronicle has charges */
    private boolean hasChronicleCharges() {
        if (!Rs2Equipment.isWearing(ItemID.CHRONICLE)) {
            if (!Rs2Inventory.hasItem(ItemID.CHRONICLE))
                return false;
        }
        
        String charges = Microbot.getConfigManager()
                .getRSProfileConfiguration(ItemChargeConfig.GROUP, ItemChargeConfig.KEY_CHRONICLE);

        // If charges are unknown, attempt to retrieve them
        if (charges == null || charges.isEmpty()) {
            if (Rs2Inventory.hasItem(ItemID.CHRONICLE)) {
                Rs2Inventory.interact(ItemID.CHRONICLE, "Check charges");
            } else if (Rs2Equipment.isWearing(ItemID.CHRONICLE)) {
                Rs2Equipment.interact(ItemID.CHRONICLE, "Check charges");
            }
            charges = Microbot.getConfigManager().getRSProfileConfiguration(ItemChargeConfig.GROUP, ItemChargeConfig.KEY_CHRONICLE);
        }

        // Validate charges
        return charges != null && Integer.parseInt(charges) > 0;
    }

    /**
     * Check if a spirit tree transport is usable
     * This method integrates with the farming system to determine if farmable spirit trees
     * are planted and healthy enough for transportation
     * 
     * @param transport The spirit tree transport to check
     * @return true if the spirit tree is available for travel
     */
    private boolean isSpiritTreeUsable(Transport transport) {
        // Use the Rs2SpiritTreeCache directly for better performance and consistency
        return Rs2SpiritTreeCache.isSpiritTreeTransportAvailable(transport);
    }
    
    @Deprecated(since = "1.6.2 - Add Restrictions to restrictions.tsv", forRemoval = true)
    public void setRestrictedTiles(Restriction... restrictions){
        this.customRestrictions = List.of(restrictions);
    }

    
    /**
     * Filters similar transports based on distance, removing consumable transport items when
     * better non-consumable alternatives exist within the configured distance.
     * 
     * @param target Optional target destination for distance-based filtering optimization
     */
    private void filterSimilarTransports(WorldPoint target) {
        int maxDistance = config.maxSimilarTransportDistance();
        if (maxDistance <= 0) {
            return;
        }

        StringBuilder filteringSummary = new StringBuilder();
        filteringSummary.append("\n=== Transport Filtering Summary ===\n");
        filteringSummary.append("\tMax similar transport distance: ").append(maxDistance).append(" tiles\n");
        if (target != null) {
            filteringSummary.append("\tTarget-based filtering enabled for: ").append(target).append("\n");
        }
        filteringSummary.append("\tFiltering Rule: Remove consumable transport items when similar non-consumable alternatives exist\n");

        // Track removed transports by category
        Map<String, Integer> removedByCategory = new HashMap<>();
        Set<Transport> allRemovedTransports = new HashSet<>();

        // IMPORTANT: Create a copy of the current teleports to avoid modifying the original set while iterating
        Set<Transport> teleportsToFilter = new HashSet<>(usableTeleports);

        // Filter usable teleports (null origin)
        filteringSummary.append("\n\t--- Processing Usable Teleports ---\n");
        boolean preferTransports = config.preferTransportToTarget();
        HashMap<WorldPoint, Set<Transport>> allUsableTransports;

        if (preferTransports) {            
            allUsableTransports = new HashMap<>(transports);                                                    
        } else {
            allUsableTransports = new HashMap<>();
        }
        // Call the optimized filter method - now with safeguards against removing critical transports
        TransportFilterResult usableTeleportsResult = filterConsumableTeleports(teleportsToFilter,
                        allUsableTransports,
                        maxDistance, target);          
        // Apply the results
        filteringSummary.append(usableTeleportsResult.filterDetails);

        // Only remove consumable teleportation items - never remove staircases or other non-teleport transports
        Set<Transport> transportsToPurge = new HashSet<>();
        for (Transport transport : usableTeleportsResult.removedTransports) {
            // Double-check that we're only removing consumable teleportation items
            if (transport.getType() == TransportType.TELEPORTATION_ITEM && transport.isConsumable()) {
                transportsToPurge.add(transport);
            }
        }

        // Only now apply the filtered changes
        usableTeleports.removeAll(transportsToPurge);
        allRemovedTransports.addAll(transportsToPurge);
        removedByCategory.putAll(usableTeleportsResult.removedByCategory);

        // Generate final summary
        filteringSummary.append("--- Filtering Results ---");
        int totalRemoved = allRemovedTransports.size();
        if (totalRemoved > 0) {
            filteringSummary.append("\n\tTotal consumable transport items removed: ").append(totalRemoved).append("\n");
        } else {
            filteringSummary.append("\tNo consumable transport items were filtered.");
        }
        filteringSummary.append("\n=== End Transport Filtering Summary ===\n");

        if (totalRemoved > 0) {
            log.debug(filteringSummary.toString());
        } else {
            log.debug(filteringSummary.toString());
        }
    }

    /**
     * Container for transport filtering results
     */
    private static class TransportFilterResult {
        private final Set<Transport> removedTransports = new HashSet<>();
        private final Map<String, Integer> removedByCategory = new HashMap<>();
        private final StringBuilder filterDetails = new StringBuilder();
        
        public void addRemovedTransport(Transport transport, Transport alternative, 
                                      String categoryName, int distance, boolean isTargetBased) {
            removedTransports.add(transport);
            
            // Track removal by category
            String category = categoryName + " over Consumable Item (" + 
                    (isTargetBased ? "target-based" : "destination-based") + ", distance: " + distance + " tiles)";
            removedByCategory.merge(category, 1, Integer::sum);
            
            // Log detailed removal information
            filterDetails.append("\t\tRemoved: '").append(transport.getDisplayInfo())
                   .append("' (Consumable Item) due to similar '").append(alternative.getDisplayInfo())
                   .append("' (").append(categoryName)
                   .append(") - Distance: ").append(distance).append(" tiles (")
                   .append(isTargetBased ? "target-based" : "destination-based").append(")\n");
        }
    }
    
    /**
     * Filters consumable transport items when similar non-consumable alternatives exist.
     * 
     * @param teleports The set of teleports to filter
     * @param allUsableTransports Map of all usable transports by location
     * @param maxDistance Maximum distance for considering transports similar
     * @param target Optional target destination for optimization
     * @return Result containing transports to remove and filtering details
     */
    private TransportFilterResult filterConsumableTeleports(Set<Transport> teleports, 
                                                            HashMap<WorldPoint, Set<Transport>> allUsableTransports,
                                                            int maxDistance, WorldPoint target) {
        TransportFilterResult result = new TransportFilterResult();
        
        // Skip processing if no filtering is needed
        if (maxDistance <= 0) {
            return result;
        }
        
        // Fast early exit if there are no teleports to filter
        if (teleports == null || teleports.isEmpty()) {
            return result;
        }
        
        // Separate consumable transport items from other transports (using streams for better performance)
        List<Transport> consumableItems = new ArrayList<>();
        List<Transport> nonConsumableTeleports = new ArrayList<>();
        
        // First pass: separate the teleports into appropriate lists
        teleports.forEach(teleport -> {
            if (teleport.getType() == TransportType.TELEPORTATION_ITEM && teleport.isConsumable()) {
                consumableItems.add(teleport);
            } else {
                nonConsumableTeleports.add(teleport);
            }
        });
        
        // Nothing to filter if no consumable items or no alternatives
        if (consumableItems.isEmpty() || nonConsumableTeleports.isEmpty()) {
            return result;
        }
        
        // Add transportation network transports (like fairy rings, spirit trees) to the non-consumable list
        if (allUsableTransports != null && !allUsableTransports.isEmpty()) {
            allUsableTransports.values().stream()
                .flatMap(Set::stream)
                .filter(transport -> 
                    transport.getType() == TransportType.FAIRY_RING || 
                    transport.getType() == TransportType.GNOME_GLIDER ||
                    transport.getType() == TransportType.SPIRIT_TREE || 
                    transport.getType() == TransportType.QUETZAL)
                .forEach(nonConsumableTeleports::add);
        }
        
        // Check each consumable item against all non-consumable alternatives
        // Using a more optimized approach to avoid unnecessary calculations
        for (Transport consumableItem : consumableItems) {
            WorldPoint consumableDestination = consumableItem.getDestination();
            if (consumableDestination == null) continue;
            
            Transport bestAlternative = null;
            int shortestDistance = Integer.MAX_VALUE;
            boolean isTargetBased = false;
            
            // First determine if we can use target-based comparison
            boolean useTargetComparison = target != null && target.getPlane() == consumableDestination.getPlane();
            
            // Pre-calculate target distance for the consumable item if needed
            int consumableToTarget = useTargetComparison ? consumableDestination.distanceTo2D(target) : 0;
            
            // Process all non-consumable alternatives
            for (Transport nonConsumableTeleport : nonConsumableTeleports) {
                WorldPoint nonConsumableDestination = nonConsumableTeleport.getDestination();
                if (nonConsumableDestination == null) continue;
                
                // Ensure we're comparing points on the same plane
                if (consumableDestination.getPlane() != nonConsumableDestination.getPlane()) {
                    continue;
                }
                
                int distance;
                
                if (useTargetComparison) {
                    // Target-based comparison (optimized)
                    WorldPoint referencePoint = calculateReferencePoint(target, consumableDestination, nonConsumableDestination);
                    int nonConsumableToReference = nonConsumableDestination.distanceTo2D(referencePoint);
                    int consumableToReference = consumableDestination.distanceTo2D(referencePoint);
                    
                    // Only consider alternatives that get us at least as close to the target
                    // This prevents removing consumable items that are better positioned
                    if (nonConsumableToReference > consumableToReference) {
                        continue;
                    }
                    
                    // Calculate distance between the transports
                    distance = nonConsumableToReference;
                    isTargetBased = true;
                } else {
                    // Simple destination-based comparison
                    distance = consumableDestination.distanceTo2D(nonConsumableDestination);
                    isTargetBased = false;
                }
                
                // Keep track of the best alternative (shortest distance)
                if (distance <= maxDistance && distance < shortestDistance) {
                    shortestDistance = distance;
                    bestAlternative = nonConsumableTeleport;
                    break; // Found a suitable alternative, no need to check further
                }
            }
            
            // If we found a suitable alternative, remove the consumable item
            if (bestAlternative != null) {
                result.addRemovedTransport(
                    consumableItem,
                    bestAlternative,
                    getTransportTypeName(bestAlternative),
                    shortestDistance,
                    isTargetBased
                );
            }
        }
        
        return result;
    }
    
    /**
     * Calculates a reference point based on the triangle formed by three world points.
     * This reference point is the centroid (average of the three points) which provides
     * a point of reference for comparing relative positions.
     * 
     * @param p1 First point (typically the target)
     * @param p2 Second point (typically consumable destination)
     * @param p3 Third point (typically non-consumable destination)
     * @return The calculated reference point (centroid of the triangle)
     */
    private WorldPoint calculateReferencePoint(WorldPoint p1, WorldPoint p2, WorldPoint p3) {
        // Calculate the centroid (average of the three points)
        int x = (p1.getX() + p2.getX() + p3.getX()) / 3;
        int y = (p1.getY() + p2.getY() + p3.getY()) / 3;
        int plane = p1.getPlane(); // All points should be on the same plane

        return new WorldPoint(x, y, plane);
    }

    /**
     * Gets a human-readable name for a transport type.
     * 
     * @param transport The transport to evaluate
     * @return Human-readable transport type name
     */
    private String getTransportTypeName(Transport transport) {
        if (transport.getType() == TransportType.TELEPORTATION_SPELL) {
            return "Teleportation Spell";
        } else if (transport.getType() == TransportType.TELEPORTATION_ITEM && !transport.isConsumable()) {
            return "Non-consumable Item";
        } else if (transport.getType() == TransportType.TELEPORTATION_PORTAL) {
            return "Teleportation Portal";
        } else if (transport.getType() == TransportType.SHIP) {
            return "Ship";
        } else if (transport.getType() == TransportType.CANOE) {
            return "Canoe";
        } else if (transport.getType() == TransportType.NPC) {
            return "NPC";
        } else if (transport.getType() == TransportType.TRANSPORT) {
            return "Transport";
        } else {
            return transport.getType().toString();
        }
    }   
}
