package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.*;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.itemcharges.ItemChargeConfig;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.*;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
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
    private final int[] boostedLevels = new int[Skill.values().length];
    private Map<Quest, QuestState> questStates = new HashMap<>();
    private Map<Integer, Integer> varbitValues = new HashMap<>();
    private Map<Integer, Integer> varplayerValues = new HashMap<>();

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

    public void refresh() {
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
            for (int i = 0; i < Skill.values().length; i++) {
                boostedLevels[i] = client.getBoostedSkillLevel(Skill.values()[i]);
            }

            refreshTransports();
            //START microbot variables
            refreshRestrictionData();
            
            // Do not switch back to inventory tab if we are inside of the telekinetic room in Mage Training Arena
            if (Rs2Player.getWorldLocation().getRegionID() != 13463) {
                Rs2Tab.switchToInventoryTab();
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

    private void refreshTransports() {
        useFairyRings &= !QuestState.NOT_STARTED.equals(Rs2Player.getQuestState(Quest.FAIRYTALE_II__CURE_A_QUEEN))
                && (Rs2Inventory.contains(ItemID.DRAMEN_STAFF, ItemID.LUNAR_STAFF)
                || Rs2Equipment.isWearing(ItemID.DRAMEN_STAFF)
                || Rs2Equipment.isWearing(ItemID.LUNAR_STAFF)
                || (ShortestPathPlugin.getPathfinderConfig().useBankItems && (Rs2Bank.hasItem(ItemID.DRAMEN_STAFF)|| Rs2Bank.hasItem(ItemID.LUNAR_STAFF)))
                || Microbot.getVarbitValue(Varbits.DIARY_LUMBRIDGE_ELITE)  == 1);
        useGnomeGliders &= QuestState.FINISHED.equals(Rs2Player.getQuestState(Quest.THE_GRAND_TREE));
        useSpiritTrees &= QuestState.FINISHED.equals(Rs2Player.getQuestState(Quest.TREE_GNOME_VILLAGE));
        useQuetzals &= QuestState.FINISHED.equals(Rs2Player.getQuestState(Quest.TWILIGHTS_PROMISE));

        transports.clear();
        transportsPacked.clear();
        usableTeleports.clear();
         Microbot.getClientThread().runOnClientThreadOptional(() -> {
            for (Map.Entry<WorldPoint, Set<Transport>> entry : allTransports.entrySet()) {
                for (Transport transport : entry.getValue()) {
                    for (Quest quest : transport.getQuests()) {
                        try {
                            QuestState currentState = questStates.get(quest);
                            QuestState newState = Rs2Player.getQuestState(quest);

                            // Only update if the new state is more progressed
                            if (currentState == null || isMoreProgressed(newState, currentState)) {
                                questStates.put(quest, newState);
                            }
                        } catch (NullPointerException ignored) {
                            System.out.println(ignored.getMessage());
                        }
                    }
                    for (TransportVarbit varbitCheck : transport.getVarbits()) {
                        varbitValues.put(varbitCheck.getVarbitId(), Microbot.getVarbitValue(varbitCheck.getVarbitId()));
                    }
                    
                    for (TransportVarPlayer varplayerCheck : transport.getVarplayers()) {
                        varplayerValues.put(varplayerCheck.getVarplayerId(), Microbot.getVarbitPlayerValue(varplayerCheck.getVarplayerId()));
                    }
                }
            }
            return true;
        });

        for (Map.Entry<WorldPoint, Set<Transport>> entry : allTransports.entrySet()) {
            WorldPoint point = entry.getKey();
            Set<Transport> usableTransports = new HashSet<>(entry.getValue().size());
            for (Transport transport : entry.getValue()) {

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
    }

    private void refreshRestrictionData() {
        restrictedPointsPacked.clear();

        Set<Quest> questsToFetch = new HashSet<>();
        Set<Integer> varbitsToFetch = new HashSet<>();
        Set<Integer> varplayersToFetch = new HashSet<>();
        List<Restriction> allRestrictions = Stream.concat(resourceRestrictions.stream(), customRestrictions.stream())
                .collect(Collectors.toList());

        for (Restriction entry : allRestrictions) {
            questsToFetch.addAll(entry.getQuests());
            
            for (TransportVarbit varbitCheck : entry.getVarbits()) {
                varbitsToFetch.add(varbitCheck.getVarbitId());
            }

            for (TransportVarPlayer varplayerCheck : entry.getVarplayers()) {
                varplayersToFetch.add(varplayerCheck.getVarplayerId());
            }
        }

        // Fetch quest states and varbit values directly
        for (Quest quest : questsToFetch) {
            try {
                QuestState currentState = questStates.get(quest);
                QuestState newState = Rs2Player.getQuestState(quest);

                // Only update if the new state is more progressed
                if (currentState == null || isMoreProgressed(newState, currentState)) {
                    questStates.put(quest, newState);
                }
            } catch (NullPointerException ignored) {
                // Handle exceptions if necessary
            }
        }
        for (Integer varbitId : varbitsToFetch) {
            varbitValues.put(varbitId, Microbot.getVarbitValue(varbitId));
        }

        for (Integer varplayerId : varplayersToFetch) {
            varplayerValues.put(varplayerId, Microbot.getVarbitPlayerValue(varplayerId));
        }

        for (Restriction entry : allRestrictions) {
            boolean restrictionApplies = false;

            // Check if there are no quests, varbits, varplayers, doesn't require a members world or skills, used for explicit restrictions
            if (entry.getQuests().isEmpty() && entry.getVarbits().isEmpty() && entry.getVarplayers().isEmpty() && !entry.isMembers() && Arrays.stream(entry.getSkillLevels()).allMatch(level -> level == 0) && entry.getItemIdRequirements().isEmpty()) {
                restrictionApplies = true;
            }
            
            // Members World Check
            if (!restrictionApplies) {
                if (entry.isMembers() && !client.getWorldType().contains(WorldType.MEMBERS)) {
                    restrictionApplies = true;
                }
            }

            // Quest check
            if (!restrictionApplies) {
                for (Quest quest : entry.getQuests()) {
                    if (questStates.getOrDefault(quest, QuestState.NOT_STARTED) != QuestState.FINISHED) {
                        restrictionApplies = true;
                        break;
                    }
                }
            }

            // Varbit check
            if (!restrictionApplies) {
                for (TransportVarbit varbitCheck : entry.getVarbits()) {
                    int varbitId = varbitCheck.getVarbitId();
                    int actualValue = varbitValues.getOrDefault(varbitId, -1);
                    if (!varbitCheck.matches(actualValue)) {
                        restrictionApplies = true;
                        break;
                    }
                }
            }
            
            // Varplayer check
            if (!restrictionApplies) {
                for (TransportVarPlayer varplayerCheck : entry.getVarplayers()) {
                    int varplayerId = varplayerCheck.getVarplayerId();
                    int actualValue = varplayerValues.getOrDefault(varplayerId, -1);
                    if (!varplayerCheck.matches(actualValue)) {
                        restrictionApplies = true;
                        break;
                    }
                }
            }

            // Skill level check
            if (!restrictionApplies && !hasRequiredLevels(entry)) {
                restrictionApplies = true;
            }
            
            if (!restrictionApplies && !entry.getItemIdRequirements().isEmpty()) {
                if (!hasRequiredItems(entry)) {
                    restrictionApplies = true;
                }
            }

            if (restrictionApplies) {
                restrictedPointsPacked.add(entry.getPackedWorldPoint());
            }
        }
    }

    public static boolean isInWilderness(WorldPoint p) {
        return WILDERNESS_ABOVE_GROUND.distanceTo(p) == 0
                && FEROX_ENCLAVE_1.distanceTo(p) != 0
                && FEROX_ENCLAVE_2.distanceTo(p) != 0
                && FEROX_ENCLAVE_3.distanceTo(p) != 0
                && FEROX_ENCLAVE_4.distanceTo(p) != 0
                && FEROX_ENCLAVE_5.distanceTo(p) != 0
                && NOT_WILDERNESS_1.distanceTo(p) != 0
                && NOT_WILDERNESS_2.distanceTo(p) != 0
                && NOT_WILDERNESS_3.distanceTo(p) != 0
                && NOT_WILDERNESS_4.distanceTo(p) != 0
                || WILDERNESS_UNDERGROUND.distanceTo(p) == 0;
    }

    public static boolean isInWilderness(int packedPoint) {
        return WorldPointUtil.distanceToArea(packedPoint, WILDERNESS_ABOVE_GROUND) == 0
                && WorldPointUtil.distanceToArea(packedPoint, FEROX_ENCLAVE_1) != 0
                && WorldPointUtil.distanceToArea(packedPoint, FEROX_ENCLAVE_2) != 0
                && WorldPointUtil.distanceToArea(packedPoint, FEROX_ENCLAVE_3) != 0
                && WorldPointUtil.distanceToArea(packedPoint, FEROX_ENCLAVE_4) != 0
                && WorldPointUtil.distanceToArea(packedPoint, FEROX_ENCLAVE_5) != 0
                && WorldPointUtil.distanceToArea(packedPoint, NOT_WILDERNESS_1) != 0
                && WorldPointUtil.distanceToArea(packedPoint, NOT_WILDERNESS_2) != 0
                && WorldPointUtil.distanceToArea(packedPoint, NOT_WILDERNESS_3) != 0
                && WorldPointUtil.distanceToArea(packedPoint, NOT_WILDERNESS_4) != 0
                || WorldPointUtil.distanceToArea(packedPoint, WILDERNESS_UNDERGROUND) == 0;
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
        for (Quest quest : transport.getQuests()) {
            QuestState state = questStates.getOrDefault(quest, QuestState.NOT_STARTED);
            if (state != QuestState.FINISHED) {
                return false;
            }
        }
        return true;
    }

    private boolean varbitChecks(Transport transport) {
        if (varbitValues.isEmpty()) return true;
        for (TransportVarbit varbitCheck : transport.getVarbits()) {
            int actualValue = varbitValues.getOrDefault(varbitCheck.getVarbitId(), -1);
            if (!varbitCheck.matches(actualValue)) {
                return false;
            }
        }
        return true;
    }

    private boolean varplayerChecks(Transport transport) {
        if (varplayerValues.isEmpty()) return true;
        for (TransportVarPlayer varplayerCheck : transport.getVarplayers()) {
            int actualValue = varplayerValues.getOrDefault(varplayerCheck.getVarplayerId(), -1);
            if (!varplayerCheck.matches(actualValue)) {
                return false;
            }
        }
        return true;
    }

    private boolean useTransport(Transport transport) {
        // Check if the feature flag is disabled
        if (!isFeatureEnabled(transport)) return false;
        // If the transport requires you to be in a members world (used for more granular member requirements)
        if (transport.isMembers() && !client.getWorldType().contains(WorldType.MEMBERS)) return false;
        // If you don't meet level requirements
        if (!hasRequiredLevels(transport)) return false;
        // If the transport has quest requirements & the quest haven't been completed
        if (transport.isQuestLocked() && !completedQuests(transport)) return false;
        // If the transport has varbit requirements & the varbits do not match
        if (!varbitChecks(transport)) return false;
        // If the transport has varplayer requirements & the varplayers do not match
        if (!varplayerChecks(transport)) return false;
        // If you don't have the required currency & amount for transport
        if (transport.getCurrencyAmount() > 0 
            && !Rs2Inventory.hasItemAmount(transport.getCurrencyName(), transport.getCurrencyAmount())
            && !(ShortestPathPlugin.getPathfinderConfig().useBankItems && Rs2Bank.count(transport.getCurrencyName()) >= transport.getCurrencyAmount())
            ) return false;
        // Check if Teleports are globally disabled
        if (TransportType.isTeleport(transport.getType()) && Rs2Walker.disableTeleports) return false;
        // Check Teleport Item Settings
        if (transport.getType() == TELEPORTATION_ITEM) return isTeleportationItemUsable(transport);
        // Check Teleport Spell Settings
        if (transport.getType() == TELEPORTATION_SPELL) return isTeleportationSpellUsable(transport);
        // Used for Generic Item Requirements
        if (!transport.getItemIdRequirements().isEmpty()) return hasRequiredItems(transport);

        return true;
    }

    /** Checks if the player has all the required skill levels for the transport */
    private boolean hasRequiredLevels(Transport transport) {
        int[] requiredLevels = transport.getSkillLevels();
        for (int i = 0; i < boostedLevels.length; i++) {
            int boostedLevel = boostedLevels[i];
            int requiredLevel = requiredLevels[i];
            if (boostedLevel < requiredLevel) {
                return false;
            }
        }
        return true;
    }

    /** Checks if the player has all the required skill levels for the restriction */
    private boolean hasRequiredLevels(Restriction restriction) {
        int[] requiredLevels = restriction.getSkillLevels();
        for (int i = 0; i < boostedLevels.length; i++) {

            if (Skill.values()[i] == Skill.AGILITY && requiredLevels[i] > 0 && !config.useAgilityShortcuts()) return false;

            int boostedLevel = boostedLevels[i];
            int requiredLevel = requiredLevels[i];

            if (boostedLevel < requiredLevel) {
                return false;
            }
        }
        return true;
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
        if (!Rs2Equipment.hasEquipped(ItemID.CHRONICLE)) {
            if (!Rs2Inventory.hasItem(ItemID.CHRONICLE))
                return false;
        }
        
        String charges = Microbot.getConfigManager()
                .getRSProfileConfiguration(ItemChargeConfig.GROUP, ItemChargeConfig.KEY_CHRONICLE);

        // If charges are unknown, attempt to retrieve them
        if (charges == null || charges.isEmpty()) {
            if (Rs2Inventory.hasItem(ItemID.CHRONICLE)) {
                Rs2Inventory.interact(ItemID.CHRONICLE, "Check charges");
            } else if (Rs2Equipment.hasEquipped(ItemID.CHRONICLE)) {
                Rs2Equipment.interact(ItemID.CHRONICLE, "Check charges");
            }
            charges = Microbot.getConfigManager().getRSProfileConfiguration(ItemChargeConfig.GROUP, ItemChargeConfig.KEY_CHRONICLE);
        }

        // Validate charges
        return charges != null && Integer.parseInt(charges) > 0;
    }
    
    /** Checks if a QuestState is further progressed than currentState **/
    private boolean isMoreProgressed(QuestState newState, QuestState currentState) {
        if (currentState == null) return false;
        if (newState == null) return false;
        
        // Define the progression order of states
        List<QuestState> progressionOrder = Arrays.asList(
                QuestState.NOT_STARTED,
                QuestState.IN_PROGRESS,
                QuestState.FINISHED
        );

        return progressionOrder.indexOf(newState) > progressionOrder.indexOf(currentState);
    }
    
    @Deprecated(since = "1.6.2 - Add Restrictions to restrictions.tsv", forRemoval = true)
    public void setRestrictedTiles(Restriction... restrictions){
        this.customRestrictions = List.of(restrictions);
    }
}
