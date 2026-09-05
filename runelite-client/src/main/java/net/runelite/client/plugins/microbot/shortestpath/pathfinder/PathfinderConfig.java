package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.itemcharges.ItemChargeConfig;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.*;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.live.LiveCollisionOverlay;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.live.LiveCollisionSnapshot;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.live.LiveCollisionView;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.policy.TransportRequirementPolicy;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.magic.RuneFilter;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.leaguetransport.Rs2LeaguesTransport;
import net.runelite.client.plugins.microbot.util.poh.PohTeleports;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WebWalkLog;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static net.runelite.client.plugins.microbot.shortestpath.TransportType.TELEPORTATION_ITEM;
import static net.runelite.client.plugins.microbot.shortestpath.TransportType.TELEPORTATION_SPELL;

@Slf4j
public class PathfinderConfig {
    private static final WorldArea WILDERNESS_ABOVE_GROUND = new WorldArea(2944, 3525, 448, 448, 0);
    private static final WorldArea WILDERNESS_ABOVE_GROUND_LEVEL_20 = new WorldArea(2944, 3680, 448, 448, 0);
    private static final WorldArea WILDERNESS_ABOVE_GROUND_LEVEL_30 = new WorldArea(2944, 3760, 448, 448, 0);
    private static final WorldArea WILDERNESS_UNDERGROUND = new WorldArea(2944, 9918, 518, 458, 0);
    private static final WorldArea WILDERNESS_UNDERGROUND_LEVEL_20 = new WorldArea(2944, 10075, 518, 301, 0);
    private static final WorldArea WILDERNESS_UNDERGROUND_LEVEL_30 = new WorldArea(2944, 10155, 518, 221, 0);
    private static final WorldArea FEROX_ENCLAVE_1 = new WorldArea(3123, 3622, 2, 10, 0);
    private static final WorldArea FEROX_ENCLAVE_2 = new WorldArea(3125, 3617, 16, 23, 0);
    private static final WorldArea FEROX_ENCLAVE_3 = new WorldArea(3138, 3636, 18, 10, 0);
	private static final WorldArea FEROX_ENCLAVE_4 = new WorldArea(3141, 3625, 14, 11, 0);
	private static final WorldArea FEROX_ENCLAVE_5 = new WorldArea(3141, 3619, 7, 6, 0);
	private static final WorldArea NOT_WILDERNESS_1 = new WorldArea(2997, 3525, 34, 9, 0);
	private static final WorldArea NOT_WILDERNESS_2 = new WorldArea(3005, 3534, 21, 10, 0);
	private static final WorldArea NOT_WILDERNESS_3 = new WorldArea(3000, 3534, 5, 5, 0);
	private static final WorldArea NOT_WILDERNESS_4 = new WorldArea(3031, 3525, 2, 2, 0);
	private static final WorldPoint SPIRIT_TREE_ETCETERIA = new WorldPoint(2613, 3855, 0);
	private static final WorldPoint SPIRIT_TREE_BRIMHAVEN = new WorldPoint(2800, 3203, 0);
	private static final WorldPoint SPIRIT_TREE_PORT_SARIM = new WorldPoint(3058, 3257, 0);
	private static final WorldPoint SPIRIT_TREE_HOSIDIUS = new WorldPoint(1693, 3540, 0);
	private static final WorldPoint SPIRIT_TREE_FARMING_GUILD = new WorldPoint(1251, 3750, 0);
	private static final Set<Long> STATIC_BLOCKED_EDGES_PACKED = loadStaticBlockedEdgesFromResources();
	// Tiles within 1 of an aggressive-NPC hazard tile (the melee-aggro ring). Stepping onto one
	// gets a high pathfinding penalty when avoidDangerousNpcs is on, so paths keep >=2 tiles away.
	private static final Set<Integer> DANGEROUS_ADJACENT_TILES_PACKED = loadDangerousTilesFromResources();

	/** Order matches {@link #spiritTreeDestinationToggle(int)} — add destinations in both places only here + switch. */
	private static final WorldPoint[] SPIRIT_TREE_DESTINATIONS_ORDERED = {
			SPIRIT_TREE_ETCETERIA,
			SPIRIT_TREE_BRIMHAVEN,
			SPIRIT_TREE_PORT_SARIM,
			SPIRIT_TREE_HOSIDIUS,
			SPIRIT_TREE_FARMING_GUILD,
	};

    private final SplitFlagMap mapData;
    private final ThreadLocal<CollisionMap> map;
    /**
     * One shared overlay behind every per-thread {@link CollisionMap}, so the client thread can swap in a
     * fresh live snapshot that all pathfinding threads pick up. Disabled until the
     * {@code useLiveCollision} config flag turns it on.
     */
    @Getter
    private final LiveCollisionOverlay liveCollisionOverlay = new LiveCollisionOverlay();
    /**
     * All transports by origin {@link WorldPoint}. The null key is used for transports centered on the player.
     */
    @Getter
    private final Map<WorldPoint, Set<Transport>> allTransports;
    @Setter
    private volatile Set<Transport> usableTeleports;

	/** Immutable exact-object snapshot for planner adapters after transport admission has run. */
	public Set<Transport> getUsableTeleportsSnapshot() {
		Set<Transport> current = usableTeleports;
		return current == null ? Collections.emptySet() : Set.copyOf(current);
	}
    private final List<WorldPoint> filteredTargets = new CopyOnWriteArrayList<>();

    @Getter
    private final ConcurrentHashMap<WorldPoint, Set<Transport>> transports;
    // Copy of transports with packed positions for the hotpath; lists are not copied and are the same reference in both maps
    @Getter
    private final PrimitiveIntHashMap<Set<Transport>> transportsPacked;
    @Getter
    private final Set<Long> blockedTransportEdgesPacked;

    /**
     * Runtime-learned blocked walking edges (packed keys), persisted to a human-editable TSV. Held
     * separately from {@link #blockedTransportEdgesPacked} because {@link #refreshTransports} clears and
     * rebuilds that set from static data on every refresh — learned edges are re-applied from here so
     * they survive. Loaded once in the constructor; grown by {@link #learnBlockedEdge}.
     */
    private final Set<Long> learnedBlockedEdgeKeys = ConcurrentHashMap.newKeySet();

    private final Client client;
    private final ShortestPathConfig config;
    private final TransportPlanningPolicy transportPlanningPolicy;

    private final List<QuestState> questStateOrder = Arrays.asList(
            QuestState.NOT_STARTED,
            QuestState.IN_PROGRESS,
            QuestState.FINISHED
    );

    @Getter
    private volatile long calculationCutoffMillis;

    /**
     * A {@link #refresh(WorldPoint)} slower than this is a user-visible cold start: the walker sits
     * on a null pathfinder for its whole duration before the first click can be issued.
     */
    private static final long SLOW_REFRESH_LOG_THRESHOLD_MS = 500L;

    /**
     * Inventory/equipment/bank fingerprint from the most recent cache-key computation, and the value
     * carried over from the previous refresh. The fingerprint hashes item <em>quantity</em>, so
     * spending coins (a charter ship fare, a toll gate) changes it and invalidates the transport
     * cache key — forcing a full re-evaluation of every transport. Tracked so a cache miss can say
     * whether the items changed or the game state did.
     */
    private volatile int lastComputedInvFingerprint;
    private volatile int previousRefreshInvFingerprint;
    /** Which verification component moved on the most recent verify-miss; see the miss log. */
    private volatile String lastVerifyMissDetail = "";
    @Getter
    private volatile boolean avoidWilderness;
    @Getter
    private volatile boolean avoidDangerousNpcs;
	@Getter
	private volatile PlannerSelectionMode plannerSelectionMode = PlannerSelectionMode.LOCAL;
    @Getter
    private volatile boolean useSpiritTrees;
    private volatile boolean useAgilityShortcuts,
            useGrappleShortcuts,
            useBoats,
            useCanoes,
            useCharterShips,
            useShips,
            useFairyRings,
            useGnomeGliders,
            useMinecarts,
            usePoh,
            useQuetzals,
            useTeleportationLevers,
            useTeleportationMinigames,
            useTeleportationPortals,
            useTeleportationSpells,
            useMagicCarpets,
            useHotAirBalloons,
            useMagicMushtrees,
            useSeasonalTransports,
            useWildernessObelisks,
            useSpiritTreeEtceteria,
            useSpiritTreeBrimhaven,
            useSpiritTreePortSarim,
            useSpiritTreeHosidius,
            useSpiritTreeFarmingGuild;
    //START microbot variables
    @Getter
    private volatile int distanceBeforeUsingTeleport;
    @Getter
    private final List<Restriction> resourceRestrictions;
    @Getter
    private List<Restriction> customRestrictions;
    @Getter
    private final Set<Integer> restrictedPointsPacked;
    private final Set<Integer> internalRestrictedPointsPacked;
    private volatile boolean useNpcs;
    //END microbot variables
    private volatile TeleportationItem useTeleportationItems;

    @Getter
    @Setter
    // Used for manual calculating paths without teleport & items in caves
    private volatile boolean ignoreTeleportAndItems = false;

    @Getter
    @Setter
    // Used to include bank items when searching for item requirements
    private volatile boolean useBankItems = false;

    private Map<Integer, Integer> refreshAvailableItemQuantities;
    private Map<Integer, Integer> refreshAvailableRuneQuantities;
    private int[] refreshBoostedLevels;
    private Map<String, int[]> refreshCurrencyCache;
    // Varplayer values snapshot for the current refreshTransports pass. Without it, every varp
    // condition on every transport paid a full cross-thread hop (getLiveVarplayerValue), and the
    // global state cache never retains zero-valued varps — ~115 varp-gated rows accounted for
    // ~2.3s of the 2.8s refilter. Captured in the same client-thread block as boosted levels.
    private Map<Integer, Integer> refreshVarplayerValues;
    private static final Skill[] SKILLS = Skill.values();

    /**
     * Memo of recent {@link #refreshTransports} results, keyed by {@link #computeTransportRefreshCacheKeyHash}
     * and guarded by the verification hash (boosted skills + transport varbits/varplayers). BOUNDED and
     * multi-entry: the banked-walk compare alternates between two config states (bank-leg vs direct), so a
     * single-slot memo missed on EVERY flip and each miss was a full ~2.5s re-filter of all 12k transports —
     * four in a row per banked walk start. A tiny LRU keeps both states (plus a couple more) warm.
     * Cleared by {@link #invalidateTransportRefreshCache()}.
     */
    private static final int TRANSPORT_REFRESH_SNAPSHOT_CACHE_SIZE = 4;

    /**
     * Item ids and currency names that some transport or restriction actually gates on. Only these may
     * participate in the transport-refresh cache key — see
     * {@link #itemAffectsTransportUsability(int, String, Set, Set)}. Null until the first refresh has
     * run, which makes the fingerprint fall back to hashing everything.
     */
    private volatile Set<Integer> transportRelevantItemIds = null;

    /**
     * The item ids usability depends on that are NOT declared on any transport row: the fairy-ring
     * staves and the Chronicle are checked against hardcoded ids, so they must be seeded explicitly or
     * picking one up would not invalidate the cache.
     */
    private static final Set<Integer> HARDCODED_USABILITY_ITEM_IDS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    ItemID.DRAMEN_STAFF,
                    ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF,
                    ItemID.CHRONICLE)));
    private final Map<Integer, TransportRefreshSnapshot> transportRefreshSnapshots =
            Collections.synchronizedMap(new java.util.LinkedHashMap<Integer, TransportRefreshSnapshot>(8, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, TransportRefreshSnapshot> eldest) {
                    return size() > TRANSPORT_REFRESH_SNAPSHOT_CACHE_SIZE;
                }
            });

    public PathfinderConfig(SplitFlagMap mapData, Map<WorldPoint, Set<Transport>> transports,
                            List<Restriction> restrictions,
                            Client client, ShortestPathConfig config) {
        this(mapData, transports, restrictions, client, config, TransportPlanningPolicy.ALLOW_ALL);
    }

    /**
     * Creates pathfinder state. Null client/config dependencies are supported for offline planning
     * and tests. A null client uses only static collision data, and refresh returns without reading
     * live state when either dependency is absent.
     */
    public PathfinderConfig(SplitFlagMap mapData, Map<WorldPoint, Set<Transport>> transports,
                            List<Restriction> restrictions,
                            Client client, ShortestPathConfig config,
                            TransportPlanningPolicy transportPlanningPolicy) {
        this.mapData = mapData;
        this.map = ThreadLocal.withInitial(() -> client == null
                ? new CollisionMap(this.mapData, this.liveCollisionOverlay, () -> -1)
                : new CollisionMap(this.mapData, this.liveCollisionOverlay));
        this.allTransports = Collections.synchronizedMap(new HashMap<>());
        replaceAllTransports(transports);
        this.usableTeleports = ConcurrentHashMap.newKeySet(allTransports.size() / 20);
        this.transports = new ConcurrentHashMap<>(allTransports.size() / 2);
        this.transportsPacked = new PrimitiveIntHashMap<>(allTransports.size() / 2);
        this.blockedTransportEdgesPacked = ConcurrentHashMap.newKeySet();
        addStaticBlockedEdges();
        this.client = client;
        this.config = config;
        this.transportPlanningPolicy = Objects.requireNonNull(
                transportPlanningPolicy, "transportPlanningPolicy");
        //START microbot variables
        this.resourceRestrictions = restrictions;
        this.customRestrictions = Collections.emptyList();
        this.internalRestrictedPointsPacked = ConcurrentHashMap.newKeySet();
        this.restrictedPointsPacked = Collections.unmodifiableSet(internalRestrictedPointsPacked);
        //END microbot variables
    }

    public CollisionMap getMap() {
        return map.get();
    }

    /**
     * Diagnostics for the live-collision overlay at one tile, for the agent server's
     * {@code /live-collision} endpoint. Reads only immutable data (the static map and the pinned
     * snapshot), so it is safe to call from any thread.
     * <p>
     * {@code overlayRaw} is the snapshot's own answer ({@code true}/{@code false}/{@code null} where
     * {@code null} means "unknown, defer to static") — proving capture and the flag translation.
     * {@code resolved} is what the pathfinder actually uses (overlay where known, else static), and
     * {@code static} is the static-only reading. Where they differ, the overlay is changing routing.
     */
    public Map<String, Object> liveCollisionDiagnostics(int x, int y, int z) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", liveCollisionOverlay.isEnabled());

        final LiveCollisionView view = liveCollisionOverlay.current();
        out.put("snapshotPresent", view != null);
        if (view != null) {
            out.put("regionCount", view.regionCount());
        }
        out.put("tile", Map.of("x", x, "y", y, "plane", z));

        final CollisionMap staticMap = new CollisionMap(mapData);
        final CollisionMap resolvedMap = new CollisionMap(mapData, liveCollisionOverlay);
        resolvedMap.beginSearch();
        out.put("static", edgeReadout(staticMap, x, y, z));
        out.put("resolved", edgeReadout(resolvedMap, x, y, z));

        if (view != null) {
            final Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("n", view.edge(x, y, z, LiveCollisionSnapshot.FLAG_NORTH));
            raw.put("e", view.edge(x, y, z, LiveCollisionSnapshot.FLAG_EAST));
            raw.put("s", view.edge(x, y - 1, z, LiveCollisionSnapshot.FLAG_NORTH));
            raw.put("w", view.edge(x - 1, y, z, LiveCollisionSnapshot.FLAG_EAST));
            out.put("overlayRaw", raw);
        }
        return out;
    }

    private static Map<String, Object> edgeReadout(CollisionMap m, int x, int y, int z) {
        final Map<String, Object> e = new LinkedHashMap<>();
        e.put("n", m.n(x, y, z));
        e.put("e", m.e(x, y, z));
        e.put("s", m.s(x, y, z));
        e.put("w", m.w(x, y, z));
        e.put("blocked", m.isBlocked(x, y, z));
        e.put("hasRegion", m.hasRegion(x, y));
        return e;
    }

    public void refresh(WorldPoint target) {
        if (client == null || config == null) {
            return;
        }
        calculationCutoffMillis = (long) config.calculationCutoff() * Constants.GAME_TICK_LENGTH;
        avoidWilderness = ShortestPathPlugin.override("avoidWilderness", config.avoidWilderness());
        avoidDangerousNpcs = ShortestPathPlugin.override("avoidDangerousNpcs", config.avoidDangerousNpcs());
		plannerSelectionMode = ShortestPathPlugin.override(
			"plannerSelectionMode", config.plannerSelectionMode());
        useAgilityShortcuts = ShortestPathPlugin.override("useAgilityShortcuts", config.useAgilityShortcuts());
        useGrappleShortcuts = ShortestPathPlugin.override("useGrappleShortcuts", config.useGrappleShortcuts());
        useBoats = ShortestPathPlugin.override("useBoats", config.useBoats());
        useCanoes = ShortestPathPlugin.override("useCanoes", config.useCanoes());
        useCharterShips = ShortestPathPlugin.override("useCharterShips", config.useCharterShips());
        useShips = ShortestPathPlugin.override("useShips", config.useShips());
        useMinecarts = ShortestPathPlugin.override("useMinecarts", config.useMinecarts());
        usePoh = ShortestPathPlugin.override("usePoh", config.usePoh());
        useSpiritTreeEtceteria = ShortestPathPlugin.override("spiritTreeEtceteria", config.spiritTreeEtceteria());
        useSpiritTreeBrimhaven = ShortestPathPlugin.override("spiritTreeBrimhaven", config.spiritTreeBrimhaven());
        useSpiritTreePortSarim = ShortestPathPlugin.override("spiritTreePortSarim", config.spiritTreePortSarim());
        useSpiritTreeHosidius = ShortestPathPlugin.override("spiritTreeHosidius", config.spiritTreeHosidius());
        useSpiritTreeFarmingGuild = ShortestPathPlugin.override("spiritTreeFarmingGuild", config.spiritTreeFarmingGuild());
        useTeleportationItems = ShortestPathPlugin.override("useTeleportationItems", config.useTeleportationItems());
        useTeleportationMinigames = ShortestPathPlugin.override("useTeleportationMinigames", config.useTeleportationMinigames());
        useTeleportationLevers = ShortestPathPlugin.override("useTeleportationLevers", config.useTeleportationLevers());
        useTeleportationPortals = ShortestPathPlugin.override("useTeleportationPortals", config.useTeleportationPortals());
        useTeleportationSpells = ShortestPathPlugin.override("useTeleportationSpells", config.useTeleportationSpells());
        useWildernessObelisks = ShortestPathPlugin.override("useWildernessObelisks", config.useWildernessObelisks());
        useMagicCarpets = ShortestPathPlugin.override("useMagicCarpets", config.useMagicCarpets());
        useHotAirBalloons = ShortestPathPlugin.override("useHotAirBalloons", config.useHotAirBalloons());
        useMagicMushtrees = ShortestPathPlugin.override("useMagicMushtrees", config.useMagicMushtrees());
        useSeasonalTransports = ShortestPathPlugin.override("useSeasonalTransports", config.useSeasonalTransports());
        distanceBeforeUsingTeleport = ShortestPathPlugin.override("distanceBeforeUsingTeleports", config.distanceBeforeUsingTeleport());

        //START microbot variables
        useNpcs = config.useNpcs();
        //END microbot variables

        if (GameState.LOGGED_IN.equals(client.getGameState())) {
            long t0 = System.currentTimeMillis();
            boolean inCombat = Rs2Player.isInCombat();
            refreshTransports(target, inCombat);
            long t1 = System.currentTimeMillis();
            //START microbot variables
            refreshRestrictionData();
            long t2 = System.currentTimeMillis();

            // Do not switch tabs here. refresh() runs often (pathfinder restarts, walker compareRoutes);
            // forcing inventory was disruptive and unnecessary — Rs2Inventory reads containers without it.

            WebWalkLog.cfg("refresh transports={}ms restr={}ms total={}ms",
                    t1 - t0, t2 - t1, t2 - t0);
            // The walker blocks on a null pathfinder for the whole of refresh(), so a slow refresh
            // is a visible cold start at route start. Surface it at INFO (not DEBUG) when it is
            // actually slow, so it shows up in normal logs without enabling debug logging.
            if (t2 - t0 >= SLOW_REFRESH_LOG_THRESHOLD_MS) {
                WebWalkLog.cfgSlow("slow refresh transports={}ms restr={}ms total={}ms",
                        t1 - t0, t2 - t1, t2 - t0);
            }
            //END microbot variables
        }
    }

    /**
     * Specialized method for adding usableTeleports to `transports`
     */
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
                transportsPacked.put(packedLocation, existingTeleports);
            } else {
                transports.put(key, usableWildyTeleports);
                transportsPacked.put(packedLocation, usableWildyTeleports);
            }
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
    private void refreshTransports(WorldPoint target, boolean inCombat) {
        // The 1.1s post-login client-thread freeze hid in the UNMEASURED parts of this method: the
        // stage timers summed to ~30ms while the outer wrapper read 1154ms, and the slow-stage log
        // never fired. Three regions were dark: this entry block (quest-state + bank/item gates),
        // the cache-key phase, and the verify/capture block after filtering. Each now has a timer,
        // carried on both the stage log and the slow log, so the next slow login names its stage.
        long entryStart = System.currentTimeMillis();
        useFairyRings = ShortestPathPlugin.override("useFairyRings", config.useFairyRings())
                && !QuestState.NOT_STARTED.equals(Rs2Player.getQuestState(Quest.FAIRYTALE_II__CURE_A_QUEEN))
                && (Rs2Inventory.contains(ItemID.DRAMEN_STAFF, ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF)
                || Rs2Equipment.isWearing(ItemID.DRAMEN_STAFF, ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF)
                || (ShortestPathPlugin.getPathfinderConfig().useBankItems && (Rs2Bank.hasItem(ItemID.DRAMEN_STAFF) || Rs2Bank.hasItem(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF)))
                || Microbot.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE) == 1);
        useGnomeGliders = ShortestPathPlugin.override("useGnomeGliders", config.useGnomeGliders())
                && QuestState.FINISHED.equals(Rs2Player.getQuestState(Quest.THE_GRAND_TREE));
        useSpiritTrees = ShortestPathPlugin.override("useSpiritTrees", config.useSpiritTrees())
                && QuestState.FINISHED.equals(Rs2Player.getQuestState(Quest.TREE_GNOME_VILLAGE));
        useQuetzals = ShortestPathPlugin.override("useQuetzals", config.useQuetzals())
                && QuestState.FINISHED.equals(Rs2Player.getQuestState(Quest.TWILIGHTS_PROMISE));

        long entryTime = System.currentTimeMillis() - entryStart;

        long keyStart = System.currentTimeMillis();
        final Rs2LeaguesTransport.LeaguesContext leaguesCtx = Rs2LeaguesTransport.leaguesContext();
        lastKeyLeaguesMs = System.currentTimeMillis() - keyStart;
        final int refreshCacheKeyHash = computeTransportRefreshCacheKeyHash(target, leaguesCtx, inCombat);
        long keyTime = System.currentTimeMillis() - keyStart;

        TransportRefreshSnapshot snap = transportRefreshSnapshots.get(refreshCacheKeyHash);
        if (snap != null && client != null) {
            int[] boostedProbe = new int[Transport.REQUIREMENT_LEVEL_COUNT];
            final int[] probeOrdinals = snap.sortedSkillOrdinals;
            Microbot.getClientThread().runOnClientThreadOptional(() -> {
                // Only the skills some transport gates on; probing all 23 both cost client-thread
                // time and let hitpoints/prayer drift invalidate an otherwise valid cache.
                if (probeOrdinals != null) {
                    for (int ordinal : probeOrdinals) {
                        if (ordinal >= 0 && ordinal < Transport.REQUIREMENT_LEVEL_COUNT) {
                            boostedProbe[ordinal] = currentRequirementLevel(ordinal);
                        }
                    }
                }
                return true;
            });
            int verProbe = computeTransportRefreshVerificationHash(boostedProbe, snap.sortedSkillOrdinals,
                    snap.sortedVarbitConditions, snap.sortedVarplayerConditions, snap.sortedQuestIds);
            if (verProbe != snap.verificationHash) {
                // Name the component that moved. "reason=verify" alone cannot distinguish a boosted
                // skill from a varbit/varplayer/quest, and guessing which one has already cost a
                // round trip. Reuse the probe we just read rather than re-querying.
                int[] now = computeTransportRefreshVerificationComponents(boostedProbe, snap.sortedSkillOrdinals,
                        snap.sortedVarbitConditions, snap.sortedVarplayerConditions, snap.sortedQuestIds);
                int[] was = snap.verificationComponents;
                lastVerifyMissDetail = was == null || was.length != now.length
                        ? " changed=unknown"
                        : " changed="
                                + (now[0] != was[0] ? "skills," : "")
                                + (now[1] != was[1] ? "varbits," : "")
                                + (now[2] != was[2] ? "varplayers," : "")
                                + (now[3] != was[3] ? "quests," : "");
            }
            if (verProbe == snap.verificationHash) {
                snap.restoreInto(this);
                if (useBankItems && config != null && config.maxSimilarTransportDistance() > 0) {
                    filterSimilarTransports(target);
                }
                WebWalkLog.cfg("refresh_transports cache_hit key={}", refreshCacheKeyHash);
                previousRefreshInvFingerprint = lastComputedInvFingerprint;
                return;
            }
        }

        // Cache miss => full re-evaluation of every transport, which is the pathfinder cold start the
        // walker blocks on. Attribute it: "key" means the cache key changed (items/coins/toggles),
        // "verify" means items were identical but game state moved (skills/varbits/varplayers/quests).
        // invFp vs prevInvFp isolates the common case of spending coins on a fare or toll.
        String cacheMissReason = transportRefreshSnapshots.isEmpty()
                ? "no_snapshot"
                : (snap == null ? "key" : "verify");
        WebWalkLog.cfgSlow("refresh_transports cache_miss reason={} key={} prevKey={} invFp={} prevInvFp={} invChanged={}{}",
                cacheMissReason,
                refreshCacheKeyHash,
                snap == null ? 0 : snap.cacheKeyHash,
                lastComputedInvFingerprint,
                previousRefreshInvFingerprint,
                lastComputedInvFingerprint != previousRefreshInvFingerprint,
                "verify".equals(cacheMissReason) ? lastVerifyMissDetail : "");
        previousRefreshInvFingerprint = lastComputedInvFingerprint;
        lastVerifyMissDetail = "";

        transports.clear();
        transportsPacked.clear();
        blockedTransportEdgesPacked.clear();
        addStaticBlockedEdges();
        // Re-apply runtime-learned edges: addStaticBlockedEdges only restores the shipped set.
        blockedTransportEdgesPacked.addAll(learnedBlockedEdgeKeys);
        usableTeleports.clear();

        long mergeStart = System.currentTimeMillis();
        Map<WorldPoint, Set<Transport>> mergedList = createMergedList();
        long mergeTime = System.currentTimeMillis() - mergeStart;

        long cacheStart = System.currentTimeMillis();
        refreshAvailableItemQuantities = new HashMap<>();
        refreshCurrencyCache = new HashMap<>();
        Rs2Inventory.items().forEach(item -> refreshAvailableItemQuantities.merge(
                item.getId(), Math.max(0, item.getQuantity()), Integer::sum));
        Rs2Equipment.all().forEach(item -> refreshAvailableItemQuantities.merge(
                item.getId(), Math.max(0, item.getQuantity()), Integer::sum));
        if (useBankItems) {
            Rs2Bank.getAll().forEach(item -> refreshAvailableItemQuantities.merge(
                    item.getId(), Math.max(0, item.getQuantity()), Integer::sum));
        }
        refreshAvailableRuneQuantities = new HashMap<>();
        Rs2Magic.getRunes(RuneFilter.builder().includeBank(useBankItems).build())
                .forEach((rune, quantity) -> refreshAvailableRuneQuantities.put(
                        rune.getItemId(), quantity));

        Set<Integer> varbitIds = new HashSet<>();
        List<int[]> varbitConditions = new ArrayList<>();
        Set<Integer> varplayerIds = new HashSet<>();
        List<int[]> varplayerConditions = new ArrayList<>();
        // Skills that some transport actually gates on (see hasRequiredLevels: a level > 0 is a
        // requirement). Only these may participate in the verification hash — otherwise hitpoints
        // regenerating invalidates the whole transport cache.
        Set<Integer> requiredSkillOrdinals = new HashSet<>();
        // Item ids / currency names some transport or restriction gates on — everything else is
        // excluded from the cache key so ordinary inventory churn stops forcing a cold start.
        Set<Integer> relevantItemIds = new HashSet<>();
        Set<String> relevantCurrencyNames = new HashSet<>();
        for (Set<Transport> ts : mergedList.values()) {
            for (Transport t : ts) {
                t.getVarbits().forEach(v -> {
                    varbitIds.add(v.getVarbitId());
                    varbitConditions.add(new int[]{v.getVarbitId(), v.getOperator().ordinal(), v.getValue()});
                });
                t.getVarplayers().forEach(v -> {
                    varplayerIds.add(v.getVarplayerId());
                    varplayerConditions.add(new int[]{v.getVarplayerId(), v.getOperator().ordinal(), v.getValue()});
                });
                int[] required = t.getSkillLevels();
                if (required != null) {
                    for (int i = 0; i < required.length; i++) {
                        if (required[i] > 0) {
                            requiredSkillOrdinals.add(i);
                        }
                    }
                }
                if (t.getItemIdRequirements() != null) {
                    t.getItemIdRequirements().stream()
                            .filter(Objects::nonNull)
                            .forEach(relevantItemIds::addAll);
                }
                if (t.getCurrencyAmount() > 0 && t.getCurrencyName() != null && !t.getCurrencyName().isEmpty()) {
                    relevantCurrencyNames.add(t.getCurrencyName());
                }
            }
        }
        // Restrictions gate on items too (hasRequiredItems(Restriction)), from their own list.
        for (List<Restriction> group : Arrays.asList(resourceRestrictions, customRestrictions)) {
            if (group == null) {
                continue;
            }
            for (Restriction r : group) {
                if (r == null || r.getItemIdRequirements() == null) {
                    continue;
                }
                r.getItemIdRequirements().stream()
                        .filter(Objects::nonNull)
                        .forEach(relevantItemIds::addAll);
            }
        }
        relevantItemIds.addAll(HARDCODED_USABILITY_ITEM_IDS);
        addSpellRuneItemIds(relevantItemIds);
        // Resolve currency to ids HERE, once, rather than comparing item names during fingerprinting —
        // reading an item's name loads its composition on the client thread. An unresolvable currency
        // disables the narrowing rather than losing that currency's invalidation.
        boolean allCurrenciesResolved = true;
        for (String currency : relevantCurrencyNames) {
            int id = currencyItemId(currency);
            if (id > 0) {
                relevantItemIds.add(id);
            } else {
                log.warn("[Walker] transport currency '{}' has no known item id — transport-refresh cache "
                        + "key falls back to fingerprinting every item", currency);
                allCurrenciesResolved = false;
            }
        }
        transportRelevantItemIds = allCurrenciesResolved
                ? Collections.unmodifiableSet(relevantItemIds)
                : null;

        refreshBoostedLevels = new int[Transport.REQUIREMENT_LEVEL_COUNT];
        Map<Integer, Integer> varplayerValues = new HashMap<>();
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            for (int i = 0; i < SKILLS.length; i++) {
                refreshBoostedLevels[i] = client.getBoostedSkillLevel(SKILLS[i]);
            }
            refreshBoostedLevels[Transport.TOTAL_LEVEL_INDEX] = client.getTotalLevel();
            Player localPlayer = client.getLocalPlayer();
            refreshBoostedLevels[Transport.COMBAT_LEVEL_INDEX] =
                    localPlayer == null ? 0 : localPlayer.getCombatLevel();
            refreshBoostedLevels[Transport.QUEST_POINTS_INDEX] = client.getVarpValue(VarPlayer.QUEST_POINTS);
            for (int id : varbitIds) {
                Microbot.getVarbitValue(id);
            }
            // Read varps directly into the refresh snapshot: the global cache drops zero values,
            // so pre-warming through it never helped the zero-valued ones.
            for (int id : varplayerIds) {
                varplayerValues.put(id, client.getVarpValue(id));
            }
            return true;
        });
        refreshVarplayerValues = varplayerValues;
        long cacheTime = System.currentTimeMillis() - cacheStart;

        long filterStart = System.currentTimeMillis();
        int totalTransports = 0;
        int checkedTransports = 0;
        long useTransportTimeNanos = 0;
        Map<TransportType, int[]> typeStats = new java.util.EnumMap<>(TransportType.class);

        // One snapshot for this refreshTransports pass (avoid re-querying unlocked regions per transport).
        // Trade-off: unlock mid-refresh is picked up on next refresh — acceptable vs client-thread churn per edge.
        // Scripts that must path immediately after unlock should trigger an explicit transport refresh / recalc.
        // Reviewers: do not "fix" staleness by calling leaguesContext() per transport — intentional batching; callers refresh explicitly when needed.

        for (Map.Entry<WorldPoint, Set<Transport>> entry : mergedList.entrySet()) {
            WorldPoint point = entry.getKey();
            Set<Transport> usableTransports = new HashSet<>(entry.getValue().size());
            for (Transport transport : entry.getValue()) {
                if (transport == null) {
                    continue;
                }
                totalTransports++;
                updateActionBasedOnQuestState(transport);

                long t0 = System.nanoTime();
                boolean usable = useTransport(transport, inCombat);
                long elapsed = System.nanoTime() - t0;
                useTransportTimeNanos += elapsed;

                TransportType type = transport.getType();
                int[] stats = typeStats.computeIfAbsent(type, k -> new int[]{0, 0, 0});
                stats[0]++;
                stats[2] += (int)(elapsed / 1_000);
                if (usable) stats[1]++;

                // stats[1] is incremented when useTransport() is true; isTransportAllowed may still reject below.
                if (!usable) {
                    addBlockedTransportEdgeIfNeeded(transport);
                    continue;
                }

                if (!Rs2LeaguesTransport.isTransportAllowed(leaguesCtx, transport)) {
                    continue;
                }
                checkedTransports++;
                if (point == null) {
                    usableTeleports.add(transport);
                } else {
                    usableTransports.add(transport);
                }
            }

            if (point != null && !usableTransports.isEmpty()) {
                transports.put(point, usableTransports);
                transportsPacked.put(WorldPointUtil.packWorldPoint(point), usableTransports);
            }
        }

        Rs2LeaguesTransport.injectLeaguesTransports(
                transport -> isTransportUsableWithLeaguesContext(transport, leaguesCtx, inCombat),
                leaguesCtx,
                usableTeleports,
                transports,
                transportsPacked,
                typeStats);
        long filterTime = System.currentTimeMillis() - filterStart;

        long verifyStart = System.currentTimeMillis();
        int[] sortedVarbitConditions = encodeSortedConditionTriples(varbitConditions);
        int[] sortedVarplayerConditions = encodeSortedConditionTriples(varplayerConditions);
        int[] sortedQuestIds = mergedList.values().stream()
                .flatMap(Set::stream)
                .filter(Objects::nonNull)
                .map(Transport::getQuests)
                .filter(Objects::nonNull)
                .flatMap(m -> m.keySet().stream())
                .filter(Objects::nonNull)
                .mapToInt(Quest::getId)
                .distinct()
                .sorted()
                .toArray();
        int[] sortedSkillOrdinals = requiredSkillOrdinals.stream().mapToInt(Integer::intValue).sorted().toArray();
        int verificationHash = computeTransportRefreshVerificationHash(refreshBoostedLevels, sortedSkillOrdinals,
                sortedVarbitConditions, sortedVarplayerConditions, sortedQuestIds);
        int[] verificationComponents = computeTransportRefreshVerificationComponents(refreshBoostedLevels,
                sortedSkillOrdinals, sortedVarbitConditions, sortedVarplayerConditions, sortedQuestIds);
        long verifyTime = System.currentTimeMillis() - verifyStart;
        long captureStart = System.currentTimeMillis();
        transportRefreshSnapshots.put(refreshCacheKeyHash, TransportRefreshSnapshot.capture(
                refreshCacheKeyHash, verificationHash, verificationComponents,
                sortedSkillOrdinals, sortedVarbitConditions, sortedVarplayerConditions, sortedQuestIds,
                transports, usableTeleports));
        long captureTime = System.currentTimeMillis() - captureStart;

        long similarStart = System.currentTimeMillis();
        if (useBankItems && config.maxSimilarTransportDistance() > 0) {
            filterSimilarTransports(target);
        }
        long similarTime = System.currentTimeMillis() - similarStart;

        refreshAvailableItemQuantities = null;
        refreshAvailableRuneQuantities = null;
        refreshBoostedLevels = null;
        refreshCurrencyCache = null;
        refreshVarplayerValues = null;

        // varbit/varplayer counts = distinct ids referenced by merged transport definitions this refresh, not total client var space.
        WebWalkLog.cfg("refresh_transports entry={}ms key={}ms merge={}ms cache={}ms filter={}ms useTrans={}ms verify={}ms capture={}ms similar={}ms total/chk={}/{} usablePost={} vb={} vp={}",
                entryTime, keyTime, mergeTime, cacheTime, filterTime, useTransportTimeNanos / 1_000_000,
                verifyTime, captureTime, similarTime,
                totalTransports, checkedTransports, usableTeleports.size(), varbitIds.size(), varplayerIds.size());

        // Surface the same breakdown at INFO when the miss is slow enough to be the visible cold
        // start, so the dominant stage is identifiable without enabling debug logging.
        long refreshTransportsTotalMs = entryTime + keyTime + mergeTime + cacheTime + filterTime
                + verifyTime + captureTime + similarTime;
        if (refreshTransportsTotalMs >= SLOW_REFRESH_LOG_THRESHOLD_MS) {
            WebWalkLog.cfgSlow("slow refresh_transports entry={}ms key={}ms merge={}ms cache={}ms filter={}ms useTrans={}ms verify={}ms capture={}ms similar={}ms total/chk={}/{} vb={} vp={}",
                    entryTime, keyTime, mergeTime, cacheTime, filterTime, useTransportTimeNanos / 1_000_000,
                    verifyTime, captureTime, similarTime,
                    totalTransports, checkedTransports, varbitIds.size(), varplayerIds.size());
            WebWalkLog.cfgSlow("slow refresh_transports keyDetail leagues={}ms inv={}ms equip={}ms bank={}ms",
                    lastKeyLeaguesMs, lastKeyInvMs, lastKeyEquipMs, lastKeyBankMs);
            typeStats.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue()[2], a.getValue()[2]))
                    .limit(3)
                    .forEach(e -> WebWalkLog.cfgSlow("slow refresh_transports type {} cnt={} passed={} timeMs={}",
                            e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2] / 1000));
        }

        typeStats.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue()[2], a.getValue()[2]))
                .limit(5)
                .forEach(e -> WebWalkLog.cfg("refresh_transports type {} cnt={} passed={} timeMs={}",
                        e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2] / 1000));

    }

    static void addSpellRuneItemIds(Set<Integer> relevantItemIds) {
        Arrays.stream(Runes.values())
                .map(Runes::getItemId)
                .forEach(relevantItemIds::add);
    }

    public boolean isBlockedTransportEdge(int originPacked, int destinationPacked) {
        return blockedTransportEdgesPacked.contains(transportEdgeKey(originPacked, destinationPacked));
    }

    public boolean isBlockedTransportStep(int originPacked, int destinationPacked) {
        return isBlockedTransportStep(originPacked, destinationPacked, blockedTransportEdgesPacked);
    }

    static boolean isBlockedTransportStep(int originPacked, int destinationPacked, Set<Long> blockedEdges) {
        if (blockedEdges == null || blockedEdges.isEmpty()) {
            return false;
        }
        if (blockedEdges.contains(transportEdgeKey(originPacked, destinationPacked))) {
            return true;
        }

        int ox = WorldPointUtil.unpackWorldX(originPacked);
        int oy = WorldPointUtil.unpackWorldY(originPacked);
        int oz = WorldPointUtil.unpackWorldPlane(originPacked);
        int dx = Integer.signum(WorldPointUtil.unpackWorldX(destinationPacked) - ox);
        int dy = Integer.signum(WorldPointUtil.unpackWorldY(destinationPacked) - oy);
        int dz = WorldPointUtil.unpackWorldPlane(destinationPacked) - oz;
        if (dz != 0 || dx == 0 || dy == 0) {
            return false;
        }

        int xThenY = WorldPointUtil.packWorldPoint(ox + dx, oy, oz);
        int yThenX = WorldPointUtil.packWorldPoint(ox, oy + dy, oz);
        return blockedEdges.contains(transportEdgeKey(originPacked, xThenY))
                || blockedEdges.contains(transportEdgeKey(xThenY, destinationPacked))
                || blockedEdges.contains(transportEdgeKey(originPacked, yThenX))
                || blockedEdges.contains(transportEdgeKey(yThenX, destinationPacked));
    }

    public void addBlockedTransportEdgeIfNeeded(Transport transport) {
        if (!blocksWalkingEdgeWhenUnavailable(transport)) {
            return;
        }
        addBlockedEdge(transport.getOrigin(), transport.getDestination());
    }

    static long transportEdgeKey(int originPacked, int destinationPacked) {
        return ((long) originPacked << 32) ^ (destinationPacked & 0xffffffffL);
    }

    private void addStaticBlockedEdges() {
        blockedTransportEdgesPacked.addAll(STATIC_BLOCKED_EDGES_PACKED);
    }

    /**
     * Records a walking edge the walker just failed to traverse (e.g. a door that moved the player
     * the wrong way, or a route click the reachability net proved walled). The observing session
     * blocks the edge immediately — it just watched the failure, and anything less loops the walker
     * into the same obstacle.
     * <p>
     * SESSION-ONLY by policy (2026-08-07): nothing is persisted, and nothing learned in an earlier
     * session is loaded. The hand-curated {@code blocked_edges.tsv} is the sole cross-session
     * authority. The two-strike persistent store this replaces spent its history managing its own
     * failure modes — the Wydin door poisoning needed probation semantics to self-heal, and the
     * store's default file leaked developer state into every test that built a config. An edge worth
     * remembering across sessions is worth a reviewed TSV row.
     * <p>
     * Only the attempted direction is blocked — not bidirectionally — so a genuinely one-way door
     * stays usable the other way. Callers must only pass <em>stable</em> map properties here;
     * temporary, quest/skill-gated doors are handled by {@code restrictions.tsv} and must not be
     * learned, or the bot would avoid them for the rest of the session after the requirement is met.
     *
     * @return {@code true} if this edge was newly blocked for this session; {@code false} if it was
     * already blocked.
     */
    public boolean learnBlockedEdge(WorldPoint origin, WorldPoint destination, String reason) {
        if (origin == null || destination == null) {
            return false;
        }
        long key = transportEdgeKey(
                WorldPointUtil.packWorldPoint(origin),
                WorldPointUtil.packWorldPoint(destination));
        if (!learnedBlockedEdgeKeys.add(key)) {
            return false;
        }
        blockedTransportEdgesPacked.add(key);
        log.info("[Walker] Learned blocked edge {} -> {} ({}) — blocked for THIS SESSION only; "
                + "permanent blocks belong in blocked_edges.tsv", origin, destination, reason);
        return true;
    }

    /**
     * Reverse of {@link #learnBlockedEdge}: removes the learned block so the edge is plannable again.
     * Exists for condition-scoped blocks (a door that refused to open for game-state reasons) that the
     * walker withdraws at the next walk session start. Static rows from blocked_edges.tsv are not
     * touched — they were never in {@code learnedBlockedEdgeKeys}, and {@code blockedTransportEdgesPacked}
     * only drops the key when it was a learned one.
     */
    public boolean unlearnBlockedEdge(WorldPoint origin, WorldPoint destination, String reason) {
        if (origin == null || destination == null) {
            return false;
        }
        long key = transportEdgeKey(
                WorldPointUtil.packWorldPoint(origin),
                WorldPointUtil.packWorldPoint(destination));
        if (!learnedBlockedEdgeKeys.remove(key)) {
            return false;
        }
        if (!STATIC_BLOCKED_EDGES_PACKED.contains(key)) {
            blockedTransportEdgesPacked.remove(key);
        }
        log.info("[Walker] Unlearned blocked edge {} -> {} ({})", origin, destination, reason);
        return true;
    }

    private void addBlockedEdge(WorldPoint origin, WorldPoint destination) {
        blockedTransportEdgesPacked.add(transportEdgeKey(
                WorldPointUtil.packWorldPoint(origin),
                WorldPointUtil.packWorldPoint(destination)));
    }

    private static Set<Long> loadStaticBlockedEdgesFromResources() {
        Set<Long> edges = new HashSet<>();
        final String delimColumn = "\t";
        final String prefixComment = "#";

        try {
            String s = new String(Util.readAllBytes(
                    ShortestPathPlugin.class.getResourceAsStream("blocked_edges.tsv")), StandardCharsets.UTF_8);
            Scanner scanner = new Scanner(s);
            String headerLine = scanner.nextLine();
            headerLine = headerLine.startsWith(prefixComment + " ")
                    ? headerLine.replace(prefixComment + " ", prefixComment)
                    : headerLine;
            headerLine = headerLine.startsWith(prefixComment)
                    ? headerLine.replace(prefixComment, "")
                    : headerLine;
            String[] headers = headerLine.split(delimColumn);

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.startsWith(prefixComment) || line.isBlank()) {
                    continue;
                }

                String[] fields = line.split(delimColumn);
                Map<String, String> fieldMap = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    if (i < fields.length) {
                        fieldMap.put(headers[i], fields[i]);
                    }
                }

                WorldPoint origin = parseBlockedEdgePoint(fieldMap.get("Origin"));
                WorldPoint destination = parseBlockedEdgePoint(fieldMap.get("Destination"));
                boolean bidirectional = Boolean.parseBoolean(fieldMap.getOrDefault("Bidirectional", "false"));
                addStaticEdge(edges, origin, destination);
                if (bidirectional) {
                    addStaticEdge(edges, destination, origin);
                }
            }
            scanner.close();
        } catch (IOException e) {
            throw new RuntimeException("Unable to load shortest-path blocked edges", e);
        }

        return Collections.unmodifiableSet(edges);
    }

    private static WorldPoint parseBlockedEdgePoint(String point) {
        if (point == null || point.isBlank()) {
            throw new IllegalArgumentException("Blocked edge point is blank");
        }
        String[] parts = point.trim().split(" ");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Blocked edge point must be 'x y plane': " + point);
        }
        return new WorldPoint(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]));
    }

    private static void addStaticEdge(Set<Long> edges, WorldPoint origin, WorldPoint destination) {
        edges.add(transportEdgeKey(
                WorldPointUtil.packWorldPoint(origin),
                WorldPointUtil.packWorldPoint(destination)));
    }

    /** True if {@code packedPoint} is within 1 tile of an aggressive-NPC hazard tile. */
    public boolean isDangerousAdjacentTile(int packedPoint) {
        return DANGEROUS_ADJACENT_TILES_PACKED.contains(packedPoint);
    }

    private static Set<Integer> loadDangerousTilesFromResources() {
        Set<Integer> tiles = new HashSet<>();
        final String prefixComment = "#";
        final String delimColumn = "\t";

        try {
            String s = new String(Util.readAllBytes(
                    ShortestPathPlugin.class.getResourceAsStream("dangerous_tiles.tsv")), StandardCharsets.UTF_8);
            Scanner scanner = new Scanner(s);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.startsWith(prefixComment) || line.isBlank()) {
                    continue;
                }
                WorldPoint hazard = parseBlockedEdgePoint(line.split(delimColumn)[0]);
                // Penalize the hazard tile and all 8 neighbours (the melee-aggro ring) so the
                // path keeps >=2 tiles away. Hazard tiles themselves are usually blocked anyway.
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        tiles.add(WorldPointUtil.packWorldPoint(
                                hazard.getX() + dx, hazard.getY() + dy, hazard.getPlane()));
                    }
                }
            }
            scanner.close();
        } catch (IOException e) {
            throw new RuntimeException("Unable to load shortest-path dangerous tiles", e);
        }

        return Collections.unmodifiableSet(tiles);
    }

    private static boolean blocksWalkingEdgeWhenUnavailable(Transport transport) {
        if (transport == null || transport.getOrigin() == null || transport.getDestination() == null) {
            return false;
        }
        return transport.getType() == TransportType.AGILITY_SHORTCUT
                || transport.getType() == TransportType.GRAPPLE_SHORTCUT;
    }


    private Map<WorldPoint, Set<Transport>> createMergedList() {
        if (!usePoh) return allTransports;
        Map<WorldPoint, Set<Transport>> mergedTransports = new HashMap<>();

        // Start with putting all the TSV imported persistent transports
        for (var entry : allTransports.entrySet()) {
            mergedTransports.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }

        // Add transports from PoH to somewhere in the world
        for (var entry : PohPanel.getAvailableTransports(allTransports).entrySet()) {
            mergedTransports
                    .computeIfAbsent(entry.getKey(), k -> new HashSet<>())
                    .addAll(entry.getValue());
        }

        // If we're already in Poh there's no reason to add teleports to Poh
        if (PohTeleports.isInHouse()) {
            return mergedTransports;
        }
        // Add transports from the world to PoH
        for (var entry : PohPanel.getTransportsToPoh().entrySet()) {
            mergedTransports
                    .computeIfAbsent(entry.getKey(), k -> new HashSet<>())
                    .addAll(entry.getValue());
        }
        return mergedTransports;
    }

    public void refresh() {
        refresh(null);
    }

    /**
     * Drop the transport-refresh snapshot cache so the next {@link #refresh(WorldPoint)} rebuilds transport maps
     * (inventory/quest/varbit changes that are not captured by the memo key, script-driven transport mutations, etc.).
     */
    public void invalidateTransportRefreshCache() {
        transportRefreshSnapshots.clear();
    }

    /**
     * Rebuilds base transport definitions from packaged TSV resources and swaps them into {@link #allTransports}.
     * The next {@link #refresh(WorldPoint)} will use the reloaded definitions.
     *
     * @return number of origin nodes loaded
     */
    public int reloadTransportDefinitionsFromResources() {
        Map<WorldPoint, Set<Transport>> reloaded = Transport.reloadFromResources();
        replaceAllTransports(reloaded);
        invalidateTransportRefreshCache();
        return allTransports.size();
    }

    private void replaceAllTransports(Map<WorldPoint, Set<Transport>> source) {
        allTransports.clear();
        if (source == null || source.isEmpty()) {
            return;
        }
        source.forEach((origin, set) -> {
            Set<Transport> valid = set == null ? new HashSet<>() : new HashSet<>(set);
            valid.remove(null);
            allTransports.put(origin, valid);
        });
    }

    private void refreshRestrictionData() {
        internalRestrictedPointsPacked.clear();
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
                        QuestState playerState = Rs2Player.getQuestState(qe.getKey());
                        QuestState requiredState = qe.getValue();
                        int playerIndex = questStateOrder.indexOf(playerState);
                        int requiredIndex = questStateOrder.indexOf(requiredState);
                        return playerIndex < requiredIndex;
                    })) {
                        return true;
                    }
                    // Varbit check
                    if (entry.getVarbits().stream().anyMatch(varbitCheck -> !varbitCheck.matches(Microbot.getVarbitValue(varbitCheck.getVarbitId())))) {
                        return true;
                    }
                    // Varplayer check
                    if (entry.getVarplayers().stream().anyMatch(varplayerCheck -> !varplayerCheck.matches(getLiveVarplayerValue(varplayerCheck.getVarplayerId())))) {
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
                .forEach(entry -> internalRestrictedPointsPacked.add(entry.getPackedWorldPoint()));
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

    public static boolean isInWildernessPackedPoint(Set<Integer> packedPoints) {
        for (int packedPoint : packedPoints) {
            if (isInWilderness(packedPoint)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isInWilderness(Set<WorldPoint> worldPoints) {
        for (WorldPoint worldPoint : worldPoints) {
            if (isInWilderness(worldPoint)) {
                return true;
            }
        }
        return false;
    }

    public boolean avoidWilderness(int packedPosition, int packedNeighborPosition, boolean targetInWilderness) {
        return avoidWilderness && !targetInWilderness
                && !isInWilderness(packedPosition) && isInWilderness(packedNeighborPosition);
    }

    public boolean isInLevel20Wilderness(int packedPoint) {
        return WorldPointUtil.distanceToArea(packedPoint, WILDERNESS_ABOVE_GROUND_LEVEL_20) == 0
                || WorldPointUtil.distanceToArea(packedPoint, WILDERNESS_UNDERGROUND_LEVEL_20) == 0;
    }

    public boolean isInLevel30Wilderness(int packedPoint) {
        return WorldPointUtil.distanceToArea(packedPoint, WILDERNESS_ABOVE_GROUND_LEVEL_30) == 0
                || WorldPointUtil.distanceToArea(packedPoint, WILDERNESS_UNDERGROUND_LEVEL_30) == 0;
    }

    private boolean completedQuests(Transport transport) {
        return TransportRequirementPolicy.completedQuests(transport, questStateOrder);
    }

    private boolean varbitChecks(Transport transport) {
        return TransportRequirementPolicy.varbitChecks(transport);
    }

    private boolean varplayerChecks(Transport transport) {
        return transport.getVarplayers().isEmpty() ||
                transport.getVarplayers().stream()
                        .allMatch(varplayerCheck -> varplayerCheck.matches(getVarplayerValue(varplayerCheck.getVarplayerId())));
    }

    /** Refresh-scoped snapshot first (no cross-thread hop); live read only outside a refresh pass. */
    private int getVarplayerValue(int varplayerId) {
        Map<Integer, Integer> snapshot = refreshVarplayerValues;
        if (snapshot != null) {
            Integer value = snapshot.get(varplayerId);
            if (value != null) {
                return value;
            }
        }
        return getLiveVarplayerValue(varplayerId);
    }

    private int getLiveVarplayerValue(int varplayerId) {
        return Microbot.getClientThread()
                .runOnClientThreadOptional(() -> client.getVarpValue(varplayerId))
                .orElse(0);
    }

    private boolean useTransport(Transport transport, boolean inCombat) {
        // This runs once per expanded catalog edge during every refresh. Keep individual rejection
        // reasons at TRACE; DEBUG already receives the per-type aggregate emitted by refreshTransports.
        if (transport == null || !transportPlanningPolicy.isAdmitted(transport)) {
            log.trace("Transport ( O: {} D: {} type={} ) has no registered Microbot executor",
                    transport == null ? null : transport.getOrigin(),
                    transport == null ? null : transport.getDestination(),
                    transport == null ? null : transport.getType());
            return false;
        }
        if (!isTransportAllowedDuringCombat(transport, inCombat)) {
            log.trace("Transport ( O: {} D: {} ) is a home teleport unavailable during combat",
                    transport.getOrigin(), transport.getDestination());
            return false;
        }
        // Check if the feature flag is disabled
        if (!isFeatureEnabled(transport)) {
            log.trace("Transport Type {} is disabled by feature flag", transport.getType());
            return false;
        }
        // If the transport requires you to be in a members world (used for more granular member requirements)
        if (transport.isMembers() && !client.getWorldType().contains(WorldType.MEMBERS)) {
            log.trace("Transport ( O: {} D: {} ) requires members world", transport.getOrigin(), transport.getDestination());
            return false;
        }
        if (transport.getType() == TransportType.SPIRIT_TREE && !isSpiritTreeRouteEnabled(transport)) {
            log.trace("Transport ( O: {} D: {} ) is a spirit tree route but the tree is disabled", transport.getOrigin(), transport.getDestination());
            return false;
        }
        // If you don't meet level requirements
        if (!hasRequiredLevels(transport)) {
            log.trace("Transport ( O: {} D: {} ) requires skill levels {}", transport.getOrigin(), transport.getDestination(), Arrays.toString(transport.getSkillLevels()));
            return false;
        }
        // If the transport has quest requirements & the quest haven't been completed
        if (transport.isQuestLocked() && !completedQuests(transport)) {
            log.trace("Transport ( O: {} D: {} ) requires quests {}", transport.getOrigin(), transport.getDestination(), transport.getQuests());
            return false;
        }

        // If the transport has varbit requirements & the varbits do not match
        if (!varbitChecks(transport)) {
            log.trace("Transport ( O: {} D: {} ) requires varbits {}", transport.getOrigin(), transport.getDestination(), transport.getVarbits());
            return false;
        }

        // If the transport has varplayer requirements & the varplayers do not match
        if (!varplayerChecks(transport)) {
            log.trace("Transport ( O: {} D: {} ) requires varplayers {}", transport.getOrigin(), transport.getDestination(), transport.getVarplayers());
            return false;
        }

        // If you don't have the required currency & amount for transport
        if (transport.getCurrencyAmount() > 0) {
            if (refreshCurrencyCache != null) {
                int[] cached = refreshCurrencyCache.computeIfAbsent(transport.getCurrencyName(), name -> {
                    int invCount = Rs2Inventory.itemQuantity(name);
                    int bankCount = useBankItems ? Rs2Bank.count(name) : 0;
                    return new int[]{invCount, bankCount};
                });
                if (cached[0] < transport.getCurrencyAmount() && cached[1] < transport.getCurrencyAmount()) {
                    log.trace("Transport ( O: {} D: {} ) requires {} x {}", transport.getOrigin(), transport.getDestination(), transport.getCurrencyAmount(), transport.getCurrencyName());
                    return false;
                }
            } else if (!Rs2Inventory.hasItemAmount(transport.getCurrencyName(), transport.getCurrencyAmount())
                    && !(useBankItems && Rs2Bank.count(transport.getCurrencyName()) >= transport.getCurrencyAmount())) {
                log.trace("Transport ( O: {} D: {} ) requires {} x {}", transport.getOrigin(), transport.getDestination(), transport.getCurrencyAmount(), transport.getCurrencyName());
                return false;
            }
        }

        // Check if Teleports are globally disabled
        if (TransportType.isTeleport(transport.getType(), transport.getOrigin()) && Rs2Walker.disableTeleports) {
            log.trace("Transport ( O: {} D: {} ) is a teleport but teleports are globally disabled", transport.getOrigin(), transport.getDestination());
            return false;
        }

        // Check Teleport Item Settings
        if (transport.getType() == TELEPORTATION_ITEM) {
            boolean isUsable = isTeleportationItemUsable(transport);
            if (!isUsable) {
                log.trace("Transport ( O: {} D: {} ) is a teleport item but is not usable", transport.getOrigin(), transport.getDestination());
            }
            return isUsable;
        }
        // Check Teleport Spell Settings
        if (transport.getType() == TELEPORTATION_SPELL) {
            boolean isUsable = isTeleportationSpellUsable(transport);
            if (!isUsable) {
                log.trace("Transport ( O: {} D: {} ) is a teleport spell but is not usable", transport.getOrigin(), transport.getDestination());
            }
            return isUsable;
        }

        // Used for Generic Item Requirements
        if (!transport.getItemIdRequirements().isEmpty()) {
            boolean hasRequiredItems = hasRequiredItems(transport);
            if (!hasRequiredItems) {
                log.trace("Transport ( O: {} D: {} ) requires items {}", transport.getOrigin(), transport.getDestination(), transport.getItemIdRequirements().stream().flatMap(Set::stream).collect(Collectors.toSet()));
            }
            return hasRequiredItems;
        }

        return true;
    }

    /**
     * Same gating as the main {@link #refreshTransports} loop, for rows injected after the merge pass
     * (Leagues catalog / Area teleports): quest action patch, {@link #useTransport}, {@link Rs2LeaguesTransport#isTransportAllowed}.
     */
    public boolean isTransportUsableWithLeaguesContext(Transport transport, Rs2LeaguesTransport.LeaguesContext leaguesCtx) {
        return isTransportUsableWithLeaguesContext(transport, leaguesCtx, Rs2Player.isInCombat());
    }

    private boolean isTransportUsableWithLeaguesContext(Transport transport,
                                                          Rs2LeaguesTransport.LeaguesContext leaguesCtx,
                                                          boolean inCombat) {
        if (client == null || transport == null || leaguesCtx == null) {
            return false;
        }
        updateActionBasedOnQuestState(transport);
        if (!useTransport(transport, inCombat)) {
            return false;
        }
        return Rs2LeaguesTransport.isTransportAllowed(leaguesCtx, transport);
    }

    boolean isTransportAllowedDuringCombat(Transport transport, boolean inCombat) {
        return transport != null && (!inCombat || !transportPlanningPolicy.isZeroRuneSpell(transport));
    }

    /**
     * Checks if the player has all the required skill levels for the transport
     */
    private boolean hasRequiredLevels(Transport transport) {
        int[] requiredLevels = transport.getSkillLevels();
        if (refreshBoostedLevels != null) {
            return meetsRequiredLevels(requiredLevels, refreshBoostedLevels);
        }
        return IntStream.range(0, requiredLevels.length)
            .filter(i -> requiredLevels[i] > 0)
            .allMatch(i -> currentRequirementLevel(i) >= requiredLevels[i]);
    }

    static boolean meetsRequiredLevels(int[] requiredLevels, int[] currentLevels) {
        if (requiredLevels == null || currentLevels == null || currentLevels.length < requiredLevels.length) {
            return false;
        }
        for (int i = 0; i < requiredLevels.length; i++) {
            if (requiredLevels[i] > 0 && currentLevels[i] < requiredLevels[i]) {
                return false;
            }
        }
        return true;
    }

    private int currentRequirementLevel(int index) {
        if (index >= 0 && index < SKILLS.length) {
            return client.getBoostedSkillLevel(SKILLS[index]);
        }
        if (index == Transport.TOTAL_LEVEL_INDEX) {
            return client.getTotalLevel();
        }
        if (index == Transport.COMBAT_LEVEL_INDEX) {
            Player localPlayer = client.getLocalPlayer();
            return localPlayer == null ? 0 : localPlayer.getCombatLevel();
        }
        if (index == Transport.QUEST_POINTS_INDEX) {
            return client.getVarpValue(VarPlayer.QUEST_POINTS);
        }
        return 0;
    }

    /**
     * Checks if the player has all the required skill levels for the restriction
     */
    private boolean hasRequiredLevels(Restriction restriction) {
        int[] requiredLevels = restriction.getSkillLevels();
        Skill[] skills = Skill.values();
        return IntStream.range(0, requiredLevels.length)
            .filter(i -> requiredLevels[i] > 0)
            .allMatch(i -> Microbot.getClient().getBoostedSkillLevel(skills[i]) >= requiredLevels[i]);
    }

    private void updateActionBasedOnQuestState(Transport transport) {
        if (Objects.equals(transport.getType(), TransportType.SHIP) &&
                (Objects.equals(transport.getName(), "Veos") || Objects.equals(transport.getName(), "Captain Magoro"))) {
            QuestState questState = Rs2Player.getQuestState(Quest.CLIENT_OF_KOUREND);
            if (questState != QuestState.FINISHED && !Objects.equals(transport.getAction(), "Talk-to")) {
                transport.setAction("Talk-to");
            }
        }
    }

    /**
     * Toggle for {@link #SPIRIT_TREE_DESTINATIONS_ORDERED}[{@code index}]. Must stay aligned with array length.
     */
    private boolean spiritTreeDestinationToggle(int index) {
        switch (index) {
            case 0:
                return useSpiritTreeEtceteria;
            case 1:
                return useSpiritTreeBrimhaven;
            case 2:
                return useSpiritTreePortSarim;
            case 3:
                return useSpiritTreeHosidius;
            case 4:
                return useSpiritTreeFarmingGuild;
            default:
                throw new AssertionError("spirit tree index " + index);
        }
    }

    private boolean isSpiritTreeRouteEnabled(Transport transport) {
        WorldPoint origin = transport.getOrigin();
        WorldPoint destination = transport.getDestination();
        for (int i = 0; i < SPIRIT_TREE_DESTINATIONS_ORDERED.length; i++) {
            if (!spiritTreeDestinationToggle(i)) {
                WorldPoint toggledPoint = SPIRIT_TREE_DESTINATIONS_ORDERED[i];
                if ((destination != null && destination.equals(toggledPoint))
                        || (origin != null && origin.distanceTo2D(toggledPoint) <= 5)) {
                    return false;
                }
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
                case POH:
                case QUETZAL:
                case WILDERNESS_OBELISK:
                case TELEPORTATION_LEVER:
                case TELEPORTATION_MINIGAME:
                case MAGIC_CARPET:
                case SPIRIT_TREE:
                    return false;
            }
        }

        return isTransportTypeEnabled(type);
    }

    /** Immutable feature-toggle snapshot for planner-independent request policy. */
    public Set<TransportType> getEnabledTransportTypes() {
        EnumSet<TransportType> enabled = EnumSet.noneOf(TransportType.class);
        for (TransportType type : TransportType.values()) {
            if (isTransportTypeEnabled(type)) {
                enabled.add(type);
            }
        }
        return Collections.unmodifiableSet(enabled);
    }

    public TeleportationItem getTeleportationItemPolicy() {
        return useTeleportationItems == null ? TeleportationItem.NONE : useTeleportationItems;
    }

    public boolean isMembersWorld() {
        return client == null || client.getWorldType().contains(WorldType.MEMBERS);
    }

    private boolean isTransportTypeEnabled(TransportType type) {
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
            case POH:
                return usePoh;
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
            case HOT_AIR_BALLOON:
                return useHotAirBalloons;
            case MAGIC_MUSHTREE:
                return useMagicMushtrees;
            case SEASONAL_TRANSPORT:
                return useSeasonalTransports;
            case WILDERNESS_OBELISK:
                return useWildernessObelisks;
            default:
                return true; // Default to enabled if no specific toggle
        }
    }

    /**
     * Checks if a teleportation item is usable
     */
    private boolean isTeleportationItemUsable(Transport transport) {
        if (!isTeleportationItemAllowedByPolicy(useTeleportationItems, transport.isConsumable())) {
            return false;
        }

        return hasRequiredItems(transport);
    }

    static boolean isTeleportationItemAllowedByPolicy(
            TeleportationItem policy,
            boolean consumable) {
        return policy != TeleportationItem.NONE
                && (policy != TeleportationItem.INVENTORY_NON_CONSUMABLE || !consumable);
    }

    /**
     * Checks if the player has any of the required equipment and inventory items for the transport
     */
    private boolean hasRequiredItems(Transport transport) {
        return TransportItemRequirement.selectProviders(
                transport.getItemRequirements(),
                this::availableRequirementItemQuantity,
                itemId -> availableItemQuantity(itemId) > 0,
                itemId -> availableItemQuantity(itemId) > 0).isPresent();
    }

    static boolean meetsItemRequirements(
            List<TransportItemRequirement> requirements,
            java.util.function.IntUnaryOperator availableQuantity) {
        if (requirements == null || requirements.isEmpty()) {
            return true;
        }
        return requirements.stream().allMatch(requirement -> requirement.isSatisfiedBy(availableQuantity));
    }

    private int availableItemQuantity(int itemId) {
        if (itemId == ItemID.CHRONICLE && !hasChronicleCharges()) {
            return 0;
        }
        if (refreshAvailableItemQuantities != null) {
            return refreshAvailableItemQuantities.getOrDefault(itemId, 0);
        }
        int quantity = Rs2Inventory.itemQuantity(itemId);
        Rs2ItemModel equipped = Rs2Equipment.get(itemId);
        if (equipped != null) {
            quantity += Math.max(1, equipped.getQuantity());
        }
        if (useBankItems) {
            quantity += Rs2Bank.count(itemId);
        }
        return quantity;
    }

    private int availableRequirementItemQuantity(int itemId) {
        Map<Integer, Integer> runeSnapshot = refreshAvailableRuneQuantities;
        if (runeSnapshot != null) {
            return Math.max(availableItemQuantity(itemId), runeSnapshot.getOrDefault(itemId, 0));
        }
        Runes rune = Runes.byItemId(itemId);
        if (rune == null) {
            return availableItemQuantity(itemId);
        }
        int runeQuantity = Rs2Magic.getRunes(
                RuneFilter.builder().includeBank(useBankItems).build()).getOrDefault(rune, 0);
        return Math.max(availableItemQuantity(itemId), runeQuantity);
    }

    /**
     * Checks if the player has any of the required equipment and inventory items for the restriction
     */
    private boolean hasRequiredItems(Restriction restriction) {
        return restriction.getItemIdRequirements()
                .stream()
                .flatMap(Collection::stream)
                .anyMatch(itemId -> Rs2Equipment.isWearing(itemId) || Rs2Inventory.hasItem(itemId));
    }


    boolean isTeleportationSpellUsable(Transport transport) {
        if (transportPlanningPolicy.isZeroRuneSpell(transport)) {
            // Every spellbook home teleport is a zero-rune widget action. Spellbook, membership,
            // quest, Wilderness and cooldown requirements were checked earlier in useTransport().
            return true;
        }

        if (!transport.getItemRequirements().isEmpty()) {
            return hasRequiredItems(transport);
        }

        boolean hasMultipleDestination = transport.getDisplayInfo().contains(":");
        String displayInfo = hasMultipleDestination
                ? transport.getDisplayInfo().split(":")[0].trim().toLowerCase()
                : transport.getDisplayInfo();
        Rs2Spells rs2Spell = Rs2Magic.getRs2Spell(displayInfo);
        if (rs2Spell == null) return false;
        return Rs2Magic.hasRequiredRunes(rs2Spell, RuneFilter.builder().includeBank(useBankItems).build());
//        return Rs2Magic.quickCanCast(displayInfo);
    }

    /**
     * Checks if the Chronicle has charges
     */
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

    @Deprecated(since = "1.6.2 - Add Restrictions to restrictions.tsv", forRemoval = true)
    public void setRestrictedTiles(Restriction... restrictions) {
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

        // Generate final summary
        filteringSummary.append("--- Filtering Results ---");
        int totalRemoved = transportsToPurge.size();
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
     * @param teleports           The set of teleports to filter
     * @param allUsableTransports Map of all usable transports by location
     * @param maxDistance         Maximum distance for considering transports similar
     * @param target              Optional target destination for optimization
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
        } else if (transport.getType() == TransportType.POH) {
            return "Player Owned House";
        } else if (transport.getType() == TransportType.TRANSPORT) {
            return "Transport";
        } else {
            return transport.getType().toString();
        }
    }

    private int computeTransportRefreshCacheKeyHash(WorldPoint target,
                                                     Rs2LeaguesTransport.LeaguesContext leaguesCtx,
                                                     boolean inCombat) {
        assert leaguesCtx != null;
        int invFp = fingerprintInventoryEquipmentBank();
        lastComputedInvFingerprint = invFp;
        int members = (client != null && client.getWorldType().contains(WorldType.MEMBERS)) ? 1 : 0;
        int preferTp = (config != null && config.preferTransportToTarget()) ? 1 : 0;
        int maxSimilar = config != null ? config.maxSimilarTransportDistance() : 0;
        return Objects.hash(
                packTransportRefreshToggleBits(),
                useTeleportationItems,
                ignoreTeleportAndItems,
                useBankItems,
                useNpcs,
                inCombat,
                invFp,
                members,
                Rs2Walker.disableTeleports,
                Microbot.getVarbitValue(VarbitID.LEAGUE_TYPE),
                leaguesCtx.isActive(),
                leaguesCtx.getUnlockedRegions().hashCode(),
                usePoh,
                PohTeleports.isInHouse(),
                maxSimilar,
                preferTp,
                distanceBeforeUsingTeleport);
    }

    private long packTransportRefreshToggleBits() {
        long bits = 0;
        int s = 0;
        if (useAgilityShortcuts) bits |= 1L << s;
        s++;
        if (useGrappleShortcuts) bits |= 1L << s;
        s++;
        if (useBoats) bits |= 1L << s;
        s++;
        if (useCanoes) bits |= 1L << s;
        s++;
        if (useCharterShips) bits |= 1L << s;
        s++;
        if (useShips) bits |= 1L << s;
        s++;
        if (useFairyRings) bits |= 1L << s;
        s++;
        if (useGnomeGliders) bits |= 1L << s;
        s++;
        if (useMinecarts) bits |= 1L << s;
        s++;
        if (usePoh) bits |= 1L << s;
        s++;
        if (useQuetzals) bits |= 1L << s;
        s++;
        if (useSpiritTrees) bits |= 1L << s;
        s++;
        if (useTeleportationLevers) bits |= 1L << s;
        s++;
        if (useTeleportationMinigames) bits |= 1L << s;
        s++;
        if (useTeleportationPortals) bits |= 1L << s;
        s++;
        if (useTeleportationSpells) bits |= 1L << s;
        s++;
        if (useMagicCarpets) bits |= 1L << s;
        s++;
        if (useHotAirBalloons) bits |= 1L << s;
        s++;
        if (useMagicMushtrees) bits |= 1L << s;
        s++;
        if (useSeasonalTransports) bits |= 1L << s;
        s++;
        if (useWildernessObelisks) bits |= 1L << s;
        s++;
        if (useSpiritTreeEtceteria) bits |= 1L << s;
        s++;
        if (useSpiritTreeBrimhaven) bits |= 1L << s;
        s++;
        if (useSpiritTreePortSarim) bits |= 1L << s;
        s++;
        if (useSpiritTreeHosidius) bits |= 1L << s;
        s++;
        if (useSpiritTreeFarmingGuild) bits |= 1L << s;
        s++;
        if (avoidWilderness) bits |= 1L << s;
        return bits;
    }

    /**
     * Items no transport can possibly gate on must not participate in the cache key — the same
     * argument already applied to skills above.
     * <p>
     * Hashing EVERY item meant any inventory, equipment or bank change minted a new key, and a new key
     * is a full re-evaluation of ~5,700 transports. Measured over a questing session: 0 cache hits, 22
     * misses, every one of them {@code invChanged=true}. Quests are the pathological case because they
     * churn the inventory constantly AND walk after nearly every step, so they paid the ~2.3s cold
     * start (7.8s worst) on almost every walk. A fishing script churns items too but walks once a trip,
     * so it only ate it occasionally — which is why this looked like a questing bug.
     * <p>
     * Usability depends on item state in exactly four places, all enumerated into the relevant sets by
     * {@link #collectTransportRelevantItemState}: transport {@code itemIdRequirements}, restriction
     * {@code itemIdRequirements}, the hardcoded fairy-ring staves and Chronicle, and currency — which
     * is matched by NAME, not id, so ids alone would silently stop coin changes invalidating the
     * cache and leave a stale "you can afford this" verdict.
     *
     * Deliberately takes an ITEM ID only. An earlier version also compared {@code item.getName()}
     * against the currency names, which was a client-thread stall in disguise: {@link
     * net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel#getName()} lazily loads the item
     * composition through {@code runOnClientThread}, so fingerprinting a full bank fired hundreds of
     * client-thread round-trips per cache-key computation and every other script's queued task timed
     * out at once. Currency is resolved to ids at collection time instead — see
     * {@link #currencyItemId(String)} — so this stays pure arithmetic.
     */
    static boolean itemAffectsTransportUsability(int itemId, Set<Integer> relevantItemIds) {
        // Null set = not built yet, or a currency we could not resolve: fingerprint everything, which
        // is exactly the old behaviour and never under-invalidates.
        return relevantItemIds == null || relevantItemIds.contains(itemId);
    }

    /**
     * Currency name to item id, mirroring the banking planner's resolver. Returns -1 for anything
     * unknown, which disables the narrowing entirely rather than silently dropping that currency's
     * invalidation.
     */
    private static int currencyItemId(String currencyName) {
        if (currencyName == null || currencyName.trim().isEmpty()) {
            return -1;
        }
        switch (currencyName.trim().toLowerCase()) {
            case "coins":
                return ItemID.COINS;
            case "ecto-token":
                return ItemID.ECTOTOKEN;
            default:
                return -1;
        }
    }

    // The cold-login key phase measured 658ms of an 833ms client-thread refresh (2026-08-13 19:40,
    // reason=no_snapshot; warm refreshes read 1ms) — these name which read pays it. Written on every
    // fingerprint, printed only on the slow log.
    private volatile long lastKeyLeaguesMs;
    private volatile long lastKeyInvMs;
    private volatile long lastKeyEquipMs;
    private volatile long lastKeyBankMs;

    private int fingerprintInventoryEquipmentBank() {
        final Set<Integer> ids = transportRelevantItemIds;
        final int[] h = {1};
        long t = System.currentTimeMillis();
        Rs2Inventory.items().forEach(item -> {
            if (!itemAffectsTransportUsability(item.getId(), ids)) return;
            h[0] = 31 * h[0] + item.getId();
            h[0] = 31 * h[0] + item.getQuantity();
        });
        lastKeyInvMs = System.currentTimeMillis() - t;
        t = System.currentTimeMillis();
        Rs2Equipment.all().forEach(item -> {
            if (!itemAffectsTransportUsability(item.getId(), ids)) return;
            h[0] = 31 * h[0] + item.getId();
            h[0] = 31 * h[0] + item.getQuantity();
        });
        lastKeyEquipMs = System.currentTimeMillis() - t;
        t = System.currentTimeMillis();
        if (useBankItems) {
            Rs2Bank.getAll().forEach(item -> {
                if (!itemAffectsTransportUsability(item.getId(), ids)) return;
                h[0] = 31 * h[0] + item.getId();
                h[0] = 31 * h[0] + item.getQuantity();
            });
        }
        lastKeyBankMs = System.currentTimeMillis() - t;
        return h[0];
    }

    private static int computeTransportRefreshVerificationHash(int[] boostedLevels, int[] sortedSkillOrdinals,
            int[] sortedVarbitConditions, int[] sortedVarplayerConditions, int[] sortedQuestIds) {
        return computeTransportRefreshVerificationHash(boostedLevels, sortedSkillOrdinals, sortedVarbitConditions, sortedVarplayerConditions,
                sortedQuestIds, questId -> {
            Quest quest = resolveQuestById(questId);
            return quest == null ? QuestState.NOT_STARTED : Rs2Player.getQuestState(quest);
        });
    }

    /**
     * @param sortedSkillOrdinals skills that some transport actually gates on. Hashing every skill
     *                            (the previous {@code Arrays.hashCode(boostedLevels)}) meant
     *                            hitpoints regenerating or prayer draining invalidated the transport
     *                            cache, forcing a full ~2.6s re-evaluation on most walks. A skill no
     *                            transport requires cannot change any transport's usability, so it
     *                            must not participate — mirroring how varbits/varplayers are already
     *                            collected from the transports themselves.
     */
    static int computeTransportRefreshVerificationHash(int[] boostedLevels, int[] sortedSkillOrdinals,
            int[] sortedVarbitConditions, int[] sortedVarplayerConditions,
            int[] sortedQuestIds, IntFunction<QuestState> questStateProvider) {
        assert boostedLevels != null;
        int h = 1;
        if (sortedSkillOrdinals != null) {
            for (int ordinal : sortedSkillOrdinals) {
                if (ordinal < 0 || ordinal >= boostedLevels.length) {
                    continue;
                }
                h = 31 * h + ordinal;
                h = 31 * h + boostedLevels[ordinal];
            }
        }
        h = 31 * h + hashVarbitConditionVerdicts(sortedVarbitConditions);
        h = 31 * h + hashVarplayerConditionVerdicts(sortedVarplayerConditions);
        for (int questId : sortedQuestIds) {
            h = 31 * h + questId;
            h = 31 * h + questStateHashCode(questStateProvider.apply(questId));
        }
        int clientOfKourendId = Quest.CLIENT_OF_KOUREND.getId();
        if (Arrays.binarySearch(sortedQuestIds, clientOfKourendId) < 0) {
            h = 31 * h + clientOfKourendId;
            h = 31 * h + questStateHashCode(questStateProvider.apply(clientOfKourendId));
        }
        return h;
    }

    /**
     * The verification hash split into its four independent parts
     * {skills, varbits, varplayers, quests}, so a verify-miss can name what actually changed instead
     * of only reporting that something did.
     */
    static int[] computeTransportRefreshVerificationComponents(int[] boostedLevels, int[] sortedSkillOrdinals,
            int[] sortedVarbitConditions, int[] sortedVarplayerConditions, int[] sortedQuestIds) {
        return computeTransportRefreshVerificationComponents(boostedLevels, sortedSkillOrdinals, sortedVarbitConditions,
                sortedVarplayerConditions, sortedQuestIds, questId -> {
            Quest quest = resolveQuestById(questId);
            return quest == null ? QuestState.NOT_STARTED : Rs2Player.getQuestState(quest);
        });
    }

    static int[] computeTransportRefreshVerificationComponents(int[] boostedLevels, int[] sortedSkillOrdinals,
            int[] sortedVarbitConditions, int[] sortedVarplayerConditions, int[] sortedQuestIds,
            IntFunction<QuestState> questStateProvider) {
        int skills = 1;
        if (boostedLevels != null && sortedSkillOrdinals != null) {
            for (int ordinal : sortedSkillOrdinals) {
                if (ordinal < 0 || ordinal >= boostedLevels.length) continue;
                skills = 31 * skills + ordinal;
                skills = 31 * skills + boostedLevels[ordinal];
            }
        }
        int varbits = hashVarbitConditionVerdicts(sortedVarbitConditions);
        int varplayers = hashVarplayerConditionVerdicts(sortedVarplayerConditions);
        int quests = 1;
        if (sortedQuestIds != null) {
            for (int questId : sortedQuestIds) {
                quests = 31 * quests + questId;
                quests = 31 * quests + questStateHashCode(questStateProvider.apply(questId));
            }
            // Mirror computeTransportRefreshVerificationHash's explicit CLIENT_OF_KOUREND fallback so this
            // component stays aligned with the full verification hash when the quest is absent from the list.
            int clientOfKourendId = Quest.CLIENT_OF_KOUREND.getId();
            if (Arrays.binarySearch(sortedQuestIds, clientOfKourendId) < 0) {
                quests = 31 * quests + clientOfKourendId;
                quests = 31 * quests + questStateHashCode(questStateProvider.apply(clientOfKourendId));
            }
        }
        return new int[]{skills, varbits, varplayers, quests};
    }


    /**
     * Deterministic {id, operatorOrdinal, value} triples for every varbit/varplayer condition any
     * transport declares, deduplicated and sorted.
     */
    static int[] encodeSortedConditionTriples(List<int[]> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return new int[0];
        }
        java.util.TreeSet<int[]> sorted = new java.util.TreeSet<>((a, b) -> {
            if (a[0] != b[0]) return Integer.compare(a[0], b[0]);
            if (a[1] != b[1]) return Integer.compare(a[1], b[1]);
            return Integer.compare(a[2], b[2]);
        });
        sorted.addAll(conditions);
        int[] out = new int[sorted.size() * 3];
        int i = 0;
        for (int[] c : sorted) {
            out[i++] = c[0];
            out[i++] = c[1];
            out[i++] = c[2];
        }
        return out;
    }

    /**
     * Hashes varplayer condition VERDICTS rather than raw varplayer values.
     * <p>
     * A {@code COOLDOWN_MINUTES} condition compares against wall-clock minutes, so its raw value
     * advances continuously and would invalidate the transport cache forever. Worse, casting Home
     * Teleport writes {@code LAST_HOME_TELEPORT} — one of the hashed varplayers — so a teleport
     * invalidated the cache simply by being used, forcing a full re-evaluation of every transport
     * (~2.6s) immediately after the teleport it had just performed.
     * <p>
     * Only a flip in whether a condition is satisfied can change a transport's usability, so that is
     * what participates. The condition identity is hashed too, so adding/removing a condition still
     * invalidates, and an expiring cooldown flips the verdict exactly once.
     */
    static int hashVarbitConditionVerdicts(int[] conditions) {
        return hashVarbitConditionVerdicts(conditions, Microbot::getVarbitValue);
    }

    /**
     * Varbit counterpart of {@link #hashVarplayerConditionVerdicts}. Same rule: only a flip in
     * whether a condition is satisfied can change a transport's usability, so a raw varbit moving
     * without changing any verdict must not invalidate the cache.
     *
     * @param varbitValueProvider injectable for tests; production reads the live varbit.
     */
    static int hashVarbitConditionVerdicts(int[] conditions,
            java.util.function.IntUnaryOperator varbitValueProvider) {
        if (conditions == null || conditions.length < 3) {
            return 1;
        }
        TransportVarbit.Operator[] operators = TransportVarbit.Operator.values();
        Map<Integer, Integer> actualValues = new HashMap<>();
        int h = 1;
        for (int i = 0; i + 2 < conditions.length; i += 3) {
            int id = conditions[i];
            int opOrdinal = conditions[i + 1];
            int value = conditions[i + 2];
            h = 31 * h + id;
            h = 31 * h + opOrdinal;
            h = 31 * h + value;
            boolean satisfied = false;
            if (opOrdinal >= 0 && opOrdinal < operators.length) {
                int actual = actualValues.computeIfAbsent(id, varbitValueProvider::applyAsInt);
                satisfied = new TransportVarbit(id, value, operators[opOrdinal]).matches(actual);
            }
            h = 31 * h + (satisfied ? 1 : 0);
        }
        return h;
    }

    static int hashVarplayerConditionVerdicts(int[] conditions) {
        return hashVarplayerConditionVerdicts(conditions, Microbot::getVarbitPlayerValue);
    }

    /** @param varplayerValueProvider injectable for tests; production reads the live varplayer. */
    static int hashVarplayerConditionVerdicts(int[] conditions,
            java.util.function.IntUnaryOperator varplayerValueProvider) {
        if (conditions == null || conditions.length < 3) {
            return 1;
        }
        TransportVarPlayer.Operator[] operators = TransportVarPlayer.Operator.values();
        Map<Integer, Integer> actualValues = new HashMap<>();
        int h = 1;
        for (int i = 0; i + 2 < conditions.length; i += 3) {
            int id = conditions[i];
            int opOrdinal = conditions[i + 1];
            int value = conditions[i + 2];
            h = 31 * h + id;
            h = 31 * h + opOrdinal;
            h = 31 * h + value;
            boolean satisfied = false;
            if (opOrdinal >= 0 && opOrdinal < operators.length) {
                int actual = actualValues.computeIfAbsent(id, varplayerValueProvider::applyAsInt);
                satisfied = new TransportVarPlayer(id, value, operators[opOrdinal]).matches(actual);
            }
            h = 31 * h + (satisfied ? 1 : 0);
        }
        return h;
    }

    private static int questStateHashCode(QuestState state) {
        if (state == null) {
            return -1;
        }
        switch (state) {
            case NOT_STARTED:
                return 0;
            case IN_PROGRESS:
                return 1;
            case FINISHED:
                return 2;
            default:
                return state.ordinal() + 3;
        }
    }

    private static Quest resolveQuestById(int questId) {
        for (Quest quest : Quest.values()) {
            if (quest.getId() == questId) {
                return quest;
            }
        }
        return null;
    }

    private static final class TransportRefreshSnapshot {
        private final int cacheKeyHash;
        private final int verificationHash;
        private final int[] sortedSkillOrdinals;
        private final int[] verificationComponents;
        private final int[] sortedVarbitConditions;
        private final int[] sortedVarplayerConditions;
        private final int[] sortedQuestIds;
        private final Map<WorldPoint, Set<Transport>> transportsData;
        private final Set<Transport> usableData;

        private TransportRefreshSnapshot(int cacheKeyHash, int verificationHash, int[] verificationComponents,
                int[] sortedSkillOrdinals,
                int[] sortedVarbitConditions, int[] sortedVarplayerConditions,
                int[] sortedQuestIds,
                Map<WorldPoint, Set<Transport>> transportsData, Set<Transport> usableData) {
            this.cacheKeyHash = cacheKeyHash;
            this.verificationHash = verificationHash;
            this.sortedSkillOrdinals = sortedSkillOrdinals;
            this.verificationComponents = verificationComponents;
            this.sortedVarbitConditions = sortedVarbitConditions;
            this.sortedVarplayerConditions = sortedVarplayerConditions;
            this.sortedQuestIds = sortedQuestIds;
            this.transportsData = transportsData;
            this.usableData = usableData;
        }

        static TransportRefreshSnapshot capture(int cacheKeyHash, int verificationHash, int[] verificationComponents,
                int[] sortedSkillOrdinals,
                int[] sortedVarbitConditions, int[] sortedVarplayerConditions,
                int[] sortedQuestIds,
                Map<WorldPoint, Set<Transport>> srcTransports, Set<Transport> srcUsable) {
            assert srcTransports != null && srcUsable != null;
            Map<WorldPoint, Set<Transport>> copy = new HashMap<>(srcTransports.size());
            for (Map.Entry<WorldPoint, Set<Transport>> e : srcTransports.entrySet()) {
                copy.put(e.getKey(), new HashSet<>(e.getValue()));
            }
            Set<Transport> usableCopy = new HashSet<>(srcUsable);
            return new TransportRefreshSnapshot(cacheKeyHash, verificationHash, verificationComponents, sortedSkillOrdinals, sortedVarbitConditions,
                    sortedVarplayerConditions, sortedQuestIds, copy, usableCopy);
        }

        void restoreInto(PathfinderConfig c) {
            c.transports.clear();
            c.transportsPacked.clear();
            c.usableTeleports.clear();
            for (Map.Entry<WorldPoint, Set<Transport>> e : transportsData.entrySet()) {
                WorldPoint wp = e.getKey();
                Set<Transport> set = new HashSet<>(e.getValue());
                c.transports.put(wp, set);
                c.transportsPacked.put(WorldPointUtil.packWorldPoint(wp), set);
            }
            c.usableTeleports.addAll(new HashSet<>(usableData));
        }
    }

    @Override
    public String toString() {
        return String.format("PathfinderConfig(useAgilityShortcuts=%b, useGrappleShortcuts=%b, useBoats=%b, useCanoes=%b, " +
                        "useCharterShips=%b, useShips=%b, useFairyRings=%b, useGnomeGliders=%b, useMinecarts=%b, " +
                        "useQuetzals=%b, useSpiritTrees=%b, useTeleportationLevers=%b, useTeleportationMinigames=%b, " +
                        "useTeleportationPortals=%b, useTeleportationSpells=%b, useMagicCarpets=%b, useWildernessObelisks=%b",
                useAgilityShortcuts, useGrappleShortcuts, useBoats, useCanoes,
                useCharterShips, useShips, useFairyRings, useGnomeGliders, useMinecarts,
                useQuetzals, useSpiritTrees, useTeleportationLevers, useTeleportationMinigames,
                useTeleportationPortals, useTeleportationSpells, useMagicCarpets, useWildernessObelisks);
    }
}
